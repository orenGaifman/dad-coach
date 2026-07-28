-- V10__communication_channels.sql
-- Communication Channels tables for SPEC-006: Communication Channels
--
-- Creates:
--   1. communication_endpoints  - Father ↔ channel identity mapping (WhatsApp, SMS, etc.)
--   2. delivery_records         - Outbound message delivery status tracking
--   3. template_messages        - Pre-approved template message registry
--   4. media_assets             - Inbound media storage with 90-day retention
--
-- Note: father_id columns use UUID but do NOT have FK constraints to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. communication_endpoints table
-- ============================================================================
CREATE TABLE communication_endpoints (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    channel_identity    VARCHAR(50) NOT NULL,
    is_primary          BOOLEAN NOT NULL DEFAULT TRUE,
    session_opens_at    TIMESTAMPTZ,
    session_closes_at   TIMESTAMPTZ,
    last_active_at      TIMESTAMPTZ,
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(channel, channel_identity)
);

CREATE INDEX idx_endpoints_father ON communication_endpoints(father_id);

-- ============================================================================
-- 2. delivery_records table
-- ============================================================================
CREATE TABLE delivery_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL,
    father_id           UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    direction           VARCHAR(10) NOT NULL,
    failure_reason      VARCHAR(100),
    retry_count         INTEGER NOT NULL DEFAULT 0,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index for active deliveries requiring processing
CREATE INDEX idx_delivery_status ON delivery_records(status) WHERE status IN ('PENDING','SENT');

-- Correlate provider status callbacks to internal records
CREATE INDEX idx_delivery_provider_id ON delivery_records(provider_message_id);

-- ============================================================================
-- 3. template_messages table
-- ============================================================================
CREATE TABLE template_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_name   VARCHAR(100) NOT NULL UNIQUE,
    language        VARCHAR(10) NOT NULL DEFAULT 'es',
    category        VARCHAR(20) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    max_variables   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- 4. media_assets table
-- ============================================================================
CREATE TABLE media_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    message_id      UUID NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size       INTEGER NOT NULL,
    content         BYTEA NOT NULL,
    downloaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- Cleanup job uses this to find expired media for deletion
CREATE INDEX idx_media_expires ON media_assets(expires_at);
