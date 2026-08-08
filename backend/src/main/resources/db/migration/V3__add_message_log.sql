-- V3: Add message log for conversation history
-- This table stores all inbound and outbound messages for AI context

CREATE TABLE IF NOT EXISTS message_log (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for efficient retrieval of recent messages by father
CREATE INDEX IF NOT EXISTS idx_message_log_father_created ON message_log(father_id, created_at DESC);

-- Cleanup old messages (keep only last 50 per father to save space)
-- This can be run periodically via a scheduled job
COMMENT ON TABLE message_log IS 'Stores conversation message history for AI context. Retention: last 50 messages per father.';
