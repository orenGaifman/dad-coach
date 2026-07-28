-- V8__conversation_engine.sql
-- Conversation Engine tables for SPEC-005: Conversation Engine & Orchestration
--
-- Creates:
--   1. conversations        - Tracks conversation lifecycle, type, status, expiration
--   2. conversation_messages - Individual messages within a conversation (inbound/outbound)
--   3. processed_messages   - Idempotency tracking with 24-hour TTL
--   4. side_effect_outbox   - Transactional outbox for async side-effects
--
-- Note: father_id columns reference UUIDs but do NOT have FK constraints to
-- the existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. conversations table
-- ============================================================================
CREATE TABLE conversations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id            UUID NOT NULL,
    type                 VARCHAR(30) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    message_count        INTEGER NOT NULL DEFAULT 0,
    father_message_count INTEGER NOT NULL DEFAULT 0,
    system_message_count INTEGER NOT NULL DEFAULT 0,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at      TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    completion_reason    VARCHAR(50),
    CONSTRAINT chk_conv_status CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','ABANDONED')),
    CONSTRAINT chk_conv_type CHECK (type IN ('ONBOARDING','DAILY_COACHING','FOLLOW_UP','REFLECTION','INACTIVITY_CHECK','CELEBRATION','DIFFICULT_SITUATION'))
);

-- ============================================================================
-- 2. conversation_messages table
-- ============================================================================
CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    direction       VARCHAR(10) NOT NULL,
    content         TEXT NOT NULL,
    message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sequence_number INTEGER NOT NULL,
    CONSTRAINT chk_msg_direction CHECK (direction IN ('INBOUND','OUTBOUND'))
);

-- ============================================================================
-- 3. processed_messages table (idempotency with TTL)
-- ============================================================================
CREATE TABLE processed_messages (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    father_id       UUID NOT NULL,
    response_id     UUID,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- ============================================================================
-- 4. side_effect_outbox table
-- ============================================================================
CREATE TABLE side_effect_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    conversation_id UUID,
    effect_type     VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_retry_at   TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);

-- ============================================================================
-- 5. Indexes
-- ============================================================================

-- Find active conversations for a father (partial index for efficiency)
CREATE INDEX idx_conversations_father_active ON conversations(father_id) WHERE status = 'ACTIVE';

-- Find conversations approaching expiration (for recovery service)
CREATE INDEX idx_conversations_expires ON conversations(expires_at) WHERE status = 'ACTIVE';

-- Retrieve messages in order within a conversation
CREATE INDEX idx_conv_messages_conversation ON conversation_messages(conversation_id, sequence_number);

-- Cleanup expired idempotency records
CREATE INDEX idx_processed_messages_expires ON processed_messages(expires_at);

-- Poll pending/failed outbox entries for processing
CREATE INDEX idx_outbox_pending ON side_effect_outbox(status, next_retry_at) WHERE status IN ('PENDING','FAILED');
