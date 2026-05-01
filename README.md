# Club Management System

A GraphQL-based club management application built with Spring Boot 4 and Java 25.

## Tech Stack

- **Java 25** / **Spring Boot 4.0.5**
- **Spring for GraphQL** (no REST endpoints)
- **JPA / Hibernate 7** with **Flyway** migrations
- **H2** (dev/test) / **PostgreSQL 16** (production)
- **TSID** for entity ID generation
- **Lombok**, **Jakarta Bean Validation**
- **Spring AI 2.0** with **Anthropic Claude** for the natural-language chatbox

## Getting Started

### Prerequisites

- JDK 25+
- PostgreSQL 16+ (production only; dev/test use embedded H2)
- Anthropic API key (only required to use the chatbox feature — see [Chatbox](#chatbox-natural-language-assistant))

### Build & Run

```bash
# Build (skip tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Run (dev profile, H2 in-memory). The chatbox expects ANTHROPIC_API_KEY in
# the environment; without it the ask query returns a provider-unavailable
# error but the rest of the API still works.
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

The GraphQL endpoint is available at `http://localhost:8080/graphql`.  
The GraphiQL UI is at `http://localhost:8080/graphiql` (dev profile only).  
The H2 console is at `http://localhost:8080/h2-console` (dev profile only, JDBC URL: `jdbc:h2:mem:dbchatbox`).

### Profiles

| Profile         | Database                      | Notes                                                                       |
| --------------- | ----------------------------- | --------------------------------------------------------------------------- |
| `dev` (default) | H2 in-memory (PG compat mode) | H2 console enabled, GraphiQL enabled                                        |
| `test`          | H2 in-memory                  | Used by automated tests                                                     |
| `prod`          | PostgreSQL                    | Requires `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` env vars |

## Domain Model

The system manages a sports/social club with the following core entities:

- **Member** — club members with status lifecycle (ACTIVE → INACTIVE → DELETED)
- **Trainer** — trainers who lead sessions (contact details only)
- **TrainerSettings** — per-trainer compensation and workflow settings (hourly rate, payment mode, auto-approve)
- **MembershipType** — subscription templates (e.g. "Gold Monthly") with pricing, linked sessions, and configurable grace period
- **MemberSubscription** — a member's active subscription to a membership type, with payment verification status
- **Payment** — payments against subscriptions
- **PaymentDocument** — payment proof documents (bank-issued PDFs) uploaded by members for admin verification
- **Session** — recurring weekly training slots
- **SessionOccurrence** — concrete instances of sessions on specific dates
- **TrainerLog** — trainer hour tracking with approval workflow
- **NotificationService** — domain interface for admin alerts and member reminders (logging-only mock in Phase 1)

## GraphQL API

### Example Queries

```graphql
# List active members
query {
  members(status: "ACTIVE") {
    id
    firstName
    lastName
    email
    currentStatus
  }
}

# Get member with subscriptions
query {
  memberById(id: "123") {
    firstName
    lastName
    subscriptions {
      membershipType {
        name
      }
      startDate
      active
    }
  }
}
```

### Example Mutations

```graphql
# Create a member
mutation {
  createMember(
    input: {
      firstName: "Jane"
      lastName: "Smith"
      email: "jane@example.com"
      memberSince: "2024-01-15"
    }
  ) {
    id
    firstName
    lastName
    currentStatus
  }
}

# Change member status
mutation {
  changeMemberStatus(
    input: { memberId: "123", status: INACTIVE, reason: "Moved abroad" }
  ) {
    status
    changedAt
    reason
  }
}

# Register a trainer (also creates initial TrainerSettings)
mutation {
  createTrainer(
    input: {
      firstName: "Bob"
      lastName: "Trainer"
      email: "bob@club.at"
      hourlyRate: 35.00
      paymentMode: "PER_SESSION"
    }
  ) {
    id
    firstName
    settings {
      hourlyRate
      paymentMode
      autoApproveHours
    }
  }
}

# Update trainer compensation settings (admin-only)
mutation {
  updateTrainerSettings(
    trainerId: "456"
    input: { hourlyRate: 40.00, paymentMode: "MONTHLY" }
  ) {
    hourlyRate
    paymentMode
    autoApproveHours
  }
}
```

## GDPR Support

- **Soft-delete**: `deleteMember` sets status to DELETED and anonymises PII
- **Automatic purge**: A scheduled job runs daily to anonymise members deleted beyond the retention period (default: 30 days)
- Subscription history is preserved with anonymised member references

## Project Structure

```
src/main/java/at/mavila/dbchatbox/
├── application/         # Scheduled jobs (GDPR purge)
├── domain/
│   ├── club/
│   │   ├── exception/   # Domain exceptions
│   │   ├── member/      # Member entity, service, GDPR service, repository
│   │   ├── membership/  # MembershipType, status management, grace period
│   │   ├── notification/ # NotificationService interface + logging-only mock
│   │   ├── payment/     # Payment, PaymentDocument, upload/review workflow
│   │   ├── subscription/ # MemberSubscription, payment status tracking
│   │   ├── trainer/     # Trainer, TrainerSettings, TrainerLog
│   │   └── training/    # Session, SessionOccurrence
│   └── support/         # TSID generator, command validator
└── infrastructure/
    └── web/graphql/     # GraphQL controllers, scalar config, error handling
```

## Features

### Payment Verification Workflow

Members can upload bank-issued PDF documents as proof of payment for their subscriptions. The workflow:

1. Subscription is created with `paymentStatus = NOT_PAID`
2. Member uploads a payment document via `uploadPaymentDocument` → status transitions to `IN_REVIEW`
3. Admin reviews via `reviewPaymentDocument` → status transitions to `REVIEWED` (approved) or back to `NOT_PAID` (rejected)

```graphql
# Upload a payment document
mutation {
  uploadPaymentDocument(
    input: {
      memberSubscriptionId: "123"
      fileName: "bank-transfer-receipt.pdf"
      fileContent: "JVBERi0x..." # Base64-encoded PDF
      notes: "Transfer from account AT12 3456"
    }
  ) {
    id
    fileName
    uploadedAt
  }
}

# Review a payment document (admin)
mutation {
  reviewPaymentDocument(
    input: { memberSubscriptionId: "123", approved: true }
  ) {
    id
    paymentStatus
  }
}
```

### Grace Period & Overdue Detection

Each membership type defines a `gracePeriodDays` (default: 30). A subscription is considered **overdue** when `today > startDate + gracePeriodDays` and `paymentStatus ≠ REVIEWED`.

```graphql
# Query overdue subscriptions (admin)
query {
  overdueSubscriptions {
    member {
      firstName
      lastName
    }
    membershipType {
      name
    }
    paymentStatus
    dueDate
    daysOverdue
  }
}

# Query subscriptions pending payment review (admin)
query {
  pendingPaymentReviews {
    id
    member {
      firstName
      lastName
    }
    paymentStatus
  }
}
```

### Notification System

The system defines a `NotificationService` interface with triggers for:

- **Overdue payment detection** — daily cron job alerts admin about expired grace periods
- **Payment document upload** — immediate admin notification when a member uploads proof
- **Payment reminders** — recurring reminders for members with unpaid subscriptions
- **Membership type publication** — notify active members when a new membership type goes live

Phase 1 uses a logging-only mock implementation. Real email delivery can be added via `@Primary` or Spring profile without changing domain code.

### Optimistic Locking

All entities use JPA `@Version` (stored as `Short`) for optimistic locking, preventing lost updates in concurrent modification scenarios.

## Chatbox — Natural-Language Assistant

A single GraphQL query — `ask(input: AskInput!): AskResult!` — accepts a free-form
question in any language and returns a synthesised answer built from the
existing read-only domain operations. Internally the assistant uses
[Spring AI 2.0](https://spring.io/projects/spring-ai) (milestone M3) to call
Anthropic Claude. Spec: [`src/specs/chatbox/Chatbox.md`](src/specs/chatbox/Chatbox.md).

### How it works (high level)

1. The client sends `ask(input: { prompt: "show me all members who haven't paid yet" })`.
2. `ChatAssistantController` validates and hands off to `ChatAssistantService`.
3. `ChatAssistantService` checks a per-hour global rate limit, then sends the
   prompt to the LLM via Spring AI's `ChatClient`. The client was configured
   at startup with the system prompt and the full tool catalog (every
   `@Tool`-annotated method in `domain.chatbox.tools`).
4. Spring AI drives the tool-calling loop internally: if the model requests a
   tool, Spring AI dispatches the call to the matching Java method on the real
   Spring bean (so `@Transactional`, validation, etc. apply), feeds the
   JSON-serialised result back to the LLM, and continues until the model
   emits its final text.
5. The service returns an `AskResult` with the answer, the model id, token
   usage reported by the provider, and end-to-end latency.

No new business logic is introduced: the domain remains the single source of
truth. The LLM only orchestrates.

### Spring AI version

The project uses **Spring AI 2.0.0-M3** — the first Spring AI line built on
Spring Framework 7 / Spring Boot 4. Earlier versions (1.0.x, 1.1.x) were
compiled against Framework 6 and fail at runtime on Boot 4 with
`NoSuchMethodError`. The milestone repo is declared in `build.gradle`; once
2.0 reaches GA the repo entry can be removed.

### Setup

Set your Anthropic API key before running:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

Copy `.env.example` to `.env` for a reusable local configuration. Switching
providers (OpenAI, Ollama, Azure OpenAI, Bedrock, …) is a three-line change:

1. Swap the Spring AI starter in `build.gradle`
   (`spring-ai-starter-model-anthropic` → `spring-ai-starter-model-openai`, …).
2. Change `spring.ai.model.chat` in `application.properties`.
3. Set the matching `spring.ai.<provider>.*` properties (api key, model id, …).

No Java code changes required — `ChatClient` is provider-agnostic.

### Example: unpaid members

```graphql
query {
  ask(input: {
    prompt: "Show me all members who have not paid yet this period"
  }) {
    answer
    model
    latencyMillis
    promptTokens
    completionTokens
  }
}
```

Response (example):

```json
{
  "data": {
    "ask": {
      "answer": "Three members currently have unpaid subscriptions:\n- Anna Müller — Gold Monthly, overdue 3 days\n- John Smith — Silver Monthly, within grace period\n- Lucas Weber — Training Only, IN_REVIEW",
      "model": "claude-haiku-4-5-20251001",
      "latencyMillis": 1104,
      "promptTokens": 1820,
      "completionTokens": 128
    }
  }
}
```

### Available tools (Phase 1, read-only)

| Tool class                | Methods                                                                               |
| ------------------------- | ------------------------------------------------------------------------------------- |
| `MemberQueryTools`        | `listMembers`, `memberById`, `memberStatusHistory`                                    |
| `MembershipQueryTools`    | `listMembershipTypes`                                                                 |
| `SubscriptionQueryTools`  | `subscriptionsForMember`, `overdueSubscriptions`, `pendingPaymentReviews`             |
| `PaymentQueryTools`       | `paymentsForSubscription`, `paymentsForMember`, `outstandingPayments`                 |
| `SessionQueryTools`       | `listSessions`, `sessionsForMember`, `nextSessionForMember`                           |
| `TrainerQueryTools`       | `listTrainers`, `trainerHours`, `pendingTrainerLogs`, `trainerPaymentSummary`         |

All tools are **read-only**. Mutating operations (creating members, recording
payments, …) are **not** exposed to the LLM — they remain available only
through the typed GraphQL API.

### Configuration

| Property                                       | Default                     | Purpose                                          |
| ---------------------------------------------- | --------------------------- | ------------------------------------------------ |
| `spring.ai.model.chat`                         | `anthropic`                 | Which Spring AI starter to activate              |
| `spring.ai.anthropic.api-key`                  | (env `ANTHROPIC_API_KEY`)   | Anthropic API key                                |
| `spring.ai.anthropic.chat.options.model`       | `claude-haiku-4-5-20251001` | Model id                                         |
| `spring.ai.anthropic.chat.options.temperature` | `0.2`                       | LLM temperature (low = deterministic)            |
| `spring.ai.anthropic.chat.options.max-tokens`  | `1024`                      | Per-call token cap                               |
| `app.chatbox.enabled`                          | `true`                      | Master on/off switch                             |
| `app.chatbox.rate-limit.requests-per-hour`     | `30`                        | Global sliding-window limit                      |

### Current Phase 1 limitations

- **No authentication** — there is no per-user identity, so the rate limit is
  global across all callers. Phase 2 will add per-principal limits and role-gated
  tool sets (see [Authorization](src/specs/chatbox/Chatbox.md#authorization)).
- **No conversation memory** — each `ask` call is independent.
- **No mutating tools** — the LLM cannot create, update, or delete data.
- **Per-call tool trace** — the `toolCalls` field in `AskResult` is always an
  empty list in Phase 1 (the internal trace is logged but not yet plumbed
  through the GraphQL type). The schema field exists so the frontend can
  start consuming it without a later schema change.

## Testing

```bash
./gradlew test
```

Tests include unit tests for domain services and integration tests for the GraphQL API.

## License

See [LICENSE](LICENSE).
