# Club Management System — Phase 2: Multi-Tenancy & Authentication

> Technical specification that **extends** [Club Management System — Phase 1](./ClubManagement.md).
> Phase 1 explicitly placed authentication and identity "out of scope ... the system assumes the
> caller's role is already resolved by the infrastructure layer" (see Phase 1 § Authorization & Roles).
> Phase 2 supplies that infrastructure: it introduces **tenants**, authenticates humans and machines
> through **Keycloak**, and scopes **every** operation to a single tenant.
>
> This document is additive. Where it changes a Phase 1 rule, it says so explicitly. Section, rule,
> and example numbers continue from Phase 1 (Phase 1 ends at rule 75 and example 20).

---

## Table of Contents

1. [Problem Statement](#problem-statement)
   - [What Phase 2 adds](#what-phase-2-adds)
   - [Scope](#scope)
   - [Non-Goals](#non-goals)
2. [Design Decisions](#design-decisions)
   - [D1 — Realm-per-tenant](#d1--realm-per-tenant)
   - [D2 — Tenant is carried by the token, but re-checked in the application](#d2--tenant-is-carried-by-the-token-but-re-checked-in-the-application)
   - [D3 — No superuser](#d3--no-superuser)
   - [D4 — Local identity is a thin link to Keycloak](#d4--local-identity-is-a-thin-link-to-keycloak)
   - [D5 — M2M is Keycloak-backed, fronted by `x-api-key`](#d5--m2m-is-keycloak-backed-fronted-by-x-api-key)
3. [Domain Model — Additions](#domain-model--additions)
   - [Tenant](#tenant)
   - [AppUser (Keycloak identity link)](#appuser-keycloak-identity-link)
   - [ApiKey](#apikey)
   - [`tenantId` added to existing entities](#tenantid-added-to-existing-entities)
4. [Tenant Scoping Model](#tenant-scoping-model)
5. [Identity & Access](#identity--access)
   - [Keycloak realm model](#keycloak-realm-model)
   - [Role mapping](#role-mapping)
   - [Resource server (multi-issuer)](#resource-server-multi-issuer)
   - [Human login (GraphQL)](#human-login-graphql)
   - [Machine-to-machine — `x-api-key`](#machine-to-machine--x-api-key)
6. [GraphQL Operations — Additions](#graphql-operations--additions)
7. [Authorization & Roles — Updated](#authorization--roles--updated)
8. [Rules & Edge Cases (76–98)](#rules--edge-cases-7698)
9. [Configuration Properties](#configuration-properties)
10. [Examples (21–26)](#examples-2126)
11. [Architecture Notes](#architecture-notes)
12. [Tenant Credentials File](#tenant-credentials-file)

---

## Problem Statement

Phase 1 runs a single club in a single database with no notion of *who* is calling. Phase 2 turns the
system into a **multi-tenant** platform that hosts several Austrian clubs side by side — initially
**WAT Simmering**, **Union Rot-Weiss**, and **ASV Pressbaum Badminton** — and gives each club its own
administrators, members, and trainers who sign in through Keycloak. A machine (another service, a
script, a future mobile app) can also call the API on behalf of a tenant using an API key.

### What Phase 2 adds

- A **`Tenant`** domain entity, and a `tenant_id` on every business table that holds tenant-owned data.
- **Keycloak** as the identity provider for humans (ADMIN / MEMBER / TRAINER) and machines (M2M).
- A **GraphQL login** operation so the existing frontend can sign a user in and obtain a token.
- An **`x-api-key`** mechanism for machine-to-machine access, backed by Keycloak service accounts.
- Strict **tenant isolation**: every query and mutation is scoped to the caller's tenant, and there is
  **no superuser** that can see across tenants.

### Scope

| Area              | Phase 2 responsibility                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------------------- |
| **Tenant**        | Tenant entity, tenant column on all owned tables, tenant resolution from the token, tenant-scoped reads |
| **AuthN (human)** | Keycloak realms per tenant; GraphQL `login` / `refreshToken` / `me`; ADMIN/MEMBER/TRAINER realm roles   |
| **AuthN (M2M)**   | `x-api-key` header backed by a Keycloak confidential client; key generation and revocation              |
| **AuthZ**         | Role-gated GraphQL operations (extends the Phase 1 access matrix); tenant-first isolation; no superuser  |
| **DevEx**         | Keycloak service in `.devcontainer/docker-compose.yml`; realm import; `data_loader.sh` token support    |

### Non-Goals

- Replacing the Phase 1 access matrix — it is **reused**, with tenant scoping layered on top.
- Production-grade SSO (SAML, social login), user self-registration, or password reset flows.
- A real secrets vault — for the PoC, client secrets and the API-key HMAC pepper live in env/properties.
- Cross-tenant reporting or a platform-operator console (deliberately excluded — see D3).

---

## Design Decisions

### D1 — Realm-per-tenant

Each tenant is a **separate Keycloak realm** (`wat-simmering`, `union-rot-weiss`,
`asv-pressbaum-badminton`). A realm is a hard security boundary in Keycloak: a user, client, role, or
token in one realm has no meaning in another. This gives tenant isolation **for free at the identity
layer** — an administrator of `wat-simmering` cannot even obtain a token for another realm — and makes
"no superuser" structural rather than something we must remember to enforce.

The tenant of a request is therefore **the realm that issued its token**. The backend maps the token's
`iss` (issuer) claim to a `Tenant` row.

> **Alternative considered (shared realm + groups):** one realm, tenant as a group/attribute and a
> custom `tenant` claim. Simpler resource-server wiring (one issuer) but isolation becomes an
> application concern we must enforce everywhere, and a misconfigured admin role could span tenants.
> Rejected for this PoC because realm-per-tenant matches the "no superuser, admin only within their
> tenant" requirement structurally. The application-layer tenant check in D2 is kept regardless, so a
> later switch to the shared-realm model would not weaken isolation.

### D2 — Tenant is carried by the token, but re-checked in the application

The token tells us the tenant (via its issuer), and Keycloak guarantees a user cannot get a token for
another realm. **We still re-enforce the tenant on every data access** — every tenant-owned query
filters by the resolved `tenantId`, and any by-id fetch verifies the row's `tenantId` matches the
caller's. The token claim selects the tenant; it never *grants* cross-tenant data on its own. (This is
defence in depth: identity-layer isolation and data-layer isolation are independent.)

**Fail closed.** If a request reaches the data layer with **no resolved tenant** — an authenticated
principal whose tenant could not be determined — the request is rejected (`401`/`403`), never served as
"system/all tenants". A null or unknown tenant is always a denial, never a wildcard.

### D3 — No superuser

There is **no** cross-tenant role, no platform-operator account, and no GraphQL operation that returns
data for more than one tenant. Administration is per-tenant: an `ADMIN` in realm A administers realm A
only. Operational tasks that genuinely need to span tenants (creating a new realm, platform metrics)
are done out-of-band via the Keycloak admin console and infrastructure tooling, **not** through this
API.

### D4 — Local identity is a thin link to Keycloak

We do **not** store passwords. Keycloak owns credentials. The database keeps a small **`AppUser`** row
per human that links a Keycloak **subject** (`sub`) to the tenant and — where applicable — to the
Phase 1 `Member` or `Trainer` it represents. This link is what makes the Phase 1 data-isolation rules
(rule 4: "the authenticated member"; rule 8: "the authenticated trainer") implementable: the token's
`sub` resolves to an `AppUser`, which resolves to a `Member`/`Trainer` id.

### D5 — M2M is Keycloak-backed, fronted by `x-api-key`

Machine callers present an opaque key in the **`x-api-key`** header. Each key is backed by a Keycloak
**confidential client with a service account** in the tenant's realm (so machine identities, like human
ones, live in Keycloak and can be disabled/rotated there). The backend stores only an **HMAC hash** of
the key (never the key itself), maps it to its tenant and to its Keycloak client, and grants it a
**restricted, read-oriented** authority set. **An API key is never an admin** (see rule 90). Because
business requirements for M2M are not yet fixed, § [Machine-to-machine](#machine-to-machine--x-api-key)
presents this as a **suggested PoC approach**, with a simpler fallback and clearly marked extension
points.

---

## Domain Model — Additions

### Tenant

Represents one club hosted on the platform. One tenant ⇄ one Keycloak realm.

| Field           | Type            | Constraints                                                                          |
| --------------- | --------------- | ------------------------------------------------------------------------------------ |
| `id`            | `Long`          | TSID, auto-generated, unique                                                         |
| `slug`          | `String`        | Not null, not blank, unique, max 60 — URL/realm-safe key (e.g. `wat-simmering`)      |
| `name`          | `String`        | Not null, not blank, max 150 — display name (e.g. `WAT Simmering`)                   |
| `keycloakRealm` | `String`        | Not null, not blank, unique, max 60 — the Keycloak realm name (usually `== slug`)    |
| `issuerUri`     | `String`        | Not null, unique, max 300 — OIDC issuer, e.g. `http://localhost:8088/realms/<realm>` |
| `active`        | `Boolean`       | Not null, default `true` — inactive tenants are rejected at authentication time       |

**Business rules:**

- `issuerUri` is the join key between a token and a tenant: the resource server reads the token's `iss`
  and looks up the `Tenant` with the matching `issuerUri`.
- `slug`, `keycloakRealm`, and `issuerUri` are immutable once created (changing them would orphan
  existing tokens and tenant-owned rows).
- The `Tenant` table itself has **no** `tenantId` — it *is* the tenant dimension.
- Seed data (3 tenants) is created by Flyway and must match the imported Keycloak realms exactly.

### AppUser (Keycloak identity link)

Links a Keycloak human identity to a tenant and, optionally, to the `Member` or `Trainer` it
represents. Holds **no credentials**.

| Field             | Type      | Constraints                                                                          |
| ----------------- | --------- | ------------------------------------------------------------------------------------ |
| `id`              | `Long`    | TSID, auto-generated, unique                                                         |
| `tenantId`        | `Long`    | Not null, references `Tenant`                                                        |
| `keycloakSubject` | `String`  | Not null, max 64 — the token `sub`; unique **within a tenant**                       |
| `username`        | `String`  | Not null, max 150 — Keycloak `preferred_username`                                    |
| `email`           | `String`  | Optional, max 255                                                                    |
| `memberId`        | `Long`    | Optional, references `Member` (same tenant) — set when this user is a member         |
| `trainerId`       | `Long`    | Optional, references `Trainer` (same tenant) — set when this user is a trainer       |
| `active`          | `Boolean` | Not null, default `true`                                                             |
| `createdAt`       | …         | Auditable (see Phase 1 `Auditable`)                                                  |

**Business rules:**

- Uniqueness is on `(tenantId, keycloakSubject)` — the same `sub` value never crosses tenants because
  realms are disjoint, but the composite key makes the intent explicit.
- An `AppUser` is created lazily on first successful login (just-in-time provisioning) if one does not
  already exist for the token's `(tenant, sub)`. The `memberId`/`trainerId` links are set by an admin
  (or by `data_loader.sh` for the demo) — see rule 84.
- `memberId` and `trainerId` are mutually exclusive in Phase 2 (a demo user is either a member or a
  trainer, not both); both may be null for a pure ADMIN.

### ApiKey

A machine credential for one tenant. Stores only a hash of the key.

| Field              | Type            | Constraints                                                                       |
| ------------------ | --------------- | --------------------------------------------------------------------------------- |
| `id`               | `Long`          | TSID, auto-generated, unique                                                      |
| `tenantId`         | `Long`          | Not null, references `Tenant`                                                     |
| `label`            | `String`        | Not null, not blank, max 120 — human-readable purpose (e.g. `nightly-export`)     |
| `keyHash`          | `String`        | Not null, unique, max 128 — HMAC-SHA256 of the raw key (Base64). Never the key    |
| `keycloakClientId` | `String`        | Optional, max 120 — the backing Keycloak confidential client (`club-m2m`)         |
| `scope`            | `String`        | Not null, default `READ` — coarse capability (`READ`); see rule 89                |
| `active`           | `Boolean`       | Not null, default `true` — revocation flips this to `false`                       |
| `lastUsedAt`       | `LocalDateTime` | Optional — updated on each successful authentication (best-effort)                |
| `createdAt`        | …               | Auditable                                                                          |

**Business rules:**

- The raw key is shown **once**, at creation (`generateApiKey` response). Only its HMAC hash is stored.
- A key always belongs to exactly one tenant and is rejected if its tenant is inactive or unresolved
  (fail closed, D2).
- `scope` gates which operations the key may call (rule 89). An API key is **never** ADMIN (rule 90).

### `tenantId` added to existing entities

Phase 2 adds a non-null `tenantId` (FK → `Tenant`) to **every tenant-owned table**. The cleanest place
is the Phase 1 **`Auditable`** base class: the eleven entities that already extend it are exactly the
tenant-owned business entities, so adding `tenantId` there scopes all of them at once.

**Entities that gain `tenantId` (via `Auditable`):** `Member`, `MemberStatusHistory`,
`MemberSubscription`, `MembershipType`, `Payment`, `PaymentDocument`, `Session`, `SessionOccurrence`,
`Trainer`, `TrainerSettings`, `TrainerLog`.

**Also gains `tenantId` (not auditable — handled explicitly):** the `membership_type_session` join
table.

**Deliberately NOT tenant-scoped (remain global):** the reference/lookup tables `Status`, `Unit`,
`MembershipTypeStatus`, `SessionType`, `SessionOccurrenceStatus`, `TrainerLogStatus`,
`TrainerPaymentMode`, `SubscriptionPaymentStatus`. These are static enum seed data shared by all
tenants (the same `ACTIVE`/`DRAFT`/`TRAINING` constants for everyone); giving them a tenant would force
per-tenant duplication for no benefit. (This mirrors the principle that global template/reference data
is tenant-agnostic; only *owned* data carries a tenant.) The new `Tenant` table is also not scoped.

**Uniqueness changes.** Phase 1 global-unique constraints become **unique-per-tenant**:

- `member.email` → unique `(tenant_id, email)`
- `trainer.email` → unique `(tenant_id, email)`
- `membership_type.name` → unique `(tenant_id, name)`
- `session_occurrence (session_id, date)` and `trainer_log (trainer_id, session_occurrence_id)` already
  reference tenant-owned parents; keep the existing unique constraints (the parents are tenant-scoped),
  and add `tenant_id` to the rows for direct filtering.

---

## Tenant Scoping Model

A single request carries one tenant from authentication through to the data layer:

1. **Resolve.** The security layer validates the token (human) or key (machine), determines the tenant
   (issuer → `Tenant`, or `ApiKey.tenantId`), and places it in a request-scoped **`TenantContext`**
   (a `ThreadLocal` holder, set by a filter, cleared in a `finally`). If no tenant can be resolved, the
   request is rejected (D2, fail closed).
2. **Read.** Every repository method that touches a tenant-owned entity filters by
   `TenantContext.getTenantId()`. By-id lookups go through a shared **`TenantScopedFinder`** that loads
   the row and returns empty if its `tenantId` does not match the context (so a guessed id from another
   tenant yields "not found", not another tenant's data).
3. **Write.** On persist, `tenantId` is set from `TenantContext` automatically (a JPA entity listener on
   `Auditable`), so application/service code never sets it by hand and cannot set it wrong.

This is the same shape as the Phase 1 member/trainer data-isolation rules, with **tenant as the
outermost boundary**: first the row must belong to the caller's tenant, then (for MEMBER/TRAINER) it
must belong to the caller's own member/trainer id.

---

## Identity & Access

### Keycloak realm model

One realm per tenant. Each realm is provisioned identically (only names/credentials differ) via an
imported realm JSON (see [TASK-keycloak-devcontainer-realms](../../tasks/TASK-keycloak-devcontainer-realms.md)).

**Per realm:**

| Object              | Value / purpose                                                                                     |
| ------------------- | --------------------------------------------------------------------------------------------------- |
| Realm name          | `wat-simmering` \| `union-rot-weiss` \| `asv-pressbaum-badminton`                                    |
| Realm roles         | `ADMIN`, `MEMBER`, `TRAINER`                                                                         |
| SPA client          | `club-spa` — public, PKCE; **Direct Access Grants enabled** (so the GraphQL `login` can use ROPC)   |
| M2M client          | `club-m2m` — confidential, **Service Accounts enabled**; holds a `M2M` service-account realm role   |
| Demo users          | one per role (admin / member / trainer), with role mappings and known passwords                     |
| Token claim mappers | default (`realm_access.roles`, `preferred_username`, `email`, `sub`)                                |

The Keycloak admin (`admin`/`admin`, bootstrap) is for provisioning only and is **not** a tenant role.

### Role mapping

Keycloak realm roles map 1:1 to Spring authorities:

| Keycloak realm role | Spring authority   | Meaning                              |
| ------------------- | ------------------ | ------------------------------------ |
| `ADMIN`             | `ROLE_ADMIN`       | Phase 1 ADMIN, scoped to this tenant |
| `MEMBER`            | `ROLE_MEMBER`      | Phase 1 MEMBER                       |
| `TRAINER`           | `ROLE_TRAINER`     | Phase 1 TRAINER                      |
| `M2M` (service acct)| `ROLE_API_CLIENT`  | Machine caller (read-oriented)       |

Roles are read from the token's `realm_access.roles` by a custom `Converter<Jwt, …>` (Keycloak does not
use the default `scope`-based authorities). The Phase 1 Operation Access Matrix is reused unchanged for
the three human roles.

### Resource server (multi-issuer)

The backend is an **OAuth2 resource server** that trusts the three realm issuers. Because there are
multiple issuers, a single static `issuer-uri` is not enough; the app uses a
`JwtIssuerAuthenticationManagerResolver` (or an equivalent `AuthenticationManagerResolver<String>`
keyed by issuer) that, per issuer, builds a `JwtDecoder` (validating signature against that realm's
JWKS, plus issuer/expiry) and a `JwtAuthenticationConverter` that (a) maps realm roles → authorities
and (b) resolves the tenant from the issuer and stamps it into `TenantContext`. An issuer not present in
the `Tenant` table is rejected. See [TASK-keycloak-resource-server-auth](../../tasks/TASK-keycloak-resource-server-auth.md).

### Human login (GraphQL)

The frontend signs in through a GraphQL mutation rather than redirecting to Keycloak, so the existing
SPA login form keeps working:

```
login(input: { tenantSlug, username, password }): AuthPayload
```

The backend resolves `tenantSlug` → realm, then performs an OIDC **password grant** (Direct Access
Grant) against that realm's token endpoint using the public `club-spa` client, and returns the tokens.

> **PoC trade-off.** The password grant (ROPC) is deprecated in OAuth 2.1 and is used here only because
> it lets a backend mutation accept username/password and keeps the PoC frontend simple. The production
> path is **Authorization Code + PKCE** handled entirely in the SPA (Keycloak hosts the login page),
> with this API only ever *validating* the resulting access token. Rule 86 records this.

`refreshToken(input: { tenantSlug, refreshToken }): AuthPayload` exchanges a refresh token for a fresh
access token. `me: CurrentUser` returns the caller's resolved identity (tenant, roles, linked member/
trainer) from the validated token — useful for the frontend to render role-appropriate UI.

### Machine-to-machine — `x-api-key`

> **This section is a suggested PoC approach** (the user noted M2M business rules are not yet fixed).
> It is intentionally concrete so it can be built and demonstrated, with the simpler **Variant B** as a
> fallback and the extension points called out.

**Concept.** A machine caller sends `x-api-key: <opaque-key>`. The key is our façade; the *identity*
behind it lives in Keycloak as the `club-m2m` confidential client's **service account**. This keeps a
single source of truth for identities (humans and machines both in Keycloak) while giving callers the
ergonomic header they expect.

**Creation (`generateApiKey`).** An `ADMIN` (tenant-scoped) calls:

```
generateApiKey(input: { label, scope }): GeneratedApiKey   # returns the raw key ONCE
```

The backend (1) generates a high-entropy random key, (2) stores only its **HMAC-SHA256** hash in
`api_key` with the tenant and `keycloakClientId = club-m2m`, and (3) returns the raw key once. The raw
key format is `cmk_<tenantSlug>_<random>` so the filter can read the tenant from the key prefix without
a DB round-trip on the hot path (it still verifies against the stored hash).

**Authentication (request time).** An `ApiKeyAuthenticationFilter` (a `OncePerRequestFilter` placed
before the bearer-token filter, acting only when `x-api-key` is present and there is no `Authorization`
header):

1. Parses the tenant slug from the key prefix; resolves the `Tenant`. **If the tenant is missing,
   unknown, or inactive → 401 (fail closed).**
2. HMAC-hashes the key and looks up an **active** `ApiKey` by hash. No match → 401.
3. Sets the authentication to a principal with authority `ROLE_API_CLIENT` (plus a scope authority such
   as `SCOPE_READ`), **never** an admin/HQ authority (rule 90), and stamps the tenant into
   `TenantContext`.
4. Updates `lastUsedAt` (best-effort, non-blocking).

**Variant A (recommended, Keycloak-faithful).** In step 3, instead of trusting the local record alone,
the filter performs a **client-credentials** grant against the tenant realm's token endpoint using
`club-m2m` (secret from config), obtains a short-lived JWT, and derives authorities + tenant from *that*
token (caching it until expiry). This means a machine identity can be disabled or its roles changed in
Keycloak and the change takes effect on the next token refresh — Keycloak stays authoritative.

**Variant B (fallback, simplest).** Skip the Keycloak round-trip; authorize purely from the local
`api_key` row (tenant + `scope`). Less faithful to "managed in Keycloak", but trivially demonstrable and
a clean baseline. The `keycloakClientId` column is still populated so an upgrade to Variant A is a
filter change only.

**Which endpoints an API-key role applies to (the open question).** For the PoC: an API key
(`ROLE_API_CLIENT`, `scope = READ`) may call the **read-only** GraphQL queries that are not
member/trainer-personal — i.e. the tenant's catalog and aggregate reads (`members`, `membershipTypes`,
`sessions`, `sessionOccurrences`, `trainers`, `outstandingPayments`, `overdueSubscriptions`). It may
**not** call mutations and may **not** call the `my*` personal queries (those require a human subject).
This is encoded as method-security on the GraphQL controllers (rule 89) and is the natural place to grow
finer scopes (e.g. `SCOPE_PAYMENTS_READ`) once real requirements exist.

---

## GraphQL Operations — Additions

### Mutations

| Mutation         | Input                                       | Returns           | Roles  | Description                                            |
| ---------------- | ------------------------------------------- | ----------------- | ------ | ------------------------------------------------------ |
| `login`          | `LoginInput!`                               | `AuthPayload`     | public | Password-grant sign-in for a tenant; returns tokens    |
| `refreshToken`   | `RefreshTokenInput!`                        | `AuthPayload`     | public | Exchange a refresh token for a new access token        |
| `generateApiKey` | `GenerateApiKeyInput!`                      | `GeneratedApiKey` | ADMIN  | Create a tenant-scoped API key (raw value shown once)  |
| `revokeApiKey`   | `id: ID!`                                   | `ApiKey`          | ADMIN  | Deactivate an API key (`active = false`)               |
| `linkAppUser`    | `LinkAppUserInput!`                         | `AppUser`         | ADMIN  | Link a Keycloak subject to a `Member`/`Trainer`        |

### Queries

| Query        | Arguments | Returns        | Roles                 | Description                                            |
| ------------ | --------- | -------------- | --------------------- | ------------------------------------------------------ |
| `me`         | —         | `CurrentUser`  | any authenticated     | The caller's resolved tenant, roles, and linked entity |
| `apiKeys`    | —         | `[ApiKey]`     | ADMIN                 | List this tenant's API keys (hashes never returned)    |
| `currentTenant` | —      | `Tenant`       | any authenticated     | The caller's tenant (display name, slug)               |

### New types (sketch)

```graphql
type AuthPayload {
  accessToken: String!
  refreshToken: String!
  expiresIn: Int!
  tokenType: String!     # "Bearer"
  tenant: Tenant!
  roles: [String!]!
}

input LoginInput { tenantSlug: String!  username: String!  password: String! }
input RefreshTokenInput { tenantSlug: String!  refreshToken: String! }

type CurrentUser {
  subject: String!
  username: String!
  email: String
  tenant: Tenant!
  roles: [String!]!
  memberId: ID
  trainerId: ID
}

type Tenant { id: ID!  slug: String!  name: String! }

input GenerateApiKeyInput { label: String!  scope: String }   # scope defaults to "READ"
type GeneratedApiKey { id: ID!  label: String!  rawKey: String!  scope: String!  createdAt: DateTime! }
type ApiKey { id: ID!  label: String!  scope: String!  active: Boolean!  lastUsedAt: DateTime  createdAt: DateTime! }

input LinkAppUserInput { keycloakSubject: String!  memberId: ID  trainerId: ID }
type AppUser { id: ID!  username: String!  memberId: ID  trainerId: ID  active: Boolean! }
```

---

## Authorization & Roles — Updated

Phase 1 § Authorization & Roles is **amended** as follows. The three human roles and the entire Phase 1
**Operation Access Matrix remain in force**; Phase 2 layers tenant scoping over all of it and adds the
M2M role.

- **Tenant is the outermost check.** Every operation is implicitly `@PreAuthorize("isAuthenticated()")`
  **and** tenant-scoped. The Phase 1 matrix decides *which role* may call an operation; the tenant layer
  decides *whose data* it sees. The two are independent and both apply.
- **No superuser (D3).** `ADMIN` means "admin of the caller's tenant". There is no role or operation
  spanning tenants.
- **Roles come from the token.** `ROLE_ADMIN` / `ROLE_MEMBER` / `ROLE_TRAINER` are derived from the
  Keycloak realm roles in `realm_access.roles`. Phase 1's "the infrastructure resolves the caller's
  role" is now realised by the resource server.
- **Member/Trainer self-isolation still applies, within the tenant.** Phase 1 rules 4 and 8 are
  unchanged but now run *after* the tenant filter: a MEMBER sees only their own data *and* only within
  their tenant. The token `sub` → `AppUser` → `memberId`/`trainerId` link (D4) supplies "the
  authenticated member/trainer".
- **M2M role.** `ROLE_API_CLIENT` may call only the read-only, non-personal queries listed in
  § [Machine-to-machine](#machine-to-machine--x-api-key); never mutations, never `my*`, never admin.

**Enforcement.** Use Spring method security (`@EnableMethodSecurity`) with `@PreAuthorize` on the
GraphQL controller methods (e.g. `@PreAuthorize("hasRole('ADMIN')")`,
`@PreAuthorize("hasAnyRole('ADMIN','MEMBER')")`, and for reads reachable by machines
`@PreAuthorize("hasAnyRole('ADMIN','MEMBER','TRAINER','API_CLIENT')")`). Tenant scoping is enforced in
the data layer (TenantContext + `TenantScopedFinder`), not in the annotations.

---

## Rules & Edge Cases (76–98)

**Tenancy**

76. **Tenant required for owned data.** Every tenant-owned row has a non-null `tenant_id`. It is set
    from `TenantContext` on insert and is never accepted from client input.
77. **Cross-tenant read is impossible.** A by-id query for a row in another tenant returns "not found"
    (not an authorization error that confirms existence). List queries only ever return the caller's
    tenant's rows.
78. **Reference data is global.** Lookup tables carry no tenant and are shared. Seeding them per tenant
    is a bug.
79. **Unique-per-tenant.** Email/name uniqueness is scoped to the tenant: two tenants may each have a
    member `anna@example.com`. (Amends Phase 1 rules 1 and 39.)
80. **Inactive tenant.** If a tenant is `active = false`, all authentication (human and machine) for it
    is rejected, even with otherwise valid credentials.
81. **Unknown issuer.** A token whose `iss` has no matching `Tenant` row is rejected, even if its
    signature is valid (it came from a realm we do not host).
82. **Fail closed on missing tenant.** An authenticated request that reaches the data layer without a
    resolved tenant is rejected. Null tenant is never treated as "all tenants" (D2).

**Authentication & identity**

83. **No passwords stored.** Credentials live only in Keycloak. The database stores Keycloak subjects,
    never passwords or password hashes.
84. **JIT provisioning + linking.** On first successful login, an `AppUser` is created for the token's
    `(tenant, sub)` if absent. Linking that user to a `Member`/`Trainer` (for self-isolation) is an
    `ADMIN` action (`linkAppUser`) or done by `data_loader.sh` for the demo.
85. **Roles are authoritative from the token.** The backend never invents or elevates roles; it maps
    exactly the realm roles present in the token. A token with no known role authenticates but is
    authorized for nothing beyond `me`/`currentTenant`.
86. **Login mechanism is a PoC choice.** The GraphQL `login` uses the password grant (Direct Access
    Grant). Production should move to Authorization Code + PKCE in the SPA; this API would then only
    validate access tokens and the `login`/`refreshToken` mutations could be removed. (Records D-level
    trade-off.)
87. **Token validation.** Access tokens are validated on every request against the issuing realm's JWKS
    (signature), plus issuer and expiry. Clock skew tolerance is the Spring Security default.
88. **`me` requires only authentication.** Any valid human token can call `me`/`currentTenant`
    regardless of role, so the frontend can bootstrap its UI.

**Machine-to-machine**

89. **API-key scope gates operations.** A `ROLE_API_CLIENT` key with `scope = READ` may call only the
    read-only, non-personal queries enumerated in § Machine-to-machine. Mutations and `my*` queries
    reject API keys. Finer scopes are an extension point.
90. **API keys are never admin.** An API-key principal hard-codes "not admin": it can never satisfy
    `hasRole('ADMIN')`, by construction, independent of any data. (A machine credential must not inherit
    administrative power.)
91. **Hashed at rest.** Only the HMAC of a key is stored; the raw key is shown once at creation and is
    unrecoverable thereafter. A leaked database yields no usable keys.
92. **Revocation is immediate.** `revokeApiKey` sets `active = false`; the next request with that key is
    rejected. (In Variant A, disabling the Keycloak client also stops new client-credentials tokens.)
93. **Key carries its tenant.** The key prefix encodes the tenant slug; the filter resolves and verifies
    the tenant before authorizing (fail closed if it cannot, rule 82).
94. **Mutual exclusivity of auth.** A request presenting both `Authorization: Bearer` and `x-api-key`
    is treated as a human request (bearer wins); the API-key filter no-ops when a bearer token is
    present, to avoid ambiguous principals.

**Operational**

95. **Tenant seed must match Keycloak.** The three `Tenant` rows (slug, realm, issuer) must exactly
    match the imported realms; a mismatch makes every token from that realm fail rule 81.
96. **Issuer consistency.** The `issuerUri` stored for a tenant must equal the `iss` Keycloak actually
    stamps on tokens (the URL clients reach Keycloak at). In the devcontainer this is
    `http://localhost:8088/realms/<realm>` for host-run apps. A mismatch is the most common cause of
    "401 invalid issuer". (See Architecture Notes.)
97. **Migration backfill.** Adding non-null `tenant_id` to existing tables requires backfilling existing
    rows to a tenant. Phase 2's V7 migration creates the three tenants and assigns any pre-existing
    demo rows to the first tenant; fresh databases are then loaded per-tenant by `data_loader.sh`.
98. **Secrets in the PoC.** Client secrets and the API-key HMAC pepper are read from environment/
    properties for the PoC. They must never be committed; production must source them from a secrets
    manager.

---

## Configuration Properties

New properties (prefix `app.*`, consistent with Phase 1):

| Property                          | Default                                       | Description                                                        |
| --------------------------------- | --------------------------------------------- | ------------------------------------------------------------------ |
| `app.keycloak.base-url`           | `http://localhost:8088`                       | Base URL the backend uses to reach Keycloak (token endpoints)      |
| `app.keycloak.spa-client-id`      | `club-spa`                                     | Public client used by the `login` password grant                  |
| `app.keycloak.m2m-client-id`      | `club-m2m`                                      | Confidential client backing API keys (Variant A)                  |
| `app.keycloak.m2m-client-secret`  | (env `KC_M2M_CLIENT_SECRET`)                  | Secret for `club-m2m`; never committed (Variant A)                |
| `app.security.api-key.header`     | `x-api-key`                                    | Header name carrying the API key                                  |
| `app.security.api-key.hmac-secret`| (env `API_KEY_HMAC_SECRET`)                   | Pepper for HMAC-SHA256 hashing of API keys                        |
| `app.security.api-key.prefix`     | `cmk`                                           | Raw-key prefix (`cmk_<tenantSlug>_<random>`)                      |
| `app.tenancy.issuers`             | (3 issuer URIs, one per realm)                | Trusted issuers for the multi-issuer resource server*             |

\* In practice the trusted issuers are derived from the `Tenant` table at startup; the property is an
optional override/allow-list. Standard `spring.security.oauth2.resourceserver.*` is **not** used as a
single static issuer because the resource server is multi-issuer (see Architecture Notes).

> **Boot-4 starter note.** Spring Boot 4 renamed several starters (the repo already uses
> `spring-boot-starter-webmvc`, `spring-boot-h2console`, `spring-boot-starter-flyway`). Resolve the
> exact security/resource-server starter coordinates against the Boot 4 BOM rather than assuming the
> Boot 3 names — see the relevant TASK.

---

## Examples (21–26)

### Example 21 — Log in as a tenant admin

```graphql
mutation {
  login(input: { tenantSlug: "wat-simmering", username: "admin.wat", password: "Admin#WAT2026" }) {
    tokenType
    expiresIn
    roles
    tenant { slug name }
    accessToken
  }
}
```

```json
{ "data": { "login": {
  "tokenType": "Bearer", "expiresIn": 300, "roles": ["ADMIN"],
  "tenant": { "slug": "wat-simmering", "name": "WAT Simmering" },
  "accessToken": "eyJhbGciOi..." } } }
```

The frontend stores the token and sends it as `Authorization: Bearer <accessToken>` on subsequent calls.

### Example 22 — A tenant-scoped query (admin)

`Authorization: Bearer <wat-simmering admin token>`

```graphql
query { members(status: "ACTIVE") { id firstName lastName } }
```

Returns only WAT Simmering members. The identical query with a Union Rot-Weiss token returns only Union
Rot-Weiss members — same operation, disjoint data (rule 77).

### Example 23 — Cross-tenant access is denied

A Union Rot-Weiss member token calls `memberById` with an id that belongs to WAT Simmering:

```graphql
query { memberById(id: "38792587163648") { firstName } }
```

```json
{ "data": { "memberById": null } }
```

"Not found" — the row exists, but not in the caller's tenant, so it is invisible (rules 77, 82). No hint
is given that the id exists elsewhere.

### Example 24 — Member self-isolation within a tenant

A WAT Simmering MEMBER token (linked via `AppUser` to member `M1`) calls `memberById` for another WAT
member `M2`: returns `null` (Phase 1 rule 4, now applied after the tenant filter). Calling it for their
own id `M1` returns their record.

### Example 25 — Generate and use an API key (M2M)

Admin creates a key (raw value returned once):

```graphql
mutation { generateApiKey(input: { label: "nightly-export", scope: "READ" }) {
  id label rawKey scope } }
```

```json
{ "data": { "generateApiKey": {
  "id": "71820948571648", "label": "nightly-export",
  "rawKey": "cmk_wat-simmering_9f2c8b1e7a...", "scope": "READ" } } }
```

A machine then calls a read-only query with the key:

```
x-api-key: cmk_wat-simmering_9f2c8b1e7a...
```

```graphql
query { membershipTypes(status: "ACTIVE") { id name price } }
```

Succeeds (WAT Simmering catalog only). The same key calling a mutation or a `my*` query is rejected
(rules 89, 90). Revoke with `revokeApiKey(id: "71820948571648")` — the next call with that key fails
(rule 92).

### Example 26 — `me` bootstraps the frontend

```graphql
query { me { username roles tenant { slug name } memberId trainerId } }
```

```json
{ "data": { "me": {
  "username": "member.anna", "roles": ["MEMBER"],
  "tenant": { "slug": "wat-simmering", "name": "WAT Simmering" },
  "memberId": "38792587163648", "trainerId": null } } }
```

The SPA uses `roles` to render the correct menus and `memberId` to scope its own views.

---

## Architecture Notes

### New build dependencies

Add (resolve exact Boot-4 coordinates against the BOM — Boot 4 renamed several starters):

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
// OAuth2 resource server (JWT validation against Keycloak JWKS).
// Boot 4 BOM — confirm the coordinate; Boot 3 name is below.
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
// HTTP client for the password-grant / client-credentials calls to Keycloak.
// Spring's RestClient (in spring-web, already present via webmvc) is sufficient — no extra dep.
testImplementation 'org.springframework.security:spring-security-test'
```

### New packages (following the Phase 1 domain/infrastructure split)

```
at.mavila.dbchatbox.domain.club.tenant       — Tenant entity, TenantRepository, TenantService
at.mavila.dbchatbox.domain.club.identity      — AppUser, ApiKey entities + repositories + services
at.mavila.dbchatbox.infrastructure.security    — TenantContext, SecurityConfig, KeycloakRealmRoleConverter,
                                                  TenantResolutionFilter, ApiKeyAuthenticationFilter,
                                                  ApiKeyHmacService, TenantScopedFinder, KeycloakAuthClient
at.mavila.dbchatbox.infrastructure.web.graphql  — AuthController, ApiKeyController, MeController (GraphQL)
```

The eleven existing entities change only by extending an `Auditable` that now carries `tenantId`; their
services change only where queries must filter by tenant (delegate by-id reads to `TenantScopedFinder`).

### Flyway migration

Next version is **V7** (V1–V6 exist). `V7__add_tenant_and_identity.sql` must, in PostgreSQL syntax
(H2 runs in `MODE=PostgreSQL`):

1. `CREATE TABLE tenant (...)`, `app_user (...)`, `api_key (...)` with TSID `BIGINT` PKs.
2. `INSERT` the three tenants (slug, name, realm, issuer) — must match the imported realms (rule 95).
3. `ALTER TABLE <each of the 11 auditable tables> ADD COLUMN tenant_id BIGINT` + FK to `tenant`; same
   for `membership_type_session`.
4. Backfill: assign existing rows `tenant_id = (first tenant)`, then `SET NOT NULL` (rule 97).
5. Replace global-unique constraints with per-tenant unique indexes (rule 79):
   `member (tenant_id, email)`, `trainer (tenant_id, email)`, `membership_type (tenant_id, name)`.
6. Add indexes on every new `tenant_id` column (tenant-filtered reads are on the hot path).

`ddl-auto=validate` means the entities and this migration must agree exactly.

### Resource server wiring (multi-issuer)

- A `SecurityFilterChain` bean: stateless, CSRF disabled (token/key auth), `authorizeHttpRequests` →
  `/graphql` and `/graphiql` permit-all at the HTTP layer (method security does the real gating),
  actuator/health as desired; `oauth2ResourceServer(...)` with an `authenticationManagerResolver`.
- The resolver builds, per trusted issuer (from the `Tenant` table), a `JwtAuthenticationProvider` with
  a JWKS-backed `JwtDecoder` and a `JwtAuthenticationConverter` whose `JwtGrantedAuthoritiesConverter`
  reads `realm_access.roles`. The converter (or a subsequent `TenantResolutionFilter`) resolves
  issuer → `Tenant` and sets `TenantContext`; unknown issuer → reject (rule 81).
- `@EnableMethodSecurity` for `@PreAuthorize` on controllers.
- `ApiKeyAuthenticationFilter` is registered **before** the bearer filter and no-ops when a bearer
  token is present (rule 94). `TenantContext` is cleared in a `finally` at the end of the chain.

### The issuer-consistency gotcha (read rule 96)

Keycloak stamps `iss` based on the URL it is reached at. The dev workflow runs the app on the **host**
via `./gradlew bootRun`, so the app reaches Keycloak at `http://localhost:8088` and tokens minted there
carry `iss = http://localhost:8088/realms/<realm>` — store exactly that in `Tenant.issuerUri`. If the
app instead runs **inside** the compose network it would reach Keycloak at `http://keycloak:8080`, and
the issuer would differ, breaking validation. Pick one base URL and keep `KC_HOSTNAME`/issuer, the
backend's `app.keycloak.base-url`, and `Tenant.issuerUri` all consistent. The SPA (browser) also uses
`http://localhost:8088`.

### Testing (mirrors the Phase 1 harness)

- Unit-test the role converter and `ApiKeyHmacService`.
- Slice/integration tests with `spring-security-test`: `@WithMockUser`/`jwt()` post-processors to
  assert role gating; a test that a second tenant's token sees disjoint data; a test that a null/unknown
  tenant is rejected (fail closed); an `ApiKeyAuthenticationFilter` test that a missing/inactive key and
  a missing tenant are rejected and that an API-key principal can never satisfy `hasRole('ADMIN')`.
- Keep the existing Phase 1 tests green; tenant scoping is additive.

---

## Tenant Credentials File

The demo credentials (Keycloak users + the bootstrap admin) are written to a single, git-ignored file
so a developer can sign in immediately after the stack starts. Generated/maintained by
`data_loader.sh` (see [TASK-data-loader-keycloak](../../tasks/TASK-data-loader-keycloak.md)).

**Path:** `scripts/keycloak-credentials.txt` (add to `.gitignore`).

**Format:**

```
# Club Management — demo credentials (DEV ONLY — do not commit)
# Keycloak: http://localhost:8088  (admin console: admin / admin)

[tenant] WAT Simmering   slug=wat-simmering   realm=wat-simmering
  ADMIN    admin.wat      / Admin#WAT2026
  TRAINER  trainer.wat    / Trainer#WAT2026
  MEMBER   member.anna    / Member#WAT2026
  M2M      client_id=club-m2m   secret=<from realm import>

[tenant] Union Rot-Weiss  slug=union-rot-weiss ...
[tenant] ASV Pressbaum Badminton  slug=asv-pressbaum-badminton ...

# API keys generated at load time (raw value shown once):
  wat-simmering   nightly-export   cmk_wat-simmering_....
```

Passwords above are PoC placeholders defined in the realm import; change them there and the file is
regenerated to match.

---

> **Implementation order (suggested).** 1) Tenant domain + V7 migration + scoping
> ([TASK-tenant-domain](../../tasks/TASK-tenant-domain.md)) → 2) Keycloak in the devcontainer + realms
> ([TASK-keycloak-devcontainer-realms](../../tasks/TASK-keycloak-devcontainer-realms.md)) → 3) resource
> server + role/tenant mapping ([TASK-keycloak-resource-server-auth](../../tasks/TASK-keycloak-resource-server-auth.md))
> → 4) GraphQL login ([TASK-graphql-login-endpoint](../../tasks/TASK-graphql-login-endpoint.md)) →
> 5) `x-api-key` ([TASK-m2m-api-key](../../tasks/TASK-m2m-api-key.md)) → 6) data loader + credentials
> ([TASK-data-loader-keycloak](../../tasks/TASK-data-loader-keycloak.md)). Each task lists exact files,
> snippets, and acceptance criteria.
