# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Common Commands

```bash
# Build (skip tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Run application (dev profile, H2 in-memory DB)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests ClassName

# Run a single test method
./gradlew test --tests ClassName.methodName

# Mutation testing
./gradlew pitest
```

The devcontainer sets `SPRING_PROFILES_ACTIVE=dev` automatically. The chatbox feature requires `ANTHROPIC_API_KEY` to be exported in your shell before running `bootRun`; without it, Spring AI logs a warning and chatbox calls fail at runtime.

## Architecture

This is a **GraphQL-only** club management system built with DDD layering. There are no REST endpoints.

```
src/main/java/at/mavila/dbchatbox/
├── domain/
│   ├── club/
│   │   ├── exception/      # Domain exceptions (MemberNotFound, DuplicateEmail, InvalidStatusTransition, ...)
│   │   ├── identity/       # AppUser, ApiKey + services (M2M key generation/validation)
│   │   ├── member/         # Member + status history + GDPR anonymization
│   │   ├── membership/     # MembershipType + grace period
│   │   ├── notification/   # NotificationService interface (logging-only mock)
│   │   ├── payment/        # Payment + PaymentDocument upload/review workflow
│   │   ├── subscription/   # MemberSubscription + SubscriptionPaymentStatus
│   │   ├── tenant/         # Tenant entity + TenantService (slug/issuer lookup)
│   │   ├── trainer/        # Trainer + TrainerSettings + TrainerLog
│   │   └── training/       # Session + SessionOccurrence
│   ├── chatbox/            # Natural-language assistant (Spring AI)
│   │   ├── exception/      # ChatRateLimitExceeded, ChatProviderUnavailable
│   │   └── tools/          # @Tool-annotated read wrappers over domain services
│   └── support/            # TSID generator, CommandValidator (Jakarta Bean Validation helper)
└── infrastructure/
    ├── ai/                 # ChatClient configuration + system prompt + Clock bean
    ├── security/           # SecurityConfig, filters (ApiKey, TenantResolution), TenantContext,
    │                       # TenantAuthManagerResolver, KeycloakAuthClient, KeycloakAdminClient,
    │                       # ApiKeyHmacService, KeycloakRealmRoleConverter
    ├── web/graphql/        # Per-entity controllers + AuthController, ApiKeyController,
    │                       # RealmMemberController, ChatAssistantController,
    │                       # GraphQlExceptionAdvice, ScalarConfiguration
    └── scheduling/         # GdprPurgeJob (daily 02:00)
```

**Request flow:** GraphQL controller (`@QueryMapping` / `@MutationMapping` / `@SchemaMapping`) → domain service → JPA repository. There is no intermediate application-service layer — controllers depend on domain services directly.

**Key files:**
- `src/main/resources/graphql/schema.graphqls` — all queries, mutations, scalars, and types
- `src/main/resources/application.properties` — GDPR cron (`app.gdpr.purge-cron`, `app.gdpr.retention-days=30`), payment/notification/chatbox/CORS settings
- `src/main/resources/application-{dev,test,prod}.properties` — profile-specific config
- `src/main/resources/db/migration/V1..V7_*.sql` — Flyway versioned migrations (`ddl-auto=validate`, so schema changes require a new migration). V7 adds the `tenant` and `api_key` tables and seeds three fixture tenants.
- `src/specs/club/ClubManagement.md` — Phase 1 product spec
- `src/specs/ClubManagement-Phase2-Multitenancy-Auth.md` — Phase 2 spec (Keycloak + multi-tenancy, implemented)
- `src/tasks/` — fine-grained implementation task files corresponding to Phase 2 spec sections
- `src/AI_PROMPT_PIPELINE.md` — spec-to-code pipeline guide with worked examples

**Tech stack:** Java 25 · Spring Boot 4.0.5 · Spring for GraphQL · JPA/Hibernate 7 · Flyway · H2 (dev/test) · PostgreSQL 16 (prod) · Lombok · TSID IDs · graphql-java-extended-scalars · Jakarta Bean Validation · Spring AI 2.0.0-M3 (Anthropic) for the chatbox · PIT mutation testing

**Entity base patterns:**
- All mutable entities extend `Auditable` (`@MappedSuperclass` providing `createdAt`, `updatedAt`, and `tenantId` via `@PrePersist`/`@PreUpdate`). `tenantId` is read from `TenantContext` at insert and is immutable thereafter — never accept it from the client.
- `Tenant` itself is the only entity that does **not** extend `Auditable` (it is the root of the tenant dimension and cannot be self-referentially scoped).
- IDs use `@Id @TsidGenerated private Long id;` — Hibernate generates TSID values before insert
- All entities carry `@Version private Short version;` for optimistic locking

**Multi-tenancy & security patterns:**
- `TenantContext` is a `ThreadLocal<Long>` set by the security filters before the GraphQL controller runs. Every JPA `@PrePersist` reads it; a `null` value throws `IllegalStateException`.
- **JWT path:** `BearerTokenAuthenticationFilter` → `TenantAuthenticationManagerResolver` (resolves an `OpaqueToken`/`NimbusJwtDecoder` per tenant's `issuer_uri`) → `TenantResolutionFilter` sets `TenantContext` from the validated JWT's `iss` claim.
- **API-key path:** `ApiKeyAuthenticationFilter` runs before the JWT filter, parses the `X-API-Key: cmk.<slug>.<raw>` header, HMAC-validates it via `ApiKeyHmacService`, sets both `SecurityContext` and `TenantContext`, and skips the JWT filter chain.
- Per-operation authorization uses `@PreAuthorize` on controller methods (e.g. `hasRole('ADMIN')`). The HTTP layer permits all `/graphql` paths — URL-level rules would only fire once, but GraphQL is a single endpoint.
- `ApiKey.keyHash` stores HMAC-SHA256 of the raw key (base64). The raw value is returned once at generation and never stored.

**Domain-specific patterns:**
- `Member` does not store a `status` field. Current status is always derived from the most recent `MemberStatusHistory` entry.
- GraphQL scalars: `Date` (LocalDate), `DateTime` (OffsetDateTime), `LocalTime`, `BigDecimal`, `Long` — registered in `ScalarConfiguration`. `LocalDateTimeScalar` is a custom implementation for backward compatibility.
- GraphiQL playground is available at `http://localhost:8080/graphql` (GET request).

## Adding a New Feature (Spec-Driven Pipeline)

Specs live in `src/specs/<category>/`. When implementing a spec, touch these layers in order:

1. **Domain** — `@Service`/`@Component` in `domain/club/<category>/`, command `record` with Jakarta Bean Validation annotations, custom exception in `domain/club/exception/` if needed. New entities must extend `Auditable` and use `@TsidGenerated`. If the schema changes, add a Flyway migration (next `V{n}__*.sql`).
2. **Infrastructure** — add type/query/mutation to `schema.graphqls`, add `@QueryMapping`/`@MutationMapping` to the matching per-entity controller (or create a new `XController`), add a `@GraphQlExceptionHandler` method to `GraphQlExceptionAdvice` for every new domain exception.
3. **Tests** — domain test (`@SpringBootTest`, AssertJ), GraphQL integration test via `ExecutionGraphQlServiceTester` with raw query documents.
4. **Docs** — Javadoc on every new/modified class and public method, update `README.md` if user-visible behavior changes.

## Coding Conventions

These are enforced by static analysis (CodeScene); violations are flagged as `Complex Method`, `Complex Conditional`, `Code Duplication`, `Excess Number of Function Arguments`.

### Null checks
Use static imports — never `== null` / `!= null`:
```java
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

if (isNull(input)) { ... }
```

### Method parameters
Public methods must have **≤ 3 parameters**. Group more into a command/parameter `record`:
```java
public record CreateMemberCommand(String firstName, String lastName, String email, ...) {}
public Member createMember(final CreateMemberCommand command) { ... }
```

### Input validation
Domain services call `commandValidator.validate(command)` as the **first line** of every public method that accepts a command record. Never add manual `if`/`throw` guards instead:
```java
public record MyParams(
    @NotNull(message = "X must not be null")
    @Positive(message = "X must be positive")
    Integer x
) {}
```
Each annotation on its own line, directly above the parameter it annotates.

### Guard clauses
Check the positive/happy-path condition and return early:
```java
// ✅
if (violations.isEmpty()) { return; }
throw violationMapper.toDomainException(violations);

// ❌ — negated condition wrapping the throw
if (!violations.isEmpty()) { throw ...; }
```

### Complex conditionals
Extract compound boolean expressions into named private predicate methods:
```java
// ✅
if (isScheduled(occurrence) && isWithinDateRange(occurrence, from, to)) { ... }

// ❌
if (occurrence.getStatus() == SCHEDULED && !occ.getDate().isBefore(from) && ...) { ... }
```

### One type per file
Every top-level class, enum, record, interface, or annotation in its own `.java` file. No inner types except `private` helpers used exclusively by the enclosing class.

### Other conventions
- Mark all method parameters and unre-assigned local variables `final`
- Prefer Stream API over imperative loops; use enhanced for-each when carrying mutable state
- Prefer `List.of(...)` / `Map.of(...)` for immutable literals
- String messages: use `"...".formatted(...)` or `String.format(...)` — no concatenation with `+`
- Use `var` only when the type is obvious from the right-hand side
- Target cyclomatic complexity ≤ 10 per method; decompose into private helpers
- Use text blocks (`"""..."""`) for multi-line strings in tests

## Testing Patterns

- **Tenant-aware integration tests** — any `@SpringBootTest` that persists or reads a tenant-owned entity must extend `TenantAwareIntegrationTest` (in `src/test/java/at/mavila/dbchatbox/`). It populates `TenantContext` with the WAT Simmering fixture tenant (id=1, seeded by V7) before each test and clears it afterwards. It also applies `@WithMockUser(roles = "ADMIN")` so `@PreAuthorize` passes; override at the method level when testing other roles.
- Domain tests: `@SpringBootTest` (full context, no mocks) using the `test` profile (H2), `@Autowired` the component under test, AssertJ assertions
- Unit tests that need mocking: `@Mock` + `@InjectMocks` via MockitoExtension
- GraphQL integration tests: `ExecutionGraphQlServiceTester` with raw GraphQL query documents. Extend `TenantAwareIntegrationTest` when the query touches tenant-scoped data; `@WithMockUser` on its own is not enough because `TenantResolutionFilter` does not fire for mock users.
- Test method naming: `test<Scenario>_<Condition>`
- Cover: happy paths, edge cases, boundary values, constraint violations, null/empty inputs

## Development Profiles

- **dev** — H2 in-memory (PostgreSQL-compatible mode), H2 console at `/h2-console`
- **test** — H2 in-memory, used automatically by `./gradlew test`
- **prod** — PostgreSQL 16
