-- V8.012__add_father_external_id_and_fks.sql
-- Father Workspace Backend (SPEC-008) — Father UUID bridge + FK constraints
--
-- Purpose:
--   1. Adds a UUID `external_id` column to the `father` table to bridge
--      the existing BIGSERIAL PK with the UUID-based workspace tables.
--   2. Adds FK constraints from all V8 workspace tables to father(external_id).
--
-- Design decisions:
--   - The existing BIGSERIAL `id` PK remains intact (no schema breakage).
--   - `external_id` is UNIQUE and NOT NULL, populated with gen_random_uuid() for existing rows.
--   - All workspace tables reference father(external_id) via their father_id UUID columns.
--   - Uses IF NOT EXISTS / DO NOTHING patterns for idempotent re-runs where possible.
--
-- Minimum PostgreSQL version: 13+ (gen_random_uuid() is built-in).
-- For PostgreSQL < 13, uncomment: CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 1. Add external_id UUID column to father table
-- ============================================================================
ALTER TABLE father
    ADD COLUMN IF NOT EXISTS external_id UUID;

-- Populate existing rows with unique UUIDs
UPDATE father SET external_id = gen_random_uuid() WHERE external_id IS NULL;

-- Now enforce NOT NULL and UNIQUE
ALTER TABLE father
    ALTER COLUMN external_id SET NOT NULL;

ALTER TABLE father
    ALTER COLUMN external_id SET DEFAULT gen_random_uuid();

-- Add unique constraint (idempotent: will fail silently if already exists in some DB versions)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_father_external_id'
    ) THEN
        ALTER TABLE father ADD CONSTRAINT uq_father_external_id UNIQUE (external_id);
    END IF;
END $$;

-- ============================================================================
-- 2. Add FK constraints from workspace tables to father(external_id)
-- ============================================================================

-- growth_signals.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_growth_signals_father'
    ) THEN
        ALTER TABLE growth_signals
            ADD CONSTRAINT fk_growth_signals_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- father_belts.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_father_belts_father'
    ) THEN
        ALTER TABLE father_belts
            ADD CONSTRAINT fk_father_belts_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- father_streaks.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_father_streaks_father'
    ) THEN
        ALTER TABLE father_streaks
            ADD CONSTRAINT fk_father_streaks_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- father_achievements.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_father_achievements_father'
    ) THEN
        ALTER TABLE father_achievements
            ADD CONSTRAINT fk_father_achievements_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- father_milestones.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_father_milestones_father'
    ) THEN
        ALTER TABLE father_milestones
            ADD CONSTRAINT fk_father_milestones_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- celebration_events.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_celebration_events_father'
    ) THEN
        ALTER TABLE celebration_events
            ADD CONSTRAINT fk_celebration_events_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- activity_reports.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_activity_reports_father'
    ) THEN
        ALTER TABLE activity_reports
            ADD CONSTRAINT fk_activity_reports_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- activity_feed_items.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_activity_feed_items_father'
    ) THEN
        ALTER TABLE activity_feed_items
            ADD CONSTRAINT fk_activity_feed_items_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

-- statistics_aggregates.father_id → father(external_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_statistics_aggregates_father'
    ) THEN
        ALTER TABLE statistics_aggregates
            ADD CONSTRAINT fk_statistics_aggregates_father
            FOREIGN KEY (father_id) REFERENCES father(external_id);
    END IF;
END $$;

COMMENT ON COLUMN father.external_id IS 'UUID identifier for the father. Referenced by all workspace tables. Bridges the BIGSERIAL PK with the UUID-based auth/API layer.';
