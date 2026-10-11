-- Unified Workflow 5.4 v3 (ai-workflow-platform docs/architecture/PHASE5_4_SPEC.md §v3.3, §v3.8): the ledger of the
-- platform's calls of Dad Coach's scheduled jobs - one row per (job, slot). A slot already DONE answers its first result
-- (a duplicate call never runs the job again); a RUNNING row whose job lock is free was left by a dead process and is taken
-- over. Rows older than 14 days are deleted.
CREATE TABLE platform_job_run (
  job_key VARCHAR(64) NOT NULL,
  slot TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','DONE')),
  result JSONB,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ,
  PRIMARY KEY (job_key, slot)
);
-- the 14-day purge (started_at) and the watchdog's last start per job
CREATE INDEX idx_platform_job_run_started ON platform_job_run (started_at);
CREATE INDEX idx_platform_job_run_job_started ON platform_job_run (job_key, started_at);
