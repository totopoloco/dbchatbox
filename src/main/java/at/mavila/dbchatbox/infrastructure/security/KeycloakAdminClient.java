package at.mavila.dbchatbox.infrastructure.security;

import static java.util.Objects.isNull;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import at.mavila.dbchatbox.domain.club.exception.KeycloakAdminException;
import at.mavila.dbchatbox.domain.club.exception.MemberNotFoundException;
import at.mavila.dbchatbox.domain.club.member.MemberView;
import at.mavila.dbchatbox.infrastructure.web.graphql.RealmUser;

/**
 * HTTP client for the Keycloak Admin REST API.
 *
 * <p>Keycloak is the single source of truth for member identity. This client reads members (with
 * their custom attributes) and writes member create/update/anonymize operations on behalf of the
 * currently authenticated user. The caller's bearer token is read transparently from the injected
 * {@link BearerTokenHolder} — no token is threaded through method signatures.</p>
 *
 * <p>For reads the caller's Keycloak account must hold {@code realm-management/view-users}; for
 * writes it must additionally hold {@code realm-management/manage-users}. Both are granted to the
 * {@code ADMIN} fixture users in the realm imports.</p>
 *
 * @since 2026-06-28
 */
@Component
@Slf4j
public class KeycloakAdminClient {

    private static final String ROLE_MEMBERS_PATH = "/admin/realms/{realm}/roles/{role}/users";
    private static final String USERS_PATH = "/admin/realms/{realm}/users";
    private static final String USER_BY_ID_PATH = "/admin/realms/{realm}/users/{id}";
    private static final String USER_REALM_ROLES_PATH = "/admin/realms/{realm}/users/{id}/role-mappings/realm";
    private static final String USER_AVAILABLE_ROLES_PATH = "/admin/realms/{realm}/users/{id}/role-mappings/realm/available";
    private static final String MEMBER_ROLE = "MEMBER";

    private static final ParameterizedTypeReference<List<KeycloakUserRepresentation>> USER_LIST_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<KeycloakRoleRepresentation>> ROLE_LIST_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final BearerTokenHolder bearerTokenHolder;

    @Autowired
    public KeycloakAdminClient(final KeycloakProperties props, final BearerTokenHolder bearerTokenHolder) {
        this(RestClient.builder().baseUrl(props.getBaseUrl()).build(), bearerTokenHolder);
    }

    /** Package-private constructor accepting a pre-built {@link RestClient} — used by tests. */
    KeycloakAdminClient(final RestClient restClient, final BearerTokenHolder bearerTokenHolder) {
        this.restClient = restClient;
        this.bearerTokenHolder = bearerTokenHolder;
    }

    /**
     * Returns all users with the {@code MEMBER} role in the given realm as raw realm-user records
     * (the {@code realmMembers} admin dump).
     *
     * @param realm the Keycloak realm name (e.g. {@code wat-simmering})
     * @return list of realm users with the MEMBER role; empty if none
     * @throws KeycloakAdminException if Keycloak rejects the call with 401 or 403
     */
    public List<RealmUser> getMembersInRealm(final String realm) {
        return execute(() -> roleUsers(realm).stream().map(KeycloakUserRepresentation::toRealmUser).toList());
    }

    /**
     * Returns all {@code MEMBER}-role users in the given realm projected into {@link MemberView}s,
     * including their custom attributes (memberId, phoneNumber, membership dates).
     *
     * @param realm the Keycloak realm name
     * @return list of member views; empty if none
     * @throws KeycloakAdminException if Keycloak rejects the call with 401 or 403
     */
    public List<MemberView> listMemberViews(final String realm) {
        return execute(() -> roleUsers(realm).stream().map(KeycloakUserRepresentation::toMemberView).toList());
    }

    /**
     * Finds a member by their TSID via the {@code memberId} custom attribute.
     *
     * @param realm    the Keycloak realm name
     * @param memberId the member TSID
     * @return the matching member view
     * @throws MemberNotFoundException if no user carries that {@code memberId}
     * @throws IllegalStateException   if more than one user carries that {@code memberId}
     * @throws KeycloakAdminException  if Keycloak rejects the call with 401 or 403
     */
    public MemberView findByMemberId(final String realm, final Long memberId) {
        final List<KeycloakUserRepresentation> reps = execute(() -> restClient.get()
            .uri(b -> b.path(USERS_PATH)
                .queryParam("q", "memberId:" + memberId)
                .queryParam("exact", "true")
                .queryParam("briefRepresentation", "false")
                .build(realm))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(USER_LIST_TYPE));

        if (isNull(reps) || reps.isEmpty()) {
            throw new MemberNotFoundException(memberId);
        }
        if (reps.size() > 1) {
            throw new IllegalStateException(
                "Multiple Keycloak users share memberId %d in realm %s".formatted(memberId, realm));
        }
        return reps.getFirst().toMemberView();
    }

    /**
     * Loads a single user by their Keycloak id and projects it into a {@link MemberView}.
     *
     * @param realm           the Keycloak realm name
     * @param keycloakSubject the Keycloak user id ({@code sub})
     * @return the member view
     * @throws KeycloakAdminException if the user is missing or Keycloak rejects the call
     */
    public MemberView getUserById(final String realm, final String keycloakSubject) {
        final KeycloakUserRepresentation rep = execute(() -> restClient.get()
            .uri(b -> b.path(USER_BY_ID_PATH).build(realm, keycloakSubject))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(KeycloakUserRepresentation.class));

        if (isNull(rep)) {
            throw new KeycloakAdminException("Keycloak returned no user for id %s".formatted(keycloakSubject));
        }
        return rep.toMemberView();
    }

    /**
     * Creates a Keycloak user and assigns the {@code MEMBER} realm role, returning the new Keycloak
     * subject (UUID). Idempotent: if Keycloak reports a 409 conflict (the email already exists), the
     * existing user's id is returned instead so the caller can converge on the existing identity.
     *
     * @param realm the Keycloak realm name
     * @param req   the user creation request
     * @return the Keycloak subject (UUID) of the created or pre-existing user
     * @throws KeycloakAdminException if Keycloak rejects the call with 401 or 403, or returns no
     *                                Location header
     */
    public String createUser(final String realm, final KeycloakCreateUserRequest req) {
        try {
            final ResponseEntity<Void> resp = restClient.post()
                .uri(b -> b.path(USERS_PATH).build(realm))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .toBodilessEntity();
            final String userId = extractUserId(resp);
            assignMemberRole(realm, userId);
            return userId;
        } catch (final HttpClientErrorException.Conflict ex) {
            return findExistingUserIdByEmail(realm, req.email());
        } catch (final HttpClientErrorException.Forbidden ex) {
            throw forbidden();
        } catch (final HttpClientErrorException.Unauthorized ex) {
            throw unauthorized();
        }
    }

    /**
     * Updates a user's standard fields and replaces their custom attributes.
     *
     * @param realm           the Keycloak realm name
     * @param keycloakSubject the Keycloak user id ({@code sub})
     * @param req             the update request (full desired attribute set)
     * @throws KeycloakAdminException if Keycloak rejects the call with 401 or 403
     */
    public void updateUser(final String realm, final String keycloakSubject, final KeycloakUpdateUserRequest req) {
        executeVoid(() -> restClient.put()
            .uri(b -> b.path(USER_BY_ID_PATH).build(realm, keycloakSubject))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(req)
            .retrieve()
            .toBodilessEntity());
    }

    // ---------------------------------------------------------------
    // internal helpers
    // ---------------------------------------------------------------

    private List<KeycloakUserRepresentation> roleUsers(final String realm) {
        final List<KeycloakUserRepresentation> reps = restClient.get()
            .uri(b -> b.path(ROLE_MEMBERS_PATH)
                .queryParam("briefRepresentation", "false")
                .build(realm, MEMBER_ROLE))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(USER_LIST_TYPE);
        return isNull(reps) ? List.of() : reps;
    }

    private void assignMemberRole(final String realm, final String userId) {
        final List<KeycloakRoleRepresentation> available = restClient.get()
            .uri(b -> b.path(USER_AVAILABLE_ROLES_PATH).build(realm, userId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(ROLE_LIST_TYPE);
        if (isNull(available)) {
            return;
        }
        final KeycloakRoleRepresentation memberRole = available.stream()
            .filter(r -> MEMBER_ROLE.equals(r.name()))
            .findFirst()
            .orElse(null);
        if (isNull(memberRole)) {
            return;
        }
        restClient.post()
            .uri(b -> b.path(USER_REALM_ROLES_PATH).build(realm, userId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(List.of(memberRole))
            .retrieve()
            .toBodilessEntity();
    }

    private String findExistingUserIdByEmail(final String realm, final String email) {
        final List<KeycloakUserRepresentation> reps = execute(() -> restClient.get()
            .uri(b -> b.path(USERS_PATH)
                .queryParam("email", email)
                .queryParam("exact", "true")
                .queryParam("briefRepresentation", "false")
                .build(realm))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(USER_LIST_TYPE));
        if (isNull(reps) || reps.isEmpty()) {
            throw new KeycloakAdminException(
                "Keycloak reported a conflict creating %s but no existing user was found by email.".formatted(email));
        }
        return reps.getFirst().id();
    }

    private static String extractUserId(final ResponseEntity<Void> resp) {
        final URI location = resp.getHeaders().getLocation();
        if (isNull(location)) {
            throw new KeycloakAdminException("Keycloak did not return a Location header for the created user.");
        }
        final String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String bearer() {
        return "Bearer " + bearerTokenHolder.getToken();
    }

    private <T> T execute(final Supplier<T> call) {
        try {
            return call.get();
        } catch (final HttpClientErrorException.Forbidden ex) {
            throw forbidden();
        } catch (final HttpClientErrorException.Unauthorized ex) {
            throw unauthorized();
        }
    }

    private void executeVoid(final Runnable call) {
        execute(() -> {
            call.run();
            return null;
        });
    }

    private static KeycloakAdminException forbidden() {
        return new KeycloakAdminException(
            "The ADMIN account lacks the required Keycloak 'realm-management' client roles "
            + "(view-users / manage-users). Add them to the ADMIN users in the realm import.");
    }

    private static KeycloakAdminException unauthorized() {
        return new KeycloakAdminException("Keycloak Admin API rejected the token — it may have expired.");
    }

    private static String attr(final Map<String, List<String>> attrs, final String key) {
        if (isNull(attrs)) {
            return null;
        }
        final List<String> vals = attrs.get(key);
        return (isNull(vals) || vals.isEmpty()) ? null : vals.getFirst();
    }

    private static boolean isBlank(final String s) {
        return isNull(s) || s.isBlank();
    }

    private static Long parseLong(final String s) {
        return isBlank(s) ? null : Long.valueOf(s.trim());
    }

    private static LocalDate parseDate(final String s) {
        return isBlank(s) ? null : LocalDate.parse(s.trim());
    }

    private static OffsetDateTime parseOffset(final String s) {
        return isBlank(s) ? null : OffsetDateTime.parse(s.trim());
    }

    private static OffsetDateTime epochMillisToOffset(final Long ms) {
        return isNull(ms) ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
    }

    /**
     * Internal Jackson binding for the Keycloak UserRepresentation JSON.
     * Unknown fields are silently ignored so the binding stays stable
     * across Keycloak minor upgrades.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakUserRepresentation(
        @JsonProperty("id")               String id,
        @JsonProperty("username")         String username,
        @JsonProperty("firstName")        String firstName,
        @JsonProperty("lastName")         String lastName,
        @JsonProperty("email")            String email,
        @JsonProperty("enabled")          boolean enabled,
        @JsonProperty("createdTimestamp") Long createdTimestamp,
        @JsonProperty("attributes")       Map<String, List<String>> attributes
    ) {
        RealmUser toRealmUser() {
            return new RealmUser(id, username, firstName, lastName, email, enabled);
        }

        MemberView toMemberView() {
            return new MemberView(
                parseLong(attr(attributes, "memberId")),
                id,
                firstName,
                lastName,
                email,
                attr(attributes, "phoneNumber"),
                parseDate(attr(attributes, "memberSince")),
                parseDate(attr(attributes, "memberUntil")),
                epochMillisToOffset(createdTimestamp),
                parseOffset(attr(attributes, "updatedAt")));
        }
    }

    /**
     * Minimal Keycloak RoleRepresentation binding — only the fields needed to POST a realm
     * role-mapping back to Keycloak.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakRoleRepresentation(
        @JsonProperty("id")   String id,
        @JsonProperty("name") String name
    ) {
    }
}
