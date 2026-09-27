-- V21: Persistent idempotency for Workflow Platform scheduled-response callbacks.
-- The platform calls POST /api/integration/workflow/scheduled-response once per fired trigger that
-- produced a user-facing message, with X-Idempotency-Key = scheduled-response:{triggerId}.
-- One row per idempotency key guarantees a redelivered/concurrent callback never sends twice.

CREATE TABLE scheduled_response_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(100) NOT NULL,
    trigger_id VARCHAR(64) NOT NULL,
    workflow_instance_id VARCHAR(64),
    father_id BIGINT NOT NULL,
    target_state_key VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    delivery_mode VARCHAR(20),
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_scheduled_response_delivery_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_scheduled_response_delivery_father FOREIGN KEY (father_id) REFERENCES father(id) ON DELETE CASCADE
);

CREATE INDEX idx_scheduled_response_delivery_father ON scheduled_response_delivery(father_id);

COMMENT ON TABLE scheduled_response_delivery IS 'Workflow Platform proactive messages delivered to fathers, one row per callback idempotency key';
