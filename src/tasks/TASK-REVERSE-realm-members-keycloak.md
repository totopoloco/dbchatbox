# TASK-REVERSE: `realmMembers` Query — Members from Keycloak (ADMIN-only)

**Status:** Complete

> Code was written first; this document records the decision, the files touched, and the tests
> still needed. "REVERSE" = implementation preceded the task.

---

## Problem

The local `member` table only contains rows created after Phase 2 multi-tenancy was introduced —
any pre-existing data was backfilled to tenant 1 (`wat-simmering`) by the V7 migration. Tenants
that were set up after the migration (e.g. `asv-pressbaum-badminton`) show an empty `members`
query because their users live in Keycloak, not in the DB.

The `members` query cannot simply be redirected to Keycloak because the GraphQL `Member` type
carries `memberSince: Date!` and `currentStatus: String!` — fields that have no equivalent in
Keycloak. Changing the existing query would be a breaking schema change.

The resolution is a **parallel query** that sources its data from the Keycloak Admin REST API and
returns only the fields Keycloak natively provides.

## Design Decision

The `member` table stays in place for the Phase 1 workflow (subscriptions, status history, GDPR,
etc.). The new `realmMembers` query represents the **transitional view** — it is how an admin
discovers who exists in Keycloak before those users are linked to a `Member` row via `linkAppUser`.

---

## What Was Built

### GraphQL schema
**File:** `src/main/resources/graphql/realm.graphqls`

New type and query extension:
```graphql
extend type Query {
  realmMembers: [RealmUser!]!
}

type RealmUser {
  id: String!
  username: String!
  firstName: String
  lastName: String
  email: String
  enabled: Boolean!
}
```

### `RealmUser` record
**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/RealmUser.java`

Plain Java record; Spring GraphQL maps fields by name convention.

### `KeycloakAdminClient`
**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/KeycloakAdminClient.java`

Calls `GET /admin/realms/{realm}/roles/MEMBER/users` using `RestClient`, forwarding the caller's
bearer token. The Keycloak endpoint returns all users who hold the `MEMBER` realm role. Unknown
fields in the Keycloak `UserRepresentation` JSON are silently ignored (`@JsonIgnoreProperties`).

`HttpClientErrorException.Forbidden` (403) and `Unauthorized` (401) are caught and rethrown as
`KeycloakAdminException` with an actionable message rather than surfacing as `INTERNAL_ERROR`.

### `KeycloakAdminException`
**File:** `src/main/java/at/mavila/dbchatbox/domain/club/exception/KeycloakAdminException.java`

Dedicated exception for Keycloak Admin API failures. Mapped to `DataFetchingException` in
`GraphQlExceptionAdvice`.

### `RealmMemberController`
**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/RealmMemberController.java`

```java
@QueryMapping
@PreAuthorize("hasRole('ADMIN')")
public List<RealmUser> realmMembers() { ... }
```

Extracts the caller's raw JWT from `SecurityContextHolder`, resolves the tenant's realm via
`TenantService + TenantContext`, and delegates to `KeycloakAdminClient`. API-key callers only
have `ROLE_M2M` and are rejected by `@PreAuthorize` before the method runs.

### Keycloak realm imports — `clientRoles`
**Files:** `.devcontainer/keycloak/import/*-realm.json` (all three tenants)

Added `realm-management/view-users` and `query-users` client roles to each admin user:
```json
"clientRoles": {
  "realm-management": ["view-users", "query-users"]
}
```

Without these, Keycloak returns 403 on the Admin API call. This change takes effect on the next
`docker compose up` (or Keycloak realm re-import).

---

## Tests Still Needed

- `realmMembers` as unauthenticated caller → rejected (no token).
- `realmMembers` as `MEMBER`-role caller → rejected (`INTERNAL_ERROR` / access denied).
- `realmMembers` as `ADMIN`-role caller with a mock `KeycloakAdminClient` → returns mapped
  `RealmUser` list.
- `KeycloakAdminClient.getMembersInRealm` when Keycloak returns 403 → `KeycloakAdminException`
  with the permissions message.
- `KeycloakAdminClient.getMembersInRealm` when Keycloak returns an empty array → empty `List`.
