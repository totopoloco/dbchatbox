# Club Management System — Architecture

> **Tech stack:** Java 25 · Spring Boot 4.0.7 · Spring for GraphQL · Spring AI 2.0 · Keycloak 26 · JPA/Hibernate 7 · Flyway · H2 (dev) · PostgreSQL 16 (prod)

> **Identity model:** Keycloak is the **single source of truth for member identity** (name, email, phone, membership dates). The `member` table is a lean, PII-free join stub. See [§7 Member Identity](#7-member-identity--keycloak-as-source-of-truth).

---

## 1. System Context

Who talks to what at the highest level.

```mermaid
graph LR
    subgraph People["Actors"]
        A1(["Club Admin"])
        A2(["Club Member"])
        A3(["Trainer"])
        A4(["M2M Client"])
    end

    APP["Club Management API<br/>Spring Boot 4 · GraphQL-only"]

    subgraph Ext["External Systems"]
        KC["Keycloak 26<br/>Identity Provider<br/>one realm per tenant"]
        AI["Anthropic Claude<br/>LLM · chatbox"]
        DB[("PostgreSQL 16<br/>prod database")]
    end

    A1 -->|"Bearer JWT"| APP
    A2 -->|"Bearer JWT"| APP
    A3 -->|"Bearer JWT"| APP
    A4 -->|"X-API-Key"| APP

    APP -->|"OIDC / Admin REST"| KC
    APP -->|"Spring AI / HTTPS"| AI
    APP -->|"JDBC"| DB
```

---

## 2. Multi-Tenant Identity Model

Three clubs, three Keycloak realms, one Spring Boot application.

```mermaid
graph TD
    subgraph Keycloak["Keycloak 26  —  :8088"]
        R1["Realm: wat-simmering<br/>─────────────────<br/>roles: ADMIN · MEMBER · TRAINER<br/>client: club-spa  (public)<br/>client: club-m2m  (service-account)"]
        R2["Realm: union-rot-weiss<br/>─────────────────<br/>same role + client structure"]
        R3["Realm: asv-pressbaum-badminton<br/>─────────────────<br/>same role + client structure"]
    end

    subgraph DB["Database — tenant table"]
        T1["id=1  wat-simmering<br/>issuer_uri = .../realms/wat-simmering"]
        T2["id=2  union-rot-weiss<br/>issuer_uri = .../realms/union-rot-weiss"]
        T3["id=3  asv-pressbaum-badminton<br/>issuer_uri = .../realms/asv-pressbaum-badminton"]
    end

    R1 -- "iss claim maps to" --> T1
    R2 -- "iss claim maps to" --> T2
    R3 -- "iss claim maps to" --> T3
```

---

## 3. Request Lifecycle — Human Login (JWT path)

```mermaid
sequenceDiagram
    autonumber
    actor User as Club Admin / Member / Trainer
    participant GQL  as Spring GraphQL<br/>/graphql
    participant Auth as AuthController
    participant KC   as Keycloak<br/>/realms/{realm}/token
    participant DB   as Database

    User  ->> GQL:  mutation login(tenantSlug, username, password)
    GQL   ->> Auth: login(@Argument input)
    Auth  ->> DB:   TenantService.requireBySlug(slug)
    DB   -->> Auth: Tenant (keycloakRealm, issuerUri)
    Auth  ->> KC:   POST /token  grant_type=password
    KC   -->> Auth: {access_token, refresh_token, expires_in}
    Auth -->> GQL:  AuthPayload {accessToken, roles, tenant}
    GQL  -->> User: JSON response

    note over User,GQL: Next request — access token as Bearer
    User  ->> GQL:  query members { ... }  Authorization: Bearer jwt
    GQL   ->> GQL:  ApiKeyAuthFilter (skip — no X-API-Key header)
    GQL   ->> GQL:  BearerTokenAuthFilter → TenantAuthManagerResolver
    GQL   ->> KC:   JWKS fetch (internal URL via keycloak:8088)
    KC   -->> GQL:  public key
    GQL   ->> GQL:  Validate iss, sig, exp → set SecurityContext
    GQL   ->> GQL:  TenantResolutionFilter → TenantContext.set(tenantId)
    GQL   ->> GQL:  @PreAuthorize("hasRole('ADMIN')") — pass/fail
    GQL   ->> KC:   KeycloakMemberService → KeycloakAdminClient<br/>GET /roles/MEMBER/users (forwards caller's JWT)
    KC   -->> GQL:  user attributes → List<MemberView>
    GQL   ->> DB:   @SchemaMapping resolvers: currentStatus (status history)<br/>+ subscriptions — scoped to tenantId
    DB   -->> GQL:  status + subscriptions
    GQL  -->> User: JSON response (members assembled from Keycloak + DB)
```

> **Note:** member reads no longer hit the `member` table — identity comes from Keycloak's Admin REST API (`GET /admin/realms/{realm}/roles/MEMBER/users`). Only the *current status* and *subscriptions* are resolved from the database. See [§7](#7-member-identity--keycloak-as-source-of-truth).

---

## 4. Request Lifecycle — M2M API Key path

```mermaid
sequenceDiagram
    autonumber
    actor Client as M2M Client<br/>(cron job / backend system)
    participant GQL  as Spring GraphQL<br/>/graphql
    participant AKAF as ApiKeyAuthenticationFilter
    participant AKS  as ApiKeyService
    participant BTH  as BearerTokenHolder
    participant KC   as Keycloak<br/>Admin REST
    participant DB   as Database

    Client ->> GQL:  query members { ... }  X-API-Key: cmk.asv-pressbaum-badminton.abc123
    GQL    ->> AKAF: doFilterInternal
    AKAF   ->> AKAF: parse prefix → slug="asv-pressbaum-badminton"
    AKAF   ->> AKS:  validate(rawKey)
    AKS    ->> DB:   ApiKeyRepository.findBySlug(slug)
    DB    -->> AKS:  ApiKey (hashed, scopes)
    AKS    ->> AKS:  HMAC-SHA256 compare
    AKS   -->> AKAF: valid → ApiKeyAuthenticationToken(ROLE_M2M, SCOPE_read)
    AKAF   ->> AKAF: SecurityContextHolder.set(token)
    AKAF   ->> GQL:  chain.doFilter → continues
    GQL    ->> GQL:  TenantResolutionFilter → TenantContext.set(tenantId from ApiKey)
    GQL    ->> GQL:  @PreAuthorize check — ROLE_M2M accepted / ROLE_ADMIN rejected

    note over GQL,KC: members query needs Keycloak — but there is no user JWT to forward
    GQL    ->> BTH:  KeycloakAdminClient.bearer()
    BTH    ->> KC:   POST /token  grant_type=client_credentials (club-m2m + secret)
    KC    -->> BTH:  service-account access_token (cached for the request)
    GQL    ->> KC:   GET /roles/MEMBER/users  (service account needs view-realm)
    KC    -->> GQL:  user attributes → List<MemberView>
    GQL    ->> DB:   status history + subscriptions scoped to tenantId
    DB    -->> GQL:  result
    GQL   -->> Client: JSON response
```

> **M2M token fallback:** API-key callers have no user JWT, so any operation that calls the Keycloak Admin REST API (e.g. `members`) obtains a **service-account token** via the per-tenant `club-m2m` client-credentials grant. The secret lives in `tenant.m2m_client_secret` (Flyway **V9**); `BearerTokenHolder` caches the token per request. The service account must hold the realm-management roles `view-users`, `manage-users`, **and `view-realm`** — the last is required because members are listed via the `/roles/{role}/users` endpoint. These roles are granted idempotently by `scripts/data_loader.sh` (realm-import JSON does not reliably apply roles to auto-created service-account users).

---

## 5. Security Filter Chain

Filters execute in this order on every `/graphql` request.

```mermaid
flowchart LR
    REQ(["Inbound<br/>HTTP request"])

    subgraph Chain["Spring Security Filter Chain"]
        direction LR
        F1["① ApiKeyAuthentication<br/>Filter<br/><br/>Parses X-API-Key header.<br/>If valid → sets SecurityContext<br/>and TenantContext, skips JWT."]
        F2["② BearerToken<br/>Authentication<br/>Filter<br/><br/>Reads Authorization header.<br/>Delegates to TenantAuthentication<br/>ManagerResolver."]
        F3["③ TenantResolution<br/>Filter<br/><br/>Reads iss from validated JWT<br/>→ resolves tenantId from DB<br/>→ TenantContext.set()"]
        F4["④ Method Security<br/>(@PreAuthorize)<br/><br/>hasRole('ADMIN')<br/>hasRole('MEMBER')<br/>isAuthenticated()"]
    end

    GQL(["GraphQL<br/>Controller"])

    REQ --> F1 --> F2 --> F3 --> F4 --> GQL

    style F1 fill:#d4edda,stroke:#28a745
    style F2 fill:#cce5ff,stroke:#004085
    style F3 fill:#fff3cd,stroke:#856404
    style F4 fill:#f8d7da,stroke:#721c24
```

---

## 6. Spring AI Chatbox Flow

The natural-language assistant reads club data via `@Tool`-annotated wrappers — it never writes.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant GQL   as ChatAssistant<br/>Controller
    participant SVC   as ChatAssistant<br/>Service
    participant RL    as ChatRateLimiter
    participant AI    as Spring AI<br/>ChatClient
    participant LLM   as Anthropic<br/>Claude API
    participant Tools as @Tool wrappers<br/>(MemberQueryTools, PaymentQueryTools, …)
    participant DB    as Database

    User  ->> GQL:  mutation ask(question: "Who has overdue payments?")
    GQL   ->> SVC:  ask(AskCommand)
    SVC   ->> RL:   checkLimit(userId)   ← rejects if > N calls/min
    RL   -->> SVC:  ok
    SVC   ->> AI:   call(systemPrompt + question)
    AI    ->> LLM:  HTTP POST /messages
    LLM  -->> AI:   tool_use → "getOverduePayments"
    AI    ->> Tools: invoke MemberQueryTools.getOverduePayments()
    Tools ->> DB:   PaymentService.findOverdue(tenantId)
    DB   -->> Tools: List<Payment>
    Tools-->> AI:   JSON result
    AI    ->> LLM:  tool_result → continue
    LLM  -->> AI:   final text response
    AI   -->> SVC:  AskResult
    SVC  -->> GQL:  AskResult
    GQL  -->> User: { answer: "3 members have overdue payments…" }
```

---

## 7. Member Identity — Keycloak as Source of Truth

Member identity moved out of the database and into Keycloak (Flyway **V8**). The `member` table is now a lean, **PII-free** join stub; all personal data — name, email, phone, and membership dates — lives on the Keycloak realm user and is read back through the Admin REST API into a `MemberView` read model.

```mermaid
graph LR
    subgraph KC["Keycloak realm user — source of truth for PII"]
        STD["Standard fields<br/>firstName · lastName · email · enabled"]
        ATTR["Custom attributes<br/>memberId (TSID) · phoneNumber<br/>memberSince · memberUntil · memberUpdatedAt"]
        CT["createdTimestamp<br/>→ MemberView.createdAt"]
    end

    subgraph DBM["DB — member table (lean stub, no PII)"]
        STUB["id = TSID (equals the memberId attribute)<br/>keycloak_subject · tenant_id<br/>anonymized · version"]
    end

    subgraph FK["DB tables that reference member.id"]
        SH["member_status_history<br/>(current status = latest entry)"]
        MS["member_subscription"]
        AU["app_user"]
    end

    KMS["KeycloakMemberService<br/>reads + writes Keycloak,<br/>owns the lean stub"]
    MSVC["MemberService<br/>status history (DB only)"]
    MV["MemberView<br/>backs the GraphQL Member type"]

    KMS -->|"Admin REST:<br/>list / create / update / anonymize"| KC
    KMS -->|"upsert stub + initial ACTIVE status"| STUB
    KC --> MV
    STUB -. "id join (memberId)" .- MV
    MSVC --> SH
    STUB --> SH & MS & AU
```

**Read / write split**

| Concern | Owner | Storage |
|---|---|---|
| Name, email, phone, membership dates, account enabled | `KeycloakMemberService` → `KeycloakAdminClient` | Keycloak user (standard fields + custom attributes) |
| Stable member id (TSID), Keycloak link, tenant scope, GDPR flag | `KeycloakMemberService` (upserts the stub) | `member` table |
| Current status + status history | `MemberService` | `member_status_history` (status derived from the latest row) |
| Read projection for GraphQL | `MemberView` | assembled from Keycloak + DB, joined on `member.id == memberId` |

**Key points**

- **`Member` carries no PII and no `@TsidGenerated`.** Its `@Id` is *assigned* from the Keycloak `memberId` attribute (a TSID minted by `KeycloakMemberService.createMember`), so the database id and the Keycloak identity always agree. The stub exists only as an FK target for `member_status_history`, `member_subscription`, and `app_user`.
- **GDPR erasure** scrubs name/email and disables the account in Keycloak (keeping only `memberId`), then flips the stub's `anonymized` flag — which replaced the former `firstName == "DELETED"` sentinel that `GdprPurgeJob` used.
- **Custom-attribute naming gotcha:** the "last updated" attribute is `memberUpdatedAt`, **not** `updatedAt` — Keycloak reserves `updated_at` for an internal epoch-integer claim, and an ISO-8601 string there breaks ROPC token issuance.
- **Realm-import fixtures** must carry an explicit `createdTimestamp`; Keycloak does not auto-populate it on import and treats it as read-only afterwards. Because `Member.createdAt` is a non-null `DateTime!` sourced from Keycloak, one fixture missing the timestamp fails the entire `members` query.

---

## 8. Domain Layer Map

How the domain packages relate to each other.

```mermaid
graph TD
    subgraph Domain["domain/club"]
        IDN["identity<br/>AppUser · ApiKey<br/>ApiKeyService · AppUserService"]
        TNT["tenant<br/>Tenant · TenantService"]
        MBR["member<br/>Member (lean stub) · MemberView<br/>KeycloakMemberService · MemberService<br/>MemberGdprService · StatusHistory"]
        SUB["subscription<br/>MemberSubscription<br/>MemberSubscriptionService"]
        MBT["membership<br/>MembershipType<br/>MembershipTypeService"]
        PAY["payment<br/>Payment · PaymentDocument<br/>PaymentService · PaymentDocumentService"]
        TRN["trainer<br/>Trainer · TrainerLog · TrainerSettings<br/>TrainerService · TrainerLogService"]
        SES["training<br/>Session · SessionOccurrence<br/>SessionService · SessionOccurrenceService"]
    end

    subgraph Chatbox["domain/chatbox"]
        TOOLS["@Tool wrappers<br/>Member · Payment · Subscription<br/>Trainer · Session · Membership"]
    end

    KCX["Keycloak<br/>(member PII)"]

    IDN --> TNT
    TNT --> MBR
    MBR -->|"Admin REST"| KCX
    MBR --> SUB
    SUB --> MBT
    MBR --> PAY
    TRN --> SES

    TOOLS --> MBR & PAY & SUB & TRN & SES
```

> **Cross-cutting (omitted above for clarity):** every entity extends `Auditable` from `domain/support` (provides `createdAt`, `updatedAt`, `tenantId`); every service may throw typed exceptions from `domain/club/exception/`, mapped to GraphQL errors in `GraphQlExceptionAdvice`.

---

## 9. Infrastructure Layer Map

### 9a. Security wiring

```mermaid
graph TB
    SC["SecurityConfig<br/>(filter chain wiring)"]

    subgraph Filters["Filter execution order"]
        direction LR
        AKAF["ApiKeyAuthentication<br/>Filter"]
        BTAF["BearerToken<br/>AuthFilter"]
        TRF["TenantResolution<br/>Filter"]
    end

    subgraph Resolvers["Resolvers & Converters"]
        direction LR
        TAMR["TenantAuthentication<br/>ManagerResolver"]
        KRRC["KeycloakRealm<br/>RoleConverter"]
        TC["TenantContext<br/>(ThreadLocal)"]
    end

    subgraph Clients["Keycloak Clients"]
        direction LR
        KAC["KeycloakAuthClient<br/>(login / refresh /<br/>m2m client-credentials)"]
        KADM["KeycloakAdminClient<br/>(member CRUD · realmMembers)"]
        BTH["BearerTokenHolder<br/>(request-scoped:<br/>user JWT or M2M token)"]
    end

    subgraph Props["Properties"]
        direction LR
        KP["KeycloakProperties"]
        AKP["ApiKeyProperties"]
        AKHS["ApiKeyHmacService"]
    end

    SC --> Filters
    SC --> TAMR
    AKAF --> AKHS & AKP
    TAMR --> KRRC & KP
    TRF  --> TC
    KADM --> BTH
    BTH  --> KAC & KP
```

### 9b. Controllers & scheduling

```mermaid
graph LR
    subgraph Auth["Auth / Identity"]
        AUTH["AuthController<br/>login · refreshToken · me"]
        RMC["RealmMemberController<br/>realmMembers"]
        AKC["ApiKeyController<br/>generateApiKey"]
    end

    subgraph Club["Club domain"]
        MBC["MemberController"]
        PYC["PaymentController"]
        SBC["SubscriptionController"]
        MSC["MembershipController"]
        TRC["TrainerController"]
        SCC["SessionController"]
    end

    subgraph Support["Cross-cutting"]
        CHT["ChatAssistantController"]
        ADV["GraphQlExceptionAdvice"]
        GDPR["GdprPurgeJob<br/>(daily 02:00)"]
    end

    AUTH --> KAC2["KeycloakAuthClient"]
    RMC  --> KADM2["KeycloakAdminClient"]
    MBC  --> KMS2["KeycloakMemberService<br/>(member identity via Keycloak)"]
    GDPR --> MBC
```

---

## 10. Devcontainer Topology

What runs where when `docker compose up` starts.

> Inside the devcontainer, Keycloak is reachable at `http://keycloak:8088` (Docker DNS) — **not** `localhost:8088`. Keycloak auto-imports the three realm JSONs on startup (`start-dev --import-realm`). After the app is up, `scripts/data_loader.sh` (a) grants the `club-m2m` service account the `view-users` / `manage-users` / `view-realm` realm-management roles in all three realms, (b) seeds members/trainers/subscriptions, and (c) writes fresh API keys to `scripts/keycloak-credentials.txt` (regenerated every run).

```mermaid
graph TB
    subgraph Host["Windows / Mac host"]
        Browser["Browser<br/>:8080 → GraphiQL<br/>:8088 → Keycloak Admin<br/>:5432 → pgAdmin"]
        SB["Spring Boot app<br/>./gradlew bootRun<br/>:8080"]
    end

    subgraph DevContainer["Dev Container  (docker-compose)"]
        KC["keycloak<br/>quay.io/keycloak:26.4<br/>:8080 → host :8088<br/>auto-imports 3 realm JSONs"]
        PG["postgres:16<br/>:5432  clubdb<br/>(prod profile)"]
        H2["H2 in-memory<br/>(dev/test profile)<br/>/h2-console"]
    end

    subgraph Cloud["Cloud (external)"]
        LLM2["Anthropic Claude API<br/>api.anthropic.com"]
    end

    Browser  <-->|"port-forward"| SB
    Browser  <-->|"port-forward"| KC
    SB       -->|"JDBC (dev: H2)"| H2
    SB       -->|"JDBC (prod)"| PG
    SB       -->|"OIDC · Admin REST (internal)"| KC
    SB       -->|"HTTPS · ANTHROPIC_API_KEY"| LLM2

    style KC  fill:#e8f4e8,stroke:#27ae60
    style PG  fill:#d6eaf8,stroke:#2980b9
    style H2  fill:#fef9e7,stroke:#f39c12
    style LLM2 fill:#f2d7f5,stroke:#8e44ad
```

---

## 11. GraphQL Schema Surface

A bird's-eye view of every query and mutation, grouped by domain.

```mermaid
mindmap
  root((GraphQL API<br/>/graphql))
    Auth
      login ⬛
      refreshToken ⬛
      me 🔒
      currentTenant 🔒
    Members
      members 👑
      memberById 👑
      memberStatusHistory 👑
      createMember 👑
      updateMember 👑
      changeMemberStatus 👑
      deleteMember 👑
    Realm
      realmMembers 👑
    Identity
      generateApiKey 👑
      apiKeys 👑
    Subscriptions
      subscriptions 🔒
      subscribe 🔒
      cancelSubscription 🔒
      overdueSubscriptions 🔒
    Membership Types
      membershipTypes 🔒
      createMembershipType 🔒
    Payments
      payments 🔒
      outstandingPayments 🔒
      recordPayment 🔒
      uploadPaymentDocument 🔒
      reviewPaymentDocument 🔒
    Trainers
      trainers 🔒
      createTrainer 🔒
      updateTrainer 🔒
      trainerHours 🔒
      trainerPaymentSummary 👑
      myTrainerPaymentSummary 🔒
    Sessions
      sessions 🔒
      createSession 🔒
      createOccurrences 🔒
      occurrences 🔒
    Chatbox
      ask 🔒
```

> **Legend:** ⬛ public (no token required) · 🔒 any authenticated role · 👑 ADMIN only

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **GraphQL-only** (no REST) | Single endpoint; per-operation authorization via `@PreAuthorize`; no URL routing complexity |
| **One Keycloak realm per tenant** | Strict isolation; no cross-tenant token leakage possible at the IdP level |
| **JWT issuer → tenant resolution** | The `iss` claim carries the realm URL, which maps 1-to-1 to a `Tenant` row — no separate tenant header needed |
| **API keys for M2M** | Avoids OAuth client-credentials complexity for cron jobs / backend calls; keys are HMAC-validated, never stored in plaintext |
| **Keycloak is the single source of truth for member identity** | Name, email, phone and membership dates live on the Keycloak user (standard fields + custom attributes); the `member` table holds no PII. One identity store, no duplication, GDPR erasure happens at the IdP |
| **Bearer-token forwarding + M2M fallback for Admin REST** | JWT callers forward their own token to the Keycloak Admin API. API-key (M2M) callers have no user JWT, so `BearerTokenHolder` obtains a `club-m2m` client-credentials service-account token instead — no long-lived admin credentials baked into the app |
| **`member` table kept as a lean FK stub** | It holds only the TSID (= Keycloak `memberId`), the Keycloak link, tenant scope, and the `anonymized` flag — the stable join target for status history, subscriptions, and `app_user`, none of which Keycloak models |
| **Spring AI `@Tool` wrappers are read-only** | The chatbox assistant can only observe state, never mutate it — architectural safety boundary |
| **TSID IDs** | Time-sorted, 64-bit, k-sortable — no UUID fragmentation, no sequence contention across tenants |
