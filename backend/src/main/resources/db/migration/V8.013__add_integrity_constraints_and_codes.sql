-- V8.013__add_integrity_constraints_and_codes.sql
-- Father Workspace Backend (SPEC-008) — Data integrity amendments
--
-- Purpose:
--   1. Adds CHECK constraint on statistics_aggregates to enforce period_end >= period_start
--   2. Adds stable `code` columns (VARCHAR UNIQUE) to achievements and milestones
--      for programmatic reference (UUIDs are generated at runtime and not stable across envs)
--
-- These are amendment migrations — they do not modify existing V8.009/V8.010/V8.011 files
-- to preserve Flyway checksums on existing environments.

-- ============================================================================
-- 1. Add period date range validation to statistics_aggregates
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_period_end_gte_start'
    ) THEN
        ALTER TABLE statistics_aggregates
            ADD CONSTRAINT chk_period_end_gte_start
            CHECK (period_end >= period_start);
    END IF;
END $$;

-- ============================================================================
-- 2. Add stable 'code' column to achievements for programmatic reference
-- ============================================================================
ALTER TABLE achievements
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

-- Populate codes from existing achievement names (snake_case derivation)
UPDATE achievements SET code = 'FIRST_STEPS' WHERE name = 'First Steps' AND code IS NULL;
UPDATE achievements SET code = 'MISSION_MASTER_10' WHERE name = 'Mission Master 10' AND code IS NULL;
UPDATE achievements SET code = 'MISSION_MASTER_50' WHERE name = 'Mission Master 50' AND code IS NULL;
UPDATE achievements SET code = 'MISSION_MASTER_100' WHERE name = 'Mission Master 100' AND code IS NULL;
UPDATE achievements SET code = 'WEEK_WARRIOR' WHERE name = 'Week Warrior' AND code IS NULL;
UPDATE achievements SET code = 'MONTH_CHAMPION' WHERE name = 'Month Champion' AND code IS NULL;
UPDATE achievements SET code = 'QUARTER_LEGEND' WHERE name = 'Quarter Legend' AND code IS NULL;
UPDATE achievements SET code = 'GOAL_GETTER' WHERE name = 'Goal Getter' AND code IS NULL;
UPDATE achievements SET code = 'GOAL_CRUSHER' WHERE name = 'Goal Crusher' AND code IS NULL;
UPDATE achievements SET code = 'DEEP_TALKER' WHERE name = 'Deep Talker' AND code IS NULL;
UPDATE achievements SET code = 'CONNECTION_KING' WHERE name = 'Connection King' AND code IS NULL;
UPDATE achievements SET code = 'RISING_STAR' WHERE name = 'Rising Star' AND code IS NULL;
UPDATE achievements SET code = 'GREEN_MACHINE' WHERE name = 'Green Machine' AND code IS NULL;
UPDATE achievements SET code = 'ELITE_FATHER' WHERE name = 'Elite Father' AND code IS NULL;
UPDATE achievements SET code = 'GRANDMASTER' WHERE name = 'Grandmaster' AND code IS NULL;

-- Set NOT NULL and UNIQUE after populating
ALTER TABLE achievements ALTER COLUMN code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_achievements_code'
    ) THEN
        ALTER TABLE achievements ADD CONSTRAINT uq_achievements_code UNIQUE (code);
    END IF;
END $$;

-- ============================================================================
-- 3. Add stable 'code' column to milestones for programmatic reference
-- ============================================================================
ALTER TABLE milestones
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

-- Populate codes from existing milestone names
UPDATE milestones SET code = 'MISSIONS_25' WHERE name = '25 Missions' AND code IS NULL;
UPDATE milestones SET code = 'MISSIONS_50' WHERE name = '50 Missions' AND code IS NULL;
UPDATE milestones SET code = 'MISSIONS_100' WHERE name = '100 Missions' AND code IS NULL;
UPDATE milestones SET code = 'MISSIONS_250' WHERE name = '250 Missions' AND code IS NULL;
UPDATE milestones SET code = 'MISSIONS_500' WHERE name = '500 Missions' AND code IS NULL;
UPDATE milestones SET code = 'ACTIVE_30_DAYS' WHERE name = '30 Days Active' AND code IS NULL;
UPDATE milestones SET code = 'ACTIVE_90_DAYS' WHERE name = '90 Days Active' AND code IS NULL;
UPDATE milestones SET code = 'ACTIVE_180_DAYS' WHERE name = '180 Days Active' AND code IS NULL;
UPDATE milestones SET code = 'ACTIVE_365_DAYS' WHERE name = '365 Days Active' AND code IS NULL;

-- Set NOT NULL and UNIQUE after populating
ALTER TABLE milestones ALTER COLUMN code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_milestones_code'
    ) THEN
        ALTER TABLE milestones ADD CONSTRAINT uq_milestones_code UNIQUE (code);
    END IF;
END $$;

COMMENT ON COLUMN achievements.code IS 'Stable programmatic identifier for the achievement. Invariant across environments (unlike the generated UUID PK).';
COMMENT ON COLUMN milestones.code IS 'Stable programmatic identifier for the milestone. Invariant across environments (unlike the generated UUID PK).';
