-- V6: Add audit columns (created_at, updated_at) to all mutable domain tables.
-- DEFAULT NOW() backfills existing rows. New rows are managed by JPA @PrePersist / @PreUpdate.
-- Each column is added in a separate ALTER TABLE statement for H2 compatibility.

ALTER TABLE member ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE member ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE member_status_history ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE member_status_history ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE member_subscription ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE member_subscription ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE membership_type ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE membership_type ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE payment ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE payment ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE payment_document ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE payment_document ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE session ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE session ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE session_occurrence ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE session_occurrence ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE trainer ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE trainer ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE trainer_settings ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE trainer_settings ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE trainer_log ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE trainer_log ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
