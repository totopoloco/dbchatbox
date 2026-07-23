# TASK: Data Loader Script (scripts/data_loader.sh)

**Status:** Complete

---

## Goal

Populate the database with realistic, interconnected demo data via the GraphQL API using plain `curl`
commands. The script targets the **dev profile** (H2, `http://localhost:8080/graphql`) and must leave
the database in a state rich enough for frontend development and manual QA — every query in the
schema should return meaningful results.

---

## Scope

No code changes to the application are required. All work is confined to `scripts/data_loader.sh`.

---

## Task List

### T1 — Script skeleton and helpers

**File:** `scripts/data_loader.sh`

- Add shebang `#!/usr/bin/env bash` and `set -euo pipefail`.
- Define `BASE_URL` variable defaulting to `http://localhost:8080/graphql` (overridable via env).
- Define a `gql` helper function that:
  - Accepts a GraphQL query string as `$1`.
  - POSTs it to `$BASE_URL` with `Content-Type: application/json` using `curl -s`.
  - Prints the raw response (caller can pipe to `jq` as needed).
- Define a `gql_id` helper that calls `gql` and extracts `.data.<field>.id` via `jq` — used to
  capture IDs for subsequent mutations.
- Print a status line (`echo "==> [step]"`) before each logical group.

---

### T2 — Create trainers

Create **3 trainers** covering different payment modes and approval settings:

| #   | First | Last     | Email                     | Phone        | Rate  | Mode    | AutoApprove |
| --- | ----- | -------- | ------------------------- | ------------ | ----- | ------- | ----------- |
| 1   | Ana   | Koller   | ana.koller@example.com    | +43 699 1001 | 35.00 | MONTHLY | true        |
| 2   | Ben   | Hartmann | ben.hartmann@example.com  | +43 699 1002 | 40.00 | HOURLY  | false       |
| 3   | Clara | Steiner  | clara.steiner@example.com | +43 699 1003 | 30.00 | MONTHLY | true        |

Mutation: `createTrainer(input: CreateTrainerInput!)`.

Capture returned IDs into shell variables: `TRAINER_ANA`, `TRAINER_BEN`, `TRAINER_CLARA`.

---

### T3 — Create membership types

Create **4 membership types** covering different durations and units, then transition each to ACTIVE
with `changeMembershipTypeStatus`.

| #   | Name               | Price  | Duration | Unit  | Prorated | Grace |
| --- | ------------------ | ------ | -------- | ----- | -------- | ----- |
| 1   | Monthly Basic      | 49.00  | 1        | MONTH | true     | 7     |
| 2   | Quarterly Standard | 129.00 | 3        | MONTH | false    | 14    |
| 3   | Annual Premium     | 449.00 | 12       | MONTH | false    | 30    |
| 4   | 10-Class Pass      | 89.00  | 10       | CLASS | false    | 5     |

Capture returned IDs: `MT_MONTHLY`, `MT_QUARTERLY`, `MT_ANNUAL`, `MT_CLASS`.

---

### T4 — Create sessions

Create **5 sessions** (mix of `TRAINING` and `FREE_GAME`) using `createSession(input: CreateSessionInput!)`.

| #   | Name            | Type      | Day       | Start | End   | Location    | Trainer       |
| --- | --------------- | --------- | --------- | ----- | ----- | ----------- | ------------- |
| 1   | Monday Strength | TRAINING  | MONDAY    | 07:00 | 08:00 | Gym Floor A | TRAINER_ANA   |
| 2   | Wed Yoga Flow   | TRAINING  | WEDNESDAY | 18:00 | 19:00 | Studio B    | TRAINER_CLARA |
| 3   | Fri Crossfit    | TRAINING  | FRIDAY    | 06:30 | 07:30 | Gym Floor A | TRAINER_BEN   |
| 4   | Sat Free Play   | FREE_GAME | SATURDAY  | 10:00 | 12:00 | Court 1     | (none)        |
| 5   | Thu Pilates     | TRAINING  | THURSDAY  | 19:00 | 20:00 | Studio B    | TRAINER_CLARA |

Capture IDs: `SESSION_STRENGTH`, `SESSION_YOGA`, `SESSION_CROSSFIT`, `SESSION_FREE`, `SESSION_PILATES`.

---

### T5 — Assign sessions to membership types

Use `assignSessionToMembership` to link sessions to the types that include them:

- **Monthly Basic** → Monday Strength, Sat Free Play
- **Quarterly Standard** → Monday Strength, Wed Yoga Flow, Fri Crossfit, Sat Free Play
- **Annual Premium** → all 5 sessions
- **10-Class Pass** → Fri Crossfit

---

### T6 — Create session occurrences

Use `createSessionOccurrences(input: CreateSessionOccurrencesInput!)` to bulk-create occurrences for
the next 8 weeks (from script run date, e.g. hard-code `2026-05-06` to `2026-07-01`).
Create occurrences for all 5 sessions.

Then mark a handful of past occurrences as completed with `completeSessionOccurrence` and cancel one
occurrence per session to exercise the `CANCELLED` status.

---

### T7 — Create members

Create **6 members** covering a variety of statuses:

| #   | First  | Last    | Email                     | Phone        | Since      | Status   |
| --- | ------ | ------- | ------------------------- | ------------ | ---------- | -------- |
| 1   | Maria  | Gruber  | maria.gruber@example.com  | +43 676 2001 | 2024-01-15 | ACTIVE   |
| 2   | Thomas | Bauer   | thomas.bauer@example.com  | +43 676 2002 | 2024-03-01 | ACTIVE   |
| 3   | Sophie | Wagner  | sophie.wagner@example.com | +43 676 2003 | 2023-09-10 | ACTIVE   |
| 4   | Lukas  | Mayer   | lukas.mayer@example.com   | +43 676 2004 | 2024-06-20 | INACTIVE |
| 5   | Anna   | Huber   | anna.huber@example.com    | +43 676 2005 | 2025-01-05 | ACTIVE   |
| 6   | Felix  | Schmidt | felix.schmidt@example.com | +43 676 2006 | 2025-11-01 | FROZEN   |

Use `createMember` (creates ACTIVE by default), then use `changeMemberStatus` to set Lukas to
`INACTIVE` (reason: "Long-term absence") and Felix to `FROZEN` (reason: "Payment hold").

Capture IDs: `MEMBER_MARIA`, `MEMBER_THOMAS`, `MEMBER_SOPHIE`, `MEMBER_LUKAS`, `MEMBER_ANNA`,
`MEMBER_FELIX`.

---

### T8 — Subscribe members

Use `subscribeMember` to create active subscriptions:

| Member | Membership Type    | Start      | Agreed Price |
| ------ | ------------------ | ---------- | ------------ |
| Maria  | Annual Premium     | 2026-01-01 | 449.00       |
| Thomas | Quarterly Standard | 2026-04-01 | 129.00       |
| Sophie | Monthly Basic      | 2026-05-01 | 49.00        |
| Anna   | 10-Class Pass      | 2026-04-15 | 89.00        |
| Felix  | Monthly Basic      | 2026-05-01 | 49.00        |

Lukas gets no active subscription (demonstrates `overdueSubscriptions` / no active sub).

Capture subscription IDs: `SUB_MARIA`, `SUB_THOMAS`, `SUB_SOPHIE`, `SUB_ANNA`, `SUB_FELIX`.

---

### T9 — Record payments

Use `recordPayment` to partially or fully pay each subscription:

| Subscription | Amount | Currency | Date       | Notes           |
| ------------ | ------ | -------- | ---------- | --------------- |
| Maria        | 449.00 | EUR      | 2026-01-02 | Full annual fee |
| Thomas       | 129.00 | EUR      | 2026-04-02 | Q2 payment      |
| Sophie       | 49.00  | EUR      | 2026-05-02 | May payment     |
| Anna         | 50.00  | EUR      | 2026-04-16 | Partial payment |

Felix's subscription intentionally has **no payment** — exercises `outstandingPayments` and
`overdueSubscriptions`.

---

### T10 — Upload and review a payment document for Sophie

Use `uploadPaymentDocument` to attach a fake proof PDF for Sophie's subscription:

```
fileName: "may-receipt.pdf"
fileContent: "JVBERi0xLjQK"   # minimal base-64 placeholder (not a real PDF)
notes: "Bank transfer screenshot"
```

Then use `reviewPaymentDocument` with `approved: true` to transition Sophie's subscription to
`REVIEWED`.

---

### T11 — Log trainer hours

Use `submitTrainerHours` for Ben (auto-approve=false → creates PENDING logs) and
`logTrainerHours` (admin path, APPROVED) for Ana and Clara.

Create **one log entry per completed occurrence** for sessions covered in T6. At minimum:

- Ana: 3 approved log entries for Monday Strength occurrences.
- Ben: 2 pending log entries for Fri Crossfit occurrences (then approve one with `approveTrainerLog`,
  reject the other with `rejectTrainerLog` giving a reason).
- Clara: 3 approved log entries for Thu Pilates occurrences.

Capture the rejected Ben log ID and use `resubmitTrainerLog` to resubmit corrected hours — exercises
the full approval workflow.

---

### T12 — Final verification queries (dry-run mode)

At the end of the script, if the first argument is `--verify`, run a selection of read queries and
print their output:

- `members` — list all members with id, name, status
- `trainers` — list all trainers
- `outstandingPayments` — should include Felix
- `overdueSubscriptions` — should include Felix
- `pendingTrainerLogs` — should include Ben's resubmitted log
- `pendingPaymentReviews` — should be empty (Sophie's was reviewed)
- `sessions` — list all sessions

This lets the developer confirm the database was loaded correctly before opening the frontend.

---

## Notes

- The script is **idempotent-friendly by design**: running it twice will fail at the duplicate-email
  checks (members and trainers). Document in the script header that the dev H2 database must be reset
  (restart the app) before re-running.
- All dates are hardcoded relative to the sprint baseline (early May 2026); adjust if the project
  baseline shifts.
- `jq` must be installed (`apt install jq` / `brew install jq`). Add a guard at the top:
  `command -v jq >/dev/null 2>&1 || { echo "jq is required"; exit 1; }`.
- The script must exit with a non-zero code on the first curl error (enforced by `set -euo pipefail`
  and checking HTTP response codes where `curl` alone is insufficient).
