-- V9__application_api.sql
-- Application API tables for SPEC-007: Application API
--
-- Creates:
--   1. api_audit_log     - Append-only audit log for all mutating API operations
--                          and admin reads on father data
--   2. idempotency_keys  - 24-hour TTL cache for idempotent request handling

-- ============================================================================
-- 1. api_audit_log table
-- ============================================================================
CREATE TABLE api_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id      UUID NOT NULL,
    actor_type      VARCHAR(20) NOT NULL,
    actor_id        UUID NOT NULL,
    operation       VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(30) NOT NULL,
    resource_id     UUID,
    result          VARCHAR(20) NOT NULL,
    error_code      VARCHAR(50),
    changes         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for querying audit entries by actor (e.g., "show me all actions by this admin")
CREATE INDEX idx_api_audit_actor ON api_audit_log(actor_id, created_at DESC);

-- Index for querying audit entries by resource (e.g., "show me all changes to this child")
CREATE INDEX idx_api_audit_resource ON api_audit_log(resource_type, resource_id, created_at DESC);

-- ============================================================================
-- 2. idempotency_keys table
-- ============================================================================
CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,
    actor_id        UUID NOT NULL,
    response_status INTEGER NOT NULL,
    response_body   JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- Index for efficient cleanup of expired idempotency records (24h TTL)
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
