-- =============================================================================
-- Migration: Add pre_qt_reminder_sent column to quality_time table
-- Version: 2
-- Date: 2025-01-XX
-- 
-- Adds tracking for the 1-hour pre-QT reminder that transitions fathers to
-- QUALITY_TIME_REMINDER state. This is separate from the morning reminder
-- (reminder_sent) which is sent at 8 AM local time.
-- =============================================================================

-- Add column for tracking pre-QT reminder (1 hour before QT starts)
ALTER TABLE quality_time 
ADD COLUMN IF NOT EXISTS pre_qt_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- Add index for efficient querying of upcoming QTs without pre-QT reminder
CREATE INDEX IF NOT EXISTS idx_quality_time_pre_qt_reminder 
ON quality_time(scheduled_start, pre_qt_reminder_sent) 
WHERE status = 'SCHEDULED' AND pre_qt_reminder_sent = false;
