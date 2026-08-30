-- V19: Add AI decision tracking columns to message_log table
-- This enables debugging AI behavior by storing:
-- - Which tool the AI chose to use
-- - Tool parameters (as JSON)
-- - Resulting state transition
-- - Whether the tool execution succeeded

ALTER TABLE message_log ADD COLUMN IF NOT EXISTS tool_used VARCHAR(100);
ALTER TABLE message_log ADD COLUMN IF NOT EXISTS tool_parameters JSONB;
ALTER TABLE message_log ADD COLUMN IF NOT EXISTS previous_state VARCHAR(50);
ALTER TABLE message_log ADD COLUMN IF NOT EXISTS new_state VARCHAR(50);
ALTER TABLE message_log ADD COLUMN IF NOT EXISTS tool_success BOOLEAN;
ALTER TABLE message_log ADD COLUMN IF NOT EXISTS error_message TEXT;

-- Add comments for documentation
COMMENT ON COLUMN message_log.tool_used IS 'The AI agent tool that was used to process this message (e.g., respond_to_father, start_quality_time)';
COMMENT ON COLUMN message_log.tool_parameters IS 'JSON object containing the parameters passed to the tool';
COMMENT ON COLUMN message_log.previous_state IS 'Workflow state before AI processing';
COMMENT ON COLUMN message_log.new_state IS 'Workflow state after AI processing (null if no transition)';
COMMENT ON COLUMN message_log.tool_success IS 'Whether the tool execution succeeded';
COMMENT ON COLUMN message_log.error_message IS 'Error message if tool execution failed';

-- Create index for efficient querying by tool used (for analytics/debugging)
CREATE INDEX IF NOT EXISTS idx_message_log_tool_used ON message_log(tool_used);
