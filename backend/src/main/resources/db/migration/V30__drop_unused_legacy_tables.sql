-- V30__drop_unused_legacy_tables.sql
-- 
-- Drop tables from legacy conversation orchestrator and workspace gamification features.
-- These tables were created by migrations V8.* and V8 but are no longer used after
-- the codebase was simplified to use the deterministic workflow engine.
--
-- IMPORTANT: This migration is irreversible. All data in these tables will be lost.
-- Run a backup before applying this migration in production.

-- ─── Drop legacy conversation tables ──────────────────────────────────────
-- These were part of the original conversation orchestrator that was replaced
-- by the workflow engine.

DROP TABLE IF EXISTS side_effect_outbox CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;
DROP TABLE IF EXISTS idempotency_keys CASCADE;

-- Note: 'conversation_messages' and 'conversations' tables are dropped,
-- but 'conversation' table is kept (used by domain.conversation.Conversation entity)
DROP TABLE IF EXISTS conversation_messages CASCADE;
DROP TABLE IF EXISTS conversations CASCADE;

-- ─── Drop legacy workspace gamification tables ────────────────────────────
-- These were part of the workspace gamification feature that was removed.
-- Belt and streak tracking is now done directly on the father entity.

-- Activity reporting
DROP TABLE IF EXISTS activity_reports CASCADE;

-- Activity feed
DROP TABLE IF EXISTS activity_feed_items CASCADE;

-- Statistics aggregation
DROP TABLE IF EXISTS statistics_aggregates CASCADE;

-- Growth signals pipeline
DROP TABLE IF EXISTS growth_signals CASCADE;

-- Father progression tables (now tracked in father table directly)
DROP TABLE IF EXISTS father_streaks CASCADE;
DROP TABLE IF EXISTS father_belts CASCADE;
DROP TABLE IF EXISTS father_achievements CASCADE;
DROP TABLE IF EXISTS father_milestones CASCADE;

-- Celebration events
DROP TABLE IF EXISTS celebration_events CASCADE;

-- Seed data tables (achievements and milestones definitions)
-- Keep these for potential future use
-- DROP TABLE IF EXISTS achievements CASCADE;
-- DROP TABLE IF EXISTS milestones CASCADE;

-- ─── Drop unused communication tables ─────────────────────────────────────
DROP TABLE IF EXISTS delivery_records CASCADE;
DROP TABLE IF EXISTS communication_endpoints CASCADE;
DROP TABLE IF EXISTS engagement_event CASCADE;

-- ─── Drop unused prompt versioning ────────────────────────────────────────
DROP TABLE IF EXISTS prompt_versions CASCADE;

-- ─── Verify remaining tables ──────────────────────────────────────────────
-- The following tables should remain:
-- - father (core entity with belt/streak columns)
-- - child (core entity)
-- - conversation (domain.conversation entity, different from legacy)
-- - coaching_session
-- - goal, father_goal
-- - mission, flash_mission_template
-- - quality_time, quality_time_commitment
-- - memory
-- - habit, reflection, weekly_summary
-- - invitations, invitation_audit_log
-- - onboarding_sessions, activation_records
-- - magic_link
-- - communication_preferences, language_preferences
-- - notification
-- - rate_limit_entries
-- - message_templates, template_messages
-- - ai_profiles, ai_telemetry, ai_daily_summary
-- - api_audit_log, state_transition_log
-- - scheduler_job_log, workflow_state_transition_log
-- - calendar_sync_log, mission_reminder_log
-- - families, media_assets
-- - achievements, milestones (seed data - kept for potential future use)
