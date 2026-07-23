-- V7: Add tenant + identity tables; scope all owned data to tenant
-- =============================================================================

-- 1. Tenant -------------------------------------------------------------------
CREATE TABLE tenant (
    id             BIGINT       NOT NULL,
    slug           VARCHAR(60)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    keycloak_realm VARCHAR(60)  NOT NULL,
    issuer_uri     VARCHAR(300) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tenant      PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug   UNIQUE (slug),
    CONSTRAINT uq_tenant_realm  UNIQUE (keycloak_realm),
    CONSTRAINT uq_tenant_issuer UNIQUE (issuer_uri)
);

-- Seed the three demo tenants. IDs 1, 2, 3 are intentionally simple integers for
-- these fixture rows. TsidIdentifierGenerator fires only on JPA-managed INSERT and
-- does not validate existing ID values on read, so plain Long integers work as FK
-- targets and in TenantContext.setTenantId(1L) from TenantAwareIntegrationTest.
-- The issuer_uri must exactly match what Keycloak stamps in its tokens (rule 96).
INSERT INTO tenant (id, slug, name, keycloak_realm, issuer_uri) VALUES
  (1, 'wat-simmering',           'WAT Simmering',           'wat-simmering',           'http://localhost:8088/realms/wat-simmering'),
  (2, 'union-rot-weiss',         'Union Rot-Weiss',         'union-rot-weiss',         'http://localhost:8088/realms/union-rot-weiss'),
  (3, 'asv-pressbaum-badminton', 'ASV Pressbaum Badminton', 'asv-pressbaum-badminton', 'http://localhost:8088/realms/asv-pressbaum-badminton');

-- 2. AppUser (Keycloak link — no passwords stored) ----------------------------
CREATE TABLE app_user (
    id               BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL,
    keycloak_subject VARCHAR(64)  NOT NULL,
    username         VARCHAR(150) NOT NULL,
    email            VARCHAR(255),
    member_id        BIGINT,
    trainer_id       BIGINT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    version          SMALLINT     NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_app_user             PRIMARY KEY (id),
    CONSTRAINT fk_app_user_tenant      FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_app_user_member      FOREIGN KEY (member_id)  REFERENCES member (id),
    CONSTRAINT fk_app_user_trainer     FOREIGN KEY (trainer_id) REFERENCES trainer (id),
    CONSTRAINT uq_app_user_tenant_subj UNIQUE (tenant_id, keycloak_subject)
);

-- 3. ApiKey (only the HMAC hash is stored — raw key is shown once) ------------
CREATE TABLE api_key (
    id                  BIGINT       NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    label               VARCHAR(120) NOT NULL,
    key_hash            VARCHAR(128) NOT NULL,
    keycloak_client_id  VARCHAR(120),
    scope               VARCHAR(40)  NOT NULL DEFAULT 'READ',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at        TIMESTAMP,
    version             SMALLINT     NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_api_key        PRIMARY KEY (id),
    CONSTRAINT fk_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_api_key_hash   UNIQUE (key_hash)
);

-- 4. Add tenant_id to each owned table, backfill existing rows to tenant 1,
--    then enforce NOT NULL and add FK + index.
-- (membership_type_session is a @JoinTable — intentionally excluded; its isolation
--  is inherited from tenant-scoped MembershipType and Session parent rows.)

ALTER TABLE member ADD COLUMN tenant_id BIGINT;
UPDATE member SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE member ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE member ADD CONSTRAINT fk_member_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_member_tenant ON member (tenant_id);

ALTER TABLE member_status_history ADD COLUMN tenant_id BIGINT;
UPDATE member_status_history SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE member_status_history ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE member_status_history ADD CONSTRAINT fk_member_status_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_member_status_history_tenant ON member_status_history (tenant_id);

ALTER TABLE member_subscription ADD COLUMN tenant_id BIGINT;
UPDATE member_subscription SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE member_subscription ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE member_subscription ADD CONSTRAINT fk_member_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_member_subscription_tenant ON member_subscription (tenant_id);

ALTER TABLE membership_type ADD COLUMN tenant_id BIGINT;
UPDATE membership_type SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE membership_type ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE membership_type ADD CONSTRAINT fk_membership_type_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_membership_type_tenant ON membership_type (tenant_id);

ALTER TABLE payment ADD COLUMN tenant_id BIGINT;
UPDATE payment SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE payment ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE payment ADD CONSTRAINT fk_payment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_payment_tenant ON payment (tenant_id);

ALTER TABLE payment_document ADD COLUMN tenant_id BIGINT;
UPDATE payment_document SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE payment_document ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE payment_document ADD CONSTRAINT fk_payment_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_payment_document_tenant ON payment_document (tenant_id);

ALTER TABLE session ADD COLUMN tenant_id BIGINT;
UPDATE session SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE session ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE session ADD CONSTRAINT fk_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_session_tenant ON session (tenant_id);

ALTER TABLE session_occurrence ADD COLUMN tenant_id BIGINT;
UPDATE session_occurrence SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE session_occurrence ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE session_occurrence ADD CONSTRAINT fk_session_occurrence_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_session_occurrence_tenant ON session_occurrence (tenant_id);

ALTER TABLE trainer ADD COLUMN tenant_id BIGINT;
UPDATE trainer SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE trainer ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE trainer ADD CONSTRAINT fk_trainer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_trainer_tenant ON trainer (tenant_id);

ALTER TABLE trainer_settings ADD COLUMN tenant_id BIGINT;
UPDATE trainer_settings SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE trainer_settings ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE trainer_settings ADD CONSTRAINT fk_trainer_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_trainer_settings_tenant ON trainer_settings (tenant_id);

ALTER TABLE trainer_log ADD COLUMN tenant_id BIGINT;
UPDATE trainer_log SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE trainer_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE trainer_log ADD CONSTRAINT fk_trainer_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id);
CREATE INDEX idx_trainer_log_tenant ON trainer_log (tenant_id);

-- 5. Replace global-unique constraints with per-tenant uniqueness (rule 79).
-- V1 used inline UNIQUE (not named CONSTRAINT), so PostgreSQL auto-names them
-- <table>_<column>_key. H2 in MODE=PostgreSQL follows the same convention.
-- IF EXISTS is used for H2 tolerance in case of naming divergence.
ALTER TABLE member          DROP CONSTRAINT IF EXISTS member_email_key;
CREATE UNIQUE INDEX uq_member_tenant_email         ON member          (tenant_id, email);

ALTER TABLE trainer         DROP CONSTRAINT IF EXISTS trainer_email_key;
CREATE UNIQUE INDEX uq_trainer_tenant_email        ON trainer         (tenant_id, email);

ALTER TABLE membership_type DROP CONSTRAINT IF EXISTS membership_type_name_key;
CREATE UNIQUE INDEX uq_membership_type_tenant_name ON membership_type (tenant_id, name);
