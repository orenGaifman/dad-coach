-- ============================================================================
-- V8.011: Seed Predefined Milestones
-- ============================================================================
-- Inserts milestones for mission count thresholds and account age markers
-- as defined in Requirement 13.7.
-- These are the base milestones available to all fathers from day one.
-- ============================================================================

INSERT INTO milestones (name, description, category, trigger_condition, icon_key, sort_order)
VALUES
    -- MISSIONS category — mission count thresholds
    ('25 Missions', 'Complete 25 missions on your fatherhood journey', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":25}', 'missions_25', 1),

    ('50 Missions', 'Complete 50 missions — halfway to triple digits', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":50}', 'missions_50', 2),

    ('100 Missions', 'Complete 100 missions — a century of dedication', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":100}', 'missions_100', 3),

    ('250 Missions', 'Complete 250 missions — a true commitment', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":250}', 'missions_250', 4),

    ('500 Missions', 'Complete 500 missions — legendary dedication', 'MISSIONS',
     '{"type":"MISSION_COUNT","threshold":500}', 'missions_500', 5),

    -- ACCOUNT_AGE category — account age markers
    ('30 Days Active', 'Active for 30 days — your first month', 'ACCOUNT_AGE',
     '{"type":"ACCOUNT_AGE_DAYS","threshold":30}', 'age_30_days', 6),

    ('90 Days Active', 'Active for 90 days — a full quarter', 'ACCOUNT_AGE',
     '{"type":"ACCOUNT_AGE_DAYS","threshold":90}', 'age_90_days', 7),

    ('180 Days Active', 'Active for 180 days — half a year of growth', 'ACCOUNT_AGE',
     '{"type":"ACCOUNT_AGE_DAYS","threshold":180}', 'age_180_days', 8),

    ('365 Days Active', 'Active for 365 days — a full year of fatherhood growth', 'ACCOUNT_AGE',
     '{"type":"ACCOUNT_AGE_DAYS","threshold":365}', 'age_365_days', 9);
