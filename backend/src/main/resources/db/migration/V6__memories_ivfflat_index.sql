-- =============================================================================
-- IVFFlat Index on Memories Embedding Column (SPEC-004)
-- Version: 6
-- Date: 2025
-- 
-- Creates the IVFFlat vector index on the memories.embedding column for
-- efficient semantic similarity search.
-- 
-- This is created as a separate migration (after V5 memories table) to allow
-- for data population before building the index, which is better for performance.
-- 
-- Index Configuration:
--   - Index type: ivfflat (Inverted File Flat)
--   - Operator class: vector_cosine_ops (for cosine similarity search)
--   - Lists: 50 (good for ~500 memories per father scale)
-- 
-- Requires: 
--   - pgvector extension (enabled in V4__enable_pgvector_extension.sql)
--   - memories table (created in V5__memories_table.sql)
-- =============================================================================

-- Create IVFFlat index on embedding column for vector similarity search
CREATE INDEX idx_memories_embedding ON memories USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

-- Add comment for documentation
COMMENT ON INDEX idx_memories_embedding IS 'IVFFlat vector index for semantic similarity search with cosine distance, configured with 50 lists for ~500 memories per father';
