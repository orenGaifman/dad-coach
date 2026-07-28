-- V8.007__create_activity_reports.sql
-- Father Workspace: Activity Reports table (SPEC-008)
--
-- Creates:
--   1. activity_reports — Stores quality time and positive activity reports
--      submitted by fathers to track real-world parenting engagement.
--
-- Requirements: 25.1, 25.7
--
-- Design decisions:
--   - report_type distinguishes QUALITY_TIME from POSITIVE_ACTIVITY
--   - duration_minutes is nullable (only applies to quality time reports)
--   - activity_type is nullable (only applies to positive activity reports)
--   - child_id is nullable (positive activities may not reference a specific child)
--   - Duplicate prevention via unique constraint on (father_id, child_id, duration_minutes, activity_date)
--
-- Note: father_id uses UUID but does NOT have FK constraints to the existing
-- 'father' table (which uses BIGSERIAL). The FK will be added when the father
-- table is migrated to UUID primary keys.

-- ============================================================================
-- 1. activity_reports table
-- ============================================================================
CREATE TABLE activity_reports (
    report_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id        UUID NOT NULL,
    child_id         UUID,
    report_type      VARCHAR(30) NOT NULL,
    duration_minutes INTEGER,
    activity_type    VARCHAR(30),
    description      TEXT,
    activity_date    DATE NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- report_type must be one of the allowed values
    CONSTRAINT chk_activity_reports_report_type CHECK (
        report_type IN ('QUALITY_TIME', 'POSITIVE_ACTIVITY')
    ),

    -- duration_minutes: nullable, but when present must be between 15 and 480
    CONSTRAINT chk_activity_reports_duration CHECK (
        duration_minutes IS NULL OR (duration_minutes >= 15 AND duration_minutes <= 480)
    ),

    -- activity_type: nullable, but when present must be one of the allowed values
    CONSTRAINT chk_activity_reports_activity_type CHECK (
        activity_type IS NULL OR activity_type IN (
            'PRAISE', 'SHARED_ACTIVITY', 'TEACHING_MOMENT', 'QUALITY_CONVERSATION', 'OTHER'
        )
    )
);

-- ============================================================================
-- 2. Unique constraint for duplicate report prevention (Requirement 25.7)
-- ============================================================================
-- The same father cannot report quality time for the same child with the same
-- duration on the same activity_date more than once.
ALTER TABLE activity_reports
    ADD CONSTRAINT uq_activity_reports_dedup
    UNIQUE (father_id, child_id, duration_minutes, activity_date);

-- ============================================================================
-- 3. Indexes
-- ============================================================================

-- Lookup all reports for a father (used by activity feed, statistics)
CREATE INDEX idx_activity_reports_father_id
    ON activity_reports (father_id);

-- Lookup reports for a father on a specific date (used by rate limiting, daily queries)
CREATE INDEX idx_activity_reports_father_date
    ON activity_reports (father_id, activity_date);
