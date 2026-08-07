-- =============================================================================
-- Dad Coach Consolidated Schema
-- Version: 1.0
-- Date: 2026-08-07
-- 
-- This is a clean, consolidated schema for fresh deployments.
-- Contains all 29 tables required by the application.
-- =============================================================================

-- =============================================================================
-- CORE DOMAIN TABLES
-- =============================================================================

-- Father - Core entity representing a father in the coaching system
CREATE TABLE IF NOT EXISTS father (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    onboarding_state VARCHAR(30) DEFAULT 'NOT_STARTED',
    coaching_phase VARCHAR(20) DEFAULT 'FOUNDATION',
    coaching_style VARCHAR(20) DEFAULT 'BALANCED',
    preferred_coaching_time TIME DEFAULT '08:00:00',
    timezone VARCHAR(64) DEFAULT 'Asia/Jerusalem',
    locale VARCHAR(10) DEFAULT 'he',
    engagement_score INTEGER DEFAULT 0,
    coaching_streak INTEGER DEFAULT 0,
    longest_streak INTEGER DEFAULT 0,
    activation_date DATE,
    last_interaction_at TIMESTAMP WITH TIME ZONE,
    pause_until DATE,
    metadata JSONB DEFAULT '{}',
    -- Google Calendar Integration
    google_calendar_enabled BOOLEAN DEFAULT FALSE,
    google_refresh_token VARCHAR(512),
    google_access_token VARCHAR(2048),
    google_token_expires_at TIMESTAMP WITH TIME ZONE,
    google_calendar_id VARCHAR(255),
    -- Goals and Tracking
    weekly_goal_minutes INTEGER NOT NULL DEFAULT 30,
    monthly_goal_minutes INTEGER NOT NULL DEFAULT 120,
    goals_started_at DATE,
    current_streak_weeks INTEGER NOT NULL DEFAULT 0,
    longest_streak_weeks INTEGER NOT NULL DEFAULT 0,
    total_quality_minutes INTEGER NOT NULL DEFAULT 0,
    -- Workflow State (Deterministic Workflow Engine)
    current_workflow_state VARCHAR(30) DEFAULT 'WELCOME',
    previous_workflow_state VARCHAR(30),
    workflow_state_entered_at TIMESTAMP WITH TIME ZONE,
    welcomed_at TIMESTAMP WITH TIME ZONE,
    -- Quality Time Tracking
    quality_time_streak INTEGER NOT NULL DEFAULT 0,
    quality_time_longest_streak INTEGER NOT NULL DEFAULT 0,
    total_quality_times_completed INTEGER NOT NULL DEFAULT 0,
    current_belt VARCHAR(20) NOT NULL DEFAULT 'WHITE'
);

CREATE INDEX IF NOT EXISTS idx_father_phone ON father(phone);
CREATE INDEX IF NOT EXISTS idx_father_status ON father(status);

-- Child - Children associated with fathers
CREATE TABLE IF NOT EXISTS child (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10),
    interests TEXT[],
    challenges TEXT[],
    relationship_quality INTEGER DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_child_father_id ON child(father_id);

-- =============================================================================
-- FAMILY & PREFERENCES
-- =============================================================================

-- Families - Family unit linking fathers
CREATE TABLE IF NOT EXISTS families (
    family_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    family_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_father_id ON families(father_id);

-- Communication Preferences
CREATE TABLE IF NOT EXISTS communication_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    preferred_coaching_time TIME NOT NULL DEFAULT '08:00:00',
    notification_frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    quiet_hours_start TIME NOT NULL DEFAULT '21:00:00',
    quiet_hours_end TIME NOT NULL DEFAULT '07:00:00',
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comm_prefs_father_id ON communication_preferences(father_id);

-- Language Preferences
CREATE TABLE IF NOT EXISTS language_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    language_code VARCHAR(5) NOT NULL DEFAULT 'he',
    date_format VARCHAR(20) NOT NULL DEFAULT 'dd/MM/yyyy',
    time_format VARCHAR(20) NOT NULL DEFAULT 'HH:mm',
    text_direction VARCHAR(3) NOT NULL DEFAULT 'RTL',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lang_prefs_father_id ON language_preferences(father_id);

-- =============================================================================
-- ONBOARDING & INVITATIONS
-- =============================================================================

-- Invitations
CREATE TABLE IF NOT EXISTS invitations (
    invitation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(15) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    max_uses INTEGER NOT NULL,
    current_uses INTEGER NOT NULL DEFAULT 0,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations(token);
CREATE INDEX IF NOT EXISTS idx_invitations_status ON invitations(status);

-- Invitation Audit Log
CREATE TABLE IF NOT EXISTS invitation_audit_log (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL,
    action VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invitation_audit_token_hash ON invitation_audit_log(token_hash);
CREATE INDEX IF NOT EXISTS idx_invitation_audit_created_at ON invitation_audit_log(created_at);

-- Onboarding Sessions
CREATE TABLE IF NOT EXISTS onboarding_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id UUID NOT NULL,
    father_id UUID,
    current_step VARCHAR(20) NOT NULL,
    status VARCHAR(15) NOT NULL,
    wizard_data BYTEA,
    language VARCHAR(5),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_sessions_invitation_id ON onboarding_sessions(invitation_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_sessions_father_id ON onboarding_sessions(father_id);

-- Activation Records
CREATE TABLE IF NOT EXISTS activation_records (
    activation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    session_id UUID NOT NULL,
    status VARCHAR(25) NOT NULL,
    deep_link_generated_at TIMESTAMP WITH TIME ZONE,
    link_clicked_at TIMESTAMP WITH TIME ZONE,
    message_received_at TIMESTAMP WITH TIME ZONE,
    conversation_started_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS idx_activation_records_father_id ON activation_records(father_id);
CREATE INDEX IF NOT EXISTS idx_activation_records_session_id ON activation_records(session_id);
CREATE INDEX IF NOT EXISTS idx_activation_records_status ON activation_records(status);

-- Rate Limit Entries
CREATE TABLE IF NOT EXISTS rate_limit_entries (
    entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type VARCHAR(10) NOT NULL,
    key_value VARCHAR(255) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(key_type, key_value, window_start)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_key_value ON rate_limit_entries(key_value);

-- =============================================================================
-- AI & PROFILES
-- =============================================================================

-- AI Profiles
CREATE TABLE IF NOT EXISTS ai_profiles (
    profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL UNIQUE,
    coaching_style VARCHAR(30) NOT NULL,
    language VARCHAR(5) NOT NULL,
    children_context TEXT,
    goals_context TEXT,
    personality_brief TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_profiles_father_id ON ai_profiles(father_id);

-- AI Telemetry
CREATE TABLE IF NOT EXISTS ai_telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    father_id UUID NOT NULL,
    conversation_id UUID,
    conversation_type VARCHAR(30),
    interaction_type VARCHAR(30) NOT NULL,
    prompt_version VARCHAR(20),
    model_provider VARCHAR(20) NOT NULL,
    model_name VARCHAR(50) NOT NULL,
    temperature REAL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    estimated_cost_usd REAL,
    total_latency_ms INTEGER NOT NULL,
    llm_latency_ms INTEGER,
    validation_passed BOOLEAN NOT NULL DEFAULT TRUE,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    quality_score REAL,
    safety_classification VARCHAR(30),
    ab_test_group VARCHAR(5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_telemetry_father_id ON ai_telemetry(father_id);
CREATE INDEX IF NOT EXISTS idx_ai_telemetry_created_at ON ai_telemetry(created_at);

-- =============================================================================
-- COMMUNICATION & DELIVERY
-- =============================================================================

-- Communication Endpoints
CREATE TABLE IF NOT EXISTS communication_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    channel_identity VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    session_opens_at TIMESTAMP WITH TIME ZONE,
    session_closes_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comm_endpoints_father_id ON communication_endpoints(father_id);
CREATE INDEX IF NOT EXISTS idx_comm_endpoints_channel_identity ON communication_endpoints(channel_identity);
CREATE UNIQUE INDEX IF NOT EXISTS idx_comm_endpoints_unique ON communication_endpoints(channel, channel_identity);

-- Delivery Records
CREATE TABLE IF NOT EXISTS delivery_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    father_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    failure_reason VARCHAR(100),
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_delivery_records_message_id ON delivery_records(message_id);
CREATE INDEX IF NOT EXISTS idx_delivery_records_father_id ON delivery_records(father_id);
CREATE INDEX IF NOT EXISTS idx_delivery_records_status ON delivery_records(status);

-- Template Messages
CREATE TABLE IF NOT EXISTS template_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_name VARCHAR(100) NOT NULL UNIQUE,
    language VARCHAR(10) NOT NULL,
    category VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_variables INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_template_messages_template_name ON template_messages(template_name);

-- Media Assets
CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    message_id UUID NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    downloaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_assets_father_id ON media_assets(father_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_message_id ON media_assets(message_id);

-- =============================================================================
-- CONVERSATIONS & MEMORY
-- =============================================================================

-- Conversation
CREATE TABLE IF NOT EXISTS conversation (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL DEFAULT 'COACHING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    objective TEXT,
    summary TEXT,
    message_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_conversation_father_id ON conversation(father_id);
CREATE INDEX IF NOT EXISTS idx_conversation_status ON conversation(status);

-- Memory
CREATE TABLE IF NOT EXISTS memory (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    category VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    importance_score INTEGER NOT NULL DEFAULT 5,
    confidence_score NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    access_count INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    superseded_by BIGINT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_father_id ON memory(father_id);
CREATE INDEX IF NOT EXISTS idx_memory_category ON memory(category);

-- =============================================================================
-- GOALS & MISSIONS
-- =============================================================================

-- Goal
CREATE TABLE IF NOT EXISTS goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    progress_percentage INTEGER NOT NULL DEFAULT 0,
    estimated_total_missions INTEGER NOT NULL DEFAULT 0,
    completed_related_missions INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_goal_father_id ON goal(father_id);
CREATE INDEX IF NOT EXISTS idx_goal_status ON goal(status);

-- Mission
CREATE TABLE IF NOT EXISTS mission (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    goal_id BIGINT REFERENCES goal(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    difficulty INTEGER NOT NULL DEFAULT 1,
    estimated_minutes INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    outcome_rating INTEGER,
    outcome_notes TEXT,
    prompt_version VARCHAR(50),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    reschedule_count INTEGER NOT NULL DEFAULT 0,
    scheduled_for TIMESTAMP WITH TIME ZONE,
    reminder_sent_at TIMESTAMP WITH TIME ZONE,
    last_reminded_at TIMESTAMP WITH TIME ZONE,
    calendar_event_id VARCHAR(255),
    reschedule_reason VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_mission_father_id ON mission(father_id);
CREATE INDEX IF NOT EXISTS idx_mission_child_id ON mission(child_id);
CREATE INDEX IF NOT EXISTS idx_mission_status ON mission(status);

-- =============================================================================
-- QUALITY TIME & COMMITMENTS
-- =============================================================================

-- Quality Time
CREATE TABLE IF NOT EXISTS quality_time (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT NOT NULL REFERENCES child(id) ON DELETE CASCADE,
    google_calendar_event_id VARCHAR(255),
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completion_notes TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_sent BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_quality_time_father_id ON quality_time(father_id);
CREATE INDEX IF NOT EXISTS idx_quality_time_child_id ON quality_time(child_id);
CREATE INDEX IF NOT EXISTS idx_quality_time_status ON quality_time(status);
CREATE INDEX IF NOT EXISTS idx_quality_time_scheduled_start ON quality_time(scheduled_start);

-- Quality Time Commitment
CREATE TABLE IF NOT EXISTS quality_time_commitment (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER,
    activity_type VARCHAR(50),
    activity_note VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    reminder_sent_at TIMESTAMP WITH TIME ZONE,
    reminder_message_id VARCHAR(100),
    completed_at TIMESTAMP WITH TIME ZONE,
    completion_note VARCHAR(500),
    points_awarded INTEGER,
    created_via VARCHAR(30),
    conversation_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_qtc_father_id ON quality_time_commitment(father_id);
CREATE INDEX IF NOT EXISTS idx_qtc_scheduled_date ON quality_time_commitment(scheduled_date);

-- =============================================================================
-- CALENDAR & SYNC
-- =============================================================================

-- Calendar Sync Log
CREATE TABLE IF NOT EXISTS calendar_sync_log (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    mission_id BIGINT,
    action VARCHAR(30) NOT NULL,
    calendar_event_id VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_calendar_sync_father_id ON calendar_sync_log(father_id);

-- =============================================================================
-- WORKFLOW & STATE MANAGEMENT
-- =============================================================================

-- Message Templates (for workflow engine)
CREATE TABLE IF NOT EXISTS message_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type VARCHAR(50) NOT NULL UNIQUE,
    template_text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_templates_type ON message_templates(message_type);

-- Workflow State Transition Log
CREATE TABLE IF NOT EXISTS workflow_state_transition_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL,
    from_state VARCHAR(30) NOT NULL,
    to_state VARCHAR(30) NOT NULL,
    trigger_reason VARCHAR(50) NOT NULL,
    trigger_message_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wstl_father_id ON workflow_state_transition_log(father_id);
CREATE INDEX IF NOT EXISTS idx_wstl_created_at ON workflow_state_transition_log(created_at);

-- State Transition Log (for state machine)
CREATE TABLE IF NOT EXISTS state_transition_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    from_state VARCHAR(30) NOT NULL,
    to_state VARCHAR(30) NOT NULL,
    trigger_reason VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stl_entity ON state_transition_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_stl_created_at ON state_transition_log(created_at);

-- Magic Link (for passwordless authentication)
CREATE TABLE IF NOT EXISTS magic_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(32) NOT NULL UNIQUE,
    father_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    redirect_path VARCHAR(255),
    context VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_magic_link_token ON magic_link(token);
CREATE INDEX IF NOT EXISTS idx_magic_link_father_id ON magic_link(father_id);
CREATE INDEX IF NOT EXISTS idx_magic_link_expires_at ON magic_link(expires_at);

-- =============================================================================
-- AUDIT & LOGGING
-- =============================================================================

-- API Audit Log
CREATE TABLE IF NOT EXISTS api_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL,
    operation VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID,
    result VARCHAR(20) NOT NULL,
    error_code VARCHAR(50),
    changes JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_audit_created_at ON api_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_api_audit_actor_id ON api_audit_log(actor_id);

-- Scheduler Job Log
CREATE TABLE IF NOT EXISTS scheduler_job_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    records_processed INTEGER NOT NULL DEFAULT 0,
    errors_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scheduler_job_name ON scheduler_job_log(job_name);
CREATE INDEX IF NOT EXISTS idx_scheduler_job_started_at ON scheduler_job_log(started_at);
