-- D-036: what the coach already said today, kept in code so the context can tell it (production review 2, 2026-10-08:
-- the same session repeated in 12 of 19 replies, the missing goal pushed in 4 replies in 7 minutes).
--   father.goal_asked_on        his local date the coach last raised a missing weekly goal
--   quality_time.mentioned_on   his local date the coach last named this session (a reminder or a reply)
-- And the number he asked for next week while this week's goal exists ("3 שעות מתחילת שבוע הבא"), so Sunday's
-- check-in starts from it instead of a promise nothing keeps.
ALTER TABLE father ADD COLUMN goal_asked_on DATE;
ALTER TABLE quality_time ADD COLUMN mentioned_on DATE;
ALTER TABLE weekly_goal ADD COLUMN next_week_target_hours INTEGER;
