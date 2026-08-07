-- V31__drop_unused_domain_tables.sql
--
-- Drop tables whose corresponding JPA entities/services were deleted.
-- These tables had no active usage in the deterministic workflow engine.
--
-- Deleted services:
--   - HabitService (domain.habit package)
--   - ReflectionService (domain.reflection package)
--   - WeeklySummaryService (domain.weeklysummary package)
--   - NotificationService (domain.notification package)
--
-- Also dropping scheduler-related tables since MissionReminderScheduler
-- and WeeklyEngagementScheduler were deleted.
--
-- IMPORTANT: This migration is irreversible. All data in dropped tables will be lost.

-- ─── Drop unused domain tables ────────────────────────────────────────────
DROP TABLE IF EXISTS habit CASCADE;
DROP TABLE IF EXISTS reflection CASCADE;
DROP TABLE IF EXISTS weekly_summary CASCADE;
DROP TABLE IF EXISTS notification CASCADE;

-- ─── Drop scheduler-related tables ────────────────────────────────────────
DROP TABLE IF EXISTS mission_reminder_log CASCADE;
DROP TABLE IF EXISTS scheduler_job_log CASCADE;
