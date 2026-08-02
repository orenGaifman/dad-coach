-- Quality Time Commitment table
-- Stores father's commitments to spend quality time with children at specific times

CREATE TABLE IF NOT EXISTS quality_time_commitment (
    id              BIGSERIAL PRIMARY KEY,
    father_id       BIGINT NOT NULL REFERENCES fathers(id) ON DELETE CASCADE,
    child_id        BIGINT REFERENCES children(id) ON DELETE SET NULL,
    
    -- Scheduled time
    scheduled_date  DATE NOT NULL,
    scheduled_time  TIME NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL, -- Combined date+time in UTC
    
    -- Duration and activity
    duration_minutes INTEGER DEFAULT 30,
    activity_type   VARCHAR(50), -- PLAY, HOMEWORK, MEAL, WALK, TALK, OTHER
    activity_note   VARCHAR(500), -- Free text describing the planned activity
    
    -- Status tracking
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    -- SCHEDULED: Father committed, waiting for time
    -- REMINDED: 30-min reminder sent
    -- COMPLETED: Father reported completion
    -- MISSED: Time passed with no completion
    -- CANCELLED: Father cancelled
    
    -- Reminder tracking
    reminder_sent_at    TIMESTAMPTZ,
    reminder_message_id VARCHAR(100),
    
    -- Completion tracking
    completed_at        TIMESTAMPTZ,
    completion_note     VARCHAR(500),
    points_awarded      INTEGER DEFAULT 0,
    
    -- Source tracking
    created_via     VARCHAR(30) DEFAULT 'WHATSAPP', -- WHATSAPP, DASHBOARD, API
    conversation_id UUID, -- Which conversation led to this commitment
    
    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_commitment_father_status ON quality_time_commitment(father_id, status);
CREATE INDEX idx_commitment_scheduled_at ON quality_time_commitment(scheduled_at);
CREATE INDEX idx_commitment_reminder_pending ON quality_time_commitment(status, scheduled_at) 
    WHERE status = 'SCHEDULED';

-- Comments
COMMENT ON TABLE quality_time_commitment IS 'Tracks father commitments to spend quality time with children';
COMMENT ON COLUMN quality_time_commitment.scheduled_at IS 'UTC timestamp of when the quality time is scheduled';
COMMENT ON COLUMN quality_time_commitment.status IS 'SCHEDULED, REMINDED, COMPLETED, MISSED, CANCELLED';
