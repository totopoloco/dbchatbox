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

## Architecture

This is a **GraphQL-only** club management system built with DDD layering. There are no REST endpoints.

```
src/main/java/at/mavila/dbchatbox/
├── domain/club/           # Business logic, entities, repositories, services
│   ├── member/            # Member + GDPR anonymization
│   ├── membership/        # Subscription lifecycle
│   ├── trainer/           # Trainer entity + compensation settings
│   ├── training/          # Session + recurring SessionOccurrence
│   ├── payment/           # Payment tracking
│   └── exception/         # Domain exceptions
├── application/           # AlgorithmService — delegates to domain services, handles defaulting
└── infrastructure/
    ├── web/graphql/       # GraphQL controllers (@QueryMapping / @MutationMapping)
    └── scheduling/        # GDPR purge job (runs daily at 02:00)
```

**Request flow:** GraphQL controller → `AlgorithmService` (application layer) → domain service → JPA repository

**Key files:**
- `src/main/resources/graphql/schema.graphqls` — all queries, mutations, scalars, and types
- `src/main/resources/application.properties` — GDPR cron schedule and retention period (30 days)
- `src/main/resources/application-dev.properties` — H2 DB + H2 console (dev profile)
- `src/main/resources/db/migration/` — Flyway versioned migrations (V1, V2)

**Tech stack:** Java 25 · Spring Boot 4.0.5 · Spring for GraphQL · JPA/Hibernate 7 · Flyway · H2 (dev/test) · PostgreSQL 16 (prod) · Lombok · TSID IDs · graphql-java-extended-scalars · Jakarta Bean Validation · PIT mutation testing

## Adding a New Feature (Spec-Driven Pipeline)

Specs live in `src/specs/<category>/`. When implementing a spec, touch these layers in order:

1. **Domain** — `@Component` service in `domain/<category>/`, parameter `record` with Jakarta Bean Validation annotations, custom exception in `exception/` sub-package if needed
2. **Application** — add delegate method to `AlgorithmService`; handle null defaulting here, not in the domain
3. **Infrastructure** — add query/type to `schema.graphqls`, add `@QueryMapping` to `AlgorithmController`, add exception handler to `GraphQLExceptionHandler` if needed
4. **Tests** — domain test (`@SpringBootTest`, AssertJ), nested class in `AlgorithmServiceTest`, GraphQL integration test via `ExecutionGraphQlServiceTester`
5. **Docs** — Javadoc on every new/modified class and public method, update `README.md`

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
Domain services must **not** contain manual validation guards. Use Jakarta Bean Validation on parameter records:
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

- Domain tests: `@SpringBootTest` (full context, no mocks), `@Autowired` the component under test, AssertJ assertions
- Unit tests that need mocking: `@Mock` + `@InjectMocks` via MockitoExtension
- GraphQL integration tests: `ExecutionGraphQlServiceTester` with raw GraphQL query documents
- Test method naming: `test<Scenario>_<Condition>`
- Cover: happy paths, edge cases, boundary values, constraint violations, null/empty inputs

## Development Profiles

- **dev** — H2 in-memory (PostgreSQL-compatible mode), H2 console at `/h2-console`
- **prod** — PostgreSQL 16
