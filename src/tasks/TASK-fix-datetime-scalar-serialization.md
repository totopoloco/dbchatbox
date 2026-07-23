# TASK: Fix DateTime Scalar Serialization for LocalDateTime Fields

**Spec reference:** `ClubManagement.md` → §10 Architecture Notes — GraphQL Configuration
**Status:** Complete

---

## Problem

Querying any `DateTime!` field through the GraphQL API throws:

```
Can't serialize value : Expected something we can convert to 'java.time.OffsetDateTime'
but was 'LocalDateTime'.
```

**Root cause:** `ExtendedScalars.DateTime` (registered in `ScalarConfiguration`) only serializes
`java.time.OffsetDateTime`. Every `DateTime!` field in the schema is backed by a `java.time.LocalDateTime`
in Java, causing a `DataFetchingException` at runtime for any query that includes one of these fields.

This was a latent bug on the pre-existing `changedAt`, `submittedAt`, `reviewedAt`, and `uploadedAt`
fields. It became observable now because `createdAt` was newly exposed in the schema.

---

## Affected Fields

| Schema type            | Field          | Java class                   | Java field     |
| ---------------------- | -------------- | ---------------------------- | -------------- |
| All 11 auditable types | `createdAt`    | `Auditable`                  | `createdAt`    |
| `MemberStatusEntry`    | `changedAt`    | `MemberStatusHistory`        | `changedAt`    |
| `TrainerLog`           | `submittedAt`  | `TrainerLog`                 | `submittedAt`  |
| `TrainerLog`           | `reviewedAt`   | `TrainerLog`                 | `reviewedAt`   |
| `PaymentDocument`      | `uploadedAt`   | `PaymentDocument`            | `uploadedAt`   |
| `DeleteMemberResult`   | `anonymizedAt` | `MemberGdprService` (record) | `anonymizedAt` |

---

## Fix

Replace `ExtendedScalars.DateTime` in `ScalarConfiguration` with a custom `GraphQLScalarType` that
handles both `LocalDateTime` and `OffsetDateTime`.

`ExtendedScalars.DateTime` is built on top of `graphql-java`'s scalar coercing API. A custom scalar
using the same ISO-8601 wire format but accepting `LocalDateTime` (treating it as system-local time,
i.e. no offset adjustment) is the least-invasive fix — it requires no entity changes, no service
changes, no Flyway migrations, and preserves the existing `DateTime` wire format for clients.

### T1 — Create `LocalDateTimeScalar`

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/LocalDateTimeScalar.java`

Implement a `GraphQLScalarType` named `"DateTime"` with a custom `Coercing<LocalDateTime, String>`:

- **Serialize** (`serialize`): if the value is `LocalDateTime`, return `value.toString()` (ISO-8601 without
  offset, e.g. `"2026-05-01T11:00:00"`). If the value is `OffsetDateTime`, return
  `value.toLocalDateTime().toString()` to keep backward compatibility.
- **Parse value** (`parseValue`): accept an ISO-8601 `String` and parse to `LocalDateTime` via
  `LocalDateTime.parse(value)`.
- **Parse literal** (`parseLiteral`): accept a `StringValue` AST node and parse the same way.

Annotate the class with `@Component` so it can be injected. Provide class-level Javadoc.

> **Note:** The scalar must be named `"DateTime"` to match the existing `scalar DateTime` declaration
> in the schema. No schema change is needed.

---

### T2 — Register the custom scalar in `ScalarConfiguration`

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/ScalarConfiguration.java`

- Inject `LocalDateTimeScalar` via constructor injection.
- In `runtimeWiringConfigurer()`, replace `.scalar(ExtendedScalars.DateTime)` with
  `.scalar(localDateTimeScalar.toGraphQLScalarType())` (or pass the bean directly if it implements
  `GraphQLScalarType`).

---

### T3 — Add GraphQL integration tests for DateTime fields

**File:** `src/test/java/at/mavila/dbchatbox/infrastructure/web/graphql/DateTimeScalarTest.java`

Using `ExecutionGraphQlServiceTester`, write tests that:

- Query `members { id createdAt }` and assert the returned `createdAt` is a non-null ISO-8601 string.
- Query `memberStatusHistory` (via `memberById { statusHistory { changedAt } }`) and assert
  `changedAt` is a non-null ISO-8601 string.
- Query `trainerLogs { submittedAt reviewedAt }` (for a trainer log with and without a `reviewedAt`)
  and assert serialization succeeds.

These tests catch any future regression where a `DateTime` field is backed by a type the scalar
cannot serialize.

---

## Acceptance Criteria

- [x] `members { createdAt }` (and any other `DateTime!` field) returns data without errors.
- [x] Serialized value is an ISO-8601 local date-time string (e.g. `"2026-05-01T11:05:32.123456"`).
- [x] `ExtendedScalars.DateTime` is no longer registered; the custom scalar handles the `DateTime`
      scalar name.
- [x] GraphQL integration tests for DateTime fields pass.
- [x] All existing tests (`./gradlew test`) continue to pass.
- [x] No entity, service, or Flyway migration files are changed.

---

## Execution order

T1 → T2 → T3

(T1 must exist before T2 can reference it; T3 can only run once T1 and T2 are wired together.)
