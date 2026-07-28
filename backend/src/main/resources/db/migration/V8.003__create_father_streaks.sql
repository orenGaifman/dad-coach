-- V8.003__create_father_streaks.sql
-- Father Workspace Backend (SPEC-008) — Streak Tracking System
--
-- Creates:
--   1. father_streaks - Tracks each father's engagement streak: consecutive
--      calendar days with at least one qualifying interaction. One record per
--      father (UNIQUE constraint on father_id).
--      Supports Requirements 12.1, 12.6.
--
-- Design notes:
--   - current_streak_days: consecutive days with qualifying interactions
--   - longest_streak_days: all-time longest streak (monotonically non-decreasing)
--   - streak_start_date: nullable — NULL when current streak is 0
--   - last_qualifying_date: the most recent date a qualifying interaction occurred
--   - timezone: used for calendar-day boundary calculations (Requirement 12.1)
--   - CHECK constraints enforce non-negative streaks and longest >= current
--
-- Note: father_id uses UUID but does NOT have FK constraints to the existing
-- 'father' table (which uses BIGSERIAL). The FK will be added when the father
-- table is migrated to UUID primary keys.

-- ============================================================================
-- 1. father_streaks table
-- ============================================================================
CREATE TABLE father_streaks (
    streak_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id            UUID NOT NULL UNIQUE,
    current_streak_days  INTEGER NOT NULL DEFAULT 0,
    longest_streak_days  INTEGER NOT NULL DEFAULT 0,
    streak_start_date    DATE,
    last_qualifying_date DATE,
    timezone             VARCHAR(50) NOT NULL DEFAULT 'UTC',
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_streak_non_negative
        CHECK (current_streak_days >= 0),

    CONSTRAINT chk_longest_streak_non_negative
        CHECK (longest_streak_days >= 0),

    CONSTRAINT chk_longest_streak_gte_current
        CHECK (longest_streak_days >= current_streak_days)
);

COMMENT ON TABLE father_streaks IS 'Tracks engagement streaks for the Father Growth System. One record per father.';
COMMENT ON COLUMN father_streaks.current_streak_days IS 'Number of consecutive calendar days with at least one qualifying interaction.';
COMMENT ON COLUMN father_streaks.longest_streak_days IS 'All-time longest streak in days. Monotonically non-decreasing.';
COMMENT ON COLUMN father_streaks.streak_start_date IS 'Date the current streak began. NULL when current_streak_days is 0.';
COMMENT ON COLUMN father_streaks.last_qualifying_date IS 'Most recent date a qualifying interaction was recorded.';
COMMENT ON COLUMN father_streaks.timezone IS 'Father timezone for calendar-day boundary calculations (e.g., Asia/Jerusalem).';
