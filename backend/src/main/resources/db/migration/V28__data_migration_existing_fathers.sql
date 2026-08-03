-- V25: Data Migration for Existing Fathers
-- Migrates existing data to the new deterministic workflow engine model
-- Requirements: 15.1 (preserve data), 15.2 (migration steps), 15.3 (idempotent migration)

-- ============================================================================
-- STEP 1: Migrate completed missions to quality_time records
-- Only migrate missions with status = 'COMPLETED' as these represent actual Quality Time
-- ============================================================================

-- First, ensure we only insert records that don't already exist (idempotency)
INSERT INTO quality_time (
    id,
    father_id,
    child_id,
    google_calendar_event_id,
    scheduled_start,
    scheduled_end,
    status,
    completion_notes,
    completed_at,
    created_at,
    updated_at,
    reminder_sent,
    follow_up_sent
)
SELECT 
    gen_random_uuid() as id,
    m.father_id,
    m.child_id,
    m.calendar_event_id as google_calendar_event_id,
    -- Use scheduled_for if available, otherwise use assigned_at as the scheduled start
    COALESCE(m.scheduled_for, m.assigned_at) as scheduled_start,
    -- Scheduled end is start + estimated_minutes (default to 30 if not set)
    COALESCE(m.scheduled_for, m.assigned_at) + (COALESCE(m.estimated_minutes, 30) || ' minutes')::INTERVAL as scheduled_end,
    'COMPLETED' as status,
    m.outcome_notes as completion_notes,
    m.completed_at,
    m.assigned_at as created_at,
    m.completed_at as updated_at,
    TRUE as reminder_sent,  -- Mark as sent since these are historical
    TRUE as follow_up_sent  -- Mark as sent since these are historical
FROM mission m
WHERE m.status = 'COMPLETED'
  AND m.completed_at IS NOT NULL
  -- Idempotency: Don't insert if we've already migrated this mission
  -- (check by looking for quality_time with same father_id, child_id, and completed_at within 1 second)
  AND NOT EXISTS (
      SELECT 1 FROM quality_time qt 
      WHERE qt.father_id = m.father_id 
        AND qt.child_id = m.child_id 
        AND qt.completed_at IS NOT NULL
        AND ABS(EXTRACT(EPOCH FROM (qt.completed_at - m.completed_at))) < 1
  );

-- ============================================================================
-- STEP 2: Also migrate quality_time_commitment records that were COMPLETED
-- These are more recent than missions and may have different data
-- ============================================================================

INSERT INTO quality_time (
    id,
    father_id,
    child_id,
    google_calendar_event_id,
    scheduled_start,
    scheduled_end,
    status,
    completion_notes,
    completed_at,
    created_at,
    updated_at,
    reminder_sent,
    follow_up_sent
)
SELECT 
    gen_random_uuid() as id,
    qtc.father_id,
    COALESCE(qtc.child_id, (
        -- If no child_id specified, use the first child of this father
        SELECT c.id FROM child c WHERE c.father_id = qtc.father_id ORDER BY c.created_at LIMIT 1
    )) as child_id,
    NULL as google_calendar_event_id,  -- quality_time_commitment didn't have calendar sync
    qtc.scheduled_at as scheduled_start,
    qtc.scheduled_at + (COALESCE(qtc.duration_minutes, 30) || ' minutes')::INTERVAL as scheduled_end,
    CASE 
        WHEN qtc.status = 'COMPLETED' THEN 'COMPLETED'
        WHEN qtc.status = 'MISSED' THEN 'MISSED'
        WHEN qtc.status = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'COMPLETED'
    END as status,
    qtc.completion_note as completion_notes,
    qtc.completed_at,
    qtc.created_at,
    COALESCE(qtc.completed_at, qtc.updated_at) as updated_at,
    TRUE as reminder_sent,
    TRUE as follow_up_sent
FROM quality_time_commitment qtc
WHERE qtc.status IN ('COMPLETED', 'MISSED', 'CANCELLED')
  -- Idempotency: Don't insert if already migrated
  AND NOT EXISTS (
      SELECT 1 FROM quality_time qt 
      WHERE qt.father_id = qtc.father_id 
        AND COALESCE(qt.child_id, 0) = COALESCE(qtc.child_id, (
            SELECT c.id FROM child c WHERE c.father_id = qtc.father_id ORDER BY c.created_at LIMIT 1
        ), 0)
        AND qt.completed_at IS NOT NULL
        AND ABS(EXTRACT(EPOCH FROM (qt.scheduled_start - qtc.scheduled_at))) < 60
  )
  -- Only migrate if child_id exists or father has at least one child
  AND (qtc.child_id IS NOT NULL OR EXISTS (
      SELECT 1 FROM child c WHERE c.father_id = qtc.father_id
  ));

-- ============================================================================
-- STEP 3: Calculate and update belt progression for all fathers
-- Belt thresholds from design:
--   White Belt: 0-2 Quality Times
--   Yellow Belt: 3-9 Quality Times  
--   Orange Belt: 10-24 Quality Times
--   Green Belt: 25-49 Quality Times
--   Blue Belt: 50-99 Quality Times
--   Brown Belt: 100-199 Quality Times
--   Black Belt: 200+ Quality Times
-- ============================================================================

-- Update total_quality_times_completed from quality_time records
UPDATE father f
SET total_quality_times_completed = (
    SELECT COUNT(*) 
    FROM quality_time qt 
    WHERE qt.father_id = f.id AND qt.status = 'COMPLETED'
);

-- Calculate and update current_belt based on completion count
UPDATE father f
SET current_belt = CASE
    WHEN f.total_quality_times_completed >= 200 THEN 'BLACK'
    WHEN f.total_quality_times_completed >= 100 THEN 'BROWN'
    WHEN f.total_quality_times_completed >= 50 THEN 'BLUE'
    WHEN f.total_quality_times_completed >= 25 THEN 'GREEN'
    WHEN f.total_quality_times_completed >= 10 THEN 'ORANGE'
    WHEN f.total_quality_times_completed >= 3 THEN 'YELLOW'
    ELSE 'WHITE'
END;

-- ============================================================================
-- STEP 4: Set initial workflow state for existing fathers
-- Per Requirement 15.2: Set to SCHEDULE_QUALITY_TIME (not WELCOME, since they're not new)
-- Only update fathers who haven't been initialized yet
-- ============================================================================

-- Set workflow state to SCHEDULE_QUALITY_TIME for existing active fathers
-- This indicates they should schedule their next Quality Time
UPDATE father f
SET 
    current_workflow_state = 'SCHEDULE_QUALITY_TIME',
    workflow_state_entered_at = NOW(),
    -- Mark them as already welcomed since they've been using the app
    welcomed_at = COALESCE(f.welcomed_at, f.created_at)
WHERE f.status = 'ACTIVE'
  -- Only update if they haven't already been set (idempotency)
  AND (f.current_workflow_state = 'WELCOME' OR f.current_workflow_state IS NULL);

-- ============================================================================
-- STEP 5: Preserve/migrate streak data from existing sources
-- Set initial streak to 0 for fresh start, but preserve longest_streak if available
-- ============================================================================

-- Try to preserve longest streak from old father_goal data or existing streak tracking
UPDATE father f
SET 
    -- Reset current streak to 0 for fresh start (per Requirement 15.2)
    quality_time_streak = 0,
    -- Preserve longest streak from existing data if available
    quality_time_longest_streak = GREATEST(
        COALESCE(f.quality_time_longest_streak, 0),
        COALESCE(f.longest_streak_weeks, 0),
        COALESCE(f.current_streak_weeks, 0)
    )
WHERE f.status = 'ACTIVE'
  -- Only update if not already set (values are 0 from column defaults)
  AND f.quality_time_longest_streak = 0;

-- ============================================================================
-- STEP 6: Create a log entry for this migration
-- Helps with debugging and verifies migration ran
-- Note: workflow_state_transition_log uses BIGINT father_id referencing father(id)
-- ============================================================================

-- Insert a workflow transition log entry to mark migration completion
INSERT INTO workflow_state_transition_log (
    id,
    father_id,
    from_state,
    to_state,
    trigger_reason,
    trigger_message_id,
    created_at
)
SELECT 
    gen_random_uuid(),
    f.id,  -- Use id (BIGINT) for FK reference
    'WELCOME',
    'SCHEDULE_QUALITY_TIME',
    'DATA_MIGRATION_V28',
    NULL,
    NOW()
FROM father f
WHERE f.status = 'ACTIVE'
  AND f.welcomed_at IS NOT NULL
  -- Only create log if we don't already have a migration log entry for this father
  AND NOT EXISTS (
      SELECT 1 FROM workflow_state_transition_log wstl 
      WHERE wstl.father_id = f.id 
        AND wstl.trigger_reason = 'DATA_MIGRATION_V28'
  );

-- ============================================================================
-- COMMENTS AND VERIFICATION
-- ============================================================================

-- Add a comment to help with debugging
COMMENT ON TABLE quality_time IS 
    'Quality Time events for the deterministic workflow engine. Includes records migrated from legacy mission and quality_time_commitment tables via V28.';

-- Note: This migration is idempotent - it can be run multiple times safely.
-- Each INSERT/UPDATE has guards to prevent duplicate processing.
-- 
-- To verify migration success, run:
--   SELECT current_belt, current_workflow_state, total_quality_times_completed, COUNT(*) 
--   FROM father WHERE status = 'ACTIVE' GROUP BY 1, 2, 3 ORDER BY 3 DESC;
--
--   SELECT COUNT(*) as migrated_quality_times FROM quality_time;
--
--   SELECT trigger_reason, COUNT(*) FROM workflow_state_transition_log 
--   WHERE trigger_reason = 'DATA_MIGRATION_V28' GROUP BY 1;
