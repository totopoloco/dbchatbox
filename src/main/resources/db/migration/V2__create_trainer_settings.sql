-- V2: Extract trainer settings into dedicated table (TrainerSettings)
-- Moves hourly_rate, payment_mode, auto_approve_hours from trainer to trainer_settings

-- ============================================================
-- TRAINER SETTINGS (1-to-1 with trainer)
-- ============================================================
CREATE TABLE trainer_settings (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    trainer_id         BIGINT        NOT NULL UNIQUE,
    hourly_rate        DECIMAL(10,2) NOT NULL,
    payment_mode       VARCHAR(50)   NOT NULL,
    auto_approve_hours BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_ts_trainer FOREIGN KEY (trainer_id) REFERENCES trainer(id)
);

CREATE UNIQUE INDEX idx_ts_trainer ON trainer_settings(trainer_id);

-- Migrate existing data from trainer to trainer_settings
-- Use trainer.id as a seed for the settings ID (shifted to avoid collisions)
INSERT INTO trainer_settings (id, trainer_id, hourly_rate, payment_mode, auto_approve_hours)
SELECT id + 1000000, id, hourly_rate, payment_mode, auto_approve_hours
FROM trainer;

-- Drop the now-redundant columns from trainer
ALTER TABLE trainer DROP COLUMN hourly_rate;
ALTER TABLE trainer DROP COLUMN payment_mode;
ALTER TABLE trainer DROP COLUMN auto_approve_hours;
