# TASK: OAuth2 Resource Server — Keycloak JWT, Roles, Tenant Resolution

**Status:** Pending

> Turns the backend into a multi-issuer OAuth2 resource server that validates Keycloak access tokens,
> maps realm roles to Spring authorities, resolves the tenant from the token issuer, and links the
> Keycloak subject to a `Member`/`Trainer`. Implements
> [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md)
> §§ Resource server, Role mapping, D2/D4 and rules 81–88.
>
> **Depends on:** [TASK-tenant-domain](./TASK-tenant-domain.md) (TenantContext, Tenant entity) and
> [TASK-keycloak-devcontainer-realms](./TASK-keycloak-devcontainer-realms.md) (realms exist).

---

## Goal

Every request carrying a valid Keycloak access token is authenticated with `ROLE_ADMIN`/`ROLE_MEMBER`/
`ROLE_TRAINER` derived from the token, with the tenant resolved from the issuer and placed in
`TenantContext`. Tokens from unknown issuers or with no resolvable tenant are rejected (fail closed). No
superuser exists.

---

## Scope

- Add Spring Security + OAuth2 resource server dependencies.
- New `infrastructure.security` classes: `SecurityConfig`, `KeycloakRealmRoleConverter`,
  `TenantAuthenticationManagerResolver`, `TenantResolutionFilter`.
- New `AppUser` domain (entity/repo/service) + JIT provisioning + `linkAppUser` mutation.
- Method security annotations on the existing GraphQL controllers.

---

## Task List

### T1 — Dependencies

**File:** `build.gradle`

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
// OAuth2 resource server — JWT validation against Keycloak JWKS.
// Confirm the exact Boot-4 coordinate against the BOM (Boot 4 renamed some starters;
// the repo already uses spring-boot-starter-webmvc / spring-boot-h2console / spring-boot-starter-flyway).
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
testImplementation 'org.springframework.security:spring-security-test'
```

> RestClient (for the login/M2M token calls in later tasks) is part of `spring-web`, already on the
> classpath via `spring-boot-starter-webmvc` — no extra dependency.

---

### T2 — Map Keycloak realm roles → authorities

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/KeycloakRealmRoleConverter.java`

Keycloak puts roles in `realm_access.roles`, not in `scope`. Convert them to `ROLE_*` authorities.

```java
public final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(final Jwt jwt) {
        final Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();
        final List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        return roles.stream()
            .map(r -> "ROLE_" + r)                    // ADMIN -> ROLE_ADMIN, M2M -> ROLE_M2M
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }
}
```

Wrap it in a `JwtAuthenticationConverter`:

```java
static JwtAuthenticationConverter jwtAuthenticationConverter() {
    final JwtAuthenticationConverter c = new JwtAuthenticationConverter();
    c.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return c;
}
```

---

### T3 — Multi-issuer authentication manager resolver

**New file:** `.../infrastructure/security/TenantAuthenticationManagerResolver.java`

The app trusts several issuers (one per realm). For each issuer, build a `JwtDecoder` (JWKS-validated,
plus issuer/expiry) and a `JwtAuthenticationProvider` using the converter from T2. Resolve the trusted
issuers from the `Tenant` table at startup; reject unknown issuers (spec rule 81).

```java
@Component
@RequiredArgsConstructor
public class TenantAuthenticationManagerResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {

    private final TenantRepository tenantRepository;
    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();

    private final JwtIssuerAuthenticationManagerResolver delegate =
        new JwtIssuerAuthenticationManagerResolver(
            (AuthenticationManagerResolver<String>) this::forIssuer);

    @Override
    public AuthenticationManager resolve(final HttpServletRequest request) {
        return delegate.resolve(request);
    }

    /** Build (once) a manager per trusted issuer; unknown issuer -> reject. */
    private AuthenticationManager forIssuer(final String issuer) {
        // Only build for issuers we actually host (rule 81).
        if (tenantRepository.findByIssuerUri(issuer).isEmpty()) {
            throw new InvalidBearerTokenException("Untrusted issuer: " + issuer);
        }
        return managers.computeIfAbsent(issuer, iss -> {
            final JwtDecoder decoder = JwtDecoders.fromIssuerLocation(iss);   // fetches JWKS + validators
            final JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
            provider.setJwtAuthenticationConverter(KeycloakRealmRoleConverter.jwtAuthenticationConverter());
            return provider::authenticate;
        });
    }
}
```

> `JwtIssuerAuthenticationManagerResolver` extracts the `iss` from the token and delegates to
> `forIssuer`. Building per-issuer providers lets each realm validate against its own JWKS while sharing
> the role converter. (If a Boot-4 API tweak is needed, the equivalent is a manual
> `AuthenticationManagerResolver<String>` keyed by issuer — same behaviour.)

---

### T4 — Resolve the tenant from the token and stamp `TenantContext`

**New file:** `.../infrastructure/security/TenantResolutionFilter.java`

A `OncePerRequestFilter` that runs **after** authentication: it reads the authenticated `Jwt`, looks up
the tenant by issuer, and sets `TenantContext`. Fail closed if the principal is a JWT but no active
tenant resolves (rules 80–82). Always clear `TenantContext` in `finally`.

```java
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                final String issuer = jwtAuth.getToken().getIssuer().toString();
                final Tenant tenant = tenantRepository.findByIssuerUri(issuer)
                    .filter(Tenant::isActive)
                    .orElse(null);
                if (tenant == null) {                       // unknown or inactive tenant -> deny (fail closed)
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unresolved tenant");
                    return;
                }
                TenantContext.setTenantId(tenant.getId());
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
```

> The API-key filter ([TASK-m2m-api-key](./TASK-m2m-api-key.md)) sets `TenantContext` on its own path;
> these two filters never both set it for one request (bearer wins — spec rule 94).

---

### T5 — `SecurityConfig`

**New file:** `.../infrastructure/security/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity            // enables @PreAuthorize on GraphQL controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantAuthenticationManagerResolver authManagerResolver;
    private final TenantResolutionFilter tenantResolutionFilter;
    // ApiKeyAuthenticationFilter is added in the M2M task; wire it here when present.

    @Bean
    SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http
          .csrf(csrf -> csrf.disable())                       // token/key auth, no cookies
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(reg -> reg
              // HTTP layer stays permissive; method security does the real gating.
              .requestMatchers("/graphql", "/graphiql", "/graphiql/**").permitAll()
              .requestMatchers("/actuator/health/**").permitAll()
              .anyRequest().permitAll())
          .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(authManagerResolver))
          .addFilterAfter(tenantResolutionFilter, BearerTokenAuthenticationFilter.class);
        // .addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class);  // M2M task
        return http;
    }
}
```

> Why permit-all at the HTTP layer: GraphQL is a single endpoint, so per-operation authorization must be
> method-level (`@PreAuthorize`), not URL-level. The resource server still authenticates every token; an
> unauthenticated call simply arrives with no authorities and is rejected by the per-method rules
> (except `login`/`refreshToken`, which are intentionally public — next task).

---

### T6 — `AppUser` domain + JIT provisioning + linking

**New files** under `at.mavila.dbchatbox.domain.club.identity`:

- `AppUser.java` — entity per spec § AppUser (extends `Auditable`, so it is tenant-scoped and gets
  `tenant_id` automatically). Unique on `(tenantId, keycloakSubject)`.
- `AppUserRepository.java` — `Optional<AppUser> findByTenantIdAndKeycloakSubject(Long, String)`.
- `AppUserService.java`:
  - `provisionOnLogin(Jwt jwt)` — find-or-create the `AppUser` for the current tenant + `sub`
    (JIT, rule 84). Called from `TenantResolutionFilter` (after tenant is set) or lazily from `me`.
  - `link(String subject, Long memberId, Long trainerId)` — admin links a subject to a `Member`/
    `Trainer` (both in the same tenant; enforce via `TenantScopedFinder`). Mutually exclusive (rule:
    spec § AppUser). Backs the `linkAppUser` mutation.
  - `currentUser()` — resolve the `AppUser` for the current token (tenant + `sub`); used by `me` and by
    the Phase 1 member/trainer self-isolation checks to obtain `memberId`/`trainerId`.

> This `memberId`/`trainerId` link is what makes Phase 1 rules 4 and 8 implementable under Keycloak: the
> token `sub` → `AppUser` → the member/trainer the caller *is*.

---

### T7 — Apply method security to the GraphQL controllers

**Files:** the existing controllers in `at.mavila.dbchatbox.infrastructure.web.graphql`
(`MemberController`, `MembershipController`, `SessionController`, `SubscriptionController`,
`PaymentController`, `TrainerController`, …).

Annotate each `@QueryMapping`/`@MutationMapping` method to match the **Phase 1 Operation Access Matrix**,
now expressed as Spring roles. Examples:

```java
@PreAuthorize("hasRole('ADMIN')")
@MutationMapping public Member createMember(@Argument CreateMemberInput input) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
@QueryMapping public Member memberById(@Argument String id) { ... }   // tenant + self-isolation in service

// Reads that machines may also call (spec § Machine-to-machine, rule 89):
@PreAuthorize("hasAnyRole('ADMIN','MEMBER','TRAINER','API_CLIENT')")
@QueryMapping public List<Session> sessions(@Argument String sessionType) { ... }
```

Rules of thumb (from the Phase 1 matrix):

- Admin-only mutations → `hasRole('ADMIN')`.
- Member/Trainer self-service → `hasAnyRole(...)` with the personal-data filter enforced in the service
  via the `AppUser` link (do **not** rely on the annotation alone for *whose* data).
- Public read catalog/aggregate queries → include `API_CLIENT` only where the M2M task says so (rule 89);
  never on mutations or `my*`.

> Tenant scoping is **not** in these annotations — it is enforced in the data layer (TenantContext +
> `TenantScopedFinder` from the tenant task). Annotations gate *role*; the data layer gates *tenant*.

---

## Security Notes

- **Fail closed on tenant.** A JWT that authenticates but resolves to no active tenant is rejected in
  `TenantResolutionFilter` — never allowed through with a null tenant (rules 81, 82).
- **No role invention.** Authorities are exactly the realm roles in the token; the backend never adds
  `ROLE_ADMIN`. A token with no known role can still call `me`/`currentTenant` only (rule 85, 88).
- **No superuser.** There is no authority that crosses tenants; `ADMIN` is always tenant-local (D3).
- **Clear `TenantContext`.** Always in a `finally`, to avoid leaking a tenant across pooled threads.
- **Stateless.** No sessions, CSRF disabled — auth is per-request via token/key only.

---

## Acceptance Criteria

- [ ] A valid Keycloak access token authenticates with `ROLE_<realm role>` and the tenant set in
      `TenantContext` from the issuer.
- [ ] A token from an issuer not in the `Tenant` table is rejected (`401`), even if well-formed.
- [ ] A token for an inactive tenant is rejected (rule 80).
- [ ] `me`/`currentTenant` work for any authenticated user; role-gated operations enforce the Phase 1
      matrix.
- [ ] `AppUser` is JIT-provisioned on first login; `linkAppUser` links a subject to a member/trainer in
      the same tenant; cross-tenant linking is impossible.
- [ ] Two different tenants' tokens calling the same query see disjoint data (integration test).
- [ ] All new classes have Javadoc; `./gradlew test` passes (Phase 1 tests green; new security tests
      using `spring-security-test` `jwt()` post-processors pass).
