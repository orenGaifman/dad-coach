-- V11__create_onboarding_tables.sql
-- User Onboarding & Activation tables for SPEC-007
--
-- Creates:
--   1. invitations              - Invitation tokens with usage tracking
--   2. onboarding_sessions      - Multi-step wizard sessions with encrypted wizard_data
--   3. activation_records       - WhatsApp activation handshake tracking
--
-- Note: father_id columns use UUID but do NOT have FK constraints to the
-- existing tables. The FK will be added when entity relationships are finalized.

-- ============================================================================
-- 1. invitations table
-- ============================================================================
CREATE TABLE invitations (
    invitation_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token               VARCHAR(32) NOT NULL,
    type                VARCHAR(15) NOT NULL,  -- SINGLE_USE, REUSABLE
    status              VARCHAR(10) NOT NULL DEFAULT 'CREATED',
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    max_uses            INTEGER NOT NULL DEFAULT 1,
    current_uses        INTEGER NOT NULL DEFAULT 0,
    metadata            JSONB,
    CONSTRAINT uq_invitations_token UNIQUE (token),
    CONSTRAINT chk_invitations_type CHECK (type IN ('SINGLE_USE', 'REUSABLE')),
    CONSTRAINT chk_invitations_status CHECK (status IN ('CREATED', 'SENT', 'OPENED', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_invitations_uses CHECK (current_uses >= 0 AND current_uses <= max_uses)
);

-- Partial index for active invitations (excludes terminal states)
CREATE INDEX idx_invitations_status_expires ON invitations(status, expires_at)
    WHERE status NOT IN ('EXPIRED', 'REVOKED', 'USED');

CREATE INDEX idx_invitations_created_by ON invitations(created_by);

-- ============================================================================
-- 2. onboarding_sessions table
-- ============================================================================
CREATE TABLE onboarding_sessions (
    session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id       UUID NOT NULL REFERENCES invitations(invitation_id),
    father_id           UUID,  -- Nullable until provisioning; FK added after fathers table exists
    current_step        VARCHAR(20) NOT NULL DEFAULT 'WELCOME',
    status              VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS',
    wizard_data         BYTEA,  -- AES-256-GCM encrypted JSONB
    language            VARCHAR(5),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    CONSTRAINT chk_sessions_step CHECK (current_step IN (
        'WELCOME', 'LANGUAGE', 'FATHER_PROFILE', 'CHILDREN', 'GOALS', 'PREFERENCES', 'REVIEW', 'ACTIVATION'
    )),
    CONSTRAINT chk_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'EXPIRED', 'ABANDONED'))
);

CREATE INDEX idx_sessions_invitation ON onboarding_sessions(invitation_id);

-- Partial index: only index father_id when it has been set (post-provisioning)
CREATE INDEX idx_sessions_father ON onboarding_sessions(father_id) WHERE father_id IS NOT NULL;

-- Partial index: only active sessions need expiration checks
CREATE INDEX idx_sessions_status_expires ON onboarding_sessions(status, expires_at)
    WHERE status = 'IN_PROGRESS';

-- ============================================================================
-- 3. activation_records table
-- ============================================================================
CREATE TABLE activation_records (
    activation_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id               UUID NOT NULL UNIQUE,
    session_id              UUID NOT NULL REFERENCES onboarding_sessions(session_id),
    status                  VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    deep_link_generated_at  TIMESTAMPTZ,
    link_clicked_at         TIMESTAMPTZ,
    message_received_at     TIMESTAMPTZ,
    conversation_started_at TIMESTAMPTZ,
    retry_count             INTEGER NOT NULL DEFAULT 0,
    failure_reason          VARCHAR(200),
    CONSTRAINT chk_activation_status CHECK (status IN (
        'PENDING', 'LINK_CLICKED', 'MESSAGE_SENT', 'CONVERSATION_STARTED', 'FAILED'
    )),
    CONSTRAINT chk_activation_retry CHECK (retry_count >= 0 AND retry_count <= 3)
);

-- Partial index: only non-terminal activation records need status-based queries
CREATE INDEX idx_activation_status ON activation_records(status) WHERE status IN ('PENDING', 'LINK_CLICKED');
