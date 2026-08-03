-- Migration: V23__create_workflow_state_transition_log.sql
-- Description: Create workflow_state_transition_log table for tracking workflow state transitions
-- Requirements: 1.4 (state transition logging), 16.1 (audit trail)

CREATE TABLE workflow_state_transition_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL REFERENCES father(id),
    from_state          VARCHAR(30) NOT NULL,
    to_state            VARCHAR(30) NOT NULL,
    trigger_reason      VARCHAR(50) NOT NULL,
    trigger_message_id  UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for querying transition history by father, ordered by most recent first
CREATE INDEX idx_wstl_father ON workflow_state_transition_log(father_id, created_at DESC);
