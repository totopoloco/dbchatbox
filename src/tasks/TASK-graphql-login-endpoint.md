# TASK: GraphQL Login Endpoint (Keycloak password grant)

**Status:** Pending

> Adds `login`, `refreshToken`, and `me` to the GraphQL schema so the existing SPA can sign a user into
> a tenant and obtain a token. Implements
> [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md)
> § Human login and rules 86, 88.
>
> **Depends on:** [TASK-tenant-domain](./TASK-tenant-domain.md), [TASK-keycloak-resource-server-auth](./TASK-keycloak-resource-server-auth.md)
> (security + `AppUser`), [TASK-keycloak-devcontainer-realms](./TASK-keycloak-devcontainer-realms.md)
> (realms exist; `club-spa` has Direct Access Grants enabled).

---

## Problem

The frontend has a username/password login form, but the backend has no login operation — Phase 1
assumed an externally-resolved identity. We need a GraphQL mutation that signs the user into a specific
tenant's realm and returns tokens the SPA can use as `Authorization: Bearer`.

## Goal

`login(input: { tenantSlug, username, password })` returns an access token, refresh token, and the
caller's roles + tenant; `refreshToken(...)` renews it; `me` returns the resolved identity. The SPA can
log in as admin/member/trainer of any of the three tenants and call role-appropriate operations.

---

## Scope

- Extend `src/main/resources/graphql/schema.graphqls` with the auth types/operations.
- New `infrastructure.security.KeycloakAuthClient` (token-endpoint calls via `RestClient`).
- New GraphQL controller `infrastructure.web.graphql.AuthController` (+ `MeController` or fold `me` in).
- `login`/`refreshToken` are **public**; `me` requires authentication.

---

## Task List

### T1 — Schema additions

**File:** `src/main/resources/graphql/schema.graphqls`

```graphql
extend type Query {
  me: CurrentUser
  currentTenant: Tenant
}

extend type Mutation {
  login(input: LoginInput!): AuthPayload!
  refreshToken(input: RefreshTokenInput!): AuthPayload!
}

type AuthPayload {
  accessToken: String!
  refreshToken: String!
  expiresIn: Int!
  tokenType: String!
  tenant: Tenant!
  roles: [String!]!
}

input LoginInput        { tenantSlug: String!  username: String!  password: String! }
input RefreshTokenInput { tenantSlug: String!  refreshToken: String! }

type Tenant { id: ID!  slug: String!  name: String! }

type CurrentUser {
  subject: String!
  username: String!
  email: String
  tenant: Tenant!
  roles: [String!]!
  memberId: ID
  trainerId: ID
}
```

> Use `extend type Query`/`extend type Mutation` if the Phase 1 schema already declares them; otherwise
> add the fields to the existing definitions.

---

### T2 — `KeycloakAuthClient` (token endpoint)

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/KeycloakAuthClient.java`

Wraps the realm token endpoint using Spring's `RestClient` (no new dependency). Accepts a
pre-resolved `Tenant` so the caller (the controller) controls the single tenant lookup — this
avoids a redundant DB round-trip when `AuthController` would otherwise re-fetch the tenant for
the response payload.

```java
@Component
@RequiredArgsConstructor
public class KeycloakAuthClient {

    private final RestClient restClient = RestClient.create();

    @Value("${app.keycloak.base-url:http://localhost:8088}") private String baseUrl;
    @Value("${app.keycloak.spa-client-id:club-spa}")         private String spaClientId;

    /** OIDC password grant (Direct Access Grant) against the tenant's realm. */
    public TokenResponse passwordGrant(final Tenant tenant, final String username, final String password) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", spaClientId);
        form.add("username", username);
        form.add("password", password);
        return postToken(tenant, form);
    }

    public TokenResponse refresh(final Tenant tenant, final String refreshToken) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", spaClientId);
        form.add("refresh_token", refreshToken);
        return postToken(tenant, form);
    }

    private TokenResponse postToken(final Tenant tenant, final MultiValueMap<String, String> form) {
        final String url = baseUrl + "/realms/" + tenant.getKeycloakRealm()
                + "/protocol/openid-connect/token";
        return restClient.post().uri(url)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new BadCredentialsException("Keycloak token request failed: " + resp.getStatusCode());
            })
            .body(TokenResponse.class);
    }

    /** Maps Keycloak's token JSON. */
    public record TokenResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in")    int expiresIn,
        @JsonProperty("token_type")    String tokenType) {}
}
```

> Do not log credentials or tokens. Map Keycloak's `401` to a generic `BadCredentialsException` so the
> mutation returns a uniform "invalid credentials" error (no user enumeration).

---

### T3 — `AuthController` (GraphQL)

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/AuthController.java`

```java
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthClient keycloak;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;   // Spring Boot auto-configures this bean

    @MutationMapping
    public AuthPayload login(@Argument final LoginInput input) {
        final Tenant tenant = requireActiveTenant(input.tenantSlug());
        final var token = keycloak.passwordGrant(tenant, input.username(), input.password());
        return toPayload(tenant, token);
    }

    @MutationMapping
    public AuthPayload refreshToken(@Argument final RefreshTokenInput input) {
        final Tenant tenant = requireActiveTenant(input.tenantSlug());
        final var token = keycloak.refresh(tenant, input.refreshToken());
        return toPayload(tenant, token);
    }

    private AuthPayload toPayload(final Tenant tenant, final KeycloakAuthClient.TokenResponse t) {
        final List<String> roles = extractRealmRoles(t.accessToken());
        return new AuthPayload(t.accessToken(), t.refreshToken(), t.expiresIn(),
                               t.tokenType(), tenant, roles);
    }

    private Tenant requireActiveTenant(final String slug) {
        return tenantRepository.findBySlugAndActiveIsTrue(slug)
            .orElseThrow(() -> new BadCredentialsException("Unknown or inactive tenant: " + slug));
    }

    /**
     * Decodes the JWT payload (base64url) to read {@code realm_access.roles}.
     * The token was just minted by a trusted realm — re-verification is not needed here.
     * This is a convenience decode for the SPA response; authorization on subsequent requests
     * always re-validates the token via the resource server.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(final String accessToken) {
        try {
            final String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return List.of();
            }
            final String segment = parts[1];
            final int padding = (4 - segment.length() % 4) % 4;
            final byte[] decoded = Base64.getUrlDecoder().decode(segment + "=".repeat(padding));
            final Map<String, Object> claims = objectMapper.readValue(decoded, new TypeReference<>() {});
            final Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (isNull(realmAccess)) {
                return List.of();
            }
            return (List<String>) realmAccess.getOrDefault("roles", List.of());
        } catch (final Exception ignored) {
            return List.of();
        }
    }
}
```

`login`/`refreshToken` are reachable without authentication because `SecurityConfig` permits `/graphql`
at the HTTP layer and these methods carry **no** `@PreAuthorize`. (Every other operation does.)

---

### T4 — `me` / `currentTenant`

Add to a controller (e.g. `MeController` or `AuthController`). These require authentication; resolve the
caller from the current token + `AppUser` link:

```java
@PreAuthorize("isAuthenticated()")
@QueryMapping
public CurrentUser me() {
    final AppUser user = appUserService.currentUser();   // JIT-provisions AppUser if first login
    final Tenant tenant = tenantRepository.findById(user.getTenantId()).orElseThrow();
    return new CurrentUser(user.getKeycloakSubject(), user.getUsername(), user.getEmail(),
                           tenant, currentRoles(), user.getMemberId(), user.getTrainerId());
}

@PreAuthorize("isAuthenticated()")
@QueryMapping
public Tenant currentTenant() {
    return tenantRepository.findById(TenantContext.getTenantId()).orElseThrow();
}

/** Reads Spring authorities from the current security context, stripping the ROLE_ prefix. */
private static List<String> currentRoles() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (isNull(auth)) {
        return List.of();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> a.substring(5))
        .collect(Collectors.toList());
}
```

---

### T5 — Tests

**New file:** `src/test/java/at/mavila/dbchatbox/infrastructure/web/graphql/AuthControllerTest.java`

- `login` with valid demo credentials returns a non-empty `accessToken` and the expected `roles`
  (integration test against the running Keycloak, or mock `KeycloakAuthClient`).
- `login` with a wrong password surfaces a uniform credentials error (no enumeration).
- `login` with an unknown `tenantSlug` is rejected.
- `me` returns the caller's tenant/roles for a `jwt()`-authenticated test request and is rejected when
  unauthenticated.

---

## Security Notes

- **ROPC is a PoC choice (rule 86).** The password grant means the backend handles credentials. The
  production path is Authorization Code + PKCE in the SPA (Keycloak hosts login); this API would then
  only validate tokens and these mutations could be dropped. Document this at the top of `AuthController`.
- **Uniform errors.** Map all Keycloak auth failures to one generic error — never reveal whether the
  username exists or the tenant has that user.
- **No secrets in `login`.** `club-spa` is a public client; no client secret is involved in the password
  grant. Never put a client secret in code reachable by the frontend path.
- **Tokens are short-lived.** Rely on `refreshToken`; do not extend access-token lifetimes to avoid
  re-auth.

---

## Acceptance Criteria

- [ ] `login` signs into the correct realm for `tenantSlug` and returns access/refresh tokens, roles,
      and tenant.
- [ ] The returned access token is accepted by the resource server on subsequent requests (end-to-end:
      `login` → call a role-gated query with the token).
- [ ] `refreshToken` renews the access token.
- [ ] `me`/`currentTenant` return the resolved identity for an authenticated caller and are rejected
      otherwise.
- [ ] Wrong password / unknown tenant produce uniform errors (no enumeration).
- [ ] The frontend can log in as admin/member/trainer across all three tenants and see role-appropriate
      data.
- [ ] All new classes have Javadoc; `./gradlew test` passes.
