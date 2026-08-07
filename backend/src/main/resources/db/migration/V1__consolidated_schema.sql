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
    name VARCHAR(100) NOT NULL,
    birth_date DATE,
    gender VARCHAR(10),
    interests TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_child_father_id ON child(father_id);

-- =============================================================================
-- FAMILY & PREFERENCES
-- =============================================================================

-- Families - Family unit linking fathers
CREATE TABLE IF NOT EXISTS families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_created_at ON families(created_at);

-- Communication Preferences
CREATE TABLE IF NOT EXISTS communication_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    preferred_channel VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    preferred_time TIME,
    timezone VARCHAR(64) DEFAULT 'Asia/Jerusalem',
    do_not_disturb_start TIME,
    do_not_disturb_end TIME,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comm_prefs_father_id ON communication_preferences(father_id);

-- Language Preferences
CREATE TABLE IF NOT EXISTS language_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'he',
    rtl_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lang_prefs_father_id ON language_preferences(father_id);

-- =============================================================================
-- ONBOARDING & INVITATIONS
-- =============================================================================

-- Invitations
CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    phone VARCHAR(32) NOT NULL,
    inviter_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    father_id BIGINT REFERENCES father(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations(token);
CREATE INDEX IF NOT EXISTS idx_invitations_phone ON invitations(phone);
CREATE INDEX IF NOT EXISTS idx_invitations_status ON invitations(status);

-- Invitation Audit Log
CREATE TABLE IF NOT EXISTS invitation_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    result VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invitation_audit_invitation_id ON invitation_audit_log(invitation_id);
CREATE INDEX IF NOT EXISTS idx_invitation_audit_created_at ON invitation_audit_log(created_at);

-- Onboarding Sessions
CREATE TABLE IF NOT EXISTS onboarding_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id UUID NOT NULL,
    father_id UUID,
    current_step VARCHAR(30) NOT NULL DEFAULT 'WELCOME',
    wizard_data JSONB DEFAULT '{}',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_sessions_invitation_id ON onboarding_sessions(invitation_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_sessions_father_id ON onboarding_sessions(father_id);

-- Activation Records
CREATE TABLE IF NOT EXISTS activation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    session_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    deep_link_generated_at TIMESTAMP WITH TIME ZONE,
    link_clicked_at TIMESTAMP WITH TIME ZONE,
    message_received_at TIMESTAMP WITH TIME ZONE,
    conversation_started_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_activation_records_father_id ON activation_records(father_id);
CREATE INDEX IF NOT EXISTS idx_activation_records_session_id ON activation_records(session_id);
CREATE INDEX IF NOT EXISTS idx_activation_records_status ON activation_records(status);

-- Rate Limit Entries
CREATE TABLE IF NOT EXISTS rate_limit_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 1,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    blocked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(key)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_key ON rate_limit_entries(key);

-- =============================================================================
-- AI & PROFILES
-- =============================================================================

-- AI Profiles
CREATE TABLE IF NOT EXISTS ai_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID NOT NULL,
    profile_type VARCHAR(50) NOT NULL,
    profile_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_profiles_father_id ON ai_profiles(father_id);
CREATE INDEX IF NOT EXISTS idx_ai_profiles_type ON ai_profiles(profile_type);

-- AI Telemetry
CREATE TABLE IF NOT EXISTS ai_telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    request_type VARCHAR(50),
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    metadata JSONB DEFAULT '{}',
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
    channel_type VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    channel_identity VARCHAR(100) NOT NULL,
    is_primary BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comm_endpoints_father_id ON communication_endpoints(father_id);
CREATE INDEX IF NOT EXISTS idx_comm_endpoints_channel_identity ON communication_endpoints(channel_identity);
CREATE UNIQUE INDEX IF NOT EXISTS idx_comm_endpoints_unique ON communication_endpoints(channel_type, channel_identity);

-- Delivery Records
CREATE TABLE IF NOT EXISTS delivery_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    endpoint_id UUID REFERENCES communication_endpoints(id),
    channel_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    external_message_id VARCHAR(255),
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_delivery_records_message_id ON delivery_records(message_id);
CREATE INDEX IF NOT EXISTS idx_delivery_records_endpoint_id ON delivery_records(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_delivery_records_status ON delivery_records(status);

-- Template Messages
CREATE TABLE IF NOT EXISTS template_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL UNIQUE,
    channel_type VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    locale VARCHAR(10) NOT NULL DEFAULT 'he',
    template_name VARCHAR(100) NOT NULL,
    template_body TEXT NOT NULL,
    variables JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_template_messages_key ON template_messages(template_key);

-- Media Assets
CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id UUID,
    asset_type VARCHAR(30) NOT NULL,
    mime_type VARCHAR(100),
    file_name VARCHAR(255),
    file_size BIGINT,
    storage_path TEXT NOT NULL,
    external_id VARCHAR(255),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_media_assets_father_id ON media_assets(father_id);

-- =============================================================================
-- CONVERSATIONS & MEMORY
-- =============================================================================

-- Conversation
CREATE TABLE IF NOT EXISTS conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    conversation_type VARCHAR(30) NOT NULL DEFAULT 'COACHING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE,
    message_count INTEGER DEFAULT 0,
    last_message_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_conversation_father_id ON conversation(father_id);
CREATE INDEX IF NOT EXISTS idx_conversation_status ON conversation(status);

-- Memory
CREATE TABLE IF NOT EXISTS memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    memory_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    importance INTEGER DEFAULT 5,
    tags TEXT[],
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_memory_father_id ON memory(father_id);
CREATE INDEX IF NOT EXISTS idx_memory_type ON memory(memory_type);

-- =============================================================================
-- GOALS & MISSIONS
-- =============================================================================

-- Goal
CREATE TABLE IF NOT EXISTS goal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    target_date DATE,
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_goal_father_id ON goal(father_id);
CREATE INDEX IF NOT EXISTS idx_goal_status ON goal(status);

-- Mission
CREATE TABLE IF NOT EXISTS mission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    goal_id UUID REFERENCES goal(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    mission_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_for TIMESTAMP WITH TIME ZONE,
    duration_minutes INTEGER,
    completed_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
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
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    google_calendar_event_id VARCHAR(255),
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_quality_time_father_id ON quality_time(father_id);
CREATE INDEX IF NOT EXISTS idx_quality_time_child_id ON quality_time(child_id);
CREATE INDEX IF NOT EXISTS idx_quality_time_status ON quality_time(status);
CREATE INDEX IF NOT EXISTS idx_quality_time_scheduled_start ON quality_time(scheduled_start);

-- Quality Time Commitment
CREATE TABLE IF NOT EXISTS quality_time_commitment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    child_id BIGINT REFERENCES child(id),
    day_of_week INTEGER NOT NULL,
    start_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    activity_type VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_qtc_father_id ON quality_time_commitment(father_id);
CREATE INDEX IF NOT EXISTS idx_qtc_day_of_week ON quality_time_commitment(day_of_week);

-- =============================================================================
-- CALENDAR & SYNC
-- =============================================================================

-- Calendar Sync Log
CREATE TABLE IF NOT EXISTS calendar_sync_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    sync_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    events_synced INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_calendar_sync_father_id ON calendar_sync_log(father_id);

-- =============================================================================
-- WORKFLOW & STATE MANAGEMENT
-- =============================================================================

-- Message Templates (for workflow engine)
CREATE TABLE IF NOT EXISTS message_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type VARCHAR(50) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'he',
    template_text TEXT NOT NULL,
    variables TEXT[],
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(message_type, locale)
);

CREATE INDEX IF NOT EXISTS idx_message_templates_type ON message_templates(message_type);

-- Workflow State Transition Log
CREATE TABLE IF NOT EXISTS workflow_state_transition_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id BIGINT NOT NULL,
    from_state VARCHAR(30),
    to_state VARCHAR(30) NOT NULL,
    trigger_reason VARCHAR(50) NOT NULL,
    trigger_message_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wstl_father_id ON workflow_state_transition_log(father_id);
CREATE INDEX IF NOT EXISTS idx_wstl_created_at ON workflow_state_transition_log(created_at);

-- State Transition Log (legacy - for state machine)
CREATE TABLE IF NOT EXISTS state_transition_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    from_state VARCHAR(50),
    to_state VARCHAR(50) NOT NULL,
    trigger_event VARCHAR(100),
    actor_id VARCHAR(100),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stl_entity ON state_transition_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_stl_created_at ON state_transition_log(created_at);

-- Magic Link (for passwordless authentication)
CREATE TABLE IF NOT EXISTS magic_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    father_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    redirect_path VARCHAR(255),
    context VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
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
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    actor_type VARCHAR(30),
    actor_id VARCHAR(100),
    request_body TEXT,
    response_status INTEGER,
    response_time_ms BIGINT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_audit_created_at ON api_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_api_audit_endpoint ON api_audit_log(endpoint);

-- Scheduler Job Log
CREATE TABLE IF NOT EXISTS scheduler_job_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    items_processed INTEGER DEFAULT 0,
    items_failed INTEGER DEFAULT 0,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_scheduler_job_name ON scheduler_job_log(job_name);
CREATE INDEX IF NOT EXISTS idx_scheduler_job_started_at ON scheduler_job_log(started_at);

-- =============================================================================
-- SEED DATA: Message Templates
-- =============================================================================

INSERT INTO message_templates (message_type, locale, template_text, variables, is_active) VALUES
-- Welcome messages
('WELCOME_GREETING', 'he', 'שלום {fatherName}! ברוך הבא ל-Dad Coach. אני כאן לעזור לך לבנות הרגל של זמן איכות עם הילדים שלך. 👨‍👧 מוכן להתחיל?', ARRAY['fatherName'], true),
('WELCOME_GREETING', 'en', 'Hi {fatherName}! Welcome to Dad Coach. I''m here to help you build a habit of quality time with your kids. 👨‍👧 Ready to get started?', ARRAY['fatherName'], true),
('WELCOME_EXPLAIN', 'he', 'Dad Coach עוזר לך לתכנן ולעקוב אחרי זמן איכות עם הילדים שלך. תזמן מפגשים, השלם אותם, וצבור התקדמות במערכת החגורות שלנו. פשוט וקל! מוכן לתאם את המפגש הראשון שלך?', ARRAY[]::TEXT[], true),
('WELCOME_EXPLAIN', 'en', 'Dad Coach helps you plan and track quality time with your kids. Schedule sessions, complete them, and earn progress in our belt system. Simple and easy! Ready to schedule your first session?', ARRAY[]::TEXT[], true),

-- Schedule messages
('SCHEDULE_SLOTS', 'he', 'בחר זמן לזמן איכות עם {childName}:', ARRAY['childName'], true),
('SCHEDULE_SLOTS', 'en', 'Choose a time for Quality Time with {childName}:', ARRAY['childName'], true),
('SCHEDULE_CONFIRM', 'he', 'מעולה! זמן איכות עם {childName} נקבע ל-{time}. תהנו! 💪', ARRAY['childName', 'time'], true),
('SCHEDULE_CONFIRM', 'en', 'Great! Quality Time with {childName} is scheduled for {time}. Enjoy! 💪', ARRAY['childName', 'time'], true),

-- Follow-up messages
('FOLLOW_UP_QUESTION', 'he', 'השלמת את זמן האיכות עם {childName}?', ARRAY['childName'], true),
('FOLLOW_UP_QUESTION', 'en', 'Did you complete your Quality Time with {childName}?', ARRAY['childName'], true),
('FOLLOW_UP_COMPLETED', 'he', 'כל הכבוד {fatherName}! 🎉 הרצף שלך עכשיו {streak}. המשך כך!', ARRAY['fatherName', 'streak'], true),
('FOLLOW_UP_COMPLETED', 'en', 'Awesome {fatherName}! 🎉 Your streak is now {streak}. Keep it up!', ARRAY['fatherName', 'streak'], true),

-- Reminder messages
('WAITING_REMINDER', 'he', 'בוקר טוב {fatherName}! זמן איכות עם {childName} היום ב-{time}. תהנו! 💪', ARRAY['fatherName', 'childName', 'time'], true),
('WAITING_REMINDER', 'en', 'Good morning {fatherName}! Quality Time with {childName} today at {time}. Have a great time! 💪', ARRAY['fatherName', 'childName', 'time'], true),

-- Processing message
('PROCESSING', 'he', 'רק רגע, אני חושב... 🤔', ARRAY[]::TEXT[], true),
('PROCESSING', 'en', 'Just a moment, I''m thinking... 🤔', ARRAY[]::TEXT[], true)

ON CONFLICT (message_type, locale) DO NOTHING;
