-- V30__drop_unused_legacy_tables.sql
-- 
-- Drop tables that have no corresponding JPA entity (20 tables).
-- These tables were created by earlier migrations but are no longer used after
-- the codebase was simplified to use the deterministic workflow engine.
--
-- Tables with entities (37 - kept):
--   activation_records, ai_profiles, ai_telemetry, api_audit_log, calendar_sync_log,
--   child, coaching_session, communication_endpoints, communication_preferences,
--   conversation, delivery_records, families, father, father_goal, flash_mission_template,
--   goal, habit, invitation_audit_log, invitations, language_preferences, magic_link,
--   media_assets, memory, message_templates, mission, mission_reminder_log, notification,
--   onboarding_sessions, quality_time, quality_time_commitment, rate_limit_entries,
--   reflection, scheduler_job_log, state_transition_log, template_messages,
--   weekly_summary, workflow_state_transition_log
--
-- IMPORTANT: This migration is irreversible. All data in dropped tables will be lost.

-- ─── Drop legacy conversation orchestrator tables ─────────────────────────
DROP TABLE IF EXISTS side_effect_outbox CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;
DROP TABLE IF EXISTS idempotency_keys CASCADE;
DROP TABLE IF EXISTS conversation_messages CASCADE;
DROP TABLE IF EXISTS conversations CASCADE;
DROP TABLE IF EXISTS conversation_message CASCADE;

-- ─── Drop legacy workspace gamification tables ────────────────────────────
DROP TABLE IF EXISTS activity_reports CASCADE;
DROP TABLE IF EXISTS activity_feed_items CASCADE;
DROP TABLE IF EXISTS statistics_aggregates CASCADE;
DROP TABLE IF EXISTS growth_signals CASCADE;
DROP TABLE IF EXISTS father_streaks CASCADE;
DROP TABLE IF EXISTS father_belts CASCADE;
DROP TABLE IF EXISTS father_achievements CASCADE;
DROP TABLE IF EXISTS father_milestones CASCADE;
DROP TABLE IF EXISTS celebration_events CASCADE;

-- ─── Drop seed data tables (no entities) ──────────────────────────────────
DROP TABLE IF EXISTS achievements CASCADE;
DROP TABLE IF EXISTS milestones CASCADE;

-- ─── Drop unused tables ───────────────────────────────────────────────────
DROP TABLE IF EXISTS engagement_event CASCADE;
DROP TABLE IF EXISTS ai_daily_summary CASCADE;
DROP TABLE IF EXISTS prompt_versions CASCADE;
