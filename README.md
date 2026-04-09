# Club Management System

A GraphQL-based club management application built with Spring Boot 4 and Java 25.

## Tech Stack

- **Java 25** / **Spring Boot 4.0.5**
- **Spring for GraphQL** (no REST endpoints)
- **JPA / Hibernate 7** with **Flyway** migrations
- **H2** (dev/test) / **PostgreSQL 16** (production)
- **TSID** for entity ID generation
- **Lombok**, **Jakarta Bean Validation**

## Getting Started

### Prerequisites

- JDK 25+
- PostgreSQL 16+ (production only; dev/test use embedded H2)

### Build & Run

```bash
# Build (skip tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Run (dev profile, H2 in-memory)
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
- **Trainer** — trainers who lead sessions
- **MembershipType** — subscription templates (e.g. "Gold Monthly") with pricing and linked sessions
- **MemberSubscription** — a member's active subscription to a membership type
- **Payment** — payments against subscriptions
- **Session** — recurring weekly training slots
- **SessionOccurrence** — concrete instances of sessions on specific dates
- **TrainerLog** — trainer hour tracking with approval workflow

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
│   │   ├── membership/  # MembershipType, MemberSubscription, Payment
│   │   ├── trainer/     # Trainer, TrainerLog
│   │   └── training/    # Session, SessionOccurrence
│   └── support/         # TSID generator
└── infrastructure/
    └── web/graphql/     # GraphQL controllers, scalar config, error handling
```

## Testing

```bash
./gradlew test
```

Tests include unit tests for domain services and integration tests for the GraphQL API.

## License

See [LICENSE](LICENSE).
