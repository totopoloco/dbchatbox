package at.mavila.dbchatbox.infrastructure.security;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import at.mavila.dbchatbox.domain.club.exception.KeycloakAdminException;
import at.mavila.dbchatbox.infrastructure.web.graphql.RealmUser;

/**
 * HTTP client for the Keycloak Admin REST API.
 *
 * <p>Calls are made on behalf of the currently authenticated user by forwarding
 * their bearer token. For this to succeed the caller's Keycloak account must hold
 * the {@code realm-management/view-users} client role in addition to the application
 * {@code ADMIN} realm role.</p>
 *
 * @since 2026-06-28
 */
@Component
@Slf4j
public class KeycloakAdminClient {

    private static final String ROLE_MEMBERS_PATH = "/admin/realms/{realm}/roles/{role}/users";
    private static final String MEMBER_ROLE = "MEMBER";

    private static final ParameterizedTypeReference<List<KeycloakUserRepresentation>> USER_LIST_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public KeycloakAdminClient(final KeycloakProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /**
     * Returns all users with the {@code MEMBER} role in the given realm.
     *
     * @param realm       the Keycloak realm name (e.g. {@code asv-pressbaum-badminton})
     * @param bearerToken the caller's raw JWT value; must carry {@code realm-management/view-users}
     * @return list of realm users with the MEMBER role; empty if none
     * @throws KeycloakAdminException if Keycloak rejects the call with 401 or 403
     */
    public List<RealmUser> getMembersInRealm(final String realm, final String bearerToken) {
        try {
            final List<KeycloakUserRepresentation> reps = restClient.get()
                .uri(ROLE_MEMBERS_PATH, realm, MEMBER_ROLE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(USER_LIST_TYPE);

            if (reps == null) {
                return List.of();
            }
            return reps.stream().map(KeycloakUserRepresentation::toRealmUser).toList();

        } catch (final HttpClientErrorException.Forbidden ex) {
            throw new KeycloakAdminException(
                "The ADMIN account lacks Keycloak 'realm-management/view-users' rights. "
                + "Add that client role to the ADMIN users in the realm import.");
        } catch (final HttpClientErrorException.Unauthorized ex) {
            throw new KeycloakAdminException(
                "Keycloak Admin API rejected the token — it may have expired.");
        }
    }

    /**
     * Internal Jackson binding for the Keycloak UserRepresentation JSON.
     * Unknown fields are silently ignored so the binding stays stable
     * across Keycloak minor upgrades.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakUserRepresentation(
        @JsonProperty("id")        String id,
        @JsonProperty("username")  String username,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName")  String lastName,
        @JsonProperty("email")     String email,
        @JsonProperty("enabled")   boolean enabled
    ) {
        RealmUser toRealmUser() {
            return new RealmUser(id, username, firstName, lastName, email, enabled);
        }
    }
}
