-- =============================================================================
-- V24 - durable idempotency (playbook §11.2, §34; DECISIONS D-012)
--
-- One row per (scope, key): TOOL:<toolKey> for side-effecting AI tools (reserve -> execute once -> replay the
-- stored response for the same key + payload hash + actor; another payload -> 409), WHATSAPP_INBOUND for Meta
-- message ids (a retried webhook is dropped, also after a restart). Rows expire (24 h tools, 7 days inbound).
-- =============================================================================
CREATE TABLE IF NOT EXISTS tool_idempotency (
    id              UUID PRIMARY KEY,
    scope           TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    payload_hash    TEXT,
    status          TEXT NOT NULL DEFAULT 'IN_PROGRESS',
    response_status INT,
    response_body   TEXT,
    actor_ref       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT tool_idem_unique UNIQUE (scope, idempotency_key),
    CONSTRAINT tool_idem_status_valid CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS tool_idem_expiry ON tool_idempotency (expires_at);
