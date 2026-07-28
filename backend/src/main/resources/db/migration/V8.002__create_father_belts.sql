-- V8.002__create_father_belts.sql
-- Father Workspace Backend (SPEC-008) — Belt Progression System
--
-- Creates:
--   1. father_belts - Tracks each father's current belt level, cached score,
--      and belt earned timestamp. One record per father (UNIQUE constraint).
--      Supports Requirements 10.1, 10.2.
--
-- Design notes:
--   - belt_level defaults to 'WHITE' (beginner level)
--   - current_score is a cached read-model; source of truth is SUM(growth_signals.points_awarded)
--   - Belt progression is monotonic (AD-8): belts never regress
--   - CHECK constraint enforces valid belt levels per Requirement 10.2
--
-- Note: father_id uses UUID but does NOT have FK constraints to the existing
-- 'father' table (which uses BIGSERIAL). The FK will be added when the father
-- table is migrated to UUID primary keys.

-- ============================================================================
-- 1. father_belts table
-- ============================================================================
CREATE TABLE father_belts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL UNIQUE,
    belt_level      VARCHAR(10) NOT NULL DEFAULT 'WHITE',
    current_score   INTEGER NOT NULL DEFAULT 0,
    belt_earned_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_belt_level
        CHECK (belt_level IN ('WHITE', 'YELLOW', 'ORANGE', 'GREEN', 'BLUE', 'PURPLE', 'BROWN', 'BLACK')),

    CONSTRAINT chk_current_score_non_negative
        CHECK (current_score >= 0)
);

COMMENT ON TABLE father_belts IS 'Tracks belt progression for the Father Growth System. One record per father.';
COMMENT ON COLUMN father_belts.belt_level IS 'Current belt level: WHITE, YELLOW, ORANGE, GREEN, BLUE, PURPLE, BROWN, BLACK';
COMMENT ON COLUMN father_belts.current_score IS 'Cached growth score (read-model). Source of truth is SUM of growth_signals.points_awarded.';
COMMENT ON COLUMN father_belts.belt_earned_at IS 'Timestamp when the current belt level was earned.';
