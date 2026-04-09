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
- What training sessions are available?
- How many hours have trainers worked?

### Scope

Phase 1 covers **five core domains**:

| Domain         | Responsibility                                                  |
| -------------- | --------------------------------------------------------------- |
| **Member**     | Registration, contact details, status tracking                  |
| **Membership** | Membership types, pricing, duration, allowed training sessions  |
| **Payment**    | Recording payments linked to member and membership type         |
| **Training**   | Training sessions with schedule, location, and assigned trainer |
| **Trainer**    | Logging training hours, overview of hours worked                |

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
- **GDPR erasure** (right to be forgotten, Art. 17 DSGVO): anonymizes all personal data in-place — see `deleteMember` mutation and GDPR section below. The row is preserved to maintain referential integrity with payments and subscriptions.
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
| `agreedPrice`      | `BigDecimal` | Optional; if set, overrides the membership type's `price` for billing                                                                       |

**Derived state:** A subscription is considered **active** when `endDate >= today`. No separate boolean needed.

**Business rules:**

- A member can subscribe to **multiple** membership types at the same time (e.g. "Free Games" first, then adding "Training" when they want to improve). The total cost for the member is the sum of all active subscriptions.
- A member can have successive subscriptions to the **same** membership type (e.g. renewed annually). Each renewal is a distinct subscription row with its own period.
- `endDate` is always set. When the administrator does not provide it, the system computes it as `startDate + duration` (using the membership type's `duration` and `unit`). The administrator can override it to a different date.
- **Early termination**: The `endSubscription` mutation sets `endDate` to today if the current `endDate` is in the future.
- **Outstanding dues** for a subscription = effective price − sum of all payments linked to that subscription. Effective price = `agreedPrice` if set, otherwise the membership type's `price`.
- **Prorated pricing** (`agreedPrice`): When a member joins mid-period (e.g. season starts November but member subscribes in March), the administrator can set `agreedPrice` to a reduced amount. If `null`, the full membership type `price` applies. The system does not calculate proration automatically — the administrator determines the amount externally.
- Examples:
  - _Anna joined the club in 2010 with a "Free Games" subscription (€120/year) — she plays casual matches on weekends. In 2024 she decided she needed proper training, so she added a "Training" subscription (€360/year). She now pays €120 + €360 = €480/year across 2 subscriptions._
  - _The Training season runs November–October (€400, duration=1 YEARS). Karl joins in March. The admin creates a subscription with `startDate=2026-03-01`, `endDate=2026-10-31`, `agreedPrice=267.00`. Next season, a new subscription is created with the full €400._
  - _Children members subscribe to "Children" (training + free games). When they turn 18, that subscription ends and they may start an "Amateur" or "Training" subscription._

### MembershipType

| Field         | Type         | Constraints                                              |
| ------------- | ------------ | -------------------------------------------------------- |
| `id`          | `Long`       | TSID, auto-generated, unique                             |
| `name`        | `String`     | Not null, not blank, unique, max 100 characters          |
| `description` | `String`     | Optional, max 500 characters                             |
| `price`       | `BigDecimal` | Not null, positive (> 0)                                 |
| `duration`    | `Integer`    | Not null, positive — number of time units for the period |
| `unitId`      | `Long`       | Not null, references Unit — the time unit for `duration` |
| `statusId`    | `Long`       | Not null, references MembershipTypeStatus                |

**Business rules:**

- `price` is the default price for one subscription period. It can be overridden per subscription via `agreedPrice`.
- `duration` + `unit` define the **default period length**. When a subscription is created without an explicit `endDate`, the system computes `endDate = startDate + duration` (in the given unit). Examples: annual membership → `price=360.00, duration=1, unit=YEARS`; quarterly → `price=100.00, duration=3, unit=MONTHS`; 90-day pass → `price=80.00, duration=90, unit=DAYS`.
- A membership type can be referenced by many subscriptions across many members.
- When created, a membership type starts in `DRAFT` status. The administrator must explicitly activate it before subscriptions can be created.
- **New subscriptions** can only be created against `ACTIVE` membership types. `DRAFT` and `INACTIVE` types reject subscription attempts.
- **Existing subscriptions** are unaffected by status changes — if a membership type becomes `INACTIVE`, active subscriptions continue to their `endDate` and payments are still accepted.
- Deleting a membership type is only allowed if no subscriptions (active or historical) reference it.

### MembershipTypeTrainingSession (Join Table)

Links which training sessions are included in a membership type. Replaces the former comma-separated `allowedTrainings` field with a proper normalized relationship.

| Field               | Type   | Constraints                                         |
| ------------------- | ------ | --------------------------------------------------- |
| `membershipTypeId`  | `Long` | Not null, references MembershipType (composite PK)  |
| `trainingSessionId` | `Long` | Not null, references TrainingSession (composite PK) |

**Business rules:**

- A membership type can include many training sessions.
- A training session can belong to many membership types.
- This is a pure join table with a composite primary key — no TSID needed.
- Membership type queries return associated training sessions via this relationship.

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
- Outstanding dues per subscription = effective price − sum of all payments. Effective price = `agreedPrice` if set, otherwise the membership type's `price`.

### TrainingSession

| Field       | Type        | Constraints                             |
| ----------- | ----------- | --------------------------------------- |
| `id`        | `Long`      | TSID, auto-generated, unique            |
| `name`      | `String`    | Not null, not blank, max 150 characters |
| `dayOfWeek` | `DayOfWeek` | Not null (MONDAY–SUNDAY)                |
| `startTime` | `LocalTime` | Not null                                |
| `endTime`   | `LocalTime` | Not null, must be after `startTime`     |
| `location`  | `String`    | Not null, not blank, max 200 characters |
| `trainerId` | `Long`      | Not null, references a Trainer          |

**Business rules:**

- Training sessions recur weekly on the specified day.
- `endTime` must be strictly after `startTime` (no overnight sessions in Phase 1).
- A trainer can lead multiple sessions; a session has exactly one trainer.

### Trainer

| Field         | Type     | Constraints                             |
| ------------- | -------- | --------------------------------------- |
| `id`          | `Long`   | TSID, auto-generated, unique            |
| `firstName`   | `String` | Not null, not blank, max 100 characters |
| `lastName`    | `String` | Not null, not blank, max 100 characters |
| `email`       | `String` | Not null, valid email format, unique    |
| `phoneNumber` | `String` | Optional                                |

### TrainerLog (Training Hours)

| Field               | Type         | Constraints                            |
| ------------------- | ------------ | -------------------------------------- |
| `id`                | `Long`       | TSID, auto-generated, unique           |
| `trainerId`         | `Long`       | Not null, references a Trainer         |
| `trainingSessionId` | `Long`       | Not null, references a TrainingSession |
| `date`              | `LocalDate`  | Not null, must not be in the future    |
| `hoursWorked`       | `BigDecimal` | Not null, positive, max 24             |
| `notes`             | `String`     | Optional, max 500 characters           |

**Business rules:**

- A trainer log entry records that a trainer conducted a specific training session on a specific date.
- The `date` must correspond to the `dayOfWeek` of the referenced training session.
- Total hours per trainer can be aggregated by date range.

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
       │                   MembershipTypeTrainingSession ── TrainingSession
       │                                                        │ (trainerId)
       └── Payment                                           Trainer
                                                                 │
                                                            TrainerLog
                                                    (trainerId + trainingSessionId)
```

**Key relationships:**

- `Member` 1──N `MemberSubscription` N──1 `MembershipType` (a member can hold many subscriptions)
- `MemberSubscription` 1──N `Payment` (payments are per subscription)
- `MembershipType` M──N `TrainingSession` (via join table)
- `MembershipType` N──1 `Unit` (each membership type has one time unit)
- `MembershipType` N──1 `MembershipTypeStatus` (each membership type has one lifecycle status)
- `Member` 1──N `MemberStatusHistory` N──1 `Status`

---

## GraphQL Operations

### Queries

| Query                    | Arguments                              | Returns                 | Description                                                                             |
| ------------------------ | -------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------- |
| `members`                | `status: String`                       | `[Member]`              | List all members, optionally filtered by current status                                 |
| `memberById`             | `id: ID!`                              | `Member`                | Get a single member by ID (includes current status)                                     |
| `memberStatusHistory`    | `memberId: ID!`                        | `[MemberStatusEntry]`   | Full status audit trail for a member                                                    |
| `memberSubscriptions`    | `memberId: ID!, active: Boolean`       | `[MemberSubscription]`  | List subscriptions for a member, optionally filter by active state (`endDate >= today`) |
| `membershipTypes`        | `status: String`                       | `[MembershipType]`      | List membership types, optionally filtered by status (e.g. `ACTIVE`)                    |
| `paymentsBySubscription` | `memberSubscriptionId: ID!`            | `[Payment]`             | List all payments for a specific subscription                                           |
| `paymentsByMember`       | `memberId: ID!`                        | `[Payment]`             | List all payments across all subscriptions for a member                                 |
| `outstandingPayments`    | —                                      | `[MemberPaymentStatus]` | Active subscriptions with unpaid dues                                                   |
| `trainingSessions`       | —                                      | `[TrainingSession]`     | List all training sessions                                                              |
| `trainers`               | —                                      | `[Trainer]`             | List all trainers                                                                       |
| `trainerHours`           | `trainerId: ID!, from: Date, to: Date` | `TrainerHoursSummary`   | Total hours worked by a trainer in a date range                                         |

### Mutations

| Mutation                       | Input                                           | Returns              | Description                                                        |
| ------------------------------ | ----------------------------------------------- | -------------------- | ------------------------------------------------------------------ |
| `createMember`                 | `CreateMemberInput!`                            | `Member`             | Register a new member (auto-creates ACTIVE status)                 |
| `updateMember`                 | `id: ID!, UpdateMemberInput!`                   | `Member`             | Update member details                                              |
| `changeMemberStatus`           | `ChangeMemberStatusInput!`                      | `MemberStatusEntry`  | Record a status transition with optional reason                    |
| `deleteMember`                 | `id: ID!`                                       | `DeleteMemberResult` | GDPR erasure: anonymize personal data, end subscriptions           |
| `subscribeMember`              | `SubscribeMemberInput!`                         | `MemberSubscription` | Subscribe a member to a membership type                            |
| `endSubscription`              | `id: ID!`                                       | `MemberSubscription` | Early termination (sets `endDate` to today if still in the future) |
| `createMembershipType`         | `CreateMembershipTypeInput!`                    | `MembershipType`     | Define a new membership type (starts as `DRAFT`)                   |
| `changeMembershipTypeStatus`   | `id: ID!, status: String!`                      | `MembershipType`     | Transition membership type status (e.g. DRAFT → ACTIVE)            |
| `assignTrainingToMembership`   | `membershipTypeId: ID!, trainingSessionId: ID!` | `MembershipType`     | Link a training session to a membership type                       |
| `removeTrainingFromMembership` | `membershipTypeId: ID!, trainingSessionId: ID!` | `MembershipType`     | Unlink a training session from a membership type                   |
| `recordPayment`                | `RecordPaymentInput!`                           | `Payment`            | Record a payment for a subscription                                |
| `createTrainingSession`        | `CreateTrainingSessionInput!`                   | `TrainingSession`    | Create a recurring training session                                |
| `createTrainer`                | `CreateTrainerInput!`                           | `Trainer`            | Register a new trainer                                             |
| `logTrainerHours`              | `LogTrainerHoursInput!`                         | `TrainerLog`         | Log hours worked by a trainer for a session                        |

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

### SubscribeMemberInput

| Field              | Type         | Constraints                                                                                                          |
| ------------------ | ------------ | -------------------------------------------------------------------------------------------------------------------- |
| `memberId`         | `ID!`        | Not null, references a Member                                                                                        |
| `membershipTypeId` | `ID!`        | Not null, references a MembershipType                                                                                |
| `startDate`        | `Date!`      | Not null, when the subscription period begins                                                                        |
| `endDate`          | `Date`       | Optional; defaults to `startDate + duration` (in the membership type's unit). If provided, must be after `startDate` |
| `agreedPrice`      | `BigDecimal` | Optional; prorated price — if null, full membership type price applies                                               |

### MemberPaymentStatus (Response Type)

| Field            | Type                 | Description                                                                                 |
| ---------------- | -------------------- | ------------------------------------------------------------------------------------------- |
| `member`         | `Member`             | The member                                                                                  |
| `subscription`   | `MemberSubscription` | The active subscription                                                                     |
| `membershipType` | `MembershipType`     | The membership type (via subscription)                                                      |
| `amountDue`      | `BigDecimal`         | Expected amount for the current period (`agreedPrice` if set, else membership type `price`) |
| `amountPaid`     | `BigDecimal`         | Total amount paid for the current period                                                    |
| `outstanding`    | `BigDecimal`         | `amountDue - amountPaid`                                                                    |

### RecordPaymentInput

| Field                  | Type          | Constraints                               |
| ---------------------- | ------------- | ----------------------------------------- |
| `memberSubscriptionId` | `ID!`         | Not null, references a MemberSubscription |
| `amount`               | `BigDecimal!` | Not null, positive (> 0)                  |
| `currency`             | `String`      | Optional, defaults to `EUR`               |
| `paymentDate`          | `Date!`       | Not null                                  |
| `notes`                | `String`      | Optional                                  |

### TrainerHoursSummary (Response Type)

| Field          | Type         | Description                              |
| -------------- | ------------ | ---------------------------------------- |
| `trainer`      | `Trainer`    | The trainer                              |
| `totalHours`   | `BigDecimal` | Sum of hours worked in the queried range |
| `sessionCount` | `Int`        | Number of sessions logged                |
| `from`         | `Date`       | Start of the queried range               |
| `to`           | `Date`       | End of the queried range                 |

### DeleteMemberResult (Response Type)

| Field              | Type       | Description                              |
| ------------------ | ---------- | ---------------------------------------- |
| `memberId`         | `ID`       | The TSID of the anonymized member        |
| `anonymizedAt`     | `DateTime` | Timestamp when the erasure was performed |
| `fieldsAnonymized` | `[String]` | List of field names that were anonymized |

---

## Authorization & Roles

Phase 1 defines two roles. Authentication itself (login, tokens, identity provider) is out of scope for Phase 1 — the system assumes the caller's role is already resolved by the infrastructure layer.

| Role       | Description                                                                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **ADMIN**  | Club administrator. Full access to all operations — manages members, membership types, subscriptions, payments, training sessions, and trainers. |
| **MEMBER** | A registered club member. Can view their own data (profile, subscriptions, payments, training schedule).                                         |

### Operation Access Matrix

Each GraphQL operation is restricted to one or more roles. Operations not listed are accessible to both roles.

#### Queries

| Query                    | ADMIN | MEMBER | Notes                                                      |
| ------------------------ | :---: | :----: | ---------------------------------------------------------- |
| `members`                |   ✓   |        | Admin-only — list all members                              |
| `memberById`             |   ✓   |   ✓    | Members can only query their own ID                        |
| `memberStatusHistory`    |   ✓   |   ✓    | Members can only view their own history                    |
| `memberSubscriptions`    |   ✓   |   ✓    | Members can only view their own subscriptions              |
| `membershipTypes`        |   ✓   |   ✓    | Members see only `ACTIVE` types; admins see all statuses   |
| `paymentsBySubscription` |   ✓   |   ✓    | Members can only view payments for their own subscriptions |
| `paymentsByMember`       |   ✓   |   ✓    | Members can only query their own payments                  |
| `outstandingPayments`    |   ✓   |        | Admin-only — aggregated view across all members            |
| `trainingSessions`       |   ✓   |   ✓    | Read-only for both roles                                   |
| `trainers`               |   ✓   |   ✓    | Read-only for both roles                                   |
| `trainerHours`           |   ✓   |        | Admin-only — trainer hour tracking                         |

#### Mutations

| Mutation                       | ADMIN | MEMBER | Notes                                                                                    |
| ------------------------------ | :---: | :----: | ---------------------------------------------------------------------------------------- |
| `createMember`                 |   ✓   |        | Admin registers new members                                                              |
| `updateMember`                 |   ✓   |   ✓    | Members can update their own contact details (email, phone); admin can update any member |
| `changeMemberStatus`           |   ✓   |        | Admin-only — status transitions                                                          |
| `deleteMember`                 |   ✓   |   ✓    | Members can request their own GDPR erasure; admin can erase any member                   |
| `subscribeMember`              |   ✓   |        | Admin-only — controls who subscribes and at what price                                   |
| `endSubscription`              |   ✓   |        | Admin-only — early termination                                                           |
| `createMembershipType`         |   ✓   |        | Admin-only                                                                               |
| `changeMembershipTypeStatus`   |   ✓   |        | Admin-only                                                                               |
| `assignTrainingToMembership`   |   ✓   |        | Admin-only                                                                               |
| `removeTrainingFromMembership` |   ✓   |        | Admin-only                                                                               |
| `recordPayment`                |   ✓   |        | Admin-only — payment recording                                                           |
| `createTrainingSession`        |   ✓   |        | Admin-only                                                                               |
| `createTrainer`                |   ✓   |        | Admin-only                                                                               |
| `logTrainerHours`              |   ✓   |        | Admin-only — trainer hour logging                                                        |

### Authorization Rules

1. **Admin-only pricing**: Only an admin can set or override `agreedPrice` when creating a subscription. This ensures that proration decisions are made by the club, not by the member.
2. **Admin-only subscription management**: Only an admin can create, renew, or terminate subscriptions. Members cannot self-subscribe or self-terminate in Phase 1.
3. **Member self-service scope**: A member can view their own profile, subscriptions, payment history, and the training schedule. They can update their own contact details (email, phone number) and request their own GDPR erasure.
4. **Member data isolation**: When a member queries `memberById`, `memberSubscriptions`, `paymentsByMember`, or `paymentsBySubscription`, the system enforces that the requested data belongs to the authenticated member. Attempting to access another member's data returns an authorization error.
5. **Membership type visibility**: Members see only `ACTIVE` membership types via `membershipTypes`. `DRAFT` and `INACTIVE` types are hidden from members — they are administrative concerns.
6. **Future self-service (out of scope)**: Phase 2 may introduce member self-registration, online payment, and subscription renewal requests. Phase 1 assumes all write operations go through an administrator.

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
16. **Outstanding calculation**: Per subscription: effective price (`agreedPrice` if set, else membership type `price`) minus the sum of all payments linked to that subscription. No rolling-period logic — each subscription is a single billing period.

### Prorated Pricing

17. **agreedPrice semantics**: `agreedPrice` on `MemberSubscription` overrides the membership type's `price` for that specific subscription. It is intended for mid-period joins where the administrator calculates a reduced fee externally.
18. **Null means full price**: If `agreedPrice` is null, the membership type's `price` is used for billing calculations.
19. **No automatic proration**: The system does not calculate prorated amounts. The administrator determines the prorated price and provides it when creating the subscription.
20. **Renewal**: When a subscription is renewed (new subscription for the next period), `agreedPrice` on the new subscription should typically be null (full price) unless the administrator explicitly sets it again.

### Membership Types

21. **Training session linkage**: Adding or removing training sessions from a membership type uses dedicated mutations — no comma-separated fields.
22. **Duration semantics**: `duration` + `unit` define the default period length for new subscriptions. They are not used in billing calculations — billing uses the subscription's own `startDate`/`endDate` and effective price.
23. **Status transitions**: Only valid transitions are allowed: `DRAFT → ACTIVE`, `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`, `DRAFT → INACTIVE`. Transitioning back to `DRAFT` is not allowed.
24. **INACTIVE with existing subscriptions**: Changing a membership type to `INACTIVE` does not end existing subscriptions. They continue to their `endDate` and payments are still accepted.

### Training Sessions

25. **Time ordering**: `endTime` must be after `startTime`; same value is invalid.
26. **Invalid day-of-week in log**: A trainer log entry's `date` must fall on the same weekday as the referenced session's `dayOfWeek`.
27. **Future date logging**: Trainer hours cannot be logged for a future date.

### Trainers

28. **Duplicate email**: Same uniqueness constraint as members.
29. **Trainer deletion**: Not in scope for Phase 1 — trainers can only be created, not removed.

### GDPR — Right to Erasure (Art. 17 DSGVO)

30. **Anonymization, not physical deletion**: A member's row is **not** deleted. Instead, all personal data fields are overwritten with anonymized values to preserve referential integrity (foreign keys from payments, subscriptions, status history). This is compliant with Art. 17 because no personal data remains that could identify the individual.
31. **Anonymized values**: `firstName` → `"DELETED"`, `lastName` → `"DELETED"`, `email` → `"deleted-{id}@anonymous.local"` (keeps uniqueness constraint satisfied), `phoneNumber` → `null`.
32. **Status history anonymization**: The `reason` field in all `MemberStatusHistory` entries for the member is set to `null` (may contain free-text personal data). A final `DELETED` status entry is added.
33. **Subscription deactivation**: All active subscriptions for the member are ended (`endDate` = today).
34. **Payment records retained**: Payment rows are **not** deleted or anonymized. Austrian tax law (BAO §132) requires financial records to be retained for 7 years. Since `Payment` links to `MemberSubscription` (not directly to personal data), no personal information leaks through payment records after anonymization.
35. **Idempotency**: Calling `deleteMember` on an already-deleted member (status = `DELETED`) returns success without further changes.
36. **No recovery**: Anonymization is irreversible. The mutation should require explicit confirmation or be clearly documented as a destructive action.
37. **Terminal status**: Once a member has status `DELETED`, no further status transitions, updates, or new subscriptions are allowed.
38. **Trainer erasure**: Not in scope for Phase 1. Trainers who request erasure will be handled manually or in a future phase.

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
          "agreedPrice": null
        },
        {
          "membershipType": {
            "name": "Training",
            "price": 360.0,
            "duration": 1,
            "unit": { "name": "YEARS" }
          },
          "startDate": "2024-01-01",
          "agreedPrice": null
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
          "agreedPrice": null
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

Note: Karl's `amountDue` is €267 (his `agreedPrice`), not the full €400. Outstanding = effective price − sum of payments.

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

### Example 7 — Log trainer hours and query summary

**Input (mutation):**

```graphql
mutation {
  logTrainerHours(
    input: {
      trainerId: "38792471048200"
      trainingSessionId: "38792471048300"
      date: "2026-04-07"
      hoursWorked: 1.5
    }
  ) {
    id
    hoursWorked
  }
}
```

**Input (query):**

```graphql
query {
  trainerHours(
    trainerId: "38792471048200"
    from: "2026-04-01"
    to: "2026-04-30"
  ) {
    trainer {
      firstName
      lastName
    }
    totalHours
    sessionCount
  }
}
```

### Example 8 — Validation error: duplicate email

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

### Example 9 — GDPR erasure: delete a member

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

---

## Complexity Targets

Not applicable in the traditional algorithmic sense. Performance targets for Phase 1:

| Operation             | Target                          |
| --------------------- | ------------------------------- |
| Single-entity queries | < 50 ms                         |
| List queries (< 200)  | < 200 ms                        |
| Mutations             | < 100 ms                        |
| Outstanding payments  | < 500 ms (involves aggregation) |

Database indices should cover: `member.email`, `member_status_history.member_id`, `member_status_history.changed_at`, `member_subscription.member_id`, `member_subscription.membership_type_id`, `member_subscription.end_date`, `payment.member_subscription_id`, `trainer_log.trainer_id`, `trainer_log.date`, `membership_type_training_session` (composite PK).

---

## Architecture Notes

### Primary Key Library

Add `hypersistence-utils` to `build.gradle`:

```groovy
implementation 'io.hypersistence:hypersistence-utils-hibernate-63:<latest>'
```

All entities use `@Tsid` on a `Long id` field.

### Domain packages (following project DDD conventions)

```
at.mavila.dbchatbox.domain.club.member        — Member entity, service, validation
at.mavila.dbchatbox.domain.club.status         — Status entity, MemberStatusHistory entity, service
at.mavila.dbchatbox.domain.club.unit           — Unit entity (reference table for time units)
at.mavila.dbchatbox.domain.club.subscription   — MemberSubscription entity, service
at.mavila.dbchatbox.domain.club.membership     — MembershipType entity, MembershipTypeStatus, MembershipTypeTrainingSession, service
at.mavila.dbchatbox.domain.club.payment        — Payment entity, service, outstanding-dues logic
at.mavila.dbchatbox.domain.club.training       — TrainingSession entity, service
at.mavila.dbchatbox.domain.club.trainer        — Trainer entity, TrainerLog, hours aggregation
```

### Database migrations

Flyway migrations under `src/main/resources/db/migration/` for all tables including:

- `status` (with seed data: ACTIVE, INACTIVE, DELETED)
- `member_status_history` (pivot table)
- `member_subscription` (links member to membership type)
- `unit` (with seed data: DAYS, WEEKS, MONTHS, YEARS)
- `membership_type_status` (with seed data: DRAFT, ACTIVE, INACTIVE)
- `membership_type_training_session` (join table)

### GDPR Compliance

The system implements Art. 17 DSGVO (right to erasure) via **in-place anonymization**:

- Personal data fields on `Member` are overwritten (not deleted) to maintain foreign key integrity.
- Payment records are **retained** per Austrian tax retention requirements (BAO §132, 7-year minimum).
- The `deleteMember` mutation is a single transactional operation: anonymize member → null out status history reasons → end active subscriptions → set status to `DELETED`.
- After anonymization, the member row is effectively a tombstone — it cannot be updated, subscribed, or re-activated.
- Audit log: the `MemberStatusHistory` entry with status `DELETED` serves as the erasure timestamp.

### Future Phases (out of scope)

- **Phase 2**: Automated invoices, payment reminders, member/trainer login portals, online hour logging, member self-registration & self-service.
- **Phase 3**: Online training registration, automated payments, statistics dashboards, full online administration.
- **Professional players**: Dedicated player/team entities, tournament participation, performance tracking, special contracts. Deferred until clarified whether professional players require separate treatment.

### Enum Storage Pattern

The reference tables `Status`, `Unit`, and `MembershipTypeStatus` act as **application-level enums**. In the Java domain layer, each is represented as a Java `enum` (e.g. `Status.ACTIVE`, `Unit.MONTHS`, `MembershipTypeStatus.DRAFT`). In the database, the `name` column is stored as `VARCHAR` and mapped via `@Enumerated(EnumType.STRING)`. This pattern ensures:

- **Readability**: Database rows contain human-readable strings (`ACTIVE`, `MONTHS`) rather than opaque integer codes.
- **Type safety**: The Java layer enforces that only declared enum constants can be used.
- **Extensibility**: New values can be added by extending the enum and inserting a matching seed row — no schema change required.
