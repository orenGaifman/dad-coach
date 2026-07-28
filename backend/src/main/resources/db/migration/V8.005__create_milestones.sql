-- V8.005__create_milestones.sql
-- Father Workspace: Milestones definition and father-milestones join table (SPEC-008)
--
-- Creates:
--   1. milestones             - Predefined significant journey checkpoints
--   2. father_milestones      - Join table recording when a father reaches a milestone
--
-- Requirements: 13.7, 13.8
--
-- Note: father_id columns use UUID but do NOT have FK constraints to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. milestones — definition table
-- ============================================================================
CREATE TABLE milestones (
    milestone_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL UNIQUE,
    description         TEXT,
    category            VARCHAR(30) NOT NULL,
    trigger_condition   JSONB NOT NULL,
    condition_version   INTEGER NOT NULL DEFAULT 1,
    icon_key            VARCHAR(100),
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_milestone_category CHECK (category IN (
        'BELT_TRANSITIONS', 'MISSIONS', 'ACCOUNT_AGE', 'GOALS', 'STREAKS', 'SPECIAL'
    ))
);

-- ============================================================================
-- 2. father_milestones — join table recording reached milestones
-- ============================================================================
CREATE TABLE father_milestones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    milestone_id    UUID NOT NULL,
    reached_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_father_milestones_milestone
        FOREIGN KEY (milestone_id) REFERENCES milestones(milestone_id),
    CONSTRAINT uq_father_milestone UNIQUE (father_id, milestone_id)
);

-- Index on father_id for querying all milestones reached by a father
CREATE INDEX idx_father_milestones_father ON father_milestones(father_id);
