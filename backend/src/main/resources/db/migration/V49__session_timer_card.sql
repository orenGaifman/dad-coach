-- V49 - D-049 (Unified Workflow Phase 5.3 slice D). Additive only.
--
-- The morning-of-session reminder and the session follow-up become tool-only turns on the platform (no model call):
-- Dad Coach's dc_session_timer_card builds them from data. Two facts the model used to read from the conversation are
-- kept here, written when such a message is handed over for delivery (SessionTimerCard.sent):
--   quality_time.morning_reminded_on  his local date this session was in a morning reminder (two sessions on one day
--                                     wake the morning twice; the second sends nothing)
--   quality_time.follow_up_asked_at   when he was last asked how this session went (one session can wake the
--                                     follow-up more than once; within three hours it sends nothing again)
ALTER TABLE quality_time ADD COLUMN morning_reminded_on DATE;
ALTER TABLE quality_time ADD COLUMN follow_up_asked_at TIMESTAMPTZ;
