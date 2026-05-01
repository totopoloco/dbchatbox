-- V3: Add payment verification workflow support
-- Adds: payment_status to member_subscription, grace_period_days to membership_type, payment_document table

-- ============================================================
-- MEMBERSHIP TYPE — add grace_period_days
-- ============================================================
ALTER TABLE membership_type ADD COLUMN grace_period_days INTEGER NOT NULL DEFAULT 30;

-- ============================================================
-- MEMBER SUBSCRIPTION — add payment_status
-- ============================================================
ALTER TABLE member_subscription ADD COLUMN payment_status VARCHAR(50) NOT NULL DEFAULT 'NOT_PAID';

CREATE INDEX idx_ms_payment_status ON member_subscription(payment_status);

-- ============================================================
-- PAYMENT DOCUMENT (proof of payment uploaded by members)
-- ============================================================
CREATE TABLE payment_document (
    id                     BIGINT        NOT NULL PRIMARY KEY,
    member_subscription_id BIGINT        NOT NULL,
    file_name              VARCHAR(255)  NOT NULL,
    content_type           VARCHAR(100)  NOT NULL,
    storage_path           VARCHAR(1000) NOT NULL,
    file_size              BIGINT        NOT NULL,
    uploaded_at            TIMESTAMP     NOT NULL,
    notes                  VARCHAR(500),
    CONSTRAINT fk_pd_subscription FOREIGN KEY (member_subscription_id) REFERENCES member_subscription(id)
);

CREATE INDEX idx_pd_subscription ON payment_document(member_subscription_id);
