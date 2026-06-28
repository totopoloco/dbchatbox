# TASK: Tenant Domain, Migration & Tenant Scoping

**Status:** Pending

> Implements the data-layer half of [ClubManagement-Phase2-Multitenancy-Auth](../specs/club/ClubManagement-Phase2-Multitenancy-Auth.md).
> Adds the `Tenant` domain, puts a `tenant_id` on every tenant-owned table, and makes all reads
> tenant-scoped. Authentication (who sets the tenant) is a **separate** task
> ([TASK-keycloak-resource-server-auth](./TASK-keycloak-resource-server-auth.md)); this task provides
> the `TenantContext` it will populate and the scoping it will rely on.

---

## Goal

Introduce a `Tenant` entity and scope all owned data to a tenant, so that — once authentication sets the
current tenant — every query and mutation only ever sees one tenant's data, with **no superuser** path.
Reference/lookup tables stay global.

---

## Scope

- New domain package `at.mavila.dbchatbox.domain.club.tenant`.
- Add `tenantId` to the existing `Auditable` base class (scopes all 11 auditable entities at once).
- New Flyway migration **V7** (V1–V6 already exist).
- New `TenantContext` + `TenantScopedFinder` in `infrastructure.security`.
- Update repositories/services of the 11 entities to read tenant-scoped.
- No Keycloak, no Spring Security in this task.

---

## Task List

### T1 — `Tenant` entity, repository, service

**New file:** `src/main/java/at/mavila/dbchatbox/domain/club/tenant/Tenant.java`

A TSID-keyed entity (use the project's existing TSID generation approach in `domain.support` — the same
one the Phase 1 entities use; do **not** introduce a new id strategy). Fields per spec § Tenant:
`slug`, `name`, `keycloakRealm`, `issuerUri`, `active`. The `Tenant` table is **not** auditable and
**not** tenant-scoped (it is the tenant dimension).

```java
@Entity
@Table(name = "tenant",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_tenant_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uq_tenant_realm", columnNames = "keycloak_realm"),
        @UniqueConstraint(name = "uq_tenant_issuer", columnNames = "issuer_uri")
    })
@Getter @Setter @NoArgsConstructor
public class Tenant {
    @Id /* TSID — match Phase 1 entities */ private Long id;
    @NotBlank @Size(max = 60)  @Column(nullable = false) private String slug;
    @NotBlank @Size(max = 150) @Column(nullable = false) private String name;
    @NotBlank @Size(max = 60)  @Column(name = "keycloak_realm", nullable = false) private String keycloakRealm;
    @NotBlank @Size(max = 300) @Column(name = "issuer_uri", nullable = false) private String issuerUri;
    @Column(nullable = false) private boolean active = true;
}
```

**New file:** `.../domain/club/tenant/TenantRepository.java`

```java
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByIssuerUri(String issuerUri);   // token iss -> tenant
    Optional<Tenant> findBySlug(String slug);             // login tenantSlug -> realm
    Optional<Tenant> findBySlugAndActiveIsTrue(String slug);
}
```

**New file:** `.../domain/club/tenant/TenantService.java` — read-only helpers
(`requireBySlug`, `requireByIssuer`) that throw a domain exception (reuse the Phase 1 exception style in
`domain.club.exception`) when a tenant is missing or inactive. **Never** return a "default" tenant.

---

### T2 — `TenantContext` (request-scoped holder)

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/TenantContext.java`

A `ThreadLocal<Long>` holder. Populated by the auth filter (next task), read by repositories and the
entity listener, cleared in a `finally` at the end of the request.

```java
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private TenantContext() {}
    public static void setTenantId(final Long tenantId) { CURRENT.set(tenantId); }
    public static Long getTenantId() { return CURRENT.get(); }     // may be null -> callers must fail closed
    public static void clear() { CURRENT.remove(); }
}
```

> The auth task is responsible for setting and clearing this. This task only defines it and consumes it.

---

### T3 — Add `tenantId` to `Auditable`

**File:** the Phase 1 `Auditable` base class in `at.mavila.dbchatbox.domain.club.support` /
`domain.support` (wherever Phase 1 placed it — it is a `@MappedSuperclass`).

Add a `tenantId` column. This scopes all 11 entities that already extend `Auditable` with one change.

```java
@MappedSuperclass
public abstract class Auditable {
    // ... existing createdAt / updatedAt ...

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(final Long tenantId) { this.tenantId = tenantId; }
}
```

**Auto-population on insert.** In the existing `@PrePersist` callback on `Auditable`, set `tenantId`
from `TenantContext` if not already set, so service code never sets it by hand:

```java
@PrePersist
void onCreate() {
    // ... existing createdAt/updatedAt logic ...
    if (this.tenantId == null) {
        this.tenantId = TenantContext.getTenantId();   // may still be null -> validate below
    }
    if (this.tenantId == null) {
        throw new IllegalStateException("Cannot persist a tenant-owned entity without a tenant in context");
    }
}
```

> Fail closed: a tenant-owned row can never be written without a tenant (spec rule 76, 82).

---

### T4 — `MembershipTypeSession` join table gets `tenant_id`

**File:** the Phase 1 `MembershipTypeSession` entity (composite PK, not auditable).

Add a `tenantId` field + column (set it from `TenantContext` in the service that creates the link, since
this entity is not `Auditable`). It is used for direct tenant filtering of the join table.

---

### T5 — Flyway migration V7

**New file:** `src/main/resources/db/migration/V7__add_tenant_and_identity.sql`

PostgreSQL syntax (H2 runs `MODE=PostgreSQL`). This migration also creates the `app_user` and `api_key`
tables consumed by later tasks, so the schema is complete in one version.

```sql
-- 1. Tenant ------------------------------------------------------------------
CREATE TABLE tenant (
    id             BIGINT       NOT NULL,
    slug           VARCHAR(60)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    keycloak_realm VARCHAR(60)  NOT NULL,
    issuer_uri     VARCHAR(300) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug   UNIQUE (slug),
    CONSTRAINT uq_tenant_realm  UNIQUE (keycloak_realm),
    CONSTRAINT uq_tenant_issuer UNIQUE (issuer_uri)
);

-- Seed the three tenants. Issuer must match what Keycloak stamps (see spec rule 96).
-- TSID values are illustrative — generate real ones or use a sequence/default consistent with Phase 1.
INSERT INTO tenant (id, slug, name, keycloak_realm, issuer_uri) VALUES
  (1, 'wat-simmering',           'WAT Simmering',           'wat-simmering',           'http://localhost:8088/realms/wat-simmering'),
  (2, 'union-rot-weiss',         'Union Rot-Weiss',         'union-rot-weiss',         'http://localhost:8088/realms/union-rot-weiss'),
  (3, 'asv-pressbaum-badminton', 'ASV Pressbaum Badminton', 'asv-pressbaum-badminton', 'http://localhost:8088/realms/asv-pressbaum-badminton');

-- 2. AppUser (Keycloak link, no passwords) -----------------------------------
CREATE TABLE app_user (
    id               BIGINT      NOT NULL,
    tenant_id        BIGINT      NOT NULL,
    keycloak_subject VARCHAR(64) NOT NULL,
    username         VARCHAR(150) NOT NULL,
    email            VARCHAR(255),
    member_id        BIGINT,
    trainer_id       BIGINT,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_tenant  FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_app_user_member  FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_app_user_trainer FOREIGN KEY (trainer_id) REFERENCES trainer (id),
    CONSTRAINT uq_app_user_tenant_subject UNIQUE (tenant_id, keycloak_subject)
);

-- 3. ApiKey (hash only) ------------------------------------------------------
CREATE TABLE api_key (
    id                  BIGINT       NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    label               VARCHAR(120) NOT NULL,
    key_hash            VARCHAR(128) NOT NULL,
    keycloak_client_id  VARCHAR(120),
    scope               VARCHAR(40)  NOT NULL DEFAULT 'READ',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_api_key PRIMARY KEY (id),
    CONSTRAINT fk_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_api_key_hash UNIQUE (key_hash)
);

-- 4. Add tenant_id to every owned table, backfill, then enforce NOT NULL ------
-- Repeat this block for each: member, member_status_history, member_subscription,
-- membership_type, payment, payment_document, session, session_occurrence,
-- trainer, trainer_settings, trainer_log, membership_type_session.
ALTER TABLE member ADD COLUMN tenant_id BIGINT;
UPDATE member SET tenant_id = 1 WHERE tenant_id IS NULL;     -- backfill to first tenant (rule 97)
ALTER TABLE member ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE member ADD CONSTRAINT fk_member_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_member_tenant ON member (tenant_id);
-- ... (same five statements for each remaining owned table) ...

-- 5. Replace global-unique constraints with per-tenant uniqueness (rule 79) ---
ALTER TABLE member          DROP CONSTRAINT IF EXISTS uq_member_email;       -- name per Phase 1
CREATE UNIQUE INDEX uq_member_tenant_email ON member (tenant_id, email);
ALTER TABLE trainer         DROP CONSTRAINT IF EXISTS uq_trainer_email;
CREATE UNIQUE INDEX uq_trainer_tenant_email ON trainer (tenant_id, email);
ALTER TABLE membership_type DROP CONSTRAINT IF EXISTS uq_membership_type_name;
CREATE UNIQUE INDEX uq_membership_type_tenant_name ON membership_type (tenant_id, name);
```

> Confirm the exact Phase 1 constraint names (`uq_member_email`, etc.) against V1–V6 before writing the
> `DROP`s. `ddl-auto=validate` will fail at startup if the entities and this schema disagree — keep them
> in lockstep.

---

### T6 — `TenantScopedFinder` for safe by-id reads

**New file:** `src/main/java/at/mavila/dbchatbox/infrastructure/security/TenantScopedFinder.java`

Centralises the "load by id, but only within the caller's tenant" pattern so no controller/service can
accidentally return another tenant's row from a guessed id (spec rule 77).

```java
@Component
@RequiredArgsConstructor
public class TenantScopedFinder {

    /** Returns the entity only if it belongs to the current tenant; otherwise empty. */
    public <T extends Auditable> Optional<T> findById(final JpaRepository<T, Long> repo, final Long id) {
        final Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return Optional.empty();   // fail closed: no tenant context => no data (rule 82)
        }
        return repo.findById(id).filter(e -> tenantId.equals(e.getTenantId()));
    }
}
```

> Note the deliberate difference from a "system operation" escape hatch: a **null** tenant returns
> **empty**, never "all tenants". (This is the fail-closed-on-null-tenant rule.)

---

### T7 — Make the 11 services tenant-scoped

For each Phase 1 domain service that reads owned data:

- **List queries:** add a `tenant_id` filter. Prefer derived methods (`findAllByTenantId(Long)`,
  `findByTenantIdAndStatus(...)`) or `@Query` with `WHERE e.tenantId = :tenantId`. Pass
  `TenantContext.getTenantId()` from the service (reject if null).
- **By-id reads:** replace `repo.findById(id)` with `tenantScopedFinder.findById(repo, id)`.
- **Writes:** nothing to do — `tenantId` is auto-set by the `Auditable` `@PrePersist` (T3).

Add a tiny guard helper used at the top of read methods:

```java
private Long currentTenantOrThrow() {
    final Long t = TenantContext.getTenantId();
    if (t == null) throw new AccessDeniedException("No tenant in context");  // or a domain exception
    return t;
}
```

Grep target after this task: no remaining `repo.findById(` on an `Auditable` entity outside
`TenantScopedFinder`.

---

## Security Notes

- **Fail closed everywhere.** A null `TenantContext` must produce *no data* (empty/denied), never a
  cross-tenant or "all tenants" result. This is the single most important invariant of the task.
- **`tenant_id` is server-set only.** It comes from `TenantContext` on insert; never accept it from
  GraphQL input or trust it from the client.
- **Reference tables stay global.** Do not add `tenant_id` to `status`, `unit`, `*_status`,
  `session_type`, `trainer_payment_mode` — they are shared enum seed data.

---

## Acceptance Criteria

- [ ] `Tenant` entity, repository, and service exist; three tenants are seeded by V7 and match the
      realms defined in [TASK-keycloak-devcontainer-realms](./TASK-keycloak-devcontainer-realms.md).
- [ ] `Auditable` carries a non-null `tenant_id`; all 11 entities and `membership_type_session` have the
      column with a FK and an index.
- [ ] Email/name uniqueness is per-tenant (two tenants can share an email).
- [ ] `TenantContext` and `TenantScopedFinder` exist; a null tenant yields empty/denied (fail closed).
- [ ] Every list query filters by tenant; every by-id read goes through `TenantScopedFinder`.
- [ ] `./gradlew bootRun` starts with `ddl-auto=validate` passing (entities ⇄ V7 agree).
- [ ] All new classes have Javadoc; `./gradlew test` passes (Phase 1 tests still green).
