# Club Management System

A multi-tenant, GraphQL-only club management backend built with Spring Boot 4 and Java 25.
Every operation is scoped to a **tenant** (a sports club) and protected by JWT or API-key
authentication.

---

## Tech Stack

- **Java 25** / **Spring Boot 4.0.5**
- **Spring for GraphQL** (no REST endpoints)
- **Spring Security 7** — stateless JWT resource server + custom API-key filter
- **Keycloak 26.4** — one realm per tenant; ROPC grant for the data loader / SPA dev
- **JPA / Hibernate 7** with **Flyway** migrations (V1–V7)
- **H2** (dev/test, PostgreSQL-compat mode) / **PostgreSQL 16** (production)
- **TSID** for collision-free distributed ID generation
- **Lombok**, **Jakarta Bean Validation**
- **Spring AI 2.0** with **Anthropic Claude** for the natural-language chatbox

---

## Quick Start (devcontainer — recommended)

The repository ships a fully configured devcontainer. One `docker compose up` starts:

| Service      | Port  | Purpose                                        |
|--------------|-------|------------------------------------------------|
| `dbchatbox-dev` | 8080 | Spring Boot app container (runs `sleep infinity`) |
| `keycloak`   | 8088  | Keycloak 26.4 with three realms pre-imported   |
| `postgres`   | 5432  | PostgreSQL 16 (optional; dev uses H2)          |

### 1 — Start the services

Open the project in VS Code with the Dev Containers extension and rebuild, or run:

```bash
docker compose -f .devcontainer/docker-compose.yml up -d
```

Keycloak takes ~30 s to become healthy. The three realms
(`wat-simmering`, `union-rot-weiss`, `asv-pressbaum-badminton`) are imported automatically
from `.devcontainer/keycloak/import/`.

### 2 — Start the application

Inside the devcontainer terminal:

```bash
# Optional — chatbox feature needs this; the rest of the API works without it
export ANTHROPIC_API_KEY=sk-ant-...

./gradlew bootRun
```

The dev profile is active automatically (`SPRING_PROFILES_ACTIVE=dev` is set in
`docker-compose.yml`). H2 resets on every restart; Flyway recreates the schema and seeds the
three tenant rows.

Endpoints:

| URL | Purpose |
|-----|---------|
| `http://localhost:8080/graphql` | GraphQL API (POST) |
| `http://localhost:8080/graphiql` | GraphiQL playground (GET) |
| `http://localhost:8080/h2-console` | H2 console (JDBC URL: `jdbc:h2:mem:dbchatbox`) |
| `http://localhost:8088` | Keycloak admin console (admin / admin) |

### 3 — Load demo data

After the app has started, run the data loader. It authenticates against each tenant's Keycloak
realm, creates trainers, members, subscriptions, and one API key per tenant, then writes the
generated credentials to `scripts/keycloak-credentials.txt` (git-ignored):

```bash
./scripts/data_loader.sh
```

To also run verification queries after loading:

```bash
./scripts/data_loader.sh --verify
```

The credentials file will contain ready-to-use bearer tokens and API keys for all three tenants.

---

## Security Architecture

### Multi-tenancy

Every request is associated with exactly one tenant. The three demo tenants are seeded by the
V7 Flyway migration:

| ID | Slug | Display name | Keycloak realm |
|----|------|-------------|----------------|
| 1 | `wat-simmering` | WAT Simmering | `wat-simmering` |
| 2 | `union-rot-weiss` | Union Rot-Weiss | `union-rot-weiss` |
| 3 | `asv-pressbaum-badminton` | ASV Pressbaum Badminton | `asv-pressbaum-badminton` |

All 11 domain entity tables carry a `tenant_id` column (NOT NULL, FK → `tenant`). The
`Auditable` `@PrePersist` callback reads `TenantContext` (a `ThreadLocal<Long>`) and stamps
the column automatically — application code never sets `tenant_id` directly.
`TenantScopedFinder` ensures every by-ID lookup filters on the current tenant's ID, so a
guessed ID from another tenant returns "not found".

### Human authentication (JWT via Keycloak)

Each tenant's Keycloak realm has a public client `club-spa` with direct-access grants enabled.
A typical login flow:

```graphql
# 1. Authenticate — no Authorization header required
mutation {
  login(input: {
    tenantSlug: "wat-simmering"
    username:   "admin.wat"
    password:   "Admin#WAT2026"
  }) {
    accessToken
    refreshToken
    expiresIn
  }
}
```

Use the returned `accessToken` as a Bearer header on every subsequent request:

```
Authorization: Bearer <accessToken>
```

The app validates the token against that realm's JWKS endpoint
(`http://localhost:8088/realms/<realm>`) via `TenantAuthenticationManagerResolver`.
Issuers not found in the `tenant` table are rejected. `TenantResolutionFilter` then reads
the `iss` claim, looks up the tenant, and sets `TenantContext` for the request lifetime.

Refresh before expiry (access tokens live 5 minutes in dev):

```graphql
mutation {
  refreshToken(input: {
    tenantSlug:   "wat-simmering"
    refreshToken: "<refreshToken>"
  }) {
    accessToken
    expiresIn
  }
}
```

Each realm carries four roles propagated as Spring authorities (`ROLE_ADMIN`, `ROLE_MEMBER`,
`ROLE_TRAINER`, `ROLE_API_CLIENT`). The current policy gates all operations on
`isAuthenticated()` — per-role enforcement is a later iteration.

### Machine-to-machine authentication (API keys)

API keys are meant for server-to-server integrations (CI pipelines, external dashboards).
They do not involve Keycloak at runtime — authentication is local.

**Key format:** `cmk.<tenantSlug>.<32-char-base64url>`  
Example: `cmk.wat-simmering.x7Kp2MnQrLvBsJdTeXfYaG`

Only the HMAC-SHA256 hash of the raw key is stored. A stolen database yields no usable keys
because the HMAC pepper (`API_KEY_HMAC_SECRET`) is never stored in the database.

**Generate a key** (requires an authenticated admin session):

```graphql
mutation {
  generateApiKey(input: { label: "ci-pipeline" }) {
    rawKey        # ← save this now; it is never shown again
    apiKey {
      id
      label
      scope
      createdAt
    }
  }
}
```

**Authenticate with a key** — send it in the `x-api-key` header (no `Bearer` prefix):

```
x-api-key: cmk.wat-simmering.x7Kp2MnQrLvBsJdTeXfYaG
```

The filter parses the tenant slug from the key prefix, verifies the HMAC, checks that the key
is active and belongs to that tenant, then sets `TenantContext` and grants authorities
`ROLE_M2M` and `SCOPE_READ`. API keys are read-only in this phase.

**Revoke a key:**

```graphql
mutation {
  revokeApiKey(id: "7890123456789012") {
    id
    active   # false after revocation
  }
}
```

**List all keys for the current tenant:**

```graphql
query {
  apiKeys {
    id
    label
    scope
    active
    lastUsedAt
    createdAt
  }
}
```

### Identity queries

After logging in, call `me` to retrieve (or JIT-provision) the current user's identity record.
This is also required for the data loader to link a Keycloak user to a domain member or trainer:

```graphql
query {
  me {
    id
    keycloakSubject   # Keycloak sub claim — needed for linkAppUser
    username
    email
    memberId
    trainerId
  }
}
```

```graphql
# Admin operation: link a Keycloak identity to a domain entity
mutation {
  linkAppUser(input: {
    keycloakSubject: "3f2a1e9c-..."   # from the user's me.keycloakSubject
    memberId: "123456789012345"       # pass memberId OR trainerId, not both
  }) {
    id
    memberId
  }
}
```

```graphql
query {
  currentTenant {
    id
    slug
    name
  }
}
```

### Environment variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `API_KEY_HMAC_SECRET` | **prod only** | `dev-only-hmac-pepper-…` (dev profile) | HMAC pepper for API key hashing. App refuses to start without it in prod. |
| `ANTHROPIC_API_KEY` | No | *(none)* | Enables the chatbox feature. The rest of the API works without it. |

Production must source `API_KEY_HMAC_SECRET` from a secrets manager. The dev-profile default
in `application-dev.properties` is committed intentionally for zero-config local development
and is worthless outside H2 dev data.

---

## Build & Run (without devcontainer)

```bash
# Build (skip tests)
./gradlew build -x test

# Build with tests
./gradlew build

# Run (dev profile, H2)
./gradlew bootRun
```

Requires JDK 25. Keycloak must be started separately, or the JWT resource-server stays idle
(requests without tokens still reach the `login` mutation; JWKS fetching is lazy).

### Profiles

| Profile | Database | Notes |
|---------|----------|-------|
| `dev` | H2 in-memory (PG compat) | H2 console, GraphiQL, PoC HMAC secret |
| `test` | H2 in-memory | Used by `./gradlew test` |
| `prod` | PostgreSQL 16 | Requires `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `API_KEY_HMAC_SECRET` |

---

## Domain Model

The system manages a sports/social club with these core entities. All 11 mutable entity types
extend `Auditable`, which auto-stamps `created_at`, `updated_at`, and `tenant_id` via JPA
lifecycle callbacks.

- **Tenant** — a sports club; root of the tenant dimension; one Keycloak realm per tenant
- **AppUser** — thin link between a Keycloak `sub` and a domain Member or Trainer; JIT-provisioned on first `me` call; no passwords stored
- **ApiKey** — M2M credentials; only the HMAC hash is persisted
- **Member** — club members with status lifecycle (ACTIVE → INACTIVE → DELETED)
- **MemberStatusHistory** — immutable audit trail of every status change
- **Trainer** — trainers who lead sessions (contact details only)
- **TrainerSettings** — per-trainer compensation settings (hourly rate, payment mode, auto-approve)
- **MembershipType** — subscription templates with pricing, linked sessions, and configurable grace period
- **MemberSubscription** — a member's active subscription, with payment verification status
- **Payment** — payments recorded against subscriptions
- **PaymentDocument** — bank-issued PDFs uploaded by members for admin review
- **Session** — recurring weekly training slots
- **SessionOccurrence** — concrete session instances on specific dates
- **TrainerLog** — trainer hour tracking with approve/reject workflow

---

## GraphQL API

### Authentication operations

See [Security Architecture](#security-architecture) for full examples.

| Operation | Type | Auth required |
|-----------|------|---------------|
| `login` | Mutation | None |
| `refreshToken` | Mutation | None |
| `me` | Query | JWT |
| `currentTenant` | Query | JWT or API key |
| `generateApiKey` | Mutation | JWT (admin) |
| `revokeApiKey` | Mutation | JWT (admin) |
| `linkAppUser` | Mutation | JWT (admin) |
| `apiKeys` | Query | JWT or API key |

All other operations require `isAuthenticated()` (JWT or API key).

### Domain operations (examples)

```graphql
# List active members
query {
  members(status: "ACTIVE") {
    id firstName lastName email currentStatus createdAt
  }
}

# Create a member
mutation {
  createMember(input: {
    firstName: "Jane"
    lastName:  "Smith"
    email:     "jane@example.com"
    memberSince: "2024-01-15"
  }) {
    id firstName lastName currentStatus
  }
}

# Register a trainer
mutation {
  createTrainer(input: {
    firstName: "Bob"
    lastName:  "Trainer"
    email:     "bob@club.at"
    hourlyRate: 35.00
    paymentMode: "PER_SESSION"
  }) {
    id firstName settings { hourlyRate paymentMode }
  }
}

# Query overdue subscriptions
query {
  overdueSubscriptions {
    member { firstName lastName }
    membershipType { name }
    paymentStatus
    dueDate
    daysOverdue
  }
}
```

---

## Features

### Payment Verification Workflow

1. Subscription created → `paymentStatus = NOT_PAID`
2. Member uploads proof → `uploadPaymentDocument` → status → `IN_REVIEW`
3. Admin approves/rejects → `reviewPaymentDocument` → status → `REVIEWED` / `NOT_PAID`

### Grace Period & Overdue Detection

Each `MembershipType` defines `gracePeriodDays`. A subscription is overdue when
`today > startDate + gracePeriodDays` and `paymentStatus ≠ REVIEWED`.

### GDPR Support

- `deleteMember` sets status to `DELETED` and anonymises PII immediately
- A scheduled job runs daily at 02:00 to anonymise members deleted beyond the retention window
  (default: 30 days, configurable via `app.gdpr.retention-days`)
- Subscription history is preserved with anonymised member references

### Optimistic Locking

All entities carry `@Version` (`Short`) for optimistic locking. Concurrent modifications
receive a conflict error rather than silently overwriting each other.

### Audit Timestamps

`Auditable` auto-sets `created_at` (once at insert) and `updated_at` (on every write) via
`@PrePersist` / `@PreUpdate`. No application code sets these fields.

---

## Chatbox — Natural-Language Assistant

`ask(input: AskInput!): AskResult!` accepts a free-form question and returns a synthesised
answer built from read-only domain operations. Requires authentication (JWT or API key).

```graphql
query {
  ask(input: { prompt: "Show me all members who have not paid yet this period" }) {
    answer
    model
    latencyMillis
    promptTokens
    completionTokens
  }
}
```

The chatbox requires `ANTHROPIC_API_KEY` to be set. Without it, the `ask` query returns a
provider-unavailable error; the rest of the API is unaffected.

### How it works

1. `ChatAssistantController` validates and forwards to `ChatAssistantService`.
2. The service checks a global rate limit (default: 30 requests/hour), then sends the prompt to
   Anthropic Claude via Spring AI's `ChatClient`.
3. Spring AI drives the tool-calling loop: Claude may call any `@Tool`-annotated read-only
   method, gets the result, and continues until it emits a final text answer.
4. The response is returned as `AskResult` with the answer, model id, token counts, and latency.

### Available tools (read-only)

| Tool class | Methods |
|-----------|---------|
| `MemberQueryTools` | `listMembers`, `memberById`, `memberStatusHistory` |
| `MembershipQueryTools` | `listMembershipTypes` |
| `SubscriptionQueryTools` | `subscriptionsForMember`, `overdueSubscriptions`, `pendingPaymentReviews` |
| `PaymentQueryTools` | `paymentsForSubscription`, `paymentsForMember`, `outstandingPayments` |
| `SessionQueryTools` | `listSessions`, `sessionsForMember`, `nextSessionForMember` |
| `TrainerQueryTools` | `listTrainers`, `trainerHours`, `pendingTrainerLogs`, `trainerPaymentSummary` |

Mutating operations are not exposed to the LLM.

### Chatbox configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `spring.ai.anthropic.api-key` | `$ANTHROPIC_API_KEY` | Anthropic API key |
| `spring.ai.anthropic.chat.options.model` | `claude-haiku-4-5-20251001` | Model id |
| `spring.ai.anthropic.chat.options.temperature` | `0.2` | Determinism |
| `spring.ai.anthropic.chat.options.max-tokens` | `1024` | Per-call token cap |
| `app.chatbox.enabled` | `true` | Master on/off switch |
| `app.chatbox.rate-limit.requests-per-hour` | `30` | Global sliding-window limit |

---

## Testing

```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests ClassName

# Run a single test method
./gradlew test --tests ClassName.methodName

# Mutation testing
./gradlew pitest
```

Tests use H2 in-memory with the `test` profile. All integration tests extend
`TenantAwareIntegrationTest`, which populates `TenantContext` (tenant id = 1, WAT Simmering)
and provides a `@WithMockUser(roles = "ADMIN")` security context before each test.

---

## License

See [LICENSE](LICENSE).
