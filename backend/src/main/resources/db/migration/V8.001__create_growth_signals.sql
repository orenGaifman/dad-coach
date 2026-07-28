-- V8.001__create_growth_signals.sql
-- Growth Signals table for SPEC-008: Father Workspace Backend — Growth System
--
-- Creates:
--   1. growth_signals — Immutable append-only store for growth signal events.
--      Each record represents a discrete contribution to a father's Growth_Score.
--
-- Design decisions:
--   - Signals are immutable once recorded (append-only, no UPDATE/DELETE in application layer)
--   - Duplicate detection via unique constraint on (father_id, signal_type, source_entity_id)
--   - scoring_policy_version enables forward-only scoring rule evolution (AD-7)
--   - points_awarded is recorded at write time and never recalculated (AD-3)
--
-- Note: father_id uses UUID but does NOT have FK constraints to the existing
-- 'father' table (which uses BIGSERIAL). The FK will be added when the father
-- table is migrated to UUID primary keys.

-- ============================================================================
-- 1. growth_signals table
-- ============================================================================
CREATE TABLE growth_signals (
    signal_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id              UUID NOT NULL,
    signal_type            VARCHAR(50) NOT NULL,
    points_awarded         INTEGER NOT NULL,
    source_entity_id       UUID NOT NULL,
    source_entity_type     VARCHAR(50) NOT NULL,
    scoring_policy_version INTEGER NOT NULL DEFAULT 1,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Points must always be positive (growth signals are always additive)
    CONSTRAINT chk_growth_signals_points_positive CHECK (points_awarded > 0)
);

-- ============================================================================
-- 2. Unique constraint for duplicate signal prevention (Requirement 11.6)
-- ============================================================================
-- A father can only receive one signal of a given type per source entity.
-- This ensures idempotent processing: replaying the same domain event
-- will not produce duplicate score contributions.
ALTER TABLE growth_signals
    ADD CONSTRAINT uq_growth_signals_dedup
    UNIQUE (father_id, signal_type, source_entity_id);

-- ============================================================================
-- 3. Indexes
-- ============================================================================

-- Lookup all signals for a father (used by score rebuild, breakdown queries)
CREATE INDEX idx_growth_signals_father_id
    ON growth_signals (father_id);

-- Retrieve signals in reverse chronological order for a father (recent signals list)
CREATE INDEX idx_growth_signals_father_created_desc
    ON growth_signals (father_id, created_at DESC);

-- Filter signals by type for a father (score-by-type breakdown, signal type queries)
CREATE INDEX idx_growth_signals_father_signal_type
    ON growth_signals (father_id, signal_type);
