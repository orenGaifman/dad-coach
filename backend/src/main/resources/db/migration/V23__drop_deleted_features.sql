-- =============================================================================
-- V23 - drop the tables of features deleted from the code (DECISIONS D-001..D-003, D-009)
--
-- Platform-only Dad Coach (D-001): the local AI, memory system, missions, coaching engine, web join wizard,
-- magic links, media, delivery ledger, scheduler job log and audit tables have no code any more. Kept (used by
-- code, or kept on purpose): father, child, communication_endpoints, quality_time, weekly_goal, goal,
-- message_log, scheduled_response_delivery, platform_person_deletion, template_messages.
--
-- Idempotent (IF EXISTS everywhere): a database that never had a table is fine. CASCADE only removes
-- constraints/indexes between the dropped tables themselves - no kept table references any of them.
-- A fresh, empty database never runs this file: it starts from the V23 baseline (db/baseline/).
-- =============================================================================

-- Memory & knowledge system (SPEC-004), safety events, embeddings (D-003)
DROP TABLE IF EXISTS embedding_retry_queue CASCADE;
DROP TABLE IF EXISTS memory_versions CASCADE;
DROP TABLE IF EXISTS memory_audit_log CASCADE;
DROP TABLE IF EXISTS memories CASCADE;
DROP TABLE IF EXISTS memory CASCADE;
DROP TABLE IF EXISTS safety_event_records CASCADE;

-- Local AI (D-001): telemetry, AI profiles, message templates of the local generator, tool wishlist
DROP TABLE IF EXISTS ai_telemetry CASCADE;
DROP TABLE IF EXISTS ai_profiles CASCADE;
DROP TABLE IF EXISTS message_templates CASCADE;
DROP TABLE IF EXISTS tool_wishlist CASCADE;

-- Local workflow engine and its jobs (D-001)
DROP TABLE IF EXISTS workflow_state_transition_log CASCADE;
DROP TABLE IF EXISTS scheduler_job_log CASCADE;
DROP TABLE IF EXISTS conversation CASCADE;

-- Missions, commitments, calendar sync log (D-003)
DROP TABLE IF EXISTS mission CASCADE;
DROP TABLE IF EXISTS quality_time_commitment CASCADE;
DROP TABLE IF EXISTS calendar_sync_log CASCADE;

-- Web join wizard (D-002) and the old dashboard login (D-005)
DROP TABLE IF EXISTS activation_records CASCADE;
DROP TABLE IF EXISTS onboarding_sessions CASCADE;
DROP TABLE IF EXISTS invitation_audit_log CASCADE;
DROP TABLE IF EXISTS invitations CASCADE;
DROP TABLE IF EXISTS families CASCADE;
DROP TABLE IF EXISTS communication_preferences CASCADE;
DROP TABLE IF EXISTS language_preferences CASCADE;
DROP TABLE IF EXISTS rate_limit_entries CASCADE;
DROP TABLE IF EXISTS magic_link CASCADE;

-- Media assets, the never-written delivery ledger, the per-request API audit of the JWT era
DROP TABLE IF EXISTS media_assets CASCADE;
DROP TABLE IF EXISTS delivery_records CASCADE;
DROP TABLE IF EXISTS api_audit_log CASCADE;

-- V19's AI-decision columns on message_log (written only by the deleted local engine)
DROP INDEX IF EXISTS idx_message_log_tool_used;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS tool_used;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS tool_parameters;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS previous_state;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS new_state;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS tool_success;
ALTER TABLE IF EXISTS message_log DROP COLUMN IF EXISTS error_message;

-- pgvector is no longer needed. On a managed database the extension may belong to the provider's admin role:
-- then it is left in place (a notice, never a failed deploy).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        BEGIN
            EXECUTE 'DROP EXTENSION vector';
        EXCEPTION WHEN insufficient_privilege OR dependent_objects_still_exist OR object_in_use THEN
            RAISE NOTICE 'pgvector kept: %', SQLERRM;
        END;
    END IF;
END $$;
