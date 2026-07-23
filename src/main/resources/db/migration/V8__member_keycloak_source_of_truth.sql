-- V8: Member identity — Keycloak as single source of truth
-- =============================================================================
-- The member table is reduced to a lean FK target: the TSID primary key, a link
-- to the Keycloak subject, and the tenant scope. All PII (name, email, phone,
-- membership dates) now lives in Keycloak as user attributes; the members query
-- reads it from the Keycloak Admin REST API instead of this table.

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

-- 3. Add unique index per tenant.
-- NOTE: a plain (not partial) unique index is used deliberately. The intent of the
-- spec's "WHERE keycloak_subject IS NOT NULL" partial index is only to allow many
-- rows with a NULL keycloak_subject; both H2 and PostgreSQL already treat NULLs as
-- DISTINCT in a unique index, so multiple NULLs are permitted while non-NULL values
-- stay unique per tenant. Partial-index WHERE clauses are PostgreSQL-only and are
-- rejected by H2 (dev/test), so the plain index is the portable equivalent.
CREATE UNIQUE INDEX uq_member_tenant_keycloak_subject
    ON member (tenant_id, keycloak_subject);

-- 4. Drop PII columns
ALTER TABLE member DROP COLUMN IF EXISTS first_name;
ALTER TABLE member DROP COLUMN IF EXISTS last_name;
ALTER TABLE member DROP COLUMN IF EXISTS email;
ALTER TABLE member DROP COLUMN IF EXISTS phone_number;
ALTER TABLE member DROP COLUMN IF EXISTS member_since;
ALTER TABLE member DROP COLUMN IF EXISTS member_until;

-- 5. Add anonymized flag (replaces the first_name sentinel used by GdprPurgeJob)
ALTER TABLE member ADD COLUMN anonymized BOOLEAN NOT NULL DEFAULT FALSE;
