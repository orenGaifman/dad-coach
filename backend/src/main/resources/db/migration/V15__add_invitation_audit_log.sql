-- V15__add_invitation_audit_log.sql
-- User Onboarding & Activation audit logging table for SPEC-007
--
-- Creates:
--   1. invitation_audit_log - Logs all token validation attempts with hashed token
--      (SHA-256), IP address, user-agent, timestamp, and result. Supports
--      Requirement 6 criteria 12 for security audit trail.
--
-- Indexes:
--   - idx_audit_log_token_hash: for querying attempts by token
--   - idx_audit_log_ip_address: for detecting IP-based abuse patterns
--   - idx_audit_log_created_at: for the scheduled cleanup job (90-day retention)

-- ============================================================================
-- 1. invitation_audit_log table
-- ============================================================================
CREATE TABLE invitation_audit_log (
    log_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash      VARCHAR(64) NOT NULL,
    action          VARCHAR(30) NOT NULL,
    result          VARCHAR(20) NOT NULL,
    ip_address      VARCHAR(45) NOT NULL,
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_action CHECK (action IN ('VALIDATION', 'OPENED', 'USED')),
    CONSTRAINT chk_audit_result CHECK (result IN ('SUCCESS', 'NOT_FOUND', 'EXPIRED', 'REVOKED', 'EXHAUSTED', 'RATE_LIMITED'))
);

-- Index on token_hash for querying all attempts against a specific token
CREATE INDEX idx_audit_log_token_hash
    ON invitation_audit_log (token_hash);

-- Index on ip_address for detecting abuse patterns from a single IP
CREATE INDEX idx_audit_log_ip_address
    ON invitation_audit_log (ip_address);

-- Index on created_at for the AuditLogCleanupJob (90-day retention)
CREATE INDEX idx_audit_log_created_at
    ON invitation_audit_log (created_at);
