-- V18: Add welcome_step column to track onboarding progress within WELCOME state
-- This enables step-by-step welcome flow:
-- INTRO -> CONNECT_CALENDAR -> SET_WEEKLY_GOAL -> SCHEDULE_FIRST_QUALITY_TIME -> DASHBOARD_TOUR -> COMPLETED

ALTER TABLE father ADD COLUMN IF NOT EXISTS welcome_step VARCHAR(40) DEFAULT 'INTRO';

-- Add comment for documentation
COMMENT ON COLUMN father.welcome_step IS 'Current step in the welcome onboarding flow: INTRO, CONNECT_CALENDAR, SET_WEEKLY_GOAL, SCHEDULE_FIRST_QUALITY_TIME, DASHBOARD_TOUR, COMPLETED';

-- Create index for efficient querying by welcome step (e.g., for analytics)
CREATE INDEX IF NOT EXISTS idx_father_welcome_step ON father(welcome_step);
