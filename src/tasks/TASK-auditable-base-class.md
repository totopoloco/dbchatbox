# TASK: Auditable Base Class Implementation

**Spec reference:** `ClubManagement.md` → §2 Auditable Base Class / §10 Auditable Base Class — JPA Lifecycle Callbacks
**Status:** Not started

---

## Goal

Add `createdAt` and `updatedAt` audit timestamps to all 11 mutable domain entities via a shared abstract
`@MappedSuperclass`. The fields must be populated automatically by JPA lifecycle callbacks — no manual
setting in application code. No GraphQL schema changes are required (fields are not exposed in Phase 1).

---

## Task List

### T1 — Create `Auditable` abstract base class

**File:** `src/main/java/at/mavila/dbchatbox/domain/support/Auditable.java`

- Declare as `public abstract class Auditable`.
- Annotate with `@MappedSuperclass` (JPA) and `@Getter` (Lombok) — no `@Setter`; fields are managed by
  lifecycle callbacks only.
- Declare `private LocalDateTime createdAt` with `@Column(name = "created_at", nullable = false, updatable = false)`.
- Declare `private LocalDateTime updatedAt` with `@Column(name = "updated_at", nullable = false)`.
- Implement `@PrePersist void prePersist()` — sets both `createdAt` and `updatedAt` to `LocalDateTime.now()`.
- Implement `@PreUpdate void preUpdate()` — sets only `updatedAt` to `LocalDateTime.now()`.
- No Spring beans, no `@Component`, no injected `Clock` — plain JPA managed class.
- One top-level class per file (project convention).

---

### T2 — Extend `Auditable` in the 11 mutable entities

For each entity below, add `extends Auditable` to the class declaration. No other changes needed —
the inherited fields and callbacks are sufficient.

| #   | Entity class          | Source file                                        |
| --- | --------------------- | -------------------------------------------------- |
| 1   | `Member`              | `domain/club/member/Member.java`                   |
| 2   | `MemberStatusHistory` | `domain/club/member/MemberStatusHistory.java`      |
| 3   | `MemberSubscription`  | `domain/club/subscription/MemberSubscription.java` |
| 4   | `MembershipType`      | `domain/club/membership/MembershipType.java`       |
| 5   | `Payment`             | `domain/club/payment/Payment.java`                 |
| 6   | `PaymentDocument`     | `domain/club/payment/PaymentDocument.java`         |
| 7   | `Session`             | `domain/club/training/Session.java`                |
| 8   | `SessionOccurrence`   | `domain/club/training/SessionOccurrence.java`      |
| 9   | `Trainer`             | `domain/club/trainer/Trainer.java`                 |
| 10  | `TrainerSettings`     | `domain/club/trainer/TrainerSettings.java`         |
| 11  | `TrainerLog`          | `domain/club/trainer/TrainerLog.java`              |

> **Note:** Reference/lookup tables (`Status`, `Unit`, `MembershipTypeStatus`, `SessionType`,
> `SessionOccurrenceStatus`, `TrainerLogStatus`, `TrainerPaymentMode`, `SubscriptionPaymentStatus`) and
> the join table `MembershipTypeSession` must **not** extend `Auditable`.

---

### T3 — Add Flyway migration `V6__add_audit_columns.sql`

**File:** `src/main/resources/db/migration/V6__add_audit_columns.sql`

Add `created_at` and `updated_at` columns to every auditable table. Use `DEFAULT NOW()` so that
existing rows are backfilled. Written in PostgreSQL syntax (H2 runs in `MODE=PostgreSQL`).

Tables to migrate (in dependency order to avoid FK constraint issues):

1. `member`
2. `member_status_history`
3. `member_subscription`
4. `membership_type`
5. `payment`
6. `payment_document`
7. `session`
8. `session_occurrence`
9. `trainer`
10. `trainer_settings`
11. `trainer_log`

Pattern per table:

```sql
ALTER TABLE <table>
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
```

---

### T4 — Write / update tests

#### T4a — Unit test for `Auditable` lifecycle callbacks

**File:** `src/test/java/at/mavila/dbchatbox/domain/support/AuditableTest.java`

- Use `@SpringBootTest` + `@Autowired` of any one auditable entity repository (e.g. `MemberRepository`).
- **Happy path — insert**: persist a new entity, reload it, assert `createdAt` and `updatedAt` are not
  null and are close to `LocalDateTime.now()`.
- **Happy path — update**: update the entity, reload it, assert `updatedAt` is strictly after the
  original `updatedAt` and `createdAt` is unchanged.
- **Immutability of `createdAt`**: assert that after an update `createdAt` equals the value recorded
  at insert time.

#### T4b — Verify all 11 entities compile and carry the fields

This is covered implicitly by the existing `@SpringBootTest` context load test
(`DbchatboxApplicationTests`) — if any entity is misconfigured, the context will fail to start.
No additional test class is required; confirm the existing test still passes after T1–T3.

---

## Acceptance Criteria

- [ ] `Auditable.java` exists in `domain/support`, is `abstract`, annotated `@MappedSuperclass`.
- [ ] All 11 mutable entities extend `Auditable`.
- [ ] No reference table or join table extends `Auditable`.
- [ ] `V6__add_audit_columns.sql` migrates all 11 tables; Flyway runs cleanly on H2 (dev/test profiles).
- [ ] `ddl-auto=validate` passes — Hibernate schema validation matches the migrated DB columns.
- [ ] After a persist + reload, `createdAt` and `updatedAt` are both populated.
- [ ] After an update + reload, `updatedAt` has advanced and `createdAt` is unchanged.
- [ ] All existing tests (`./gradlew test`) continue to pass.

---

## Execution order

T1 → T2 → T3 → T4
(T2 depends on T1 existing; T3 is independent of T1/T2 but must be in place before tests run against
a live DB context; T4 can only be written once T1–T3 are complete.)
