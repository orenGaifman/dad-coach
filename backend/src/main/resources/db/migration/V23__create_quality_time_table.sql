-- Quality Time table for Deterministic Workflow Engine
-- Tracks scheduled quality time events with children, backed by Google Calendar
-- Requirements: 3.4, 15.1

CREATE TABLE quality_time (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id               BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id                BIGINT NOT NULL REFERENCES child(id) ON DELETE CASCADE,
    google_calendar_event_id VARCHAR(255),
    scheduled_start         TIMESTAMPTZ NOT NULL,
    scheduled_end           TIMESTAMPTZ NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completion_notes        TEXT,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reminder_sent           BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_sent          BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_qt_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT chk_qt_time_range CHECK (scheduled_end > scheduled_start)
);

-- Index for looking up quality times by father
CREATE INDEX idx_quality_time_father ON quality_time(father_id);

-- Partial index for finding scheduled quality times for a father, ordered by start time
-- Used for "what's my next scheduled quality time?" queries
CREATE INDEX idx_quality_time_father_scheduled ON quality_time(father_id, scheduled_start) 
    WHERE status = 'SCHEDULED';

-- Partial index for scheduler jobs that need to find quality times that have ended
-- Used for the follow-up transition job that transitions fathers to QUALITY_TIME_FOLLOW_UP
CREATE INDEX idx_quality_time_status ON quality_time(status, scheduled_end) 
    WHERE status = 'SCHEDULED';

-- Comments for documentation
COMMENT ON TABLE quality_time IS 'Quality Time events for the deterministic workflow engine. Each record represents a scheduled time slot for a father to spend with their child.';
COMMENT ON COLUMN quality_time.id IS 'Unique identifier for the quality time event';
COMMENT ON COLUMN quality_time.father_id IS 'Reference to the father who scheduled this quality time';
COMMENT ON COLUMN quality_time.child_id IS 'Reference to the child this quality time is scheduled with';
COMMENT ON COLUMN quality_time.google_calendar_event_id IS 'Google Calendar event ID for sync purposes';
COMMENT ON COLUMN quality_time.scheduled_start IS 'Start time of the quality time slot in UTC';
COMMENT ON COLUMN quality_time.scheduled_end IS 'End time of the quality time slot in UTC';
COMMENT ON COLUMN quality_time.status IS 'Current status: SCHEDULED, COMPLETED, MISSED, or CANCELLED';
COMMENT ON COLUMN quality_time.completion_notes IS 'Notes from father about what they did during quality time';
COMMENT ON COLUMN quality_time.completed_at IS 'Timestamp when the quality time was marked as completed';
COMMENT ON COLUMN quality_time.reminder_sent IS 'Whether the morning reminder has been sent';
COMMENT ON COLUMN quality_time.follow_up_sent IS 'Whether the follow-up message has been sent after event end time';
