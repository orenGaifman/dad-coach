-- =============================================================================
-- Memory Audit Log Table for Memory Knowledge System (SPEC-004)
-- Version: 15
-- Date: 2025
-- 
-- Creates the memory_audit_log table for tracking all operations performed
-- on memories. This is an append-only audit log per SPEC-004 Requirement 18.
-- 
-- Per SPEC-004 Requirement 18 (Memory Audit & Version History):
--   - Audit log is append-only (no updates or deletes)
--   - Written synchronously with memory operations (rollback on audit failure)
--   - Records: operation_type, from_state, to_state, trigger_type, triggered_by
--   - Queryable by father_id and time range
-- 
-- Per SPEC-004 Requirement 2 (Memory Lifecycle States):
--   - Every state transition logged with: memory_id, from_state, to_state,
--     trigger_reason, triggered_by (system/father), and timestamp
-- 
-- Requires: memories table (created in V12__memories_table.sql)
-- =============================================================================

-- =============================================================================
-- MEMORY_AUDIT_LOG TABLE
-- Append-only audit log for all memory operations
-- =============================================================================

CREATE TABLE IF NOT EXISTS memory_audit_log (
    -- Primary key
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Reference to the memory (not a foreign key - memory may be deleted but audit preserved)
    memory_id       UUID NOT NULL,
    
    -- Father reference (BIGINT to match father table primary key)
    father_id       BIGINT NOT NULL,
    
    -- Type of operation performed
    -- Examples: CREATED, UPDATED, STATE_CHANGE, CONFIRMED, SUPERSEDED, DELETED, GDPR_ERASURE
    operation_type  VARCHAR(30) NOT NULL,
    
    -- State transition tracking (nullable for non-state-change operations)
    from_state      VARCHAR(20),
    to_state        VARCHAR(20),
    
    -- What triggered this operation
    -- Examples: EXTRACTION, RETRIEVAL, DECAY_JOB, CONSOLIDATION_JOB, FATHER_ACTION, GDPR_REQUEST
    trigger_type    VARCHAR(30) NOT NULL,
    
    -- Who/what triggered this operation
    -- Examples: SYSTEM, FATHER, DECAY_SERVICE, EXTRACTION_SERVICE, GDPR_ERASURE_JOB
    triggered_by    VARCHAR(50) NOT NULL,
    
    -- Additional context stored as JSON
    -- Examples: { "reason": "explicit_correction", "confidence_change": "-0.3" }
    metadata        JSONB,
    
    -- Timestamp of the operation (immutable)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add comments for documentation
COMMENT ON TABLE memory_audit_log IS 'Append-only audit log for all memory operations per SPEC-004 Requirement 18';
COMMENT ON COLUMN memory_audit_log.memory_id IS 'Reference to the memory (preserved even after memory deletion)';
COMMENT ON COLUMN memory_audit_log.father_id IS 'Father ID for querying audit history by father';
COMMENT ON COLUMN memory_audit_log.operation_type IS 'Type of operation: CREATED, UPDATED, STATE_CHANGE, CONFIRMED, SUPERSEDED, DELETED, GDPR_ERASURE';
COMMENT ON COLUMN memory_audit_log.from_state IS 'Previous state for state transitions (null for creation)';
COMMENT ON COLUMN memory_audit_log.to_state IS 'New state for state transitions (null for non-state operations)';
COMMENT ON COLUMN memory_audit_log.trigger_type IS 'What triggered this operation: EXTRACTION, RETRIEVAL, DECAY_JOB, FATHER_ACTION, etc.';
COMMENT ON COLUMN memory_audit_log.triggered_by IS 'Actor that triggered: SYSTEM, FATHER, or specific service name';
COMMENT ON COLUMN memory_audit_log.metadata IS 'Additional JSON context for the operation';
COMMENT ON COLUMN memory_audit_log.created_at IS 'Immutable timestamp when the operation was logged';

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Primary query pattern: get audit history for a father ordered by time (most recent first)
-- Per design.md: CREATE INDEX idx_memory_audit_father ON memory_audit_log(father_id, created_at DESC);
CREATE INDEX idx_memory_audit_father ON memory_audit_log(father_id, created_at DESC);

-- Query by memory_id to get full history of a specific memory
CREATE INDEX idx_memory_audit_memory ON memory_audit_log(memory_id, created_at);

-- Query by operation type (useful for monitoring/reporting)
CREATE INDEX idx_memory_audit_operation ON memory_audit_log(operation_type, created_at);
