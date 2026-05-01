# TASK: Expose `createdAt` in the GraphQL API + Add Missing Documentation

**Spec reference:** `ClubManagement.md` → §2 Auditable Base Class / §10 Auditable Base Class — JPA Lifecycle Callbacks
**Status:** Complete

---

## Goal

Two related gaps from the `Auditable` implementation (committed in `e06921b`) need to be resolved:

1. `createdAt` must be added to all 11 auditable GraphQL output types so clients can see when a record
   was first created.
2. All code introduced or modified by the audit feature is missing field-level / method-level Javadoc
   and the `README.md` does not document audit timestamps at all.

`updatedAt` is intentionally **not** exposed in the schema — it remains an internal JPA audit field.

---

## Task List

### T1 — Add `createdAt: DateTime!` to all 11 auditable GraphQL output types

**File:** `src/main/resources/graphql/schema.graphqls`

Add `createdAt: DateTime!` as the last field of each of the 11 auditable entity output types. The
`DateTime` scalar is already registered (`graphql-java-extended-scalars`, mapped via
`ScalarConfiguration`).

| GraphQL output type  | Corresponding Java entity |
| -------------------- | ------------------------- |
| `Member`             | `Member`                  |
| `MemberStatusEntry`  | `MemberStatusHistory`     |
| `MembershipType`     | `MembershipType`          |
| `MemberSubscription` | `MemberSubscription`      |
| `Payment`            | `Payment`                 |
| `PaymentDocument`    | `PaymentDocument`         |
| `Session`            | `Session`                 |
| `SessionOccurrence`  | `SessionOccurrence`       |
| `Trainer`            | `Trainer`                 |
| `TrainerSettings`    | `TrainerSettings`         |
| `TrainerLog`         | `TrainerLog`              |

No changes to input types, resolvers, or data fetchers are needed — `Auditable.getCreatedAt()` is
already accessible via Lombok `@Getter` and Spring for GraphQL maps it automatically by field name.

---

### T2 — Add Javadoc to `Auditable.java` fields and lifecycle callback methods

**File:** `src/main/java/at/mavila/dbchatbox/domain/support/Auditable.java`

The class already has correct class-level Javadoc. What is missing:

- Field-level Javadoc for `createdAt` — describe what it is, when it is set, and that it is
  immutable after insert.
- Field-level Javadoc for `updatedAt` — describe what it is and when it is refreshed.
- Method-level Javadoc for `prePersist()` — describe the `@PrePersist` lifecycle callback and what
  it initializes.
- Method-level Javadoc for `preUpdate()` — describe the `@PreUpdate` lifecycle callback and what
  it updates.

---

### T3 — Update Javadoc in all 11 entity classes to mention inherited audit timestamps

**Files:** the 11 entity source files listed in the table above.

Each entity already has a class-level Javadoc comment. Add a `<p>` paragraph (or `@see` reference)
to each class Javadoc noting that the class inherits `createdAt` and `updatedAt` from
`{@link at.mavila.dbchatbox.domain.support.Auditable}`.

> **Scope:** only the class-level Javadoc block needs to change. Do not add Javadoc to individual
> fields or methods in the entity classes unless they are new or modified as part of this task.

---

### T4 — Update `README.md` to document audit timestamps

**File:** `README.md`

Add a dedicated sub-section under the existing **Domain Model** section (after the existing entity
list). The section should:

- Name the feature: "Audit Timestamps".
- State that all 11 mutable entities automatically carry `createdAt` and `updatedAt` via the
  `Auditable` abstract base class.
- Explain that `createdAt` is exposed in the GraphQL API as `DateTime!` and `updatedAt` is internal.
- Show a short GraphQL query example querying `createdAt` on one entity (e.g. `members`).

---

## Acceptance Criteria

- [x] `createdAt: DateTime!` is present on all 11 auditable GraphQL output types in `schema.graphqls`.
- [x] Querying `createdAt` on any auditable entity via GraphQL returns a non-null value.
- [x] `Auditable.java` has Javadoc on both fields and both lifecycle callback methods.
- [x] All 11 entity classes have an updated class-level Javadoc referencing `Auditable`.
- [x] `README.md` has a section describing audit timestamps with a GraphQL usage example.
- [x] All existing tests (`./gradlew test`) continue to pass.

---

## Execution order

T1 → T2 → T3 → T4

(T1 must come first so that any new integration tests can reference `createdAt` in GraphQL documents.
T2–T4 are independent of each other once T1 is in place and can be done in any order.)
