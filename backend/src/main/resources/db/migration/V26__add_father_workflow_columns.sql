-- Father table additions for deterministic workflow engine
-- Requirements: 1.2 (single active workflow state), 8.2 (dashboard metrics)

-- Workflow state columns
ALTER TABLE father ADD COLUMN current_workflow_state VARCHAR(30) DEFAULT 'WELCOME';
ALTER TABLE father ADD COLUMN previous_workflow_state VARCHAR(30);
ALTER TABLE father ADD COLUMN workflow_state_entered_at TIMESTAMPTZ;
ALTER TABLE father ADD COLUMN welcomed_at TIMESTAMPTZ;

-- Dashboard metrics columns
ALTER TABLE father ADD COLUMN quality_time_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN quality_time_longest_streak INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN total_quality_times_completed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE father ADD COLUMN current_belt VARCHAR(20) NOT NULL DEFAULT 'WHITE';

-- Index for querying fathers by workflow state (used by scheduler jobs)
CREATE INDEX idx_father_workflow_state ON father(current_workflow_state);

-- Comments
COMMENT ON COLUMN father.current_workflow_state IS 'Current workflow state: WELCOME, SCHEDULE_QUALITY_TIME, WAITING, QUALITY_TIME_FOLLOW_UP, ACTIVITY_IDEAS';
COMMENT ON COLUMN father.previous_workflow_state IS 'Previous state, used for returning from ACTIVITY_IDEAS overlay state';
COMMENT ON COLUMN father.workflow_state_entered_at IS 'Timestamp when father entered current workflow state';
COMMENT ON COLUMN father.welcomed_at IS 'Timestamp when father completed WELCOME state (never returns to WELCOME after this)';
COMMENT ON COLUMN father.quality_time_streak IS 'Current consecutive Quality Time completions';
COMMENT ON COLUMN father.quality_time_longest_streak IS 'Historical maximum streak';
COMMENT ON COLUMN father.total_quality_times_completed IS 'Total number of completed Quality Times';
COMMENT ON COLUMN father.current_belt IS 'Current belt level: WHITE, YELLOW, ORANGE, GREEN, BLUE, BROWN, BLACK';
