-- =============================================================================
-- Enable pgvector Extension for Memory Knowledge System
-- Version: 11
-- Date: 2025
-- 
-- This migration enables the pgvector extension required for vector similarity
-- search in the Memory Knowledge System. The extension must be enabled before
-- creating tables that use the vector data type.
-- 
-- Requirements:
-- - PostgreSQL must have pgvector extension installed
-- - Database user must have permission to create extensions (or extension 
--   must be pre-installed by superuser)
-- =============================================================================

-- Enable pgvector extension for vector similarity search
-- This is required before creating columns with vector(1536) type
CREATE EXTENSION IF NOT EXISTS vector;
