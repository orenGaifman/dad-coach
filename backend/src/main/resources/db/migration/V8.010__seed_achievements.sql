-- ============================================================================
-- V8.010: Seed Predefined Achievements
-- ============================================================================
-- Inserts the 15 predefined achievements defined in Requirement 13.3.
-- These are the base achievements available to all fathers from day one.
-- ============================================================================

INSERT INTO achievements (name, description, category, criteria_json, icon_key, sort_order)
VALUES
    -- MISSIONS category
    ('First Steps', 'Complete your first mission', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":1}', 'first_steps', 1),

    ('Mission Master 10', 'Complete 10 missions', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":10}', 'mission_master_10', 2),

    ('Mission Master 50', 'Complete 50 missions', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":50}', 'mission_master_50', 3),

    ('Mission Master 100', 'Complete 100 missions', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":100}', 'mission_master_100', 4),

    -- CONSISTENCY category
    ('Week Warrior', 'Maintain a 7-day engagement streak', 'CONSISTENCY',
     '{"type":"STREAK_DAYS","threshold":7}', 'week_warrior', 5),

    ('Month Champion', 'Maintain a 30-day engagement streak', 'CONSISTENCY',
     '{"type":"STREAK_DAYS","threshold":30}', 'month_champion', 6),

    ('Quarter Legend', 'Maintain a 90-day engagement streak', 'CONSISTENCY',
     '{"type":"STREAK_DAYS","threshold":90}', 'quarter_legend', 7),

    -- GOALS category
    ('Goal Getter', 'Complete your first goal', 'GOALS',
     '{"type":"GOAL_COUNT","threshold":1}', 'goal_getter', 8),

    ('Goal Crusher', 'Complete 5 goals', 'GOALS',
     '{"type":"GOAL_COUNT","threshold":5}', 'goal_crusher', 9),

    -- CONVERSATIONS category
    ('Deep Talker', 'Have 10 meaningful conversations', 'CONVERSATIONS',
     '{"type":"CONVERSATION_COUNT","threshold":10}', 'deep_talker', 10),

    ('Connection King', 'Have 50 meaningful conversations', 'CONVERSATIONS',
     '{"type":"CONVERSATION_COUNT","threshold":50}', 'connection_king', 11),

    -- GROWTH category
    ('Rising Star', 'Reach Yellow Belt', 'GROWTH',
     '{"type":"BELT_REACHED","belt":"YELLOW"}', 'rising_star', 12),

    ('Green Machine', 'Reach Green Belt', 'GROWTH',
     '{"type":"BELT_REACHED","belt":"GREEN"}', 'green_machine', 13),

    ('Elite Father', 'Reach Purple Belt', 'GROWTH',
     '{"type":"BELT_REACHED","belt":"PURPLE"}', 'elite_father', 14),

    ('Grandmaster', 'Reach Black Belt', 'GROWTH',
     '{"type":"BELT_REACHED","belt":"BLACK"}', 'grandmaster', 15);
