-- ============================================================================
-- V8.006: Celebration Events
-- ============================================================================
-- Table:
--   celebration_events — records significant moments worth celebrating
--
-- Requirements: 14.2
--
-- Note: father_id column uses UUID but does NOT have a FK constraint to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. celebration_events — metadata for significant growth moments
-- ============================================================================
CREATE TABLE celebration_events (
    event_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id                   UUID NOT NULL,
    event_type                  VARCHAR(30) NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT,
    related_growth_signal_points INTEGER,
    celebration_message         TEXT,
    motivational_prompt         TEXT,
    displayed                   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_celebration_event_type CHECK (
        event_type IN ('BELT_LEVEL_UP', 'ACHIEVEMENT_EARNED', 'MILESTONE_REACHED', 'STREAK_MILESTONE')
    )
);

-- Partial index for querying undisplayed events per father (hot path)
CREATE INDEX idx_celebration_events_undisplayed
    ON celebration_events(father_id)
    WHERE displayed = FALSE;

-- Index for querying recent events per father ordered by time
CREATE INDEX idx_celebration_events_father_created
    ON celebration_events(father_id, created_at DESC);
