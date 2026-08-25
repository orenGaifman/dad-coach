-- =============================================================================
-- Memory Versions Table for Memory Knowledge System (SPEC-004)
-- Version: 14
-- Date: 2025
-- 
-- Creates the memory_versions table for tracking version history of memories.
-- This table stores snapshots of memory state at each change, enabling audit
-- trails and the ability to reconstruct memory history.
-- 
-- Per SPEC-004 Requirement 10 (Memory Audit & Version History):
--   - Version history snapshots content, confidence, importance at each change
--   - Preserved for superseded memories allowing reconstruction if needed
--   - Supports the audit trail for all memory operations
-- 
-- Requires: memories table (created in V12__memories_table.sql)
-- =============================================================================

-- =============================================================================
-- MEMORY_VERSIONS TABLE
-- Stores version history snapshots for memory changes
-- =============================================================================

CREATE TABLE IF NOT EXISTS memory_versions (
    -- Primary key
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Reference to parent memory (cascade delete when memory is deleted)
    memory_id       UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
    
    -- Version number within the memory (1, 2, 3, ...)
    version_number  INTEGER NOT NULL,
    
    -- Snapshot of memory content at this version
    content         TEXT NOT NULL,
    
    -- Snapshot of confidence score at this version
    confidence      NUMERIC(3,2) NOT NULL,
    
    -- Snapshot of importance score at this version
    importance      INTEGER NOT NULL,
    
    -- Timestamp when this version was created (the change occurred)
    changed_at      TIMESTAMPTZ NOT NULL,
    
    -- Reason for the change (e.g., CREATED, UPDATED, CONFIRMED, SUPERSEDED, CORRECTION)
    change_reason   VARCHAR(30) NOT NULL,
    
    -- Ensure unique version numbers per memory
    UNIQUE(memory_id, version_number)
);

-- Add comments for documentation
COMMENT ON TABLE memory_versions IS 'Memory version history - stores snapshots of memory state at each change for audit and reconstruction';
COMMENT ON COLUMN memory_versions.memory_id IS 'Reference to the parent memory this version belongs to';
COMMENT ON COLUMN memory_versions.version_number IS 'Sequential version number within the memory (1 = initial state)';
COMMENT ON COLUMN memory_versions.content IS 'Snapshot of memory content at this version';
COMMENT ON COLUMN memory_versions.confidence IS 'Snapshot of confidence score (0.0-1.0) at this version';
COMMENT ON COLUMN memory_versions.importance IS 'Snapshot of importance score (1-10) at this version';
COMMENT ON COLUMN memory_versions.changed_at IS 'Timestamp when this version was created';
COMMENT ON COLUMN memory_versions.change_reason IS 'Reason for creating this version: CREATED, UPDATED, CONFIRMED, SUPERSEDED, CORRECTION, DECAY';

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Primary query pattern: get all versions for a memory in order
CREATE INDEX IF NOT EXISTS idx_memory_versions_memory_id ON memory_versions(memory_id, version_number);

-- Query by change time (useful for cleanup jobs)
CREATE INDEX IF NOT EXISTS idx_memory_versions_changed_at ON memory_versions(changed_at);
