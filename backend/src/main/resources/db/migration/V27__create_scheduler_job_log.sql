-- V24: Create scheduler_job_log table for tracking scheduler job executions
-- Requirements: 12.3, 16.3 - Scheduler jobs must be idempotent and persist execution logs

CREATE TABLE scheduler_job_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name            VARCHAR(100) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,
    records_processed   INTEGER NOT NULL DEFAULT 0,
    errors_count        INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_scheduler_job_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

-- Index for querying job history by job name and start time (for debugging and monitoring)
CREATE INDEX idx_scheduler_job_name_started ON scheduler_job_log(job_name, started_at DESC);

COMMENT ON TABLE scheduler_job_log IS 'Tracks execution of scheduled jobs for idempotency and debugging';
COMMENT ON COLUMN scheduler_job_log.job_name IS 'Identifier for the scheduler job (e.g., morning_reminder, follow_up_transition, stale_state_detection)';
COMMENT ON COLUMN scheduler_job_log.started_at IS 'Timestamp when the job started execution';
COMMENT ON COLUMN scheduler_job_log.completed_at IS 'Timestamp when the job completed (null if still running or failed without completion)';
COMMENT ON COLUMN scheduler_job_log.records_processed IS 'Count of records successfully processed during this job run';
COMMENT ON COLUMN scheduler_job_log.errors_count IS 'Count of errors encountered during this job run';
COMMENT ON COLUMN scheduler_job_log.status IS 'Current status of the job: RUNNING, COMPLETED, or FAILED';
