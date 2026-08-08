-- V2__add_weekly_goal_table.sql
-- Add weekly goal tracking table for the weekly goal feature

CREATE TABLE weekly_goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    week_start_date DATE NOT NULL,
    target_hours INTEGER NOT NULL CHECK (target_hours >= 1),
    actual_minutes INTEGER NOT NULL DEFAULT 0 CHECK (actual_minutes >= 0),
    scheduled_count INTEGER NOT NULL DEFAULT 0 CHECK (scheduled_count >= 0),
    completed_count INTEGER NOT NULL DEFAULT 0 CHECK (completed_count >= 0),
    starting_belt VARCHAR(20) NOT NULL,
    ending_belt VARCHAR(20),
    belt_promoted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    
    CONSTRAINT uk_weekly_goal_father_week UNIQUE (father_id, week_start_date),
    CONSTRAINT ck_weekly_goal_status CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT ck_weekly_goal_belt CHECK (starting_belt IN ('WHITE', 'YELLOW', 'ORANGE', 'GREEN', 'BLUE', 'BROWN', 'BLACK')),
    CONSTRAINT ck_weekly_goal_ending_belt CHECK (ending_belt IS NULL OR ending_belt IN ('WHITE', 'YELLOW', 'ORANGE', 'GREEN', 'BLUE', 'BROWN', 'BLACK'))
);

-- Index for finding active goals by father
CREATE INDEX idx_weekly_goal_father_status ON weekly_goal(father_id, status);

-- Index for weekly scheduler to find goals to process
CREATE INDEX idx_weekly_goal_status ON weekly_goal(status);

-- Index for finding goals by week (for scheduler)
CREATE INDEX idx_weekly_goal_week ON weekly_goal(week_start_date, status);

COMMENT ON TABLE weekly_goal IS 'Weekly quality time goals set by fathers. Meeting the goal results in belt promotion.';
COMMENT ON COLUMN weekly_goal.week_start_date IS 'The Sunday that starts this goal week';
COMMENT ON COLUMN weekly_goal.target_hours IS 'Target quality time hours for the week (minimum 1)';
COMMENT ON COLUMN weekly_goal.actual_minutes IS 'Total quality time minutes completed this week';
COMMENT ON COLUMN weekly_goal.starting_belt IS 'Belt level at the start of the week';
COMMENT ON COLUMN weekly_goal.ending_belt IS 'Belt level at the end of the week (after potential promotion)';
COMMENT ON COLUMN weekly_goal.belt_promoted IS 'Whether the father was promoted to the next belt this week';
