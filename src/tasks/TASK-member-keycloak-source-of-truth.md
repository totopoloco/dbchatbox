# TASK: Member Identity — Keycloak as Single Source of Truth

**Status:** Not Started

---

## Problem

The `member` table currently holds full PII alongside the domain TSID primary key. The Keycloak
realm import holds the same users independently. Two copies means drift risk and violates the
identity authority that Phase 2 gave to Keycloak.

Concretely:

- `data_loader.sh` calls `createMember` mutation to create DB rows for members that already exist
  in the Keycloak realm import — the data is written twice.
- The `members` query reads from the DB while `realmMembers` reads from Keycloak; they are meant
  to converge but today they do not.
- Fields like `memberSince`, `memberUntil`, and `phoneNumber` have no home in Keycloak, so they
  cannot be retired from the DB without first adding them to Keycloak as user attributes.

---

## Goal

After this task:

- The `members { firstName, email, currentStatus, createdAt, lastName, memberSince }` query returns
  data sourced from the Keycloak Admin REST API for the authenticated tenant's realm.
- The `member` table retains only the TSID primary key, `keycloak_subject`, and `tenant_id` — no PII.
- The `data_loader.sh` T7 member-creation block is removed; fixture members live entirely in the
  realm import JSONs with their custom attributes.
- `createMember`, `updateMember`, and `deleteMember` mutations write to Keycloak (Admin API) rather
  than directly to the `member` table.
- All FK references from `member_subscription`, `member_status_history`, and `app_user` continue to
  use the stable TSID stored in the Keycloak `memberId` user attribute.

---

## Architecture After the Change

```
GraphQL: members / memberById
        ↓
KeycloakMemberService  (new domain service, replaces member DB reads)
        ↓                        ↑ custom attributes:
KeycloakAdminClient              │  phoneNumber, memberSince,
  GET /admin/realms/{r}/roles    │  memberUntil, memberId (TSID)
         MEMBER/users  ──────────┘

DB: member (id TSID, keycloak_subject, tenant_id, version)  ← FK target only
    member_status_history                                    ← unchanged
    member_subscription  → FK member.id                     ← unchanged
```

The TSID in the `memberId` Keycloak attribute equals the `member.id` primary key.  This bridges
Keycloak identity to all DB FK references without any DB changes to downstream tables.

---

## Decisions

The following questions were raised during planning and resolved before implementation begins.

| # | Question | Decision |
|---|---|---|
| D-1 | How does `KeycloakMemberService` get the caller's JWT? | Request-scoped `BearerTokenHolder` Spring bean — any component injects it without explicit parameters |
| D-2 | Should ADMIN users get `manage-users` in Keycloak? | Yes — safe because each realm is one tenant; add it to all three realm imports |
| D-3 | How is the lean `member` stub created for existing Keycloak fixture users? | Make `createMember` idempotent: 409 from Keycloak → read `memberId` attribute → upsert stub |
| D-4 | Can API-key callers use the chatbox? | No — restrict `ask` to JWT-authenticated callers; chatbox is human-facing |

---

## What Must Not Change

- `member_status_history`, `member_subscription`, `payment`, `payment_document` tables — no schema
  changes; FK targets remain `member.id` (TSID).
- `changeMemberStatus` mutation and `memberStatusHistory` query — still DB-backed.
- The `subscriptions` nested resolver on `Member` — still calls `MemberSubscriptionService`.
- `realmMembers` admin query — left as-is (raw Keycloak dump for tooling purposes).
- GDPR cron (`GdprPurgeJob`) — updated in scope below, but retention logic is unchanged.

---

## Scope

### 1 — Keycloak realm import JSONs (all three tenants)

**Files:**
- `.devcontainer/keycloak/import/wat-simmering-realm.json`
- `.devcontainer/keycloak/import/union-rot-weiss-realm.json`
- `.devcontainer/keycloak/import/asv-pressbaum-badminton-realm.json`

For every user with the `MEMBER` realm role add an `attributes` object with the following keys.
Keycloak's `UserRepresentation` stores custom attributes as `Map<String, List<String>>` — each
value is a single-element list.

```json
"attributes": {
  "memberId":    ["550894081304985601"],
  "phoneNumber": ["+43 676 2001"],
  "memberSince": ["2024-01-15"],
  "memberUntil": []
}
```

- `memberId`: a stable, unique TSID Long expressed as a decimal string.  Use different values per
  fixture user across all three realms.  These must not collide.
- `memberSince` / `memberUntil`: ISO date strings (`YYYY-MM-DD`). `memberUntil` is optional.
- `phoneNumber`: optional; omit or use an empty list for users without one.
- `createdAt`: use Keycloak's built-in `createdTimestamp` (epoch ms).  No attribute needed.
- `updatedAt`: store as a custom attribute `updatedAt` (ISO-8601 offset datetime string).  Updated
  by the app on every `updateMember` call.  Set a fixture value in the import JSON.

The ADMIN and TRAINER realm users do not carry `memberId` — they are not members.

**Declarative User Profile — required for Keycloak 22+**

Since Keycloak 22, every realm has the Declarative User Profile enabled by default. Under this
system, unmanaged attributes (those not explicitly declared in the realm's user profile config)
are **silently dropped** when written via the Admin API. Without the configuration below, all five
custom attributes (`memberId`, `phoneNumber`, `memberSince`, `memberUntil`, `updatedAt`) will be
accepted at write time but never stored.

Add the following `userProfile` block at the realm level of each import JSON (alongside `users`,
`clients`, `roles`, etc.) — **not** inside individual user objects:

```json
"userProfile": {
  "unmanagedAttributePolicy": "ADMIN_EDIT"
}
```

`ADMIN_EDIT` means: admin tokens can read and write any attribute; end-users cannot see or modify
these fields from the Keycloak account console. This is the correct policy for club-managed data
that members must not be able to self-modify. Using `ENABLED` (full self-service) or `DISABLED`
(the default — drops everything) would both be wrong for this use case.

> **Known issue (keycloak#30240):** With `ADMIN_EDIT`, if a user updates their own profile
> through the account console, unmanaged attributes survive because the policy prevents the
> console from touching them at all. If in a future iteration any of these attributes are
> promoted to declared/managed attributes (with explicit `userProfile.attributes` entries), the
> protection comes from permission rules on those declarations instead.

For every user with the `ADMIN` realm role, add `manage-users` to `clientRoles.realm-management`
alongside the existing `view-users` and `query-users` (decision D-2):

```json
"clientRoles": {
  "realm-management": ["view-users", "query-users", "manage-users"]
}
```

### 2 — Flyway migration V8

**File:** `src/main/resources/db/migration/V8__member_keycloak_source_of_truth.sql`

```sql
-- 1. Drop the per-tenant email index (email column is being removed)
DROP INDEX IF EXISTS uq_member_tenant_email;

-- 2. Add the Keycloak link column
ALTER TABLE member ADD COLUMN keycloak_subject VARCHAR(64);
-- Backfill from app_user where the link already exists
UPDATE member m
   SET keycloak_subject = (
         SELECT au.keycloak_subject
           FROM app_user au
          WHERE au.member_id = m.id
          LIMIT 1)
 WHERE keycloak_subject IS NULL;
-- Not enforced as NOT NULL yet — existing rows with no AppUser link stay nullable
-- until the provisioning flow is wired.

-- 3. Add unique index per tenant
CREATE UNIQUE INDEX uq_member_tenant_keycloak_subject
    ON member (tenant_id, keycloak_subject)
    WHERE keycloak_subject IS NOT NULL;

-- 4. Drop PII columns
ALTER TABLE member DROP COLUMN IF EXISTS first_name;
ALTER TABLE member DROP COLUMN IF EXISTS last_name;
ALTER TABLE member DROP COLUMN IF EXISTS email;
ALTER TABLE member DROP COLUMN IF EXISTS phone_number;
ALTER TABLE member DROP COLUMN IF EXISTS member_since;
ALTER TABLE member DROP COLUMN IF EXISTS member_until;

-- 5. Add anonymized flag (replaces the first_name sentinel used by GdprPurgeJob)
ALTER TABLE member ADD COLUMN anonymized BOOLEAN NOT NULL DEFAULT FALSE;
```

> **H2 note:** H2 in `MODE=PostgreSQL` supports `ALTER TABLE ... DROP COLUMN` in a single
> statement. `DROP INDEX IF EXISTS` also works. No workarounds needed.

> **Why `anonymized` column?** `MemberRepository.findGdprPurgeCandidateIds()` currently detects
> already-anonymized members via `WHERE m.firstName <> :anonymizedMarker`. After V8 removes the
> `first_name` column that JPQL field reference becomes a compile error. The `anonymized` boolean
> replaces it cleanly: `MemberGdprService.deleteMember()` sets `member.setAnonymized(true)` and
> the purge query uses `WHERE m.anonymized = false`.

### 3 — `Member.java` entity (strip PII fields)

**File:** `src/main/java/at/mavila/dbchatbox/domain/club/member/Member.java`

Remove the six PII `@Column` fields: `firstName`, `lastName`, `email`, `phoneNumber`, `memberSince`,
`memberUntil`.  Keep and add:

```java
@Id @TsidGenerated private Long id;
@Column(name = "keycloak_subject", length = 64) private String keycloakSubject;
@Column(nullable = false) @Builder.Default private boolean anonymized = false;
// tenant_id + createdAt + updatedAt are inherited from Auditable
@Version @Column(nullable = false) private Short version;
```

The `anonymized` flag replaces the `firstName` string sentinel that `GdprPurgeJob` previously used
to detect already-scrubbed members.  `MemberGdprService.deleteMember()` must set it to `true`.

Remove `@Builder` fields for the dropped PII columns.  Update `@Table` to remove any unique
constraint annotation that referenced `email`.

### 4 — `MemberView.java` (new projection DTO)

**Package:** `at.mavila.dbchatbox.domain.club.member`

```java
public record MemberView(
    Long id,
    String keycloakSubject,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    java.time.LocalDate memberSince,
    java.time.LocalDate memberUntil,
    java.time.OffsetDateTime createdAt,
    java.time.OffsetDateTime updatedAt
) {}
```

This is the DTO returned from `KeycloakMemberService` and used as the backing type for the GraphQL
`Member` type.  The `id` field carries the TSID so `@SchemaMapping` resolvers can still look up
status history and subscriptions by member ID.

### 5 — `KeycloakAdminClient.java` (extend attribute mapping)

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/KeycloakAdminClient.java`

Extend `KeycloakUserRepresentation` to map the `attributes` field:

```java
@JsonProperty("attributes")
Map<String, List<String>> attributes,
@JsonProperty("createdTimestamp")
Long createdTimestamp
```

> **`briefRepresentation=false` is mandatory on all list/search calls.** When Keycloak returns
> users in brief representation (the default), the `attributes` map is omitted entirely. Every
> Admin API call that needs custom attributes must include `briefRepresentation=false`. This applies
> to `getMembersInRealm`, the `findByMemberId` attribute search, and the 409 fallback email lookup.
>
> `RestClient.uri(template, vars)` only resolves path variables — it cannot append query parameters
> that way.  Use the `UriComponentsBuilder` overload for any call that needs a query parameter:
> ```java
> .uri(b -> b.path(ROLE_MEMBERS_PATH)
>            .queryParam("briefRepresentation", "false")
>            .build(realm, MEMBER_ROLE))
> ```
> Apply the same pattern to the attribute-search and email-lookup URIs.

Add a helper to extract a single attribute value:

```java
private static String attr(Map<String, List<String>> attrs, String key) {
    if (isNull(attrs)) return null;
    final List<String> vals = attrs.get(key);
    return (isNull(vals) || vals.isEmpty()) ? null : vals.get(0);
}
```

Update `toRealmUser()` — or create a separate `toMemberView()` — to populate:

- `id` from `attr("memberId")` parsed to `Long`
- `phoneNumber` from `attr("phoneNumber")`
- `memberSince` from `attr("memberSince")` parsed as `LocalDate`
- `memberUntil` from `attr("memberUntil")` parsed as `LocalDate` (nullable)
- `createdAt` from `createdTimestamp` converted to `OffsetDateTime` (epoch ms → UTC)
- `updatedAt` from `attr("updatedAt")` parsed as `OffsetDateTime`

**Remove the `bearerToken` parameter from the existing `getMembersInRealm` method** — it becomes
`getMembersInRealm(String realm)` and reads the token internally via `bearerTokenHolder.getToken()`.
Update the `RealmMemberController` call site accordingly (see step 7).

Add two new public methods.  All methods receive the bearer token via the injected
`BearerTokenHolder` (see step 5a) rather than as a parameter:

```java
/** Creates a Keycloak user and returns the assigned Keycloak subject (UUID). */
public String createUser(String realm, KeycloakCreateUserRequest req);

/** Updates user attributes and standard fields for an existing user. */
public void updateUser(String realm, String keycloakSubject, KeycloakUpdateUserRequest req);
```

`KeycloakCreateUserRequest` and `KeycloakUpdateUserRequest` must be **top-level records in their
own files** (`infrastructure/security/KeycloakCreateUserRequest.java` and
`infrastructure/security/KeycloakUpdateUserRequest.java`) — not inner types.
CLAUDE.md requires one type per file; inner types are only allowed for `private` helpers
used exclusively by the enclosing class, but these request records are also used by
`KeycloakMemberService`.  Each carries `firstName`, `lastName`, `email`, and the attributes map.
The create method uses `POST /admin/realms/{realm}/users`; the update uses
`PUT /admin/realms/{realm}/users/{id}`.  Both require `manage-users` — added to ADMIN fixture users
in step 1 (decision D-2).

Handle the 409 Conflict case in `createUser`: catch `HttpClientErrorException.Conflict`, call
`GET /admin/realms/{realm}/users?email={email}&briefRepresentation=false` to locate the existing
user, and return their `id` (UUID).  The `{realm}` in this fallback call **must be the same realm
parameter received by `createUser`** — it is always derived from `TenantContext` by the caller
(`KeycloakMemberService`), never hardcoded.  This supports the idempotent `createMember` flow
(decision D-3).

For `findByMemberId` (used by `findById`), call
`GET /admin/realms/{realm}/users?q=memberId:{tsid}&exact=true&briefRepresentation=false`.
The `exact=true` parameter (supported from Keycloak 21+) prevents substring prefix-matches against
other TSIDs that share a common prefix.  If the result set is empty, throw `MemberNotFoundException`.
If it contains more than one user (should be impossible given the unique-per-realm constraint on
`memberId`), throw `IllegalStateException`.

### 5a — `BearerTokenHolder.java` (new request-scoped bean)

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/BearerTokenHolder.java`

A `@RequestScope @Component` that reads the current Keycloak JWT from `SecurityContextHolder` on
demand (decision D-1).  Any Spring component can inject it without the caller needing to extract or
pass the token explicitly.

```java
@Component
@RequestScope
public class BearerTokenHolder {

    /**
     * Returns the raw JWT value for the current request.
     *
     * @throws IllegalStateException if the current authentication is not a JwtAuthenticationToken
     *         (e.g. API-key path — callers that may run on the API-key path must guard against this)
     */
    public String getToken() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException(
            "No JWT available in current request — BearerTokenHolder requires JWT authentication");
    }

    /** Returns true when the current request carries a Keycloak JWT (not an API key). */
    public boolean isJwtPresent() {
        return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken;
    }
}
```

`KeycloakAdminClient`, `KeycloakMemberService`, and `RealmMemberController` all inject this bean
and call `getToken()` without any parameter threading.  The `isJwtPresent()` guard is used
wherever a method may be reached from the API-key path (none currently, since the chatbox is now
JWT-only per decision D-4, but the guard is cheap defensive code).

`@RequestScope` means a new instance is created per HTTP request — the `SecurityContextHolder`
read happens at the time `getToken()` is called, so it is always current.

### 6 — `KeycloakMemberService.java` (new domain service)

**Package:** `at.mavila.dbchatbox.domain.club.member`

```java
@Component
@RequiredArgsConstructor
public class KeycloakMemberService {

    private final MemberRepository memberRepository;
    private final MemberStatusHistoryRepository statusHistoryRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final TenantService tenantService;
    private final CommandValidator commandValidator;
    // BearerTokenHolder injected into KeycloakAdminClient — not needed here directly

    /**
     * Lists members in the current tenant's Keycloak realm, optionally filtered by status.
     * Fetches all MEMBER-role users from Keycloak, then filters in-memory by looking up each
     * member's current status via MemberService.getCurrentStatus(Long memberId).
     * Pass null to return all members regardless of status.
     */
    public List<MemberView> findAll(Status status);

    /** Finds a member by their TSID (matched via the memberId attribute in Keycloak). */
    public MemberView findById(Long id);

    /**
     * Creates a new member (idempotent — decision D-3):
     * 1. Generate a TSID for the new member.
     * 2. Call KeycloakAdminClient.createUser() with the TSID as the memberId attribute.
     *    - If Keycloak returns 409 (user already exists), createUser() returns the existing
     *      user's UUID and reads their existing memberId attribute as the TSID instead.
     * 3. Upsert the lean Member stub (id=TSID, keycloakSubject=Keycloak UUID).
     * 4. If the stub was newly inserted, create an initial ACTIVE MemberStatusHistory entry.
     * Returns the assembled MemberView.
     */
    public MemberView createMember(CreateMemberCommand command);

    /**
     * Updates a member's PII in Keycloak only. DB stub is not touched.
     */
    public MemberView updateMember(Long id, UpdateMemberCommand command);

    /** Returns the current status of a member from the latest status history entry. */
    public Status getCurrentStatus(MemberView view);
}
```

No bearer-token parameters on any method — all Admin API calls go through `KeycloakAdminClient`
which reads the token from the injected `BearerTokenHolder`.

**Tenant isolation invariant (must be enforced in every method that calls `KeycloakAdminClient`):**
```java
final Tenant tenant = tenantService.requireById(TenantContext.getTenantId());
final String realm = tenant.getKeycloakRealm();
// pass realm to every keycloakAdminClient call
```
Never accept `realm` as a method parameter from callers or from user input — always derive it from
`TenantContext`. A `null` tenant ID must propagate as `IllegalStateException` (same contract as
`Auditable.prePersist()`).  This is the same pattern used in `RealmMemberController` today.

### 7 — `MemberController.java` (replace DB calls with Keycloak calls)

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/MemberController.java`

Replace `MemberService` injection with `KeycloakMemberService` for query and write methods.
No token extraction is needed in the controller — `BearerTokenHolder` is injected inside
`KeycloakAdminClient` and reads the token transparently (decision D-1).

`@QueryMapping members` now returns `List<MemberView>` — the Spring GraphQL field-naming
convention maps automatically to the `Member` GraphQL type without schema changes.

`@SchemaMapping(typeName = "Member", field = "currentStatus")` now accepts `MemberView` instead of
`Member`.  The TSID is `view.id()`.

`@SchemaMapping(typeName = "Member", field = "subscriptions")` now accepts `MemberView`; member ID
is `view.id()`.

`createMember` and `updateMember` mutations delegate to `KeycloakMemberService.createMember()` and
`updateMember()`.  No token handling in the controller — the service + `BearerTokenHolder` take
care of it.

`deleteMember` mutation:
1. Calls `KeycloakAdminClient.updateUser(...)` to anonymize the Keycloak account (set
   `firstName`/`lastName` to `"DELETED"`, email to `"deleted-{memberId}@anonymous.local"`, disable
   the account, clear `phoneNumber`/`memberSince`/`memberUntil` attributes).
2. Calls `MemberGdprService.deleteMember(id)` for the DB-side GDPR steps (status history, active
   subscription termination).

`changeMemberStatus` and `memberStatusHistory` are unchanged — still DB-backed via `MemberService`.
`MemberController` retains the `MemberService` injection alongside `KeycloakMemberService` so
that `changeMemberStatus` and `memberStatusHistory` can still reach the DB-backed service.

Also update `RealmMemberController` to inject `BearerTokenHolder` instead of extracting the token
manually from `SecurityContextHolder` — it becomes a one-liner: `bearerTokenHolder.getToken()`.

#### `MemberGdprService.deleteMember()` — required updates

`MemberGdprService.deleteMember()` currently sets PII fields that no longer exist after V8.
Without these changes the code will not compile:

- Remove the calls `member.setFirstName(DELETED_NAME)`, `member.setLastName(DELETED_NAME)`,
  `member.setEmail(...)`, `member.setPhoneNumber(null)` — these fields are dropped by V8.
- Replace with `member.setAnonymized(true)`.
- Remove the `DELETED_NAME` and `DELETED_EMAIL_TEMPLATE` constants — they are now unused.
  `GdprPurgeJob` no longer passes `anonymizedMarker` (step 12) and the repository query uses the
  `anonymized` boolean flag instead.
- Update the `fieldsAnonymized` list in the returned `DeleteMemberResult` to reflect that PII was
  erased in Keycloak (by the preceding `updateUser` call in the mutation handler) and that the DB
  member row is now flagged as `anonymized = true`.

#### `@SchemaMapping` resolvers for nested `Member` contexts

The `Member` GraphQL type appears not only as the direct return of `members`/`memberById`/`createMember`/`updateMember`
(backed by `MemberView`) but also as a **nested field** in several other types:

- `MemberSubscription.member: Member!`
- `MemberPaymentStatus.member: Member!`
- `OverdueSubscription.member: Member!`

When these types are returned from DB queries (e.g. `memberSubscriptions`, `outstandingPayments`,
`overdueSubscriptions`), the `member` field is resolved from the JPA navigation `getMember()`,
which returns the **lean `Member` entity** — a stub with no PII fields after V8.  Spring GraphQL
will then try to resolve `firstName`, `email`, `memberSince` etc. from the entity getters; they
return `null`, violating the `!` (non-null) schema contract and producing GraphQL errors.

Add `@SchemaMapping` resolvers in `MemberController` to intercept each nested `member` field and
return a `MemberView` instead:

```java
@SchemaMapping(typeName = "MemberSubscription", field = "member")
public MemberView memberOnSubscription(final MemberSubscription subscription) {
    return keycloakMemberService.findById(subscription.getMember().getId());
}

@SchemaMapping(typeName = "MemberPaymentStatus", field = "member")
public MemberView memberOnPaymentStatus(final MemberPaymentStatus status) {
    return keycloakMemberService.findById(status.getMember().getId());
}

@SchemaMapping(typeName = "OverdueSubscription", field = "member")
public MemberView memberOnOverdueSubscription(final OverdueSubscription os) {
    return keycloakMemberService.findById(os.getMember().getId());
}
```

Once these resolvers return `MemberView`, the existing `@SchemaMapping(typeName = "Member", field = "currentStatus")`
and `subscriptions` resolvers already accept `MemberView` and will continue to work.

> These resolvers result in one Keycloak Admin API call per subscription/payment row — acceptable
> because these queries are admin-only and the result sets are small.  If this becomes a bottleneck,
> batch loading via `DataLoader` is the standard Spring GraphQL solution, but that is out of scope here.

### 8 — `MemberService.java` (remove PII write paths)

Remove `createMember(CreateMemberCommand)` and `updateMember(Long, UpdateMemberCommand)` public
methods — those are now owned by `KeycloakMemberService`.  Keep:

- `changeMemberStatus`
- `findById(Long)` — returns `Member` stub (needed internally by status/GDPR services)
- `getStatusHistory`
- `getCurrentStatus(Member)` — signature stays for internal use; add an overload
  `getCurrentStatus(Long memberId)` for callers that only have the TSID.
- `findAll` — remove; chatbox member tools are updated to call `KeycloakMemberService` (see step 11).

### 9 — `CreateMemberCommand.java` and `UpdateMemberCommand.java`

No structural changes required.  The validation annotations remain correct.

### 10 — `MemberRepository.java`

Remove all five email-based query methods — the `email` column no longer exists after V8 and all
five will fail Hibernate validation at startup:

- `existsByEmail(String email)`
- `existsByEmailAndTenantId(String email, Long tenantId)`
- `findByEmail(String email)`
- `existsByEmailAndIdNot(String email, Long id)`
- `existsByEmailAndIdNotAndTenantId(String email, Long id, Long tenantId)`

Duplicate-email enforcement is delegated to Keycloak (per-realm unique email); Keycloak returns
409 Conflict which `KeycloakAdminClient.createUser()` maps to `DuplicateEmailException`.

Keep `findAllByTenantId(Long tenantId)` — the `GdprPurgeJob` still needs to iterate purge
candidates scoped to a tenant.

Update `findGdprPurgeCandidateIds` — replace the `m.firstName <> :anonymizedMarker` predicate
with `m.anonymized = false`:

```java
@Query("""
    SELECT m.id FROM Member m
    WHERE m.anonymized = false
    AND EXISTS (
      SELECT 1 FROM MemberStatusHistory h
      WHERE h.member = m
      AND h.status = :deletedStatus
      AND h.changedAt < :cutoff
      AND h.changedAt = (
        SELECT MAX(h2.changedAt) FROM MemberStatusHistory h2 WHERE h2.member = m
      )
    )
    """)
List<Long> findGdprPurgeCandidateIds(
    @Param("deletedStatus") Status deletedStatus,
    @Param("cutoff") LocalDateTime cutoff);
```

The `:anonymizedMarker` parameter is removed; the method signature shrinks by one argument.
Update `GdprPurgeJob` accordingly.

### 11 — Chatbox: restrict to JWT callers + update member tools

**Decision D-4:** the `ask` query is human-facing; API-key callers are blocked.

**File:** `src/main/java/at/mavila/dbchatbox/infrastructure/web/graphql/ChatAssistantController.java`

Add `@PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER') or hasRole('TRAINER')")` to the `ask`
`@QueryMapping` handler.  This rejects callers with only `ROLE_M2M` (API-key path) before the
handler runs, so `BearerTokenHolder.getToken()` is guaranteed to succeed inside any tool call.

**File:** `src/main/java/at/mavila/dbchatbox/domain/chatbox/tools/MemberQueryTools.java`

Replace the `MemberService` injection with `KeycloakMemberService`.  Update `listMembers()` and
`memberById()` to call the new service.  The `toSummary()` mapping changes from `Member` entity
getters to `MemberView` record accessors — the field names are the same so the `MemberSummary`
record is unchanged.

`SubscriptionQueryTools` calls `sub.getMember()` on a `MemberSubscription` and reads
`member.getFirstName()`, `member.getLastName()`, `member.getEmail()` — all removed in V8.  These
calls will fail to compile.  Fix:

- Inject `KeycloakMemberService` into `SubscriptionQueryTools`.
- In `toSummary()` and `toOverdueSummary()`, replace `member.getFirstName()` etc. with
  `keycloakMemberService.findById(member.getId())` and read the PII from the returned `MemberView`.
- Do **not** drop these fields from the DTOs — the `@Tool` description explicitly promises member
  name and email to the LLM, so removing them would silently degrade assistant quality.

### 12 — `GdprPurgeJob.java`

**`GdprPurgeJob` must NOT call `KeycloakAdminClient`.**

The job is `@Scheduled` — it runs outside of any HTTP request. There is no JWT in
`SecurityContextHolder`, and `BearerTokenHolder` is `@RequestScope`. Calling
`bearerTokenHolder.getToken()` from a scheduled thread throws `ScopeNotActiveException` and
crashes the job.

The Keycloak anonymization timing is:
- **Immediate / interactive**: `deleteMember` mutation (JWT present) → `KeycloakAdminClient.updateUser()`
  anonymizes the Keycloak account synchronously. By the time the retention window expires, the
  Keycloak account is already anonymized and disabled.
- **Deferred / cron**: `GdprPurgeJob` handles only the DB side — it deletes `MemberStatusHistory`
  entries, ends subscriptions, and sets `member.anonymized = true` — no Keycloak calls at all.

The purge candidate query changes from the `firstName` sentinel to the `anonymized` flag (see
step 10). Update the `GdprPurgeJob` call site to match the new two-argument
`findGdprPurgeCandidateIds(deletedStatus, cutoff)` signature (the `anonymizedMarker` argument is
removed).

### 13 — `data_loader.sh` update

**File:** `scripts/data_loader.sh`

The `createMember` calls inside `seed_tenant_data()` stay but their semantics change (decision D-3):

- The fixture realm users (`member.anna`, `member.urw`, `member.asv`) exist in Keycloak before the
  data loader runs.  The data loader itself calls `createMember` with **new addresses** such as
  `member-a@${slug}.example.com` — these do not pre-exist in Keycloak, so no 409 occurs on a
  fresh Keycloak boot.
- The 409/idempotent path is exercised when the data loader is re-run without resetting Keycloak
  (e.g., H2 is reset by an app restart but Keycloak retains the users from the previous run).  In
  that scenario `createMember(email: "member-a@wat-simmering.example.com", ...)` gets a 409 from
  Keycloak; the idempotent handler reads the existing user's `memberId` attribute, upserts the DB
  stub, and returns `MemberView` as if creation succeeded.
- The data loader captures the returned `id` (TSID) from the mutation response exactly as it does
  today.  No hard-coded constants needed.
- `linkAppUser` remains in place — it creates the `AppUser` row that links the Keycloak `sub` to
  the `member.id` for self-isolation rules (separate from the member stub).
- The input to `createMember` still carries `firstName`, `lastName`, `email`, `phoneNumber`,
  `memberSince` — these are forwarded to the Keycloak Admin API.  For the 409 case they are
  ignored (existing Keycloak attributes take precedence).

### 14 — Tests

- Remove or rewrite `MemberController` integration tests that test DB member creation via
  `createMember` mutation — replace with a mock `KeycloakMemberService` `@TestConfiguration`.
- Add unit tests for `KeycloakAdminClient` attribute round-trip:
  - JSON with all custom attributes → `MemberView` fields populated correctly.
  - JSON with missing optional attributes → nulls, no NPE.
- Add integration test for `KeycloakMemberService.findAll()` with a `@MockBean`
  `KeycloakAdminClient` returning fixture data.
- Update `TenantAwareIntegrationTest`-based tests that pre-insert a `Member` row via JPA:
  remove the PII field setters; only set `keycloakSubject` (any UUID string) and `id`.

---

## Note on `updatedAt`

Keycloak's `UserRepresentation` has `createdTimestamp` but no standard last-modified field.
Store `updatedAt` as a custom Keycloak attribute (ISO-8601 offset datetime string).  The app
writes it on every `updateMember` call.  Set a fixture value in the realm import JSONs alongside
the other custom attributes.

---

## Files Changed Summary

| File | Change |
|---|---|
| `.devcontainer/keycloak/import/*-realm.json` (×3) | Add custom attributes to MEMBER users; add `manage-users` to ADMIN `clientRoles` |
| `src/main/resources/db/migration/V8__member_keycloak_source_of_truth.sql` | Drop PII columns; add `keycloak_subject` |
| `infrastructure/security/BearerTokenHolder.java` | New `@RequestScope` bean — exposes current JWT to any injecting component |
| `domain/club/member/Member.java` | Remove PII fields; add `keycloakSubject` and `anonymized` |
| `domain/club/member/MemberView.java` | New DTO record |
| `domain/club/member/KeycloakMemberService.java` | New domain service; no bearer-token parameters |
| `domain/club/member/MemberService.java` | Remove `createMember` / `updateMember`; remove `findAll` |
| `domain/club/member/MemberRepository.java` | Remove all 5 email-based methods; update `findGdprPurgeCandidateIds` to use `anonymized` flag |
| `infrastructure/security/KeycloakAdminClient.java` | Add attribute mapping; `createUser` / `updateUser`; inject `BearerTokenHolder`; remove `bearerToken` param from `getMembersInRealm` |
| `infrastructure/security/KeycloakCreateUserRequest.java` | New top-level record (CLAUDE.md: one type per file) |
| `infrastructure/security/KeycloakUpdateUserRequest.java` | New top-level record |
| `infrastructure/web/graphql/MemberController.java` | Switch to `KeycloakMemberService`; update `@SchemaMapping` signatures; add nested `member` resolvers for `MemberSubscription`, `MemberPaymentStatus`, `OverdueSubscription` |
| `infrastructure/web/graphql/RealmMemberController.java` | Replace manual token extraction with `BearerTokenHolder` |
| `infrastructure/web/graphql/ChatAssistantController.java` | Add `@PreAuthorize` — JWT callers only |
| `domain/chatbox/tools/MemberQueryTools.java` | Switch to `KeycloakMemberService` |
| `domain/chatbox/tools/SubscriptionQueryTools.java` | Inject `KeycloakMemberService`; replace `member.getFirstName()`/`getEmail()` with `findById` lookups |
| `domain/club/member/MemberGdprService.java` | Remove PII field setters (compile error after V8); add `member.setAnonymized(true)`; remove dead `DELETED_NAME` / `DELETED_EMAIL_TEMPLATE` constants |
| `scheduling/GdprPurgeJob.java` | Remove `anonymizedMarker` argument from `findGdprPurgeCandidateIds` call |
| `scripts/data_loader.sh` | `createMember` calls stay; mutation is now idempotent on 409 |
| Integration tests for `MemberController` | Swap JPA `Member` fixtures for `MemberView` stubs; mock `KeycloakAdminClient` |
