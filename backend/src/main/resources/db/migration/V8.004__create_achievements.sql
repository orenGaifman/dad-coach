-- ============================================================================
-- V8.004: Achievements Definition & Father Achievements
-- ============================================================================
-- Tables:
--   1. achievements              - Predefined achievement definitions
--   2. father_achievements       - Join table tracking earned achievements per father
--
-- Note: father_id columns use UUID but do NOT have FK constraints to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. ACHIEVEMENTS — predefined badge/reward definitions
-- ============================================================================
CREATE TABLE achievements (
    achievement_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL UNIQUE,
    description         TEXT,
    category            VARCHAR(30) NOT NULL,
    criteria_json       JSONB NOT NULL,
    criteria_version    INTEGER NOT NULL DEFAULT 1,
    icon_key            VARCHAR(100),
    sort_order          INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_achievements_category CHECK (
        category IN ('MISSIONS', 'CONSISTENCY', 'GROWTH', 'CONVERSATIONS', 'GOALS', 'SPECIAL')
    )
);

-- ============================================================================
-- 2. FATHER_ACHIEVEMENTS — tracks which achievements each father has earned
-- ============================================================================
CREATE TABLE father_achievements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL,
    achievement_id      UUID NOT NULL REFERENCES achievements(achievement_id),
    earned_at           TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_father_achievement UNIQUE (father_id, achievement_id)
);

-- Indexes for common query patterns
CREATE INDEX idx_father_achievements_father_id ON father_achievements(father_id);
CREATE INDEX idx_father_achievements_earned_at ON father_achievements(father_id, earned_at DESC);
