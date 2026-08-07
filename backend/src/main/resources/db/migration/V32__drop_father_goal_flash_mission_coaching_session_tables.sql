-- V32__drop_father_goal_flash_mission_coaching_session_tables.sql
--
-- Drop tables whose corresponding JPA entities/services were deleted.
--
-- Deleted code:
--   - FatherGoal entity (domain.goal.FatherGoal)
--   - FatherGoalRepository (domain.goal.FatherGoalRepository)
--   - FatherGoalService (domain.goal.FatherGoalService)
--   - FlashMissionTemplate entity (domain.flash.FlashMissionTemplate)
--   - FlashMissionTemplateRepository (domain.flash.FlashMissionTemplateRepository)
--   - FlashMissionService (domain.flash.FlashMissionService)
--   - CoachingSession entity (domain.conversation.CoachingSession)
--   - CoachingSessionRepository (domain.conversation.CoachingSessionRepository)
--   - CoachingSessionOutcome enum (domain.conversation.CoachingSessionOutcome)
--
-- These features were part of a legacy design and are not used by the
-- current deterministic workflow engine or frontend.
--
-- IMPORTANT: This migration is irreversible. All data in dropped tables will be lost.

DROP TABLE IF EXISTS father_goal CASCADE;
DROP TABLE IF EXISTS flash_mission_template CASCADE;
DROP TABLE IF EXISTS coaching_session CASCADE;
