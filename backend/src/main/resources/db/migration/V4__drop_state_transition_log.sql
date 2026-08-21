-- V4: Drop redundant state_transition_log table
-- The generic state_transition_log is replaced by workflow_state_transition_log
-- which provides workflow-specific audit trail with more context

DROP TABLE IF EXISTS state_transition_log;
