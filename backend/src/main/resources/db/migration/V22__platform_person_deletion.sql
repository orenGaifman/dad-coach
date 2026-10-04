-- A father deleted for good (self-service or admin) is deleted on the AI Workflow Platform too: his person,
-- conversations, messages, executions and scheduled turns. The platform owns that deletion; Dad Coach only
-- asks for it, through the platform's generic product-facing API. This table is the outbox of those requests:
-- written in the same transaction as the father's deletion, sent after commit and retried with backoff until
-- the platform confirms - so a platform outage never leaves a deleted father's data behind.
--
-- external_user_id ("whatsapp:+972...") is needed to register the person under its ref before the delete (older
-- platform persons were created without one) and to keep a deleted father's messages away from the platform
-- until it confirms; it is cleared when the request completes. purge_local: the father's own Dad Coach data is
-- purged once the platform confirms (self-service deletion); an admin delete purges it at once.
CREATE TABLE IF NOT EXISTS platform_person_deletion (
    father_id         BIGINT PRIMARY KEY,
    person_ref        VARCHAR(64) NOT NULL,
    external_user_id  VARCHAR(64),
    purge_local       BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error        VARCHAR(500),
    completed_at      TIMESTAMPTZ,
    outcome           VARCHAR(30)
);

CREATE INDEX IF NOT EXISTS idx_platform_person_deletion_due ON platform_person_deletion (next_attempt_at) WHERE completed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_platform_person_deletion_pending_id ON platform_person_deletion (external_user_id) WHERE completed_at IS NULL;
