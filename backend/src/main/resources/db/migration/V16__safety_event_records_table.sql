-- =============================================================================
-- Safety Event Records Table for Memory Knowledge System (SPEC-004)
-- Version: 16
-- Date: 2025
-- 
-- Creates the safety_event_records table for storing safety-related events
-- separately from normal memories. These events have long retention (7 years)
-- for legal/compliance reasons and are NOT deleted during GDPR erasure.
-- 
-- Per SPEC-004 Requirement 24 (Sensitive Memory Service):
--   - Safety events stored in separate table (not mixed with memories)
--   - Records include: event_type, summary (≤100 chars), requires_review flag
--   - Expiration enforced on safety records (7-year retention by default)
--   - Review workflow: reviewed_by, reviewed_at fields
--   - Never mixed into normal memory retrieval
--   - Queryable by father_id for support use cases
-- 
-- Note: father_id uses UUID (not BIGINT) intentionally to avoid foreign key
-- coupling with the father table. Safety events are architecturally isolated
-- and retained independently for legal compliance.
-- =============================================================================

-- =============================================================================
-- SAFETY_EVENT_RECORDS TABLE
-- Separate storage for safety-related events with long retention
-- =============================================================================

CREATE TABLE IF NOT EXISTS safety_event_records (
    -- Primary key
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Father reference (UUID, no FK to father table - intentional isolation)
    father_id           UUID NOT NULL,
    
    -- Event classification
    event_type          VARCHAR(30) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    
    -- Content fields
    summary             VARCHAR(100) NOT NULL,
    description         TEXT,
    
    -- Optional context links (no FKs - references may be deleted)
    conversation_id     UUID,
    memory_id           UUID,
    
    -- Additional metadata as JSON for extensibility
    metadata            JSONB,
    
    -- Review workflow
    requires_review     BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_by         UUID,
    reviewed_at         TIMESTAMPTZ,
    review_notes        TEXT,
    
    -- Timestamps
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL
);

-- Add comments for documentation
COMMENT ON TABLE safety_event_records IS 'Safety event records - stored separately from memories with 7-year retention for legal compliance per SPEC-004 Req 24';
COMMENT ON COLUMN safety_event_records.father_id IS 'Father UUID (no FK intentionally - safety events are isolated and retained independently)';
COMMENT ON COLUMN safety_event_records.event_type IS 'Type of safety event: SELF_HARM_MENTION, CHILD_ABUSE_CONCERN, DOMESTIC_VIOLENCE_INDICATOR, SUBSTANCE_ABUSE_MENTION, MENTAL_HEALTH_CRISIS, OTHER_SAFETY_CONCERN';
COMMENT ON COLUMN safety_event_records.severity IS 'Severity level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN safety_event_records.summary IS 'Brief summary for quick scanning (max 100 chars per SPEC-004)';
COMMENT ON COLUMN safety_event_records.description IS 'Detailed description for in-depth review (max 500 chars)';
COMMENT ON COLUMN safety_event_records.requires_review IS 'Flag indicating whether this event needs human review';
COMMENT ON COLUMN safety_event_records.expires_at IS 'When this record can be permanently deleted (default: 7 years from creation)';

-- =============================================================================
-- INDEXES
-- Based on JPA entity @Index annotations for query optimization
-- =============================================================================

-- Primary support query: find all events for a father ordered by time
CREATE INDEX idx_safety_events_father ON safety_event_records(father_id, created_at DESC);

-- Review workflow: find events requiring review, prioritized by severity
CREATE INDEX idx_safety_events_requires_review ON safety_event_records(requires_review, severity);

-- Time-based queries: ordering by creation time
CREATE INDEX idx_safety_events_created_at ON safety_event_records(created_at DESC);

-- Retention cleanup job: find expired records for deletion
CREATE INDEX idx_safety_events_expires_at ON safety_event_records(expires_at);

-- Event type analysis: queries by event type
CREATE INDEX idx_safety_events_event_type ON safety_event_records(event_type);

-- Severity analysis: queries by severity level
CREATE INDEX idx_safety_events_severity ON safety_event_records(severity);

