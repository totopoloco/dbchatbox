-- V1: Create Club Management schema
-- All tables for Phase 1: Member, Trainer, Sessions, Memberships, Subscriptions, Payments

-- ============================================================
-- MEMBER
-- ============================================================
CREATE TABLE member (
    id          BIGINT       NOT NULL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(50),
    member_since DATE        NOT NULL,
    member_until DATE
);

-- ============================================================
-- TRAINER
-- ============================================================
CREATE TABLE trainer (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    first_name         VARCHAR(100)  NOT NULL,
    last_name          VARCHAR(100)  NOT NULL,
    email              VARCHAR(255)  NOT NULL UNIQUE,
    phone_number       VARCHAR(50),
    hourly_rate        DECIMAL(10,2) NOT NULL,
    payment_mode       VARCHAR(50)   NOT NULL,
    auto_approve_hours BOOLEAN       NOT NULL DEFAULT FALSE
);

-- ============================================================
-- MEMBER STATUS HISTORY (audit trail)
-- ============================================================
CREATE TABLE member_status_history (
    id         BIGINT       NOT NULL PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    changed_at TIMESTAMP    NOT NULL,
    reason     VARCHAR(500),
    CONSTRAINT fk_msh_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE INDEX idx_msh_member_id ON member_status_history(member_id);
CREATE INDEX idx_msh_changed_at ON member_status_history(member_id, changed_at DESC);

-- ============================================================
-- MEMBERSHIP TYPE
-- ============================================================
CREATE TABLE membership_type (
    id            BIGINT        NOT NULL PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL UNIQUE,
    description   VARCHAR(500),
    price         DECIMAL(10,2) NOT NULL,
    duration      INTEGER       NOT NULL,
    unit          VARCHAR(50)   NOT NULL,
    status        VARCHAR(50)   NOT NULL,
    prorated_mode BOOLEAN       NOT NULL DEFAULT FALSE
);

-- ============================================================
-- SESSION (recurring weekly template)
-- ============================================================
CREATE TABLE session (
    id           BIGINT       NOT NULL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    session_type VARCHAR(50)  NOT NULL,
    day_of_week  VARCHAR(10)  NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    location     VARCHAR(200) NOT NULL,
    trainer_id   BIGINT,
    CONSTRAINT fk_session_trainer FOREIGN KEY (trainer_id) REFERENCES trainer(id)
);

CREATE INDEX idx_session_trainer ON session(trainer_id);
CREATE INDEX idx_session_day ON session(day_of_week);

-- ============================================================
-- MEMBERSHIP TYPE <-> SESSION (join table)
-- ============================================================
CREATE TABLE membership_type_session (
    membership_type_id BIGINT NOT NULL,
    session_id         BIGINT NOT NULL,
    PRIMARY KEY (membership_type_id, session_id),
    CONSTRAINT fk_mts_membership_type FOREIGN KEY (membership_type_id) REFERENCES membership_type(id),
    CONSTRAINT fk_mts_session FOREIGN KEY (session_id) REFERENCES session(id)
);

-- ============================================================
-- MEMBER SUBSCRIPTION
-- Note: member_id is nullable to support GDPR purge (rule 61)
-- ============================================================
CREATE TABLE member_subscription (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    member_id          BIGINT,
    membership_type_id BIGINT        NOT NULL,
    start_date         DATE          NOT NULL,
    end_date           DATE          NOT NULL,
    agreed_price       DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_ms_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_ms_membership_type FOREIGN KEY (membership_type_id) REFERENCES membership_type(id)
);

CREATE INDEX idx_ms_member ON member_subscription(member_id);
CREATE INDEX idx_ms_membership_type ON member_subscription(membership_type_id);

-- ============================================================
-- PAYMENT
-- ============================================================
CREATE TABLE payment (
    id                     BIGINT        NOT NULL PRIMARY KEY,
    member_subscription_id BIGINT        NOT NULL,
    amount                 DECIMAL(10,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    payment_date           DATE          NOT NULL,
    notes                  VARCHAR(500),
    CONSTRAINT fk_payment_subscription FOREIGN KEY (member_subscription_id) REFERENCES member_subscription(id)
);

CREATE INDEX idx_payment_subscription ON payment(member_subscription_id);

-- ============================================================
-- SESSION OCCURRENCE (concrete dated instance)
-- ============================================================
CREATE TABLE session_occurrence (
    id         BIGINT      NOT NULL PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    date       DATE        NOT NULL,
    status     VARCHAR(50) NOT NULL,
    notes      VARCHAR(500),
    CONSTRAINT fk_so_session FOREIGN KEY (session_id) REFERENCES session(id),
    CONSTRAINT uq_session_date UNIQUE (session_id, date)
);

CREATE INDEX idx_so_session ON session_occurrence(session_id);
CREATE INDEX idx_so_date ON session_occurrence(date);

-- ============================================================
-- TRAINER LOG (training hours)
-- ============================================================
CREATE TABLE trainer_log (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    trainer_id            BIGINT        NOT NULL,
    session_occurrence_id BIGINT        NOT NULL,
    hours_worked          DECIMAL(5,2)  NOT NULL,
    status                VARCHAR(50)   NOT NULL,
    submitted_at          TIMESTAMP     NOT NULL,
    reviewed_at           TIMESTAMP,
    rejection_reason      VARCHAR(500),
    notes                 VARCHAR(500),
    CONSTRAINT fk_tl_trainer FOREIGN KEY (trainer_id) REFERENCES trainer(id),
    CONSTRAINT fk_tl_occurrence FOREIGN KEY (session_occurrence_id) REFERENCES session_occurrence(id),
    CONSTRAINT uq_trainer_occurrence UNIQUE (trainer_id, session_occurrence_id)
);

CREATE INDEX idx_tl_trainer ON trainer_log(trainer_id);
CREATE INDEX idx_tl_occurrence ON trainer_log(session_occurrence_id);
CREATE INDEX idx_tl_status ON trainer_log(status);
