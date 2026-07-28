-- V14__add_father_status_onboarding.sql
-- Add CHECK constraint on father.status to enforce valid status values at the database level.
-- Ensures ONBOARDING is a recognized value (along with all other FatherStatus enum values).
-- Idempotent: skips if the constraint already exists.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_father_status'
          AND conrelid = 'father'::regclass
    ) THEN
        ALTER TABLE father
            ADD CONSTRAINT chk_father_status
            CHECK (status IN ('NOT_STARTED', 'ONBOARDING', 'ACTIVE', 'PAUSED', 'CHURNED', 'REACTIVATED', 'DELETED'));
    END IF;
END
$$;
