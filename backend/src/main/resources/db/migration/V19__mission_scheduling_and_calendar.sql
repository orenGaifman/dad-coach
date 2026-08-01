-- V19: Mission scheduling enhancements and Google Calendar integration
-- Adds support for mission rescheduling, reminders, and calendar sync

-- ============================================================================
-- MISSION TABLE ENHANCEMENTS
-- ============================================================================

-- Add scheduling-related columns to mission table
ALTER TABLE mission
    ADD COLUMN IF NOT EXISTS reschedule_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS scheduled_for TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS calendar_event_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reschedule_reason VARCHAR(100);

-- Index for finding missions due for reminders
CREATE INDEX IF NOT EXISTS idx_mission_reminder_pending 
    ON mission (father_id, status, scheduled_for, reminder_sent_at)
    WHERE status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS');

-- Index for finding overdue missions
CREATE INDEX IF NOT EXISTS idx_mission_overdue
    ON mission (expires_at, status)
    WHERE status IN ('ASSIGNED', 'ACCEPTED');

-- ============================================================================
-- FATHER GOOGLE CALENDAR INTEGRATION
-- ============================================================================

-- Add Google Calendar integration columns to father table
ALTER TABLE father
    ADD COLUMN IF NOT EXISTS google_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS google_refresh_token TEXT,
    ADD COLUMN IF NOT EXISTS google_access_token TEXT,
    ADD COLUMN IF NOT EXISTS google_token_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS google_calendar_id VARCHAR(255);

-- ============================================================================
-- MISSION REMINDER LOG
-- ============================================================================

-- Track all reminders sent for a mission
CREATE TABLE IF NOT EXISTS mission_reminder_log (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL REFERENCES mission(id) ON DELETE CASCADE,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    reminder_type VARCHAR(30) NOT NULL, -- SCHEDULED, OVERDUE, FOLLOW_UP
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP', -- WHATSAPP, PUSH, EMAIL
    response_received_at TIMESTAMP WITH TIME ZONE,
    response_type VARCHAR(20) -- ACCEPTED, RESCHEDULED, SKIPPED, NO_RESPONSE
);

CREATE INDEX IF NOT EXISTS idx_mission_reminder_log_mission 
    ON mission_reminder_log (mission_id);

CREATE INDEX IF NOT EXISTS idx_mission_reminder_log_father 
    ON mission_reminder_log (father_id, sent_at DESC);

-- ============================================================================
-- CALENDAR SYNC LOG
-- ============================================================================

-- Track calendar synchronization events
CREATE TABLE IF NOT EXISTS calendar_sync_log (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    mission_id BIGINT REFERENCES mission(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL, -- CREATE, UPDATE, DELETE
    calendar_event_id VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_calendar_sync_log_father 
    ON calendar_sync_log (father_id, synced_at DESC);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON COLUMN mission.reschedule_count IS 'Number of times this mission has been rescheduled by the father';
COMMENT ON COLUMN mission.scheduled_for IS 'When the father said they will do the mission (user-specified time)';
COMMENT ON COLUMN mission.reminder_sent_at IS 'When the first reminder was sent for this mission';
COMMENT ON COLUMN mission.last_reminded_at IS 'When the most recent reminder was sent';
COMMENT ON COLUMN mission.calendar_event_id IS 'Google Calendar event ID if synced';
COMMENT ON COLUMN mission.reschedule_reason IS 'Why the mission was rescheduled (e.g., TOO_BUSY, CHILD_UNAVAILABLE)';

COMMENT ON COLUMN father.google_calendar_enabled IS 'Whether the father has connected Google Calendar';
COMMENT ON COLUMN father.google_refresh_token IS 'Google OAuth refresh token for Calendar API';
COMMENT ON COLUMN father.google_calendar_id IS 'The specific calendar to sync events to';

COMMENT ON TABLE mission_reminder_log IS 'Audit log of all reminders sent to fathers about their missions';
COMMENT ON TABLE calendar_sync_log IS 'Audit log of Google Calendar synchronization events';
