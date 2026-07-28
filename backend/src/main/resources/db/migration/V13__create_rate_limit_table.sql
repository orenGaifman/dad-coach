-- V13__create_rate_limit_table.sql
-- User Onboarding & Activation rate limiting table for SPEC-007
--
-- Creates:
--   1. rate_limit_entries - Tracks rate limit attempts by key type (IP, PHONE)
--      with sliding window approach. Supports the OnboardingRateLimiter component.
--
-- Indexes:
--   - Compound unique constraint on (key_type, key_value, window_start) ensures
--     one entry per rate limit window per key.
--   - Partial index on active windows for efficient cleanup queries.

-- ============================================================================
-- 1. rate_limit_entries table
-- ============================================================================
CREATE TABLE rate_limit_entries (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type        VARCHAR(10) NOT NULL,
    key_value       VARCHAR(255) NOT NULL,
    window_start    TIMESTAMPTZ NOT NULL,
    attempt_count   INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rate_limit_window UNIQUE (key_type, key_value, window_start),
    CONSTRAINT chk_rate_limit_key_type CHECK (key_type IN ('IP', 'PHONE')),
    CONSTRAINT chk_rate_limit_attempt_count CHECK (attempt_count >= 1)
);

-- Index on window_start for efficient cleanup queries (ordered by recency)
CREATE INDEX idx_rate_limit_active_windows
    ON rate_limit_entries (window_start DESC);
