# TASK: Machine-to-Machine Access via `x-api-key`

**Status:** Complete

> Implements the M2M mechanism in
> [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md)
> §§ D5, Machine-to-machine, and rules 89–94. A machine caller sends `x-api-key: <key>`; the backend
> resolves the tenant, authenticates against a hashed record (optionally backed by a Keycloak service
> account), and grants a **read-only, never-admin** principal.
>
> **This is the core experiment** of the PoC. Because real M2M business rules are not yet fixed, the
> design is presented as **Variant A (recommended, Keycloak-backed)** and **Variant B (fallback,
> local-only)** — build B first if you want the shortest path, then upgrade to A by changing only the
> filter.
>
> **Depends on:** [TASK-tenant-domain](./TASK-tenant-domain.md) (`api_key` table in V7, `TenantContext`),
> [TASK-keycloak-resource-server-auth](./TASK-keycloak-resource-server-auth.md) (`SecurityConfig`),
> [TASK-keycloak-devcontainer-realms](./TASK-keycloak-devcontainer-realms.md) (`club-m2m` client — for
> Variant A).

---

## Problem

Other machines (a nightly export job, a future mobile backend, a partner integration) need to call the
API without a human login, scoped to one tenant. We want the ergonomic `x-api-key` header that callers
expect, while keeping machine identities in Keycloak and never storing a usable key in our database.

## Goal

An admin can generate a tenant-scoped API key (shown once); a machine uses it via `x-api-key` to call
the tenant's **read-only, non-personal** queries; the key can be revoked; and an API-key principal can
**never** satisfy `hasRole('ADMIN')`.

---

## Scope

- `ApiKey` entity/repo/service in `at.mavila.dbchatbox.domain.club.identity` (table created by V7).
- `ApiKeyHmacService` (HMAC-SHA256 with a configured pepper).
- GraphQL `generateApiKey` / `revokeApiKey` mutations + `apiKeys` query (all ADMIN, tenant-scoped).
- `ApiKeyAuthenticationFilter` wired into `SecurityConfig` before the bearer filter.
- Method-security so API keys reach only the allowed read queries (rule 89).

---

## Task List

### T1 — `ApiKey` entity, repository, service

**New files** under `at.mavila.dbchatbox.domain.club.identity`:

- `ApiKey.java` — entity per spec § ApiKey (extends `Auditable` → tenant-scoped, gets `tenant_id`
  automatically; `key_hash` unique). Fields: `label`, `keyHash`, `keycloakClientId`, `scope` (default
  `READ`), `active`, `lastUsedAt`.
- `ApiKeyRepository.java`:

```java
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHashAndActiveIsTrue(String keyHash);
    List<ApiKey> findByTenantId(Long tenantId);
}
```

- `ApiKeyService.java`:
  - `generate(String label, String scope)` — ADMIN path: build the raw key, store only its hash, return
    the raw key once (see T3 for format). Sets `tenantId` from `TenantContext` (auto via `Auditable`),
    `keycloakClientId = club-m2m`, `active = true`.
  - `revoke(Long id)` — load via `TenantScopedFinder` (so an admin can only revoke their own tenant's
    keys), set `active = false`.
  - `list()` — `findByTenantId(TenantContext.getTenantId())`; **never** returns `keyHash`.
  - `authenticate(String rawKey)` — used by the filter (T4): hash, look up active key, return it or
    empty. Updates `lastUsedAt` best-effort.

---

### T2 — `ApiKeyHmacService`

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/ApiKeyHmacService.java`

HMAC-SHA256 the raw key with a configured pepper, Base64-encode for storage/lookup. (HMAC, not bcrypt,
because lookup must be deterministic — we query by hash. The pepper means a stolen DB alone cannot
verify guesses.)

```java
@Component
public class ApiKeyHmacService {

    private final byte[] pepper;

    public ApiKeyHmacService(@Value("${app.security.api-key.hmac-secret}") final String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.api-key.hmac-secret must be configured");
        }
        this.pepper = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(final String rawKey) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to HMAC the API key", e);
        }
    }
}
```

---

### T3 — `generateApiKey` / `revokeApiKey` / `apiKeys` (GraphQL)

**File:** `src/main/resources/graphql/schema.graphqls`

```graphql
extend type Query {
  apiKeys: [ApiKey!]!
}
extend type Mutation {
  generateApiKey(input: GenerateApiKeyInput!): GeneratedApiKey!
  revokeApiKey(id: ID!): ApiKey!
}

input GenerateApiKeyInput { label: String!  scope: String }   # scope defaults to "READ"

type GeneratedApiKey {        # returned ONCE, on creation
  id: ID!  label: String!  rawKey: String!  scope: String!  createdAt: DateTime!
}
type ApiKey {                  # listing — never exposes the hash or raw value
  id: ID!  label: String!  scope: String!  active: Boolean!  lastUsedAt: DateTime  createdAt: DateTime!
}
```

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/ApiKeyController.java`

```java
@Controller
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public GeneratedApiKey generateApiKey(@Argument GenerateApiKeyInput input) {
        return apiKeyService.generate(input.label(),
                                      input.scope() == null ? "READ" : input.scope());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public ApiKey revokeApiKey(@Argument String id) {
        return apiKeyService.revoke(Long.valueOf(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public List<ApiKey> apiKeys() {
        return apiKeyService.list();
    }
}
```

**Raw key format (in `ApiKeyService.generate`):** `cmk_<tenantSlug>_<random>`, where `<random>` is
≥ 32 bytes of `SecureRandom`, URL-safe Base64 (no padding). The tenant slug prefix lets the filter
resolve the tenant from the key without a DB hit on the hot path; the hash is still verified.

```java
final String raw = "cmk_" + tenant.getSlug() + "_" +
    base64Url(SecureRandom.getInstanceStrong(), 32);   // 256 bits of entropy
final ApiKey entity = new ApiKey();
entity.setLabel(label);
entity.setScope(scope);
entity.setKeycloakClientId("club-m2m");
entity.setKeyHash(hmac.hash(raw));     // store hash only — never `raw`
entity.setActive(true);
apiKeyRepository.save(entity);          // tenant_id auto-set by Auditable @PrePersist
return new GeneratedApiKey(entity.getId(), label, raw, scope, entity.getCreatedAt());
```

---

### T4 — `ApiKeyAuthenticationFilter`

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/ApiKeyAuthenticationFilter.java`

A `OncePerRequestFilter` placed **before** `BearerTokenAuthenticationFilter`. It **no-ops** when an
`Authorization` header is present (bearer wins — rule 94) or when `x-api-key` is absent. On a key, it
resolves the tenant, authenticates, and sets a restricted principal + `TenantContext`. **Fail closed.**

```java
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ApiKeyHmacService hmac;
    private final TenantRepository tenantRepository;

    @Value("${app.security.api-key.header:x-api-key}") private String headerName;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final String apiKey = req.getHeader(headerName);
        final boolean hasBearer = req.getHeader(HttpHeaders.AUTHORIZATION) != null;

        if (apiKey == null || apiKey.isBlank() || hasBearer) {
            chain.doFilter(req, res);                       // not an API-key request (rule 94)
            return;
        }

        try {
            // 1. Tenant from the key prefix: cmk_<tenantSlug>_<random>
            final String tenantSlug = parseTenantSlug(apiKey);    // null if malformed
            final Tenant tenant = (tenantSlug == null) ? null
                : tenantRepository.findBySlugAndActiveIsTrue(tenantSlug).orElse(null);
            if (tenant == null) {                                  // fail closed (rules 82, 93)
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }

            // 2. Verify against the stored hash (active key only)
            final Optional<ApiKey> match = apiKeyService.authenticate(apiKey);   // hashes + looks up
            if (match.isEmpty() || !tenant.getId().equals(match.get().getTenantId())) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }

            // 3. Restricted principal — ROLE_API_CLIENT + scope, NEVER admin (rule 90)
            final var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_API_CLIENT"),
                new SimpleGrantedAuthority("SCOPE_" + match.get().getScope()));   // e.g. SCOPE_READ
            final var authentication = new ApiKeyAuthenticationToken(
                tenant.getId(), match.get().getId(), authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 4. Tenant context for the data layer
            TenantContext.setTenantId(tenant.getId());

            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
```

**New file:** `.../infrastructure/security/ApiKeyAuthenticationToken.java` — a small
`AbstractAuthenticationToken` that carries `tenantId` and `apiKeyId`, is constructed `authenticated`,
and whose principal is the api-key id (a machine, not a user). It deliberately exposes **no** way to be
an admin (rule 90 is structural: `ROLE_ADMIN` is simply never in its authorities).

> **Variant A (recommended, Keycloak-backed).** In step 3, instead of (or in addition to) the local
> authorities, call `club-m2m` **client-credentials** against the tenant realm to obtain a JWT, derive
> authorities + tenant from that token (reusing the resource-server converter), and cache it until
> expiry. Keycloak then stays authoritative — disabling the `club-m2m` client or changing its roles
> takes effect on the next token. Add a `clientCredentials(tenantSlug)` method to `KeycloakAuthClient`
> (grant_type=client_credentials, client_id=club-m2m, client_secret from
> `app.keycloak.m2m-client-secret` resolved per realm). Keep the local `api_key` lookup as the gate that
> maps the opaque header to a tenant + active/revoked state.
>
> **Variant B (fallback).** Exactly the code above — authorize purely from the local `api_key` row. No
> Keycloak round-trip. `keycloakClientId` is still stored, so upgrading to A is a filter-only change.

---

### T5 — Wire the filter into `SecurityConfig`

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/SecurityConfig.java`
(from [TASK-keycloak-resource-server-auth](./TASK-keycloak-resource-server-auth.md))

```java
private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;   // inject

// inside filterChain(...), before the resource-server bearer filter:
http.addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class);
```

Order matters: the API-key filter runs first and no-ops when a bearer token is present, so the two auth
mechanisms never both populate the `SecurityContext` (rule 94).

---

### T6 — Authorize API keys onto the allowed read queries only

**Files:** the GraphQL query controllers (`MemberController`, `MembershipController`,
`SessionController`, `SubscriptionController`, `PaymentController`, `TrainerController`).

Add `API_CLIENT` to the role list of **only** the read-only, non-personal queries enumerated in spec
rule 89:

```java
@PreAuthorize("hasAnyRole('ADMIN','MEMBER','TRAINER','API_CLIENT')")
@QueryMapping public List<Session> sessions(@Argument String sessionType) { ... }

@PreAuthorize("hasAnyRole('ADMIN','API_CLIENT')")
@QueryMapping public List<MemberPaymentStatus> outstandingPayments() { ... }   // admin-or-machine read
```

Allowed for `API_CLIENT` (rule 89): `members`, `membershipTypes`, `sessions`, `sessionOccurrences`,
`trainers`, `outstandingPayments`, `overdueSubscriptions`. **Not** allowed: any mutation, and the
personal `my*` queries (`mySessions`, `myNextSession`, `myTrainerPaymentSummary`) — those require a human
subject and must exclude `API_CLIENT`. Tenant scoping still applies via `TenantContext` (a key only ever
sees its own tenant).

> Optional hardening: enforce the scope too, e.g. `hasRole('API_CLIENT') and hasAuthority('SCOPE_READ')`
> on machine-reachable reads, so a future `WRITE` scope is required for any future machine mutation.

---

### T7 — Tests

**New file:** `src/test/java/.../infrastructure/security/ApiKeyAuthenticationFilterTest.java`

- A valid active key authenticates with `ROLE_API_CLIENT` + `SCOPE_READ` and sets the right tenant.
- A revoked/inactive key → `401`.
- A malformed key or one whose tenant prefix is unknown/inactive → `401` (fail closed).
- A request with **both** `x-api-key` and `Authorization: Bearer` ignores the API key (bearer wins).
- **An API-key principal can never satisfy `hasRole('ADMIN')`** — assert a mutation/admin query is
  rejected for an `ApiKeyAuthenticationToken` (rule 90).
- `ApiKeyHmacService.hash` is stable for the same input and changes if the pepper changes.

---

## Security Notes

- **Never store the raw key.** Only the HMAC is persisted; the raw value is returned once and is
  unrecoverable (rule 91). A leaked database yields no usable keys (the pepper is not in it).
- **Never admin (rule 90).** The API-key token's authorities never include `ROLE_ADMIN`/HQ. This is
  structural, not data-dependent — do not add a code path that could elevate a machine.
- **Fail closed (rules 82, 93).** Missing/unknown/inactive tenant, missing/inactive key, or malformed
  key → reject. Null tenant is never "all tenants".
- **Bearer wins (rule 94).** Avoid ambiguous principals: if a human token is present, the API-key filter
  does nothing.
- **Clear context.** Clear both `TenantContext` and `SecurityContext` in `finally`.
- **Pepper is required.** The app must refuse to start if `app.security.api-key.hmac-secret` is unset
  (the `ApiKeyHmacService` constructor enforces this).
- **Reads only for the PoC.** Keep API keys read-only until real M2M write requirements exist; introduce
  a `WRITE` scope deliberately, not by default.

---

## Acceptance Criteria

- [ ] `generateApiKey` (ADMIN) returns a `cmk_<tenantSlug>_<random>` key **once**; only its hash is
      stored.
- [ ] `x-api-key` authenticates a machine as `ROLE_API_CLIENT` + `SCOPE_READ`, scoped to the key's
      tenant.
- [ ] An API key can call the allowed read queries (rule 89) and **only** within its tenant; it cannot
      call mutations or `my*` queries.
- [ ] An API-key principal can never satisfy `hasRole('ADMIN')` (test-proven, rule 90).
- [ ] `revokeApiKey` makes the next call with that key fail (rule 92).
- [ ] Missing/unknown/inactive tenant or key → `401` (fail closed); bearer-plus-key ignores the key.
- [ ] (If Variant A) a disabled `club-m2m` client / changed roles take effect on the next token.
- [ ] All new classes have Javadoc; `./gradlew test` passes.
