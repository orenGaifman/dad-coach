-- =============================================================================
-- Memories Table for Memory Knowledge System (SPEC-004)
-- Version: 8
-- Date: 2025
-- 
-- Creates the memories table with all fields from the design document.
-- This table stores long-term contextual knowledge about fathers and their families.
-- 
-- Note: This is the new 'memories' table (plural) that replaces the simpler 
-- 'memory' table from V1. The IVFFlat index on the embedding column is created
-- in a separate migration to allow for data population first.
-- 
-- Requires: pgvector extension (enabled in V7__enable_pgvector_extension.sql)
-- =============================================================================

-- =============================================================================
-- MEMORIES TABLE
-- Core table for storing contextual knowledge with vector embeddings
-- =============================================================================

CREATE TABLE IF NOT EXISTS memories (
    -- Primary key
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Ownership and subject references
    -- Note: References father/child tables which use BIGINT primary keys
    father_id               BIGINT NOT NULL REFERENCES father(id),
    child_id                BIGINT REFERENCES child(id),
    
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
    
    -- Goal and event linking
    goal_id                 UUID,
    event_date              DATE,
    event_end_date          DATE,
    is_recurring            BOOLEAN DEFAULT FALSE,
    
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

-- Add comment for documentation
COMMENT ON TABLE memories IS 'Memory Knowledge System - stores contextual knowledge about fathers, children, and families with vector embeddings for semantic search';
COMMENT ON COLUMN memories.category IS 'Memory category: IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, CHALLENGE, MILESTONE, CONTEXT, CONVERSATION_SUMMARY, EVENT, HABIT, FAMILY';
COMMENT ON COLUMN memories.subject_type IS 'Subject of the memory: FATHER, CHILD, or FAMILY';
COMMENT ON COLUMN memories.state IS 'Lifecycle state: ACTIVE, CONFIRMED, SUPERSEDED, ARCHIVED, EXPIRED, DELETED';
COMMENT ON COLUMN memories.source_type IS 'Source: CONVERSATION_EXTRACTION, ONBOARDING, FATHER_CORRECTION, SYSTEM_GENERATED, MISSION_OUTCOME';
COMMENT ON COLUMN memories.embedding IS 'OpenAI text-embedding-ada-002 vector (1536 dimensions) for semantic similarity search';

-- =============================================================================
-- INDEXES
-- Basic indexes for common query patterns (IVFFlat index is created separately)
-- =============================================================================

-- Primary query patterns: by father + state
CREATE INDEX idx_memories_father_state ON memories(father_id, state);

-- Category filtering: by father + category + state
CREATE INDEX idx_memories_father_category ON memories(father_id, category, state);

-- Child-specific queries: by father + subject_type + child + state
CREATE INDEX idx_memories_father_child ON memories(father_id, subject_type, child_id, state);

-- Expiration queries: active memories with expiration dates
CREATE INDEX idx_memories_expires ON memories(expires_at) WHERE state = 'ACTIVE';

-- Note: The IVFFlat index on embedding column is created in a separate migration
-- to allow for data population before building the index (better for performance)
