-- =============================================================================
-- Fix memories table - change father_id and child_id from BIGINT to UUID
-- Version: 17
-- Date: 2025
-- 
-- The Memory entity uses UUID for father_id and child_id, but V12 created
-- the table with BIGINT columns (referencing the father/child tables).
-- This migration drops and recreates the table with UUID columns.
-- 
-- Since the memories system is new and data can be regenerated from
-- conversations, we drop the existing table and recreate it.
-- =============================================================================

-- Drop dependent objects first (indexes are dropped automatically with table)
DROP TABLE IF EXISTS memory_versions CASCADE;
DROP TABLE IF EXISTS memory_audit_log CASCADE;
DROP TABLE IF EXISTS memories CASCADE;

-- =============================================================================
-- RECREATE MEMORIES TABLE WITH UUID COLUMNS
-- =============================================================================

CREATE TABLE memories (
    -- Primary key
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Ownership and subject references (UUID type, no FK constraints)
    -- These are logical references, not enforced FKs (allows flexibility)
    father_id               UUID NOT NULL,
    child_id                UUID,
    
    -- Classification
    category                VARCHAR(30) NOT NULL,
    subject_type            VARCHAR(10) NOT NULL,  -- FATHER, CHILD, FAMILY
    
    -- Content with length constraint (max 500 characters per SPEC-004)
    content                 TEXT NOT NULL CHECK (length(content) <= 500),
    
    -- Scoring with range constraints
    importance_score        INTEGER NOT NULL CHECK (importance_score BETWEEN 1 AND 10),
    confidence_score        NUMERIC(3,2) NOT NULL CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    
    -- Lifecycle state
    state                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- Source tracking
    source_type             VARCHAR(30) NOT NULL,
    source_conversation_id  UUID,
    
    -- Supersession and conflict tracking
    superseded_by           UUID REFERENCES memories(id),
    conflict_group_id       UUID,
    needs_user_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Goal and event linking
    goal_id                 UUID,
    event_date              DATE,
    event_end_date          DATE,
    is_recurring            BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Vector embedding for semantic similarity search (1536 dimensions for OpenAI ada-002)
    embedding               vector(1536),
    
    -- Access and confirmation tracking
    confirmation_count      INTEGER NOT NULL DEFAULT 0,
    access_count            INTEGER NOT NULL DEFAULT 0,
    
    -- Timestamps
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_confirmed_at       TIMESTAMPTZ,
    last_accessed_at        TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ
);

-- Add comments for documentation
COMMENT ON TABLE memories IS 'Memory Knowledge System - stores contextual knowledge about fathers, children, and families with vector embeddings for semantic search';
COMMENT ON COLUMN memories.father_id IS 'UUID reference to the father (logical reference, not FK to allow flexibility)';
COMMENT ON COLUMN memories.child_id IS 'UUID reference to a specific child (nullable for father-only or family memories)';
COMMENT ON COLUMN memories.category IS 'Memory category: IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, CHALLENGE, MILESTONE, CONTEXT, CONVERSATION_SUMMARY, EVENT, HABIT, FAMILY';
COMMENT ON COLUMN memories.subject_type IS 'Subject of the memory: FATHER, CHILD, or FAMILY';
COMMENT ON COLUMN memories.state IS 'Lifecycle state: ACTIVE, CONFIRMED, SUPERSEDED, ARCHIVED, EXPIRED, DELETED';
COMMENT ON COLUMN memories.source_type IS 'Source: CONVERSATION_EXTRACTION, ONBOARDING, FATHER_CORRECTION, SYSTEM_GENERATED, MISSION_OUTCOME';
COMMENT ON COLUMN memories.embedding IS 'OpenAI text-embedding-ada-002 vector (1536 dimensions) for semantic similarity search';

-- =============================================================================
-- INDEXES
-- =============================================================================

-- Primary query patterns: by father + state
CREATE INDEX idx_memories_father_state ON memories(father_id, state);

-- Category filtering: by father + category + state
CREATE INDEX idx_memories_father_category ON memories(father_id, category, state);

-- Child-specific queries: by father + subject_type + child + state
CREATE INDEX idx_memories_father_child ON memories(father_id, subject_type, child_id, state);

-- Expiration queries: active memories with expiration dates
CREATE INDEX idx_memories_expires ON memories(expires_at) WHERE state = 'ACTIVE';

-- Conflict resolution queries
CREATE INDEX idx_memories_conflict_group ON memories(conflict_group_id) WHERE conflict_group_id IS NOT NULL;

-- User confirmation pending queries
CREATE INDEX idx_memories_needs_confirmation ON memories(father_id, needs_user_confirmation) WHERE needs_user_confirmation = TRUE;

-- =============================================================================
-- RECREATE MEMORY_AUDIT_LOG TABLE
-- Matches MemoryAuditLog.java entity exactly
-- =============================================================================

CREATE TABLE memory_audit_log (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id               UUID NOT NULL,  -- No FK to allow logging after delete
    father_id               UUID NOT NULL,
    operation_type          VARCHAR(30) NOT NULL,
    from_state              VARCHAR(20),
    to_state                VARCHAR(20),
    trigger_type            VARCHAR(30) NOT NULL,
    triggered_by            VARCHAR(100) NOT NULL,
    state_before            JSONB,
    state_after             JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_audit_father ON memory_audit_log(father_id, created_at DESC);
CREATE INDEX idx_memory_audit_memory ON memory_audit_log(memory_id, created_at DESC);

COMMENT ON TABLE memory_audit_log IS 'Append-only audit trail for all memory operations per SPEC-004';
