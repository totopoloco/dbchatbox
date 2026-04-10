# Club Management System — Phase 1

> Technical specification for digitizing the core administration of an Austrian sports club (_Verein_),
> replacing manual processes (Excel, WhatsApp, paper) with a centralized system exposed via GraphQL.

---

## Problem Statement

An Austrian sports club (_Verein_) is a permanent association of persons organized under statutes with
shared goals and registered members. Today, most administrative work is done manually — member lists in
spreadsheets, payment tracking on paper, training schedules via WhatsApp.

**Phase 1** replaces these manual processes with a digital system that answers fundamental questions:

- Who is a member?
- Who has paid?
- Until when is a membership valid?
- What sessions (training, free games, etc.) are available?
- When is my next session?
- How many hours have trainers worked?

### Scope

Phase 1 covers **five core domains**:

| Domain         | Responsibility                                                                                        |
| -------------- | ----------------------------------------------------------------------------------------------------- |
| **Member**     | Registration, contact details, status tracking                                                        |
| **Membership** | Membership types, pricing, duration, linked sessions                                                  |
| **Payment**    | Recording payments linked to member and membership type                                               |
| **Session**    | Scheduled sessions (training, free games, etc.), occurrences, calendar, availability                  |
| **Trainer**    | Hour submission, approval workflow, payment tracking (hourly rate, per-session or monthly settlement) |

Professional players (e.g. Bundesliga-level athletes) are treated as regular members in Phase 1.
A future phase may introduce dedicated player/team management with tournaments, statistics, and contracts.

### Primary Key Strategy — TSID

All entity primary keys use **TSID** (Time-Sorted Unique Identifier) stored as `Long` (64-bit).

**Rationale** (see [Database Primary Keys in 2026](https://medium.com/@mesfandiari77/database-primary-keys-in-2026-uuid-vs-tsid-vs-identity-auto-increment-e44e9fca68c1)):

- **B-tree friendly**: Time-sorted, so inserts are sequential — no page splitting or index fragmentation.
- **Compact**: 64-bit `Long` is half the size of a UUID (128-bit), faster for joins and indexes.
- **Distributed-safe**: Embeds timestamp + node/random component — collision-free across nodes.
- **URL-friendly**: Base-62 encoded representation is shorter than UUID dashes.
- **No IDOR risk**: Unlike auto-increment, IDs are not guessable.

**JPA mapping** via `hypersistence-utils`:

```java
@Id
@Tsid
private Long id;
```

In GraphQL, TSID IDs are exposed as `ID` scalar (serialized as `String`).

---

## Domain Model

### Member

Represents a **person's membership in the club** — their identity, contact details, and when they joined. This is the long-lived club-level relationship. What a member _participates in_ (amateurs, children, professional, etc.) is modeled separately via `MemberSubscription`.

| Field         | Type        | Constraints                                       |
| ------------- | ----------- | ------------------------------------------------- |
| `id`          | `Long`      | TSID, auto-generated, unique                      |
| `firstName`   | `String`    | Not null, not blank, max 100 characters           |
| `lastName`    | `String`    | Not null, not blank, max 100 characters           |
| `email`       | `String`    | Not null, valid email format, unique              |
| `phoneNumber` | `String`    | Optional, E.164 format recommended                |
| `memberSince` | `LocalDate` | Not null, must not be in the future               |
| `memberUntil` | `LocalDate` | Optional; if present, must be after `memberSince` |

**Note:** Status is **not** stored directly on the Member entity. See `Status` and `MemberStatusHistory` below. Membership types are **not** stored directly on the Member entity — see `MemberSubscription`.

**Business rules:**

- A member whose `memberUntil` date is in the past should be considered expired — queries must account for this.
- Email must be unique across all members (excluding anonymized `DELETED` records).
- **Soft-delete** (standard deactivation): record a status transition to `INACTIVE`. Does not remove personal data.
- **GDPR erasure** (right to be forgotten, Art. 17 DSGVO): a two-phase process. Phase 1 (immediate): the `deleteMember` mutation anonymizes all personal data in-place and sets status to `DELETED`. The row is preserved to maintain referential integrity. Phase 2 (deferred): a scheduled purge job hard-deletes anonymized member rows after a configurable retention period (default 30 days). See GDPR rules 52–61 below.
- A member can hold **zero, one, or many** active subscriptions simultaneously (e.g. amateur training + children coaching).

### Status (Reference Table)

A lookup table of all possible member statuses. Values are modeled as a **Java enum** (`Status`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`     |
| ---------- |
| `ACTIVE`   |
| `INACTIVE` |
| `DELETED`  |

Additional statuses can be added in the future (e.g. `SUSPENDED`, `PENDING`) without schema changes.

- `DELETED` is a terminal status set by the GDPR erasure process. Once a member is `DELETED`, no further status transitions are allowed.

### Unit (Reference Table)

A lookup table of time units used for membership duration. Values are modeled as a **Java enum** (`Unit`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`   |
| -------- |
| `DAYS`   |
| `WEEKS`  |
| `MONTHS` |
| `YEARS`  |

Additional units can be added in the future without schema changes.

### MembershipTypeStatus (Reference Table)

A lookup table of lifecycle statuses for membership types. Values are modeled as a **Java enum** (`MembershipTypeStatus`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`     | Description                                                                                                                                                        |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `DRAFT`    | Being set up — not yet available for subscriptions. Admin is configuring price, sessions, etc.                                                                     |
| `ACTIVE`   | Live — new subscriptions can be created against this membership type.                                                                                              |
| `INACTIVE` | Discontinued — no new subscriptions allowed, but existing active subscriptions run to their `endDate`. Payments against existing subscriptions are still accepted. |

Additional statuses can be added in the future without schema changes.

**Transitions:**

- `DRAFT → ACTIVE` — launch the membership type (make it available for subscriptions).
- `ACTIVE → INACTIVE` — discontinue (stop accepting new subscriptions).
- `INACTIVE → ACTIVE` — reactivate (start accepting new subscriptions again).
- `DRAFT → INACTIVE` — cancel before launch (allowed but atypical).
- Transitioning **to** `DRAFT` from `ACTIVE` or `INACTIVE` is **not allowed** — once launched, a type cannot return to draft.

### SessionType (Reference Table)

A lookup table of session categories. Values are modeled as a **Java enum** (`SessionType`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`      | Description                                         |
| ----------- | --------------------------------------------------- |
| `TRAINING`  | Coach-led training session with an assigned trainer |
| `FREE_GAME` | Open play / free game session — no trainer required |

Additional session types can be added in the future (e.g. `TOURNAMENT`, `WORKSHOP`) without schema changes.

**Type-specific constraints:**

- `TRAINING` sessions **require** a non-null `trainerId` on the `Session` entity.
- `FREE_GAME` sessions **must not** have a `trainerId` (it must be `null`).
- Future types will define their own constraints.

### SessionOccurrenceStatus (Reference Table)

A lookup table of lifecycle statuses for individual session occurrences. Values are modeled as a **Java enum** (`SessionOccurrenceStatus`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`      | Description                                                      |
| ----------- | ---------------------------------------------------------------- |
| `SCHEDULED` | The occurrence is planned and upcoming                           |
| `CANCELLED` | The occurrence was cancelled (e.g. holiday, trainer unavailable) |
| `COMPLETED` | The occurrence took place — trainers can log hours against it    |

Additional statuses can be added in the future without schema changes.

### TrainerLogStatus (Reference Table)

A lookup table of approval statuses for trainer hour submissions. Values are modeled as a **Java enum** (`TrainerLogStatus`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`     | Description                                                                                         |
| ---------- | --------------------------------------------------------------------------------------------------- |
| `PENDING`  | Hours submitted, awaiting admin approval                                                            |
| `APPROVED` | Hours approved by admin (or auto-approved) — trainer is entitled to payment                         |
| `REJECTED` | Hours rejected by admin (e.g. discrepancy, session not conducted as reported). Trainer can resubmit |

Additional statuses can be added in the future without schema changes.

**Transitions:**

- `PENDING → APPROVED` — admin approves submitted hours (or system auto-approves if `autoApproveHours` is `true` in the trainer's `TrainerSettings`).
- `PENDING → REJECTED` — admin rejects submitted hours with a reason.
- `REJECTED → PENDING` — trainer resubmits corrected hours (the existing log is updated, not duplicated).
- `APPROVED` is terminal — once approved, hours cannot be unapproved or modified.

### TrainerPaymentMode (Reference Table)

A lookup table of how trainers are compensated. Values are modeled as a **Java enum** (`TrainerPaymentMode`) in the domain layer and stored as `String` (`VARCHAR`) in the database using `@Enumerated(EnumType.STRING)`.

| Field  | Type     | Constraints                                    |
| ------ | -------- | ---------------------------------------------- |
| `id`   | `Long`   | TSID, auto-generated, unique                   |
| `name` | `String` | Not null, not blank, unique, max 50 characters |

**Seed data:**

| `name`        | Description                                                                                           |
| ------------- | ----------------------------------------------------------------------------------------------------- |
| `PER_SESSION` | Trainer is paid after each session (once hours are `APPROVED`)                                        |
| `MONTHLY`     | Trainer is paid monthly — approved hours are aggregated and settled at the end of each calendar month |

Additional payment modes can be added in the future without schema changes.

### MemberStatusHistory (Pivot Table)

Tracks every status transition for a member, providing a full audit trail.

| Field       | Type            | Constraints                             |
| ----------- | --------------- | --------------------------------------- |
| `id`        | `Long`          | TSID, auto-generated, unique            |
| `memberId`  | `Long`          | Not null, references Member             |
| `statusId`  | `Long`          | Not null, references Status             |
| `changedAt` | `LocalDateTime` | Not null, defaults to current timestamp |
| `reason`    | `String`        | Optional, max 500 characters            |

**Business rules:**

- When a member is created, an initial entry with status `ACTIVE` is recorded automatically.
- The **current status** of a member is the status in the most recent `MemberStatusHistory` entry (ordered by `changedAt` descending).
- Deactivating a member inserts a new row with status `INACTIVE`; it does not update or delete existing rows.
- This design preserves a full audit trail of all status changes.

### MemberSubscription

Links a member to a membership type for a **specific period**. One subscription = one period = one expected payment. When the period ends and the member wants to continue, the administrator creates a new subscription (renewal).

| Field              | Type         | Constraints                                                                                                                                 |
| ------------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`               | `Long`       | TSID, auto-generated, unique                                                                                                                |
| `memberId`         | `Long`       | Not null, references Member                                                                                                                 |
| `membershipTypeId` | `Long`       | Not null, references MembershipType                                                                                                         |
| `startDate`        | `LocalDate`  | Not null, when this subscription period begins                                                                                              |
| `endDate`          | `LocalDate`  | Not null, when this subscription period ends; must be after `startDate`. Defaults to `startDate + duration` (in the membership type's unit) |
| `agreedPrice`      | `BigDecimal` | Not null; the locked-in price for this subscription period, always populated at creation (see business rules below)                         |

**Derived state:** A subscription is considered **active** when `endDate >= today`. No separate boolean needed.

**Business rules:**

- A member can subscribe to **multiple** membership types at the same time (e.g. "Free Games" first, then adding "Training" when they want to improve). The total cost for the member is the sum of all active subscriptions.
- A member can have successive subscriptions to the **same** membership type (e.g. renewed annually). Each renewal is a distinct subscription row with its own period.
- `endDate` is always set. When the administrator does not provide it, the system computes it as `startDate + duration` (using the membership type's `duration` and `unit`). The administrator can override it to a different date.
- **Early termination**: The `endSubscription` mutation sets `endDate` to today if the current `endDate` is in the future.
- **Outstanding dues** for a subscription = `agreedPrice` − sum of all payments linked to that subscription.
- **Price resolution at creation**: `agreedPrice` is always populated when a subscription is created. The system resolves it in priority order: (1) explicit value provided by the admin, (2) auto-calculated prorated price if the membership type has `proratedMode = true`, (3) the membership type’s current `price`. Once stored, `agreedPrice` is immutable — it captures the price agreed at subscription time and is not affected by future changes to the membership type’s `price`.
- **Prorated pricing**: When a member joins mid-period (e.g. season starts November but member subscribes in March), the administrator can provide an explicit `agreedPrice` to a reduced amount. Alternatively, if the membership type has `proratedMode = true`, the system auto-calculates the prorated price (see MembershipType rules).
- Examples:
  - _Anna joined the club in 2010 with a "Free Games" subscription (€120/year) — she plays casual matches on weekends. In 2024 she decided she needed proper training, so she added a "Training" subscription (€360/year). She now pays €120 + €360 = €480/year across 2 subscriptions._
  - _The Training season runs November–October (€400, duration=1 YEARS). Karl joins in March. The admin creates a subscription with `startDate=2026-03-01`, `endDate=2026-10-31`, `agreedPrice=267.00`. Next season, a new subscription is created with the full €400._
  - _Children members subscribe to "Children" (training + free games). When they turn 18, that subscription ends and they may start an "Amateur" or "Training" subscription._

### MembershipType

| Field          | Type         | Constraints                                                                  |
| -------------- | ------------ | ---------------------------------------------------------------------------- |
| `id`           | `Long`       | TSID, auto-generated, unique                                                 |
| `name`         | `String`     | Not null, not blank, unique, max 100 characters                              |
| `description`  | `String`     | Optional, max 500 characters                                                 |
| `price`        | `BigDecimal` | Not null, positive (> 0)                                                     |
| `duration`     | `Integer`    | Not null, positive — number of time units for the period                     |
| `unitId`       | `Long`       | Not null, references Unit — the time unit for `duration`                     |
| `statusId`     | `Long`       | Not null, references MembershipTypeStatus                                    |
| `proratedMode` | `Boolean`    | Not null, default `false` — enables automatic proration for mid-period joins |

**Business rules:**

- `price` is the default price for one subscription period. When a subscription is created without an explicit `agreedPrice`, the system copies this value into the subscription's `agreedPrice` (or auto-prorates it if `proratedMode` is true).
- `duration` + `unit` define the **default period length**. When a subscription is created without an explicit `endDate`, the system computes `endDate = startDate + duration` (in the given unit). Examples: annual membership → `price=360.00, duration=1, unit=YEARS`; quarterly → `price=100.00, duration=3, unit=MONTHS`; 90-day pass → `price=80.00, duration=90, unit=DAYS`.
- A membership type can be referenced by many subscriptions across many members.
- When created, a membership type starts in `DRAFT` status. The administrator must explicitly activate it before subscriptions can be created.
- **New subscriptions** can only be created against `ACTIVE` membership types. `DRAFT` and `INACTIVE` types reject subscription attempts.
- **Existing subscriptions** are unaffected by status changes — if a membership type becomes `INACTIVE`, active subscriptions continue to their `endDate` and payments are still accepted.
- Deleting a membership type is only allowed if no subscriptions (active or historical) reference it.
- **Prorated mode**: When `proratedMode` is `true` and a subscription is created without an explicit `agreedPrice`, the system automatically calculates a prorated price based on the remaining time: `agreedPrice = price × (remaining_days / total_period_days)`, where `total_period_days` is the number of days in one full period (`duration` in the given `unit`) and `remaining_days` is the number of days from `startDate` to `endDate`. If the admin provides an explicit `agreedPrice`, it takes precedence over automatic proration. When `proratedMode` is `false` and the admin does not provide an explicit `agreedPrice`, the system copies the membership type’s current `price` into the subscription’s `agreedPrice`.

### MembershipTypeSession (Join Table)

Links which sessions are included in a membership type. A membership type can grant access to training sessions, free game sessions, or a mix of both.

| Field              | Type   | Constraints                                        |
| ------------------ | ------ | -------------------------------------------------- |
| `membershipTypeId` | `Long` | Not null, references MembershipType (composite PK) |
| `sessionId`        | `Long` | Not null, references Session (composite PK)        |

**Business rules:**

- A membership type can include many sessions (of any `SessionType`).
- A session can belong to many membership types.
- This is a pure join table with a composite primary key — no TSID needed.
- Membership type queries return associated sessions via this relationship.
- Members discover which sessions are available to them through: `MemberSubscription → MembershipType → MembershipTypeSession → Session`.

### Payment

| Field                  | Type         | Constraints                               |
| ---------------------- | ------------ | ----------------------------------------- |
| `id`                   | `Long`       | TSID, auto-generated, unique              |
| `memberSubscriptionId` | `Long`       | Not null, references a MemberSubscription |
| `amount`               | `BigDecimal` | Not null, positive (> 0)                  |
| `currency`             | `String`     | Not null, ISO 4217, default `EUR`         |
| `paymentDate`          | `LocalDate`  | Not null                                  |
| `notes`                | `String`     | Optional, max 500 characters              |

**Business rules:**

- A payment is linked to a **MemberSubscription**, which already captures the member and the membership type. No need to duplicate those foreign keys.
- A subscription can have multiple payments (e.g. partial payments).
- Outstanding dues per subscription = `agreedPrice` − sum of all payments.

### Session

Represents a **recurring weekly schedule slot** for any club activity — training, free games, or future session types. A session defines _when_ and _where_ an activity happens on a weekly basis. Individual dated instances are materialized as `SessionOccurrence` records.

| Field           | Type        | Constraints                                                                |
| --------------- | ----------- | -------------------------------------------------------------------------- |
| `id`            | `Long`      | TSID, auto-generated, unique                                               |
| `name`          | `String`    | Not null, not blank, max 150 characters                                    |
| `sessionTypeId` | `Long`      | Not null, references SessionType                                           |
| `dayOfWeek`     | `DayOfWeek` | Not null (MONDAY–SUNDAY)                                                   |
| `startTime`     | `LocalTime` | Not null                                                                   |
| `endTime`       | `LocalTime` | Not null, must be after `startTime`                                        |
| `location`      | `String`    | Not null, not blank, max 200 characters                                    |
| `trainerId`     | `Long`      | Conditional: **required** for `TRAINING`, **must be null** for `FREE_GAME` |

**Business rules:**

- Sessions recur weekly on the specified day.
- `endTime` must be strictly after `startTime` (no overnight sessions in Phase 1).
- A trainer can lead multiple sessions; a `TRAINING` session has exactly one trainer.
- `FREE_GAME` sessions have no trainer — they represent open court/field time available to members.
- **Trainer overlap validation**: When creating or updating a `TRAINING` session, the system must verify that the assigned trainer does not already have another session on the **same `dayOfWeek`** with an **overlapping time range** (`startTime`/`endTime`). Two sessions overlap if one's start is before the other's end and vice versa.
- **Location overlap validation**: No two sessions may be scheduled on the same `dayOfWeek`, at the same `location`, with overlapping time ranges. This prevents double-booking a court or field.
- The `sessionType` determines which validations apply and how the session is presented to members.

### SessionOccurrence

A **concrete, date-specific instance** of a `Session`. While `Session` defines the recurring weekly template, `SessionOccurrence` materializes each individual date on which the session actually takes place. This enables:

- Tracking every occurrence (past and future) for both training and free game sessions.
- Cancelling individual dates (e.g. holidays) without affecting the recurring template.
- Logging trainer hours against a specific occurrence.
- Showing members their upcoming schedule and next session reminder.

| Field       | Type        | Constraints                                  |
| ----------- | ----------- | -------------------------------------------- |
| `id`        | `Long`      | TSID, auto-generated, unique                 |
| `sessionId` | `Long`      | Not null, references Session                 |
| `date`      | `LocalDate` | Not null                                     |
| `statusId`  | `Long`      | Not null, references SessionOccurrenceStatus |
| `notes`     | `String`    | Optional, max 500 characters                 |

**Business rules:**

- The `date` must correspond to the `dayOfWeek` of the referenced `Session` (e.g. a Monday session can only have occurrences on Mondays).
- A session can have at most **one occurrence per date** — no duplicate (sessionId, date) pairs.
- Occurrences are created either individually or in **bulk** via a date-range + weekday pattern (see `createSessionOccurrences` mutation).
- New occurrences are created with status `SCHEDULED`.
- An occurrence can transition: `SCHEDULED → CANCELLED`, `SCHEDULED → COMPLETED`. `CANCELLED` and `COMPLETED` are terminal — no further transitions.
- **Cancellation**: Sets status to `CANCELLED`. Does not delete the row (preserves audit trail).
- **Completion**: Sets status to `COMPLETED`. Trainer hours can only be logged against `COMPLETED` occurrences.
- **Future occurrences**: Occurrences can be created for future dates (pre-scheduling a full season or semester).
- **Past occurrences**: Occurrences for past dates can be created retroactively (e.g. backfilling records) with status `COMPLETED`.
- Members see only `SCHEDULED` and `COMPLETED` occurrences for sessions linked to their active subscriptions. `CANCELLED` occurrences may be shown with a visual indicator but are excluded from the "next session" reminder logic.

### Trainer

Represents the **identity and contact details** of a trainer — who they are. Compensation and workflow settings are stored separately in `TrainerSettings` (see below).

| Field         | Type     | Constraints                             |
| ------------- | -------- | --------------------------------------- |
| `id`          | `Long`   | TSID, auto-generated, unique            |
| `firstName`   | `String` | Not null, not blank, max 100 characters |
| `lastName`    | `String` | Not null, not blank, max 100 characters |
| `email`       | `String` | Not null, valid email format, unique    |
| `phoneNumber` | `String` | Optional                                |

**Note:** Compensation fields (`hourlyRate`, `paymentMode`, `autoApproveHours`) are **not** stored on the Trainer entity. See `TrainerSettings` below.

**Business rules:**

- A trainer's core identity (name, email, phone) is managed via `createTrainer` / `updateTrainer`.
- A trainer always has exactly one associated `TrainerSettings` record, created automatically when the trainer is registered.
- Email must be unique across all trainers.

### TrainerSettings

Stores **compensation and workflow configuration** for a trainer — how they are paid and whether their hours require manual approval. This is a dedicated entity (separate table) with a **one-to-one** relationship to `Trainer`, ensuring a clean separation between identity and admin-managed settings.

| Field              | Type         | Constraints                                                                            |
| ------------------ | ------------ | -------------------------------------------------------------------------------------- |
| `id`               | `Long`       | TSID, auto-generated, unique                                                           |
| `trainerId`        | `Long`       | Not null, references Trainer, unique (one-to-one)                                      |
| `hourlyRate`       | `BigDecimal` | Not null, positive (> 0) — the trainer's rate per hour of work                         |
| `paymentModeId`    | `Long`       | Not null, references TrainerPaymentMode — how the trainer is compensated               |
| `autoApproveHours` | `Boolean`    | Not null, default `false` — if `true`, submitted hours are auto-approved by the system |

**Business rules:**

- `hourlyRate` is the agreed rate at which the trainer is compensated. Total payment for an approved log = `hourlyRate × hoursWorked`.
- `paymentMode` determines the settlement cadence: `PER_SESSION` means payment is due upon each approval; `MONTHLY` means approved hours are aggregated and settled at month end.
- `autoApproveHours`: When `true`, any `logTrainerHours` or `submitTrainerHours` call for this trainer sets the log status directly to `APPROVED` instead of `PENDING`. This is useful for trusted, long-standing trainers where the admin does not want to manually approve each session.
- When `autoApproveHours` is `false` (default), submitted hours start at `PENDING` and require explicit admin approval via the `approveTrainerLog` mutation.
- Settings are managed exclusively by admins via `updateTrainerSettings`. Trainers cannot modify their own settings.
- When a trainer is created via `createTrainer`, the initial settings (hourly rate, payment mode, auto-approve) are provided in the input and the system creates both the `Trainer` and its `TrainerSettings` in a single transaction.

> **Design note — entity attribute placement review:** All domain entities were reviewed for attributes that belong in a dedicated settings/configuration table vs. the core entity. The `Trainer` entity was the only case where admin-managed configuration (`hourlyRate`, `paymentMode`, `autoApproveHours`) was mixed with core identity fields (`firstName`, `lastName`, `email`, `phoneNumber`). These are now separated into `TrainerSettings`. Other entities (Member, MembershipType, Session, SessionOccurrence, Payment, MemberSubscription, TrainerLog) have all attributes integral to their identity or purpose — no further extraction is warranted. For `MembershipType`, the `proratedMode` flag was considered but kept in place because it defines a core behavioral characteristic of the type (how pricing works), not an independently manageable setting.

### TrainerLog (Training Hours)

| Field                 | Type            | Constraints                                                             |
| --------------------- | --------------- | ----------------------------------------------------------------------- |
| `id`                  | `Long`          | TSID, auto-generated, unique                                            |
| `trainerId`           | `Long`          | Not null, references a Trainer                                          |
| `sessionOccurrenceId` | `Long`          | Not null, references a SessionOccurrence                                |
| `hoursWorked`         | `BigDecimal`    | Not null, positive, max 24                                              |
| `statusId`            | `Long`          | Not null, references TrainerLogStatus — approval state of this entry    |
| `submittedAt`         | `LocalDateTime` | Not null, defaults to current timestamp — when the hours were submitted |
| `reviewedAt`          | `LocalDateTime` | Optional — when the admin approved or rejected (null while `PENDING`)   |
| `rejectionReason`     | `String`        | Optional, max 500 characters — reason provided by admin when rejecting  |
| `notes`               | `String`        | Optional, max 500 characters                                            |

**Business rules:**

- A trainer log entry records that a trainer conducted a specific session on a specific date (via the occurrence).
- The referenced `SessionOccurrence` must have status `COMPLETED`.
- The referenced `SessionOccurrence` must belong to a `Session` of type `TRAINING` with the same `trainerId` as this log entry.
- The `date` is derived from the `SessionOccurrence` — no separate date field needed on the log.
- Total hours per trainer can be aggregated by date range (using the occurrence's `date`).
- **Approval workflow**: New entries are created with status `PENDING` (or `APPROVED` if the trainer's `TrainerSettings.autoApproveHours` is `true`). The admin reviews pending entries and either approves or rejects them. Rejected entries can be resubmitted by the trainer (the log entry is updated in place — `hoursWorked` and `notes` are modified, status returns to `PENDING`, `rejectionReason` is cleared).
- **Trainer payment calculation**: For `APPROVED` entries, the owed amount = `trainerSettings.hourlyRate × hoursWorked`. The `trainerHours` query includes only `APPROVED` log entries in its aggregation. A separate `pendingTrainerLogs` query shows entries awaiting review.

---

## Entity Relationship Summary

```
Member
  │
  ├── MemberStatusHistory ── Status
  │
  └── MemberSubscription ──── MembershipType ── Unit
       │                          │            │
       │                          │         MembershipTypeStatus
       │                   MembershipTypeSession ── Session ── SessionType
       │                                               │
       │                                               ├── SessionOccurrence ── SessionOccurrenceStatus
       │                                               │        │
       └── Payment                                     │   TrainerLog ── TrainerLogStatus
                                                       │
                                                    Trainer
                                                       │
                                                  TrainerSettings ── TrainerPaymentMode
```

**Key relationships:**

- `Member` 1──N `MemberSubscription` N──1 `MembershipType` (a member can hold many subscriptions)
- `MemberSubscription` 1──N `Payment` (payments are per subscription)
- `MembershipType` M──N `Session` (via `MembershipTypeSession` join table)
- `MembershipType` N──1 `Unit` (each membership type has one time unit)
- `MembershipType` N──1 `MembershipTypeStatus` (each membership type has one lifecycle status)
- `Session` N──1 `SessionType` (each session has one type: TRAINING, FREE_GAME, etc.)
- `Session` N──0..1 `Trainer` (only TRAINING sessions have a trainer)
- `Session` 1──N `SessionOccurrence` (each session has many dated occurrences)
- `SessionOccurrence` N──1 `SessionOccurrenceStatus` (SCHEDULED, CANCELLED, COMPLETED)
- `TrainerLog` N──1 `SessionOccurrence` (trainer hours are logged per occurrence)
- `TrainerLog` N──1 `Trainer`
- `TrainerLog` N──1 `TrainerLogStatus` (PENDING, APPROVED, REJECTED)
- `Trainer` 1──1 `TrainerSettings` (each trainer has exactly one settings record)
- `TrainerSettings` N──1 `TrainerPaymentMode` (PER_SESSION, MONTHLY)
- `Member` 1──N `MemberStatusHistory` N──1 `Status`

---

## GraphQL Operations

### Queries

| Query                     | Arguments                                              | Returns                 | Description                                                                                   |
| ------------------------- | ------------------------------------------------------ | ----------------------- | --------------------------------------------------------------------------------------------- |
| `members`                 | `status: String`                                       | `[Member]`              | List all members, optionally filtered by current status                                       |
| `memberById`              | `id: ID!`                                              | `Member`                | Get a single member by ID (includes current status)                                           |
| `memberStatusHistory`     | `memberId: ID!`                                        | `[MemberStatusEntry]`   | Full status audit trail for a member                                                          |
| `memberSubscriptions`     | `memberId: ID!, active: Boolean`                       | `[MemberSubscription]`  | List subscriptions for a member, optionally filter by active state (`endDate >= today`)       |
| `membershipTypes`         | `status: String`                                       | `[MembershipType]`      | List membership types, optionally filtered by status (e.g. `ACTIVE`)                          |
| `paymentsBySubscription`  | `memberSubscriptionId: ID!`                            | `[Payment]`             | List all payments for a specific subscription                                                 |
| `paymentsByMember`        | `memberId: ID!`                                        | `[Payment]`             | List all payments across all subscriptions for a member                                       |
| `outstandingPayments`     | —                                                      | `[MemberPaymentStatus]` | Active subscriptions with unpaid dues                                                         |
| `sessions`                | `sessionType: String`                                  | `[Session]`             | List all sessions, optionally filtered by type (e.g. `TRAINING`, `FREE_GAME`)                 |
| `sessionOccurrences`      | `sessionId: ID, from: Date, to: Date, status: String`  | `[SessionOccurrence]`   | List occurrences for a session within a date range, optionally filtered by status             |
| `mySessions`              | `from: Date, to: Date, sessionType: String`            | `[SessionOccurrence]`   | Sessions available to the authenticated member via their active subscriptions                 |
| `myNextSession`           | `sessionType: String`                                  | `SessionOccurrence`     | The next upcoming `SCHEDULED` occurrence for the authenticated member (for frontend reminder) |
| `trainers`                | —                                                      | `[Trainer]`             | List all trainers                                                                             |
| `availableTrainers`       | `dayOfWeek: String!, startTime: Time!, endTime: Time!` | `[Trainer]`             | Trainers not assigned to any session overlapping the given day + time range                   |
| `trainerHours`            | `trainerId: ID!, from: Date, to: Date`                 | `TrainerHoursSummary`   | Total **approved** hours worked by a trainer in a date range                                  |
| `pendingTrainerLogs`      | `trainerId: ID`                                        | `[TrainerLog]`          | Pending hour submissions; admin sees all or filters by trainer; trainer sees only own         |
| `myTrainerPaymentSummary` | `from: Date!, to: Date!`                               | `TrainerPaymentSummary` | Approved hours × hourly rate for the authenticated trainer in a date range                    |

### Mutations

| Mutation                      | Input                                         | Returns               | Description                                                                   |
| ----------------------------- | --------------------------------------------- | --------------------- | ----------------------------------------------------------------------------- |
| `createMember`                | `CreateMemberInput!`                          | `Member`              | Register a new member (auto-creates ACTIVE status)                            |
| `updateMember`                | `id: ID!, UpdateMemberInput!`                 | `Member`              | Update member details                                                         |
| `changeMemberStatus`          | `ChangeMemberStatusInput!`                    | `MemberStatusEntry`   | Record a status transition with optional reason                               |
| `deleteMember`                | `id: ID!`                                     | `DeleteMemberResult`  | GDPR erasure: anonymize personal data, end subscriptions                      |
| `subscribeMember`             | `SubscribeMemberInput!`                       | `MemberSubscription`  | Subscribe a member to a membership type                                       |
| `endSubscription`             | `id: ID!`                                     | `MemberSubscription`  | Early termination (sets `endDate` to today if still in the future)            |
| `createMembershipType`        | `CreateMembershipTypeInput!`                  | `MembershipType`      | Define a new membership type (starts as `DRAFT`)                              |
| `changeMembershipTypeStatus`  | `id: ID!, status: String!`                    | `MembershipType`      | Transition membership type status (e.g. DRAFT → ACTIVE)                       |
| `assignSessionToMembership`   | `membershipTypeId: ID!, sessionId: ID!`       | `MembershipType`      | Link a session to a membership type                                           |
| `removeSessionFromMembership` | `membershipTypeId: ID!, sessionId: ID!`       | `MembershipType`      | Unlink a session from a membership type                                       |
| `recordPayment`               | `RecordPaymentInput!`                         | `Payment`             | Record a payment for a subscription                                           |
| `createSession`               | `CreateSessionInput!`                         | `Session`             | Create a recurring session (training or free game)                            |
| `createSessionOccurrences`    | `CreateSessionOccurrencesInput!`              | `[SessionOccurrence]` | Bulk-create occurrences for a session over a date range (calendar scheduling) |
| `cancelSessionOccurrence`     | `id: ID!`                                     | `SessionOccurrence`   | Cancel a specific occurrence (sets status to `CANCELLED`)                     |
| `completeSessionOccurrence`   | `id: ID!`                                     | `SessionOccurrence`   | Mark a specific occurrence as completed (sets status to `COMPLETED`)          |
| `createTrainer`               | `CreateTrainerInput!`                         | `Trainer`             | Register a new trainer (also creates initial `TrainerSettings`)               |
| `updateTrainer`               | `id: ID!, UpdateTrainerInput!`                | `Trainer`             | Update trainer contact details (name, email, phone)                           |
| `updateTrainerSettings`       | `trainerId: ID!, UpdateTrainerSettingsInput!` | `TrainerSettings`     | Update trainer compensation and workflow settings (admin-only)                |
| `logTrainerHours`             | `LogTrainerHoursInput!`                       | `TrainerLog`          | Admin directly logs hours (status set to `APPROVED`, bypasses approval flow)  |
| `submitTrainerHours`          | `SubmitTrainerHoursInput!`                    | `TrainerLog`          | Trainer submits hours (status `PENDING` or auto-approved per trainer setting) |
| `approveTrainerLog`           | `id: ID!`                                     | `TrainerLog`          | Approve a pending trainer log entry (sets status to `APPROVED`)               |
| `rejectTrainerLog`            | `RejectTrainerLogInput!`                      | `TrainerLog`          | Reject a pending trainer log entry with a reason                              |
| `resubmitTrainerLog`          | `ResubmitTrainerLogInput!`                    | `TrainerLog`          | Resubmit corrected hours after rejection (resets to `PENDING`)                |

---

## Input / Output Types

### MemberStatusEntry (Response/History Type)

| Field       | Type       | Description                        |
| ----------- | ---------- | ---------------------------------- |
| `id`        | `ID`       | TSID of the history entry          |
| `status`    | `String`   | Status name (e.g. `ACTIVE`)        |
| `changedAt` | `DateTime` | When the transition occurred       |
| `reason`    | `String`   | Optional reason for the transition |

### ChangeMemberStatusInput

| Field      | Type      | Constraints                                  |
| ---------- | --------- | -------------------------------------------- |
| `memberId` | `ID!`     | Not null, references a Member                |
| `status`   | `String!` | Not null, must match an existing Status name |
| `reason`   | `String`  | Optional, max 500 characters                 |

### CreateMembershipTypeInput

| Field          | Type          | Constraints                                                                      |
| -------------- | ------------- | -------------------------------------------------------------------------------- |
| `name`         | `String!`     | Not null, not blank, unique, max 100 characters                                  |
| `description`  | `String`      | Optional, max 500 characters                                                     |
| `price`        | `BigDecimal!` | Not null, positive (> 0)                                                         |
| `duration`     | `Integer!`    | Not null, positive — number of time units for the period                         |
| `unit`         | `String!`     | Not null, must match an existing Unit name (e.g. `DAYS`, `MONTHS`, `YEARS`)      |
| `proratedMode` | `Boolean`     | Optional, defaults to `false` — enables automatic proration for mid-period joins |

### SubscribeMemberInput

| Field              | Type         | Constraints                                                                                                                                                                                   |
| ------------------ | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `memberId`         | `ID!`        | Not null, references a Member                                                                                                                                                                 |
| `membershipTypeId` | `ID!`        | Not null, references a MembershipType                                                                                                                                                         |
| `startDate`        | `Date!`      | Not null, when the subscription period begins                                                                                                                                                 |
| `endDate`          | `Date`       | Optional; defaults to `startDate + duration` (in the membership type's unit). If provided, must be after `startDate`                                                                          |
| `agreedPrice`      | `BigDecimal` | Optional; if null, the system populates from prorated calculation (when `proratedMode` is true) or the membership type’s current `price`. If provided, this explicit value is stored directly |

### MemberPaymentStatus (Response Type)

| Field            | Type                 | Description                                                               |
| ---------------- | -------------------- | ------------------------------------------------------------------------- |
| `member`         | `Member`             | The member                                                                |
| `subscription`   | `MemberSubscription` | The active subscription                                                   |
| `membershipType` | `MembershipType`     | The membership type (via subscription)                                    |
| `amountDue`      | `BigDecimal`         | Expected amount for the current period (the subscription’s `agreedPrice`) |
| `amountPaid`     | `BigDecimal`         | Total amount paid for the current period                                  |
| `outstanding`    | `BigDecimal`         | `amountDue - amountPaid`                                                  |

### RecordPaymentInput

| Field                  | Type          | Constraints                               |
| ---------------------- | ------------- | ----------------------------------------- |
| `memberSubscriptionId` | `ID!`         | Not null, references a MemberSubscription |
| `amount`               | `BigDecimal!` | Not null, positive (> 0)                  |
| `currency`             | `String`      | Optional, defaults to `EUR`               |
| `paymentDate`          | `Date!`       | Not null                                  |
| `notes`                | `String`      | Optional                                  |

### TrainerHoursSummary (Response Type)

| Field          | Type         | Description                                                         |
| -------------- | ------------ | ------------------------------------------------------------------- |
| `trainer`      | `Trainer`    | The trainer                                                         |
| `totalHours`   | `BigDecimal` | Sum of **approved** hours worked in the queried range               |
| `sessionCount` | `Int`        | Number of approved sessions logged                                  |
| `totalOwed`    | `BigDecimal` | `totalHours × trainerSettings.hourlyRate` — total compensation owed |
| `from`         | `Date`       | Start of the queried range                                          |
| `to`           | `Date`       | End of the queried range                                            |

### DeleteMemberResult (Response Type)

| Field              | Type       | Description                              |
| ------------------ | ---------- | ---------------------------------------- |
| `memberId`         | `ID`       | The TSID of the anonymized member        |
| `anonymizedAt`     | `DateTime` | Timestamp when the erasure was performed |
| `fieldsAnonymized` | `[String]` | List of field names that were anonymized |

### CreateSessionInput

| Field         | Type      | Constraints                                                                      |
| ------------- | --------- | -------------------------------------------------------------------------------- |
| `name`        | `String!` | Not null, not blank, max 150 characters                                          |
| `sessionType` | `String!` | Not null, must match an existing SessionType name (e.g. `TRAINING`, `FREE_GAME`) |
| `dayOfWeek`   | `String!` | Not null (MONDAY–SUNDAY)                                                         |
| `startTime`   | `Time!`   | Not null                                                                         |
| `endTime`     | `Time!`   | Not null, must be after `startTime`                                              |
| `location`    | `String!` | Not null, not blank, max 200 characters                                          |
| `trainerId`   | `ID`      | Required if `sessionType` is `TRAINING`; must be null for `FREE_GAME`            |

### CreateSessionOccurrencesInput

Bulk-creates session occurrences for a date range. The backend generates one occurrence for each date in `[startDate, endDate]` that matches the session's `dayOfWeek`. This is designed to support a frontend calendar where the admin selects a date range (e.g. a semester or season) and the system fills in all weekly occurrences automatically.

| Field       | Type     | Constraints                                                                                    |
| ----------- | -------- | ---------------------------------------------------------------------------------------------- |
| `sessionId` | `ID!`    | Not null, references a Session                                                                 |
| `startDate` | `Date!`  | Not null, the start of the date range (inclusive)                                              |
| `endDate`   | `Date!`  | Not null, must be after `startDate`; the end of the date range (inclusive)                     |
| `skipDates` | `[Date]` | Optional; specific dates within the range to exclude (e.g. public holidays, club-closed dates) |

**Behavior:**

- The backend iterates from `startDate` to `endDate`, selecting every date that matches the session's `dayOfWeek`.
- Dates listed in `skipDates` are excluded.
- If an occurrence already exists for a (sessionId, date) pair, it is **skipped** (idempotent — no error, no duplicate).
- Returns the list of newly created occurrences (excludes already-existing ones).
- Example: Session is on `MONDAY`, `startDate=2026-09-01`, `endDate=2026-12-31`, `skipDates=["2026-12-28"]` → creates ~16 Monday occurrences (minus the skipped holiday).

### LogTrainerHoursInput

| Field                 | Type          | Constraints                                                      |
| --------------------- | ------------- | ---------------------------------------------------------------- |
| `trainerId`           | `ID!`         | Not null, references a Trainer                                   |
| `sessionOccurrenceId` | `ID!`         | Not null, references a SessionOccurrence with status `COMPLETED` |
| `hoursWorked`         | `BigDecimal!` | Not null, positive, max 24                                       |
| `notes`               | `String`      | Optional                                                         |

**Behavior:** Admin-only. Sets status directly to `APPROVED` (no approval workflow). Sets `submittedAt` and `reviewedAt` to current timestamp.

### SubmitTrainerHoursInput

| Field                 | Type          | Constraints                                                      |
| --------------------- | ------------- | ---------------------------------------------------------------- |
| `sessionOccurrenceId` | `ID!`         | Not null, references a SessionOccurrence with status `COMPLETED` |
| `hoursWorked`         | `BigDecimal!` | Not null, positive, max 24                                       |
| `notes`               | `String`      | Optional                                                         |

**Behavior:** The `trainerId` is inferred from the authenticated trainer (TRAINER role) or must be provided by admin. If the trainer's `TrainerSettings.autoApproveHours` is `true`, status is set to `APPROVED` and `reviewedAt` is populated. Otherwise, status is `PENDING`.

### RejectTrainerLogInput

| Field    | Type      | Constraints                                                  |
| -------- | --------- | ------------------------------------------------------------ |
| `id`     | `ID!`     | Not null, references a TrainerLog                            |
| `reason` | `String!` | Not null, max 500 characters — required reason for rejection |

### ResubmitTrainerLogInput

| Field         | Type          | Constraints                                              |
| ------------- | ------------- | -------------------------------------------------------- |
| `id`          | `ID!`         | Not null, references a TrainerLog with status `REJECTED` |
| `hoursWorked` | `BigDecimal!` | Not null, positive, max 24 — corrected hours             |
| `notes`       | `String`      | Optional — updated notes                                 |

**Behavior:** Updates the existing log entry in place: sets `hoursWorked` and `notes` to new values, clears `rejectionReason`, resets status to `PENDING` (or `APPROVED` if `TrainerSettings.autoApproveHours`), clears `reviewedAt`.

### CreateTrainerInput

| Field              | Type          | Constraints                                                             |
| ------------------ | ------------- | ----------------------------------------------------------------------- |
| `firstName`        | `String!`     | Not null, not blank, max 100 characters                                 |
| `lastName`         | `String!`     | Not null, not blank, max 100 characters                                 |
| `email`            | `String!`     | Not null, valid email format, unique                                    |
| `phoneNumber`      | `String`      | Optional                                                                |
| `hourlyRate`       | `BigDecimal!` | Not null, positive (> 0) — initial setting for `TrainerSettings`        |
| `paymentMode`      | `String!`     | Not null, must match TrainerPaymentMode (e.g. `PER_SESSION`, `MONTHLY`) |
| `autoApproveHours` | `Boolean`     | Optional, defaults to `false`                                           |

**Behavior:** Creates both the `Trainer` (identity) and its `TrainerSettings` (compensation/workflow) in a single transaction. The settings fields (`hourlyRate`, `paymentMode`, `autoApproveHours`) are stored in the `TrainerSettings` entity, not on the `Trainer` itself.

### UpdateTrainerInput

| Field         | Type     | Constraints                          |
| ------------- | -------- | ------------------------------------ |
| `firstName`   | `String` | Optional, max 100 characters         |
| `lastName`    | `String` | Optional, max 100 characters         |
| `email`       | `String` | Optional, valid email format, unique |
| `phoneNumber` | `String` | Optional                             |

**Behavior:** Updates only the trainer's **identity and contact details**. Both trainers (own record) and admins (any record) can use this mutation. Compensation and workflow settings are managed separately via `updateTrainerSettings`.

### UpdateTrainerSettingsInput

| Field              | Type         | Constraints                             |
| ------------------ | ------------ | --------------------------------------- |
| `hourlyRate`       | `BigDecimal` | Optional, positive (> 0)                |
| `paymentMode`      | `String`     | Optional, must match TrainerPaymentMode |
| `autoApproveHours` | `Boolean`    | Optional                                |

**Behavior:** Admin-only. Updates the trainer's compensation and workflow settings in the `TrainerSettings` entity. Trainers cannot modify their own settings — attempting to call this as a TRAINER returns an authorization error.

### TrainerPaymentSummary (Response Type)

| Field              | Type         | Description                                              |
| ------------------ | ------------ | -------------------------------------------------------- |
| `trainer`          | `Trainer`    | The trainer                                              |
| `from`             | `Date`       | Start of the queried range                               |
| `to`               | `Date`       | End of the queried range                                 |
| `approvedHours`    | `BigDecimal` | Sum of approved hours in the range                       |
| `approvedSessions` | `Int`        | Count of approved log entries                            |
| `hourlyRate`       | `BigDecimal` | The trainer's current hourly rate (from TrainerSettings) |
| `totalOwed`        | `BigDecimal` | `approvedHours × hourlyRate`                             |
| `paymentMode`      | `String`     | The trainer's payment mode (from TrainerSettings)        |
| `pendingHours`     | `BigDecimal` | Sum of hours in `PENDING` status (not yet approved)      |
| `pendingSessions`  | `Int`        | Count of pending log entries                             |

---

## Authorization & Roles

Phase 1 defines three roles. Authentication itself (login, tokens, identity provider) is out of scope for Phase 1 — the system assumes the caller's role is already resolved by the infrastructure layer.

| Role        | Description                                                                                                                             |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| **ADMIN**   | Club administrator. Full access to all operations — manages members, membership types, subscriptions, payments, sessions, and trainers. |
| **MEMBER**  | A registered club member. Can view their own data (profile, subscriptions, payments, training schedule).                                |
| **TRAINER** | A registered trainer. Can submit their own hours, view their own sessions and log history, and view their payment summary.              |

### Operation Access Matrix

Each GraphQL operation is restricted to one or more roles. Operations not listed are accessible to both roles.

#### Queries

| Query                     | ADMIN | MEMBER | TRAINER | Notes                                                                     |
| ------------------------- | :---: | :----: | :-----: | ------------------------------------------------------------------------- |
| `members`                 |   ✓   |        |         | Admin-only — list all members                                             |
| `memberById`              |   ✓   |   ✓    |         | Members can only query their own ID                                       |
| `memberStatusHistory`     |   ✓   |   ✓    |         | Members can only view their own history                                   |
| `memberSubscriptions`     |   ✓   |   ✓    |         | Members can only view their own subscriptions                             |
| `membershipTypes`         |   ✓   |   ✓    |         | Members see only `ACTIVE` types; admins see all statuses                  |
| `paymentsBySubscription`  |   ✓   |   ✓    |         | Members can only view payments for their own subscriptions                |
| `paymentsByMember`        |   ✓   |   ✓    |         | Members can only query their own payments                                 |
| `outstandingPayments`     |   ✓   |        |         | Admin-only — aggregated view across all members                           |
| `sessions`                |   ✓   |   ✓    |    ✓    | Read-only for all roles                                                   |
| `sessionOccurrences`      |   ✓   |   ✓    |    ✓    | Read-only for all roles; members/trainers can browse all occurrences      |
| `mySessions`              |   ✓   |   ✓    |    ✓    | Members: via subscriptions; trainers: sessions they are assigned to       |
| `myNextSession`           |   ✓   |   ✓    |    ✓    | Next upcoming session for the authenticated member or trainer             |
| `trainers`                |   ✓   |   ✓    |    ✓    | Read-only for all roles                                                   |
| `availableTrainers`       |   ✓   |        |         | Admin-only — used when creating TRAINING sessions                         |
| `trainerHours`            |   ✓   |        |    ✓    | Admin sees any trainer; trainers see only their own                       |
| `pendingTrainerLogs`      |   ✓   |        |    ✓    | Admin sees all pending; trainers see only their own pending submissions   |
| `myTrainerPaymentSummary` |   ✓   |        |    ✓    | Trainer's payment summary (approved hours × hourly rate) for a date range |

#### Mutations

| Mutation                      | ADMIN | MEMBER | TRAINER | Notes                                                                                     |
| ----------------------------- | :---: | :----: | :-----: | ----------------------------------------------------------------------------------------- |
| `createMember`                |   ✓   |        |         | Admin registers new members                                                               |
| `updateMember`                |   ✓   |   ✓    |         | Members can update their own contact details (email, phone); admin can update any member  |
| `changeMemberStatus`          |   ✓   |        |         | Admin-only — status transitions                                                           |
| `deleteMember`                |   ✓   |   ✓    |         | Members can request their own GDPR erasure; admin can erase any member                    |
| `subscribeMember`             |   ✓   |        |         | Admin-only — controls who subscribes and at what price                                    |
| `endSubscription`             |   ✓   |        |         | Admin-only — early termination                                                            |
| `createMembershipType`        |   ✓   |        |         | Admin-only                                                                                |
| `changeMembershipTypeStatus`  |   ✓   |        |         | Admin-only                                                                                |
| `assignSessionToMembership`   |   ✓   |        |         | Admin-only                                                                                |
| `removeSessionFromMembership` |   ✓   |        |         | Admin-only                                                                                |
| `recordPayment`               |   ✓   |        |         | Admin-only — member payment recording                                                     |
| `createSession`               |   ✓   |        |         | Admin-only — create session templates                                                     |
| `createSessionOccurrences`    |   ✓   |        |         | Admin-only — bulk calendar scheduling                                                     |
| `cancelSessionOccurrence`     |   ✓   |        |         | Admin-only — cancel individual occurrences                                                |
| `completeSessionOccurrence`   |   ✓   |        |         | Admin-only — mark occurrences as completed                                                |
| `createTrainer`               |   ✓   |        |         | Admin-only — creates trainer + initial TrainerSettings                                    |
| `updateTrainer`               |   ✓   |        |    ✓    | Trainers update own contact details; admin updates any trainer                            |
| `updateTrainerSettings`       |   ✓   |        |         | Admin-only — update compensation and workflow settings                                    |
| `updateTrainerSettings`       |   ✓   |        |         | Admin-only — update compensation and workflow settings                                    |
| `logTrainerHours`             |   ✓   |        |         | Admin-only — directly log hours (bypasses approval if desired)                            |
| `submitTrainerHours`          |   ✓   |        |    ✓    | Trainer submits own hours; starts as PENDING (or auto-approved). Admin can submit for any |
| `approveTrainerLog`           |   ✓   |        |         | Admin-only — approve pending hours                                                        |
| `rejectTrainerLog`            |   ✓   |        |         | Admin-only — reject pending hours with a reason                                           |
| `resubmitTrainerLog`          |   ✓   |        |    ✓    | Trainer resubmits corrected hours after rejection. Admin can do for any                   |

> **Note:** `updateTrainer` has no MEMBER column entry because members and trainers are independent roles — a member is not a trainer. `updateTrainerSettings` is admin-only with no trainer or member access.

### Authorization Rules

1. **Admin-only pricing**: Only an admin can set or override `agreedPrice` when creating a subscription. This ensures that proration decisions are made by the club, not by the member.
2. **Admin-only subscription management**: Only an admin can create, renew, or terminate subscriptions. Members cannot self-subscribe or self-terminate in Phase 1.
3. **Member self-service scope**: A member can view their own profile, subscriptions, payment history, and their available session schedule. They can update their own contact details (email, phone number) and request their own GDPR erasure.
4. **Member data isolation**: When a member queries `memberById`, `memberSubscriptions`, `paymentsByMember`, or `paymentsBySubscription`, the system enforces that the requested data belongs to the authenticated member. Attempting to access another member's data returns an authorization error.
5. **Membership type visibility**: Members see only `ACTIVE` membership types via `membershipTypes`. `DRAFT` and `INACTIVE` types are hidden from members — they are administrative concerns.
6. **Session visibility for members**: Members can see all sessions and occurrences via `sessions`/`sessionOccurrences` (public schedule), but `mySessions` and `myNextSession` filter to only sessions linked to the member's active subscriptions. Attendance is voluntary — the system only informs, it does not enforce.
7. **Trainer self-service scope**: A trainer can submit their own hours (`submitTrainerHours`), view their own pending/approved/rejected logs (`pendingTrainerLogs`, `trainerHours`), view their payment summary (`myTrainerPaymentSummary`), view their assigned sessions (`mySessions`, `myNextSession`), and update their own contact details (`updateTrainer` — limited to `firstName`, `lastName`, `email`, `phoneNumber`). A trainer **cannot** modify their own settings — `hourlyRate`, `paymentMode`, and `autoApproveHours` are managed exclusively by admins via `updateTrainerSettings`.
8. **Trainer data isolation**: When a trainer queries `trainerHours`, `pendingTrainerLogs`, or `myTrainerPaymentSummary`, the system enforces that the data belongs to the authenticated trainer. Attempting to access another trainer's data returns an authorization error.
9. **Admin approval authority**: Only an admin can approve or reject trainer hour submissions (`approveTrainerLog`, `rejectTrainerLog`). The admin can also directly log hours (`logTrainerHours`) which bypasses the approval workflow entirely (status is set to `APPROVED` immediately).
10. **Future self-service (out of scope)**: Phase 2 may introduce member self-registration, online payment, and subscription renewal requests. Phase 1 assumes all member write operations go through an administrator.

---

## Rules & Edge Cases

### Members

1. **Duplicate email**: Attempting to create a member with an email already in use must return a validation error.
2. **Expired membership**: A member with `memberUntil` in the past should appear in queries with an indication of expiry, regardless of their current status.
3. **Deactivation**: Changing a member's status to `INACTIVE` does not delete payment, training, subscription, or status history.
4. **Blank names**: First name and last name must not be blank (whitespace-only is invalid).

### Status

5. **Invalid status**: Attempting to set a status that does not exist in the `Status` table must return a validation error.
6. **Idempotent transitions**: Setting a member to a status they already have is allowed (creates a new history entry).

### Subscriptions

7. **Multiple active subscriptions**: A member can have multiple active subscriptions simultaneously (e.g. "Free Games" + "Training").
8. **Duplicate subscription**: A member may subscribe to the same membership type more than once (e.g. successive annual renewals); each renewal is a distinct subscription with its own period and payment tracking.
9. **Subscription requires ACTIVE type**: A subscription can only be created against a membership type with status `ACTIVE`. Attempting to subscribe to a `DRAFT` or `INACTIVE` type returns a validation error.
10. **End subscription**: The `endSubscription` mutation sets `endDate` to today if the current `endDate` is in the future. It does not delete payment history.
11. **Payment after end**: Payments cannot be recorded against an ended subscription (`endDate` in the past).
12. **Renewal**: Continuing a membership after the period ends requires creating a new subscription. There is no auto-renewal in Phase 1.

### Payments

13. **Future payment date**: Allowed — a payment can be recorded in advance.
14. **Overpayment**: The system records actuals; overpayment simply results in `outstanding ≤ 0`.
15. **Zero amount**: Not allowed — amount must be strictly positive.
16. **Outstanding calculation**: Per subscription: `agreedPrice` minus the sum of all payments linked to that subscription. No rolling-period logic — each subscription is a single billing period.

### Prorated Pricing

17. **agreedPrice semantics**: `agreedPrice` on `MemberSubscription` is not null — it is always populated at subscription creation and represents the locked-in billing amount for that subscription period. Once stored, it is immutable and not affected by later changes to the membership type's `price`.
18. **Default price (non-prorated mode)**: If the admin does not provide an explicit `agreedPrice` and `proratedMode` is `false`, the system copies the membership type's current `price` into the subscription's `agreedPrice`.
19. **Automatic proration**: When `proratedMode` is `true` on the membership type and the admin does not provide an explicit `agreedPrice`, the system automatically calculates: `agreedPrice = price × (remaining_days / total_period_days)`. This is computed at subscription creation time and stored on the `MemberSubscription` record. The formula uses calendar days. Example: membership type costs €365/year (365 days), member subscribes on July 1 with `endDate` = Dec 31 → 184 remaining days → `agreedPrice = 365 × (184/365) = €184.00`.
20. **Manual override always wins**: If the admin provides an explicit `agreedPrice` when creating a subscription, it takes precedence regardless of `proratedMode`. This allows the admin to negotiate special prices.
21. **Renewal**: When a subscription is renewed (new subscription for the next period), the admin can omit `agreedPrice` in the input — the system will populate it from the membership type's price (or prorated calculation if applicable). The admin can also provide an explicit override.

### Membership Types

22. **Session linkage**: Adding or removing sessions from a membership type uses dedicated mutations (`assignSessionToMembership` / `removeSessionFromMembership`). A membership type can include sessions of any type (training, free games, or a mix).
23. **Duration semantics**: `duration` + `unit` define the default period length for new subscriptions. They are not used in billing calculations — billing uses the subscription's own `startDate`/`endDate` and `agreedPrice`.
24. **Status transitions**: Only valid transitions are allowed: `DRAFT → ACTIVE`, `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`, `DRAFT → INACTIVE`. Transitioning back to `DRAFT` is not allowed.
25. **INACTIVE with existing subscriptions**: Changing a membership type to `INACTIVE` does not end existing subscriptions. They continue to their `endDate` and payments are still accepted.

### Sessions

26. **Time ordering**: `endTime` must be after `startTime`; same value is invalid.
27. **Session type determines trainer requirement**: `TRAINING` sessions must have a non-null `trainerId`. `FREE_GAME` sessions must have `trainerId` as `null`. Mismatched values return a validation error.
28. **Trainer overlap**: A trainer cannot be assigned to two sessions on the same `dayOfWeek` with overlapping time ranges. The system checks this when creating or updating a session. Two sessions overlap if `session1.startTime < session2.endTime AND session2.startTime < session1.endTime`.
29. **Location overlap**: No two sessions may share the same `dayOfWeek` + `location` with overlapping time ranges. This prevents double-booking a court, field, or hall.
30. **Session immutability**: Once a session has occurrences (past or future), its `dayOfWeek`, `startTime`, `endTime`, and `sessionType` cannot be changed. To reschedule, create a new session and generate new occurrences.

### Session Occurrences

31. **Day-of-week consistency**: A session occurrence's `date` must fall on the same weekday as the referenced session's `dayOfWeek`.
32. **Uniqueness**: No duplicate `(sessionId, date)` pairs. Bulk creation skips already-existing dates silently (idempotent).
33. **Status transitions**: `SCHEDULED → CANCELLED`, `SCHEDULED → COMPLETED`. Both `CANCELLED` and `COMPLETED` are terminal.
34. **Bulk creation (calendar scheduling)**: The `createSessionOccurrences` mutation generates one occurrence per matching weekday in a date range. The admin provides `startDate`, `endDate`, and optionally `skipDates` (holidays). This supports the frontend calendar workflow where the admin schedules an entire season or semester at once.
35. **Cancellation**: Cancelling an occurrence does not affect other occurrences or the session template. The cancelled occurrence remains in the database for audit purposes.
36. **Trainer hours require COMPLETED**: Trainer hours (`logTrainerHours`) can only be logged against occurrences with status `COMPLETED`. Attempting to log against `SCHEDULED` or `CANCELLED` returns an error.
37. **Future occurrences**: Pre-scheduling future dates is allowed and encouraged (e.g. schedule all Mondays for the next semester).
38. **Member visibility**: Members discover available sessions through their subscriptions: `MemberSubscription → MembershipType → MembershipTypeSession → Session → SessionOccurrence`. The `mySessions` query resolves this chain. The `myNextSession` query returns the nearest upcoming `SCHEDULED` occurrence. Attendance is voluntary — the system informs but does not track whether a member actually attended.

### Trainers

39. **Duplicate email**: Same uniqueness constraint as members.
40. **Trainer deletion**: Not in scope for Phase 1 — trainers can only be created, not removed.
41. **Hourly rate**: The `hourlyRate` on `TrainerSettings` is the contractual rate. Changes to `hourlyRate` apply only to **future** log entries — already-approved hours are not retroactively recalculated.
42. **Payment mode**: `PER_SESSION` means the trainer's compensation is due after each `APPROVED` log entry. `MONTHLY` means approved hours are aggregated at month end. The system tracks what is owed; actual disbursement is out of scope for Phase 1 (handled externally via bank transfer, etc.).

### Trainer Hour Submissions & Approval

43. **Submission**: Trainers submit hours via `submitTrainerHours`. The system validates that the referenced `SessionOccurrence` is `COMPLETED`, belongs to a `TRAINING` session assigned to this trainer, and that no existing log entry already exists for the same (trainerId, sessionOccurrenceId) pair.
44. **Duplicate log prevention**: At most one `TrainerLog` entry may exist per (trainerId, sessionOccurrenceId) pair — enforced by a unique constraint.
45. **Auto-approval**: When `autoApproveHours` is `true` in the trainer's `TrainerSettings`, `submitTrainerHours` sets the log status directly to `APPROVED` and populates `reviewedAt`. No admin action is required. This is a per-trainer setting — the admin can enable or disable it at any time via `updateTrainerSettings`.
46. **Manual approval**: When `autoApproveHours` is `false` (the default in `TrainerSettings`), submitted hours start at `PENDING`. The admin must call `approveTrainerLog` or `rejectTrainerLog`. The `pendingTrainerLogs` query shows all entries awaiting review.
47. **Rejection**: When the admin rejects a log entry, a `rejectionReason` must be provided (not blank). The trainer can see the reason and resubmit corrected hours via `resubmitTrainerLog`.
48. **Resubmission**: `resubmitTrainerLog` updates the existing entry in place (does not create a new row). Only `REJECTED` entries can be resubmitted. The status resets to `PENDING` (or `APPROVED` if `TrainerSettings.autoApproveHours`), `rejectionReason` is cleared, and `reviewedAt` is reset.
49. **Approved is terminal**: Once a log entry is `APPROVED`, it cannot be changed — not unapproved, not modified, not deleted. If a correction is needed, the admin must handle it manually (out of scope for Phase 1).
50. **Admin direct logging**: `logTrainerHours` (admin-only) bypasses the approval workflow entirely — the entry is created with status `APPROVED` and both `submittedAt` and `reviewedAt` set to the current timestamp. This is intended for retroactive corrections or when the admin logs hours on behalf of the trainer.
51. **Payment calculation**: For any date range, the trainer's total owed = sum of (`hoursWorked × trainerSettings.hourlyRate`) across all `APPROVED` log entries within that range. The `trainerHours` and `myTrainerPaymentSummary` queries expose this calculation.

### GDPR — Right to Erasure (Art. 17 DSGVO)

52. **Anonymization, not physical deletion (immediate step)**: When `deleteMember` is called, the member's row is **not** physically deleted. Instead, all personal data fields are overwritten with anonymized values to preserve referential integrity (foreign keys from payments, subscriptions, status history). This is compliant with Art. 17 because no personal data remains that could identify the individual.
53. **Anonymized values**: `firstName` → `"DELETED"`, `lastName` → `"DELETED"`, `email` → `"deleted-{id}@anonymous.local"` (keeps uniqueness constraint satisfied), `phoneNumber` → `null`.
54. **Status history anonymization**: The `reason` field in all `MemberStatusHistory` entries for the member is set to `null` (may contain free-text personal data). A final `DELETED` status entry is added.
55. **Subscription deactivation**: All active subscriptions for the member are ended (`endDate` = today).
56. **Payment records retained**: Payment rows are **not** deleted or anonymized. Austrian tax law (BAO §132) requires financial records to be retained for 7 years. Since `Payment` links to `MemberSubscription` (not directly to personal data), no personal information leaks through payment records after anonymization. Payments survive the purge job (see rule 61) — `MemberSubscription` rows referenced by payments are retained as orphaned records with no link back to personal data.
57. **Idempotency**: Calling `deleteMember` on an already-deleted member (status = `DELETED`) returns success without further changes.
58. **No recovery**: Anonymization is irreversible. The mutation should require explicit confirmation or be clearly documented as a destructive action.
59. **Terminal status**: Once a member has status `DELETED`, no further status transitions, updates, or new subscriptions are allowed. The member is effectively frozen until the purge job removes the row.
60. **Trainer erasure**: Not in scope for Phase 1. Trainers who request erasure will be handled manually or in a future phase.
61. **Scheduled purge job**: A configurable scheduled job (`cron`) runs periodically (default: every 24 hours) and **hard-deletes** all member rows that have been in `DELETED` status for longer than a configurable retention period (default: 30 days). The retention period allows the admin to detect accidental erasures before permanent removal. The purge cascades in order: `MemberStatusHistory` entries → `MemberSubscription` rows that have **no** associated `Payment` records → the `Member` row itself. `MemberSubscription` rows that still have `Payment` records are **retained** (with their `memberId` set to `null` or left as a dangling reference — the payment retention obligation under BAO §132 takes precedence). Both the schedule interval and the retention period are externalized as application configuration properties (`app.gdpr.purge-cron` and `app.gdpr.retention-days`).

---

## Examples

### Example 1 — Register a new member

**Input:**

```graphql
mutation {
  createMember(
    input: {
      firstName: "Anna"
      lastName: "Müller"
      email: "anna.mueller@example.com"
      phoneNumber: "+43 660 1234567"
      memberSince: "2010-09-01"
    }
  ) {
    id
    firstName
    lastName
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createMember": {
      "id": "38792587163648",
      "firstName": "Anna",
      "lastName": "Müller"
    }
  }
}
```

The system automatically creates an `ACTIVE` status entry in `MemberStatusHistory`. No subscription is created yet — that is a separate step.

### Example 2 — Subscribe a member to a membership type

Anna starts with "Free Games" — casual weekend matches:

**Input:**

```graphql
mutation {
  subscribeMember(
    input: {
      memberId: "38792587163648"
      membershipTypeId: "38792471048192"
      startDate: "2010-09-01"
    }
  ) {
    id
    membershipType {
      name
      price
    }
    startDate
  }
}
```

**Expected output:**

```json
{
  "data": {
    "subscribeMember": {
      "id": "38792587163700",
      "membershipType": { "name": "Free Games", "price": 120.0 },
      "startDate": "2010-09-01"
    }
  }
}
```

Years later, Anna decides she wants proper coaching. She adds a "Training" subscription without ending her "Free Games" one — her total cost is now €120 + €360 = €480/year.

### Example 3 — Subscribe mid-season with prorated price

The "Training" season runs November–October (€400, duration=1 YEARS). Karl joins in March. The administrator calculates 8 remaining months, sets `agreedPrice = 267.00`, and provides an explicit `endDate` for the shortened period:

**Input:**

```graphql
mutation {
  subscribeMember(
    input: {
      memberId: "38792587163900"
      membershipTypeId: "38792471048193"
      startDate: "2026-03-01"
      endDate: "2026-10-31"
      agreedPrice: 267.00
    }
  ) {
    id
    membershipType {
      name
      price
    }
    startDate
    endDate
    agreedPrice
  }
}
```

**Expected output:**

```json
{
  "data": {
    "subscribeMember": {
      "id": "38792587163901",
      "membershipType": { "name": "Training", "price": 400.0 },
      "startDate": "2026-03-01",
      "endDate": "2026-10-31",
      "agreedPrice": 267.0
    }
  }
}
```

Karl owes €267 (not the full €400) for this shortened period. Next season, a new subscription is created with the full €400 and the standard 1-year duration.

### Example 4 — Query a member with subscriptions and status

**Input:**

```graphql
query {
  memberById(id: "38792587163648") {
    firstName
    lastName
    email
    currentStatus
    memberSince
    subscriptions(active: true) {
      membershipType {
        name
        price
        duration
        unit {
          name
        }
      }
      startDate
      agreedPrice
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "memberById": {
      "firstName": "Anna",
      "lastName": "Müller",
      "email": "anna.mueller@example.com",
      "currentStatus": "ACTIVE",
      "memberSince": "2010-09-01",
      "subscriptions": [
        {
          "membershipType": {
            "name": "Free Games",
            "price": 120.0,
            "duration": 1,
            "unit": { "name": "YEARS" }
          },
          "startDate": "2010-09-01",
          "agreedPrice": 120.0
        },
        {
          "membershipType": {
            "name": "Training",
            "price": 360.0,
            "duration": 1,
            "unit": { "name": "YEARS" }
          },
          "startDate": "2024-01-01",
          "agreedPrice": 360.0
        }
      ]
    }
  }
}
```

### Example 5 — Query outstanding payments (per subscription)

**Input:**

```graphql
query {
  outstandingPayments {
    member {
      firstName
      lastName
    }
    subscription {
      id
      startDate
      endDate
      agreedPrice
    }
    membershipType {
      name
      price
      duration
      unit {
        name
      }
    }
    amountDue
    amountPaid
    outstanding
  }
}
```

**Expected output:**

```json
{
  "data": {
    "outstandingPayments": [
      {
        "member": { "firstName": "Anna", "lastName": "Müller" },
        "subscription": {
          "id": "38792587163700",
          "startDate": "2010-09-01",
          "endDate": "2011-08-31",
          "agreedPrice": 120.0
        },
        "membershipType": {
          "name": "Free Games",
          "price": 120.0,
          "duration": 1,
          "unit": { "name": "YEARS" }
        },
        "amountDue": 120.0,
        "amountPaid": 0.0,
        "outstanding": 120.0
      },
      {
        "member": { "firstName": "Karl", "lastName": "Weber" },
        "subscription": {
          "id": "38792587163901",
          "startDate": "2026-03-01",
          "endDate": "2026-10-31",
          "agreedPrice": 267.0
        },
        "membershipType": {
          "name": "Training",
          "price": 400.0,
          "duration": 1,
          "unit": { "name": "YEARS" }
        },
        "amountDue": 267.0,
        "amountPaid": 0.0,
        "outstanding": 267.0
      }
    ]
  }
}
```

Note: Karl's `amountDue` is €267 (his `agreedPrice`), not the full €400. Outstanding = `agreedPrice` − sum of payments.

### Example 6 — Change member status with reason

**Input:**

```graphql
mutation {
  changeMemberStatus(
    input: {
      memberId: "38792587163648"
      status: "INACTIVE"
      reason: "Member requested leave of absence"
    }
  ) {
    id
    status
    changedAt
    reason
  }
}
```

### Example 7 — Create a training session and a free game session

**Input (training):**

```graphql
mutation {
  createSession(
    input: {
      name: "Monday Evening Training"
      sessionType: "TRAINING"
      dayOfWeek: "MONDAY"
      startTime: "18:00"
      endTime: "19:30"
      location: "Main Hall"
      trainerId: "38792471048200"
    }
  ) {
    id
    name
    sessionType {
      name
    }
    dayOfWeek
  }
}
```

**Input (free game):**

```graphql
mutation {
  createSession(
    input: {
      name: "Saturday Open Play"
      sessionType: "FREE_GAME"
      dayOfWeek: "SATURDAY"
      startTime: "10:00"
      endTime: "12:00"
      location: "Court A"
    }
  ) {
    id
    name
    sessionType {
      name
    }
    dayOfWeek
  }
}
```

Note: The free game session has no `trainerId`. Attempting to set one would return a validation error.

### Example 8 — Bulk-create session occurrences for a season

The admin schedules all Monday training occurrences for the spring semester:

**Input:**

```graphql
mutation {
  createSessionOccurrences(
    input: {
      sessionId: "38792471048300"
      startDate: "2026-09-01"
      endDate: "2026-12-31"
      skipDates: ["2026-12-28"]
    }
  ) {
    id
    date
    status {
      name
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createSessionOccurrences": [
      {
        "id": "38792587164000",
        "date": "2026-09-07",
        "status": { "name": "SCHEDULED" }
      },
      {
        "id": "38792587164001",
        "date": "2026-09-14",
        "status": { "name": "SCHEDULED" }
      },
      {
        "id": "38792587164002",
        "date": "2026-09-21",
        "status": { "name": "SCHEDULED" }
      }
    ]
  }
}
```

_(Truncated — all Mondays in the range except 2026-12-28 are created.)_

### Example 9 — Log trainer hours against a completed occurrence (admin direct)

First, mark the occurrence as completed, then the admin logs hours directly (bypasses approval — status is set to `APPROVED`):

**Input (complete):**

```graphql
mutation {
  completeSessionOccurrence(id: "38792587164000") {
    id
    date
    status {
      name
    }
  }
}
```

**Input (admin logs hours directly):**

```graphql
mutation {
  logTrainerHours(
    input: {
      trainerId: "38792471048200"
      sessionOccurrenceId: "38792587164000"
      hoursWorked: 1.5
    }
  ) {
    id
    hoursWorked
    status {
      name
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "logTrainerHours": {
      "id": "38792587164100",
      "hoursWorked": 1.5,
      "status": { "name": "APPROVED" }
    }
  }
}
```

**Input (query summary — only approved hours are included):**

```graphql
query {
  trainerHours(
    trainerId: "38792471048200"
    from: "2026-09-01"
    to: "2026-09-30"
  ) {
    trainer {
      firstName
      lastName
    }
    totalHours
    totalOwed
    sessionCount
  }
}
```

### Example 10 — Member views their available sessions

Anna has "Free Games" and "Training" subscriptions. She queries her upcoming sessions:

**Input:**

```graphql
query {
  mySessions(from: "2026-09-01", to: "2026-09-14") {
    id
    date
    status {
      name
    }
    session {
      name
      sessionType {
        name
      }
      startTime
      endTime
      location
      trainer {
        firstName
        lastName
      }
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "mySessions": [
      {
        "id": "38792587164000",
        "date": "2026-09-07",
        "status": { "name": "SCHEDULED" },
        "session": {
          "name": "Monday Evening Training",
          "sessionType": { "name": "TRAINING" },
          "startTime": "18:00",
          "endTime": "19:30",
          "location": "Main Hall",
          "trainer": { "firstName": "Marco", "lastName": "Fischer" }
        }
      },
      {
        "id": "38792587164010",
        "date": "2026-09-12",
        "status": { "name": "SCHEDULED" },
        "session": {
          "name": "Saturday Open Play",
          "sessionType": { "name": "FREE_GAME" },
          "startTime": "10:00",
          "endTime": "12:00",
          "location": "Court A",
          "trainer": null
        }
      }
    ]
  }
}
```

Attendance is voluntary — the system simply shows what is available. The frontend uses `myNextSession` to remind Anna of her nearest upcoming session.

### Example 11 — Next session reminder

**Input:**

```graphql
query {
  myNextSession {
    id
    date
    session {
      name
      sessionType {
        name
      }
      startTime
      location
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "myNextSession": {
      "id": "38792587164000",
      "date": "2026-09-07",
      "session": {
        "name": "Monday Evening Training",
        "sessionType": { "name": "TRAINING" },
        "startTime": "18:00",
        "location": "Main Hall"
      }
    }
  }
}
```

### Example 12 — Validation error: duplicate email

**Input:**

```graphql
mutation {
  createMember(
    input: {
      firstName: "Max"
      lastName: "Huber"
      email: "anna.mueller@example.com"
      memberSince: "2026-04-08"
    }
  ) {
    id
  }
}
```

**Expected output:** GraphQL error with classification `VALIDATION` and message indicating duplicate email.

### Example 13 — GDPR erasure: delete a member

**Input:**

```graphql
mutation {
  deleteMember(id: "38792587163648") {
    memberId
    anonymizedAt
    fieldsAnonymized
  }
}
```

**Expected output:**

```json
{
  "data": {
    "deleteMember": {
      "memberId": "38792587163648",
      "anonymizedAt": "2026-04-08T14:30:00",
      "fieldsAnonymized": ["firstName", "lastName", "email", "phoneNumber"]
    }
  }
}
```

**After erasure, querying the member returns anonymized data:**

```json
{
  "data": {
    "memberById": {
      "firstName": "DELETED",
      "lastName": "DELETED",
      "email": "deleted-38792587163648@anonymous.local",
      "currentStatus": "DELETED",
      "memberSince": "2010-09-01",
      "subscriptions": []
    }
  }
}
```

Payment records linked through subscriptions remain intact for tax compliance (BAO §132).

### Example 14 — Admin workflow: create a Free Game membership type (end-to-end)

This scenario demonstrates the full admin workflow for setting up a "Free Games" membership type from scratch through publication.

**Step 1: Create the membership type (DRAFT)**

```graphql
mutation {
  createMembershipType(
    input: {
      name: "Freies Spiel Montag"
      description: "Free game sessions every Monday evening, 18:00–20:30"
      price: 200.00
      duration: 1
      unit: "YEARS"
      proratedMode: true
    }
  ) {
    id
    name
    status {
      name
    }
    proratedMode
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createMembershipType": {
      "id": "71820948571648",
      "name": "Freies Spiel Montag",
      "status": { "name": "DRAFT" },
      "proratedMode": true
    }
  }
}
```

**Step 2: Create a FREE_GAME session**

```graphql
mutation {
  createSession(
    input: {
      name: "Monday Evening Free Game"
      sessionType: "FREE_GAME"
      dayOfWeek: "MONDAY"
      startTime: "18:00"
      endTime: "20:30"
      location: "Court, Primary School, Street x 88"
    }
  ) {
    id
    name
    sessionType {
      name
    }
    dayOfWeek
    startTime
    endTime
    location
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createSession": {
      "id": "71820948571700",
      "name": "Monday Evening Free Game",
      "sessionType": { "name": "FREE_GAME" },
      "dayOfWeek": "MONDAY",
      "startTime": "18:00",
      "endTime": "20:30",
      "location": "Court, Primary School, Street x 88"
    }
  }
}
```

**Step 3: Assign the session to the membership type**

```graphql
mutation {
  assignSessionToMembershipType(
    membershipTypeId: "71820948571648"
    sessionId: "71820948571700"
  ) {
    membershipType {
      name
    }
    session {
      name
    }
  }
}
```

**Step 4: Bulk-create occurrences for the season (skipping holidays)**

```graphql
mutation {
  createSessionOccurrences(
    input: {
      sessionId: "71820948571700"
      startDate: "2026-09-01"
      endDate: "2027-06-30"
      skipDates: ["2026-12-25", "2027-01-01", "2027-01-06", "2027-04-05"]
    }
  ) {
    created
    sessionOccurrences {
      date
      status {
        name
      }
    }
  }
}
```

The backend generates one `SCHEDULED` occurrence for every Monday between 2026-09-01 and 2027-06-30, excluding the specified holiday dates.

**Step 5: Publish the membership type (DRAFT → ACTIVE)**

```graphql
mutation {
  changeMembershipTypeStatus(id: "71820948571648", status: "ACTIVE") {
    id
    name
    status {
      name
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "changeMembershipTypeStatus": {
      "id": "71820948571648",
      "name": "Freies Spiel Montag",
      "status": { "name": "ACTIVE" }
    }
  }
}
```

> **Note (Phase 2):** Upon activation, the system will eventually send SMS and email notifications to all active members informing them that a new membership option is available. Phase 1 does not implement the notification — the `changeMembershipTypeStatus` mutation is the future integration point.

**Step 6: Member subscribes mid-season (prorated mode applies)**

When the membership type has `proratedMode: true` and a member subscribes on 2027-01-15 (167 days remaining out of a 365-day period), the system auto-calculates the prorated price if no explicit `agreedPrice` is provided:

```graphql
mutation {
  subscribeMember(
    input: {
      memberId: "38792587163648"
      membershipTypeId: "71820948571648"
      startDate: "2027-01-15"
      endDate: "2027-06-30"
    }
  ) {
    id
    startDate
    endDate
    agreedPrice
  }
}
```

**Expected output:**

```json
{
  "data": {
    "subscribeMember": {
      "id": "71820948571900",
      "startDate": "2027-01-15",
      "endDate": "2027-06-30",
      "agreedPrice": 91.51
    }
  }
}
```

Calculation: `200.00 × (167 / 365) = 91.51` (rounded to 2 decimal places). The member pays only for the remaining portion of the season.

### Example 15 — Admin workflow: create a Training membership type with trainer selection

This scenario demonstrates the admin workflow for setting up a "Training" membership type, including querying for available trainers and assigning one to the session.

**Step 1: Create the membership type (DRAFT)**

```graphql
mutation {
  createMembershipType(
    input: {
      name: "Mittwochstraining Anfänger"
      description: "Beginner training sessions every Wednesday, 18:00–20:00"
      price: 360.00
      duration: 1
      unit: "YEARS"
      proratedMode: true
    }
  ) {
    id
    name
    status {
      name
    }
    proratedMode
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createMembershipType": {
      "id": "71820948572000",
      "name": "Mittwochstraining Anfänger",
      "status": { "name": "DRAFT" },
      "proratedMode": true
    }
  }
}
```

**Step 2: Query available trainers for the desired time slot**

Before creating the training session, the admin checks which trainers are free on Wednesday 18:00–20:00:

```graphql
query {
  availableTrainers(
    dayOfWeek: "WEDNESDAY"
    startTime: "18:00"
    endTime: "20:00"
  ) {
    id
    firstName
    lastName
  }
}
```

**Expected output:**

```json
{
  "data": {
    "availableTrainers": [
      { "id": "55120948571000", "firstName": "Karl", "lastName": "Weber" },
      { "id": "55120948571001", "firstName": "Eva", "lastName": "Gruber" }
    ]
  }
}
```

The query returns only trainers who do **not** have an existing session overlapping Wednesday 18:00–20:00. The admin selects Karl Weber for this training.

**Step 3: Create a TRAINING session with the selected trainer**

```graphql
mutation {
  createSession(
    input: {
      name: "Wednesday Beginner Training"
      sessionType: "TRAINING"
      dayOfWeek: "WEDNESDAY"
      startTime: "18:00"
      endTime: "20:00"
      location: "Court, Primary School, Street y 99"
      trainerId: "55120948571000"
    }
  ) {
    id
    name
    sessionType {
      name
    }
    dayOfWeek
    startTime
    endTime
    location
    trainer {
      firstName
      lastName
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "createSession": {
      "id": "71820948572100",
      "name": "Wednesday Beginner Training",
      "sessionType": { "name": "TRAINING" },
      "dayOfWeek": "WEDNESDAY",
      "startTime": "18:00",
      "endTime": "20:00",
      "location": "Court, Primary School, Street y 99",
      "trainer": { "firstName": "Karl", "lastName": "Weber" }
    }
  }
}
```

The system validates that Karl Weber has no overlapping session on Wednesdays 18:00–20:00 before persisting.

**Step 4: Assign the session to the membership type**

```graphql
mutation {
  assignSessionToMembershipType(
    membershipTypeId: "71820948572000"
    sessionId: "71820948572100"
  ) {
    membershipType {
      name
    }
    session {
      name
      trainer {
        firstName
        lastName
      }
    }
  }
}
```

**Step 5: Bulk-create occurrences for the season (skipping holidays)**

```graphql
mutation {
  createSessionOccurrences(
    input: {
      sessionId: "71820948572100"
      startDate: "2026-09-01"
      endDate: "2027-06-30"
      skipDates: ["2026-12-25", "2027-01-01", "2027-01-06", "2027-04-05"]
    }
  ) {
    created
    sessionOccurrences {
      date
      status {
        name
      }
    }
  }
}
```

The backend generates one `SCHEDULED` occurrence for every Wednesday between 2026-09-01 and 2027-06-30, excluding the specified holiday dates.

**Step 6: Publish the membership type (DRAFT → ACTIVE)**

```graphql
mutation {
  changeMembershipTypeStatus(id: "71820948572000", status: "ACTIVE") {
    id
    name
    status {
      name
    }
  }
}
```

**Expected output:**

```json
{
  "data": {
    "changeMembershipTypeStatus": {
      "id": "71820948572000",
      "name": "Mittwochstraining Anfänger",
      "status": { "name": "ACTIVE" }
    }
  }
}
```

> **Note (Phase 2):** Upon activation, a notification (SMS + email) will be sent to all active members. Phase 1 does not implement the notification itself.

**Step 7: Member subscribes mid-season (prorated mode applies)**

A member subscribes on 2027-03-01 (122 days remaining out of 365):

```graphql
mutation {
  subscribeMember(
    input: {
      memberId: "38792587163648"
      membershipTypeId: "71820948572000"
      startDate: "2027-03-01"
      endDate: "2027-06-30"
    }
  ) {
    id
    startDate
    endDate
    agreedPrice
  }
}
```

**Expected output:**

```json
{
  "data": {
    "subscribeMember": {
      "id": "71820948572200",
      "startDate": "2027-03-01",
      "endDate": "2027-06-30",
      "agreedPrice": 120.22
    }
  }
}
```

Calculation: `360.00 × (122 / 365) = 120.22` (rounded to 2 decimal places).

### Example 16 — Trainer submits hours (manual approval workflow)

Karl Weber (trainer) conducts Wednesday's beginner training on 2026-09-03. The admin marks the occurrence as completed, then Karl submits his hours. Karl's `TrainerSettings.autoApproveHours` is `false`, so hours go to `PENDING`.

**Step 1: Admin marks the occurrence as completed**

```graphql
mutation {
  completeSessionOccurrence(id: "71820948573000") {
    id
    date
    status {
      name
    }
  }
}
```

**Step 2: Trainer submits hours**

```graphql
mutation {
  submitTrainerHours(
    input: {
      sessionOccurrenceId: "71820948573000"
      hoursWorked: 2.0
      notes: "Full session, 8 participants"
    }
  ) {
    id
    hoursWorked
    status {
      name
    }
    submittedAt
  }
}
```

**Expected output:**

```json
{
  "data": {
    "submitTrainerHours": {
      "id": "71820948573100",
      "hoursWorked": 2.0,
      "status": { "name": "PENDING" },
      "submittedAt": "2026-09-03T20:15:00"
    }
  }
}
```

**Step 3: Admin reviews pending logs**

```graphql
query {
  pendingTrainerLogs(trainerId: "55120948571000") {
    id
    hoursWorked
    notes
    submittedAt
    sessionOccurrence {
      date
      session {
        name
      }
    }
  }
}
```

**Step 4: Admin approves**

```graphql
mutation {
  approveTrainerLog(id: "71820948573100") {
    id
    status {
      name
    }
    reviewedAt
  }
}
```

**Expected output:**

```json
{
  "data": {
    "approveTrainerLog": {
      "id": "71820948573100",
      "status": { "name": "APPROVED" },
      "reviewedAt": "2026-09-04T09:30:00"
    }
  }
}
```

### Example 17 — Trainer submits hours (auto-approval)

Eva Gruber (trainer) has `TrainerSettings.autoApproveHours: true`. She submits hours after her Thursday training session:

```graphql
mutation {
  submitTrainerHours(
    input: { sessionOccurrenceId: "71820948573200", hoursWorked: 1.5 }
  ) {
    id
    hoursWorked
    status {
      name
    }
    submittedAt
    reviewedAt
  }
}
```

**Expected output:**

```json
{
  "data": {
    "submitTrainerHours": {
      "id": "71820948573300",
      "hoursWorked": 1.5,
      "status": { "name": "APPROVED" },
      "submittedAt": "2026-09-04T20:10:00",
      "reviewedAt": "2026-09-04T20:10:00"
    }
  }
}
```

No admin action needed — hours are immediately approved and the trainer is entitled to payment.

### Example 18 — Admin rejects hours, trainer resubmits

The admin notices Karl submitted 3.0 hours for a 2-hour session and rejects:

**Step 1: Admin rejects**

```graphql
mutation {
  rejectTrainerLog(
    input: {
      id: "71820948573400"
      reason: "Session is 2 hours (18:00–20:00), submitted 3.0 hours. Please correct."
    }
  ) {
    id
    status {
      name
    }
    rejectionReason
    reviewedAt
  }
}
```

**Expected output:**

```json
{
  "data": {
    "rejectTrainerLog": {
      "id": "71820948573400",
      "status": { "name": "REJECTED" },
      "rejectionReason": "Session is 2 hours (18:00–20:00), submitted 3.0 hours. Please correct.",
      "reviewedAt": "2026-09-05T10:00:00"
    }
  }
}
```

**Step 2: Trainer sees the rejection and resubmits**

```graphql
mutation {
  resubmitTrainerLog(
    input: {
      id: "71820948573400"
      hoursWorked: 2.0
      notes: "Corrected — 2h session"
    }
  ) {
    id
    hoursWorked
    status {
      name
    }
    rejectionReason
  }
}
```

**Expected output:**

```json
{
  "data": {
    "resubmitTrainerLog": {
      "id": "71820948573400",
      "hoursWorked": 2.0,
      "status": { "name": "PENDING" },
      "rejectionReason": null
    }
  }
}
```

The entry is now back to `PENDING` for the admin to review again.

### Example 19 — Trainer views their payment summary

Karl checks his September payment summary:

```graphql
query {
  myTrainerPaymentSummary(from: "2026-09-01", to: "2026-09-30") {
    trainer {
      firstName
      lastName
    }
    approvedHours
    approvedSessions
    hourlyRate
    totalOwed
    paymentMode
    pendingHours
    pendingSessions
  }
}
```

**Expected output:**

```json
{
  "data": {
    "myTrainerPaymentSummary": {
      "trainer": { "firstName": "Karl", "lastName": "Weber" },
      "approvedHours": 8.0,
      "approvedSessions": 4,
      "hourlyRate": 35.0,
      "totalOwed": 280.0,
      "paymentMode": "MONTHLY",
      "pendingHours": 2.0,
      "pendingSessions": 1
    }
  }
}
```

Karl has 4 approved sessions (8 hours × €35/hr = €280 owed) and 1 session still pending approval.

---

## Complexity Targets

Not applicable in the traditional algorithmic sense. Performance targets for Phase 1:

| Operation             | Target                          |
| --------------------- | ------------------------------- |
| Single-entity queries | < 50 ms                         |
| List queries (< 200)  | < 200 ms                        |
| Mutations             | < 100 ms                        |
| Outstanding payments  | < 500 ms (involves aggregation) |

Database indices should cover: `member.email`, `member_status_history.member_id`, `member_status_history.changed_at`, `member_subscription.member_id`, `member_subscription.membership_type_id`, `member_subscription.end_date`, `payment.member_subscription_id`, `session.day_of_week`, `session.trainer_id`, `session.session_type_id`, `session_occurrence.session_id`, `session_occurrence.date`, `session_occurrence(session_id, date)` (unique), `trainer_log.trainer_id`, `trainer_log.session_occurrence_id`, `trainer_log.status_id`, `trainer_log(trainer_id, session_occurrence_id)` (unique), `trainer_settings.trainer_id` (unique), `membership_type_session` (composite PK).

---

## Architecture Notes

### Build Dependencies

The following dependencies must be present in `build.gradle` for Phase 1:

```groovy
dependencies {
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-graphql'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.springframework.boot:spring-boot-h2console'

    // GraphQL extended scalars (Date, DateTime, BigDecimal, Long, etc.)
    implementation 'com.graphql-java:graphql-java-extended-scalars:22.0'

    // TSID generation — provides @Tsid annotation for JPA entity primary keys
    implementation 'io.hypersistence:hypersistence-utils-hibernate-63:<latest>'

    // Utilities
    implementation 'org.apache.commons:commons-lang3'
    compileOnly    'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Database drivers
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'

    // Flyway dialect support — required at runtime for PostgreSQL migrations
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-graphql-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway-test'
}
```

> **Note:** The `flyway-database-postgresql` runtime dependency is required by Flyway 10+ to connect to PostgreSQL. Without it, Flyway will fail to detect the database type at startup. H2 support is built into Flyway core and requires no additional dialect dependency.

### Primary Key Library — TSID

All entities use `@Tsid` on a `Long id` field, provided by `hypersistence-utils`.

### GraphQL Configuration

The following shared GraphQL properties must be set in `application.properties`:

```properties
# GraphQL configuration (shared)
spring.graphql.http.path=/graphql
spring.graphql.schema.printer.enabled=true
```

- `spring.graphql.http.path=/graphql` — exposes the GraphQL endpoint at `/graphql`.
- `spring.graphql.schema.printer.enabled=true` — enables the schema introspection endpoint, useful for development and tooling (GraphiQL, Postman, etc.).

### Domain packages (following project DDD conventions)

```
at.mavila.dbchatbox.domain.club.member        — Member entity, service, validation
at.mavila.dbchatbox.domain.club.status         — Status entity, MemberStatusHistory entity, service
at.mavila.dbchatbox.domain.club.unit           — Unit entity (reference table for time units)
at.mavila.dbchatbox.domain.club.subscription   — MemberSubscription entity, service
at.mavila.dbchatbox.domain.club.membership     — MembershipType entity, MembershipTypeStatus, MembershipTypeSession, service
at.mavila.dbchatbox.domain.club.payment        — Payment entity, service, outstanding-dues logic
at.mavila.dbchatbox.domain.club.session        — Session entity, SessionType, SessionOccurrence, SessionOccurrenceStatus, service
at.mavila.dbchatbox.domain.club.trainer        — Trainer entity, TrainerSettings entity, TrainerLog, hours aggregation
```

### Database Migrations — Flyway

Flyway manages all schema changes via versioned SQL scripts under `src/main/resources/db/migration/`.

#### Profile-specific configuration

The application uses Spring profiles to target different databases:

**`application-dev.properties`** (H2 — local development):

```properties
spring.datasource.url=jdbc:h2:mem:dbchatbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

**`application-prod.properties`** (PostgreSQL — production):

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:postgres}:${DB_PORT:5432}/${DB_NAME:exercises_db}
spring.datasource.username=${DB_USERNAME:devuser}
spring.datasource.password=${DB_PASSWORD:devpassword}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.h2.console.enabled=false
```

> **Important:** H2 is configured with `MODE=PostgreSQL` so that Flyway migration scripts written for PostgreSQL syntax also work in the H2 development environment. Use standard PostgreSQL SQL in all migration files.

**`application-test.properties`** (H2 — test suite):

Mirrors the `dev` profile datasource configuration so that tests run against an in-memory H2 database.

#### Migration scripts

Flyway migrations under `src/main/resources/db/migration/` for all tables including:

- `status` (with seed data: ACTIVE, INACTIVE, DELETED)
- `member_status_history` (pivot table)
- `member_subscription` (links member to membership type)
- `unit` (with seed data: DAYS, WEEKS, MONTHS, YEARS)
- `membership_type_status` (with seed data: DRAFT, ACTIVE, INACTIVE)
- `session_type` (with seed data: TRAINING, FREE_GAME)
- `session` (recurring weekly schedule — references session_type, optionally trainer)
- `session_occurrence_status` (with seed data: SCHEDULED, CANCELLED, COMPLETED)
- `session_occurrence` (concrete dated instances of sessions)
- `membership_type_session` (join table — replaces former membership_type_training_session)
- `trainer_settings` (one-to-one with trainer — compensation and workflow settings)

### Data Access Layer — JPA Repositories

All database access must use **Spring Data JPA repositories** (`JpaRepository` / `CrudRepository`). Direct use of `EntityManager` is **not allowed**.

**Conventions:**

- Each entity gets its own repository interface in the same domain subpackage as the entity.
- Repository interfaces extend `JpaRepository<EntityType, Long>` (since all PKs are `Long` / TSID).
- Use **derived query methods** (e.g. `findByEmail`, `findByMemberIdOrderByChangedAtDesc`) where the method name clearly expresses the query.
- Use **`@Query` (JPQL)** for queries that cannot be cleanly expressed via method naming (e.g. aggregations, joins, subqueries).
- **Never inject `EntityManager`** into services or repositories. If a query requires native SQL or complex criteria, use a `@Query(nativeQuery = true)` method on the repository interface instead.
- Repositories are injected into **domain services** via constructor injection (Lombok `@RequiredArgsConstructor`).

**Repository naming:** `<Entity>Repository` — e.g. `MemberRepository`, `PaymentRepository`, `MemberStatusHistoryRepository`.

```java
// ✅ Good — Spring Data JPA repository with derived query methods
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}

// ✅ Good — JPQL for complex queries
public interface MemberStatusHistoryRepository extends JpaRepository<MemberStatusHistory, Long> {
    @Query("SELECT h FROM MemberStatusHistory h WHERE h.memberId = :memberId ORDER BY h.changedAt DESC")
    List<MemberStatusHistory> findByMemberIdOrderByChangedAtDesc(@Param("memberId") Long memberId);

    default Optional<MemberStatusHistory> findLatestByMemberId(Long memberId) {
        return findByMemberIdOrderByChangedAtDesc(memberId).stream().findFirst();
    }
}

// ❌ Bad — EntityManager usage
@Repository
public class MemberRepositoryImpl {
    @PersistenceContext
    private EntityManager entityManager; // NOT ALLOWED
}
```

### Service Layer Design — Avoid Monolithic Services

The service layer must follow **domain-driven decomposition**. Each bounded context (domain subpackage) has its own dedicated service. There is **no single monolithic `ClubService`** or `AlgorithmService` that handles all operations.

**Conventions:**

- **One service per domain aggregate**: `MemberService`, `SubscriptionService`, `PaymentService`, `MembershipTypeService`, `SessionService`, `TrainerService`.
- Each service is a `@Component` (or `@Service`) annotated with `@RequiredArgsConstructor` and lives in its respective domain subpackage.
- A service orchestrates operations within its own aggregate boundary. For cross-aggregate operations (e.g. `deleteMember` which touches members, subscriptions, and status history), a **domain orchestrator** or **application service** coordinates the calls — but each step is delegated to the responsible domain service.
- Services must **not** grow beyond their aggregate scope — a `MemberService` should not contain subscription logic or payment logic.
- Services inject **repositories** (for data access) and **other domain services or collaborators** (for cross-cutting concerns like validation) via constructor injection.

**Cross-aggregate orchestration example:**

The `deleteMember` mutation involves three aggregates (member, subscription, status). Instead of cramming all logic into `MemberService`, use a thin application-level orchestrator:

```java
// ✅ Good — thin orchestrator delegates to domain services
@Component
@RequiredArgsConstructor
public class MemberDeletionOrchestrator {
    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final MemberStatusService memberStatusService;

    @Transactional
    public DeleteMemberResult deleteMember(final Long memberId) {
        memberStatusService.recordStatus(memberId, Status.DELETED, "GDPR erasure");
        subscriptionService.endAllActiveSubscriptions(memberId);
        return memberService.anonymize(memberId);
    }
}

// ❌ Bad — monolithic service handles everything
@Service
public class ClubService {
    // 2000+ lines covering members, subscriptions, payments, trainers...
}
```

**Service per domain subpackage:**

```
at.mavila.dbchatbox.domain.club.member        → MemberService
at.mavila.dbchatbox.domain.club.status         → MemberStatusService
at.mavila.dbchatbox.domain.club.subscription   → SubscriptionService
at.mavila.dbchatbox.domain.club.membership     → MembershipTypeService
at.mavila.dbchatbox.domain.club.payment        → PaymentService
at.mavila.dbchatbox.domain.club.session        → SessionService
at.mavila.dbchatbox.domain.club.trainer        → TrainerService, TrainerLogService
```

### GDPR Compliance

The system implements Art. 17 DSGVO (right to erasure) via **in-place anonymization**:

- Personal data fields on `Member` are overwritten (not deleted) to maintain foreign key integrity.
- Payment records are **retained** per Austrian tax retention requirements (BAO §132, 7-year minimum).
- The `deleteMember` mutation is a single transactional operation: anonymize member → null out status history reasons → end active subscriptions → set status to `DELETED`.
- After anonymization, the member row is effectively a tombstone — it cannot be updated, subscribed, or re-activated.
- Audit log: the `MemberStatusHistory` entry with status `DELETED` serves as the erasure timestamp.

### Future Phases (out of scope)

- **Phase 2**: Automated invoices, payment reminders, **member notifications** (SMS + email on membership type activation, session cancellation, etc.), member/trainer login portals, online hour logging, member self-registration & self-service, online payment.
- **Phase 3**: Online training registration, automated payments, statistics dashboards, full online administration.
- **Professional players**: Dedicated player/team entities, tournament participation, performance tracking, special contracts. Deferred until clarified whether professional players require separate treatment.

> **Notification hook (Phase 2 preparation):** When a membership type transitions from `DRAFT → ACTIVE` (i.e. it is "published"), the system should eventually notify all active members via SMS and email that a new membership option is available. Phase 1 defines the activation lifecycle but does **not** implement the notification itself. The `changeMembershipTypeStatus` mutation is the integration point — Phase 2 will attach a notification service to this event.

### Enum Storage Pattern

The reference tables `Status`, `Unit`, `MembershipTypeStatus`, `SessionType`, `SessionOccurrenceStatus`, `TrainerLogStatus`, and `TrainerPaymentMode` act as **application-level enums**. In the Java domain layer, each is represented as a Java `enum` (e.g. `Status.ACTIVE`, `Unit.MONTHS`, `MembershipTypeStatus.DRAFT`, `SessionType.TRAINING`, `SessionOccurrenceStatus.SCHEDULED`, `TrainerLogStatus.PENDING`, `TrainerPaymentMode.PER_SESSION`). In the database, the `name` column is stored as `VARCHAR` and mapped via `@Enumerated(EnumType.STRING)`. This pattern ensures:

- **Readability**: Database rows contain human-readable strings (`ACTIVE`, `MONTHS`) rather than opaque integer codes.
- **Type safety**: The Java layer enforces that only declared enum constants can be used.
- **Extensibility**: New values can be added by extending the enum and inserting a matching seed row — no schema change required.
