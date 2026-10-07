-- Production's schema at V22 (pg_dump --schema-only of the Supabase database, 2026-10-07), for the upgrade test
-- (ProductionUpgradeMigrationTest). No data. pgvector's vector(1536) column is text here (the test database has
-- no pgvector; V23 drops that table anyway) and its ivfflat index is left out.


CREATE TABLE public.activation_records (
    activation_id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    session_id uuid NOT NULL,
    status character varying(25) NOT NULL,
    deep_link_generated_at timestamp with time zone,
    link_clicked_at timestamp with time zone,
    message_received_at timestamp with time zone,
    conversation_started_at timestamp with time zone,
    retry_count integer DEFAULT 0 NOT NULL,
    failure_reason character varying(200)
);

CREATE TABLE public.ai_profiles (
    profile_id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    coaching_style character varying(30) NOT NULL,
    language character varying(5) NOT NULL,
    children_context text,
    goals_context text,
    personality_brief text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.ai_telemetry (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    father_id uuid NOT NULL,
    conversation_id uuid,
    conversation_type character varying(30),
    interaction_type character varying(30) NOT NULL,
    prompt_version character varying(20),
    model_provider character varying(20) NOT NULL,
    model_name character varying(50) NOT NULL,
    temperature real,
    input_tokens integer NOT NULL,
    output_tokens integer NOT NULL,
    estimated_cost_usd real,
    total_latency_ms integer NOT NULL,
    llm_latency_ms integer,
    validation_passed boolean DEFAULT true NOT NULL,
    fallback_used boolean DEFAULT false NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    quality_score real,
    safety_classification character varying(30),
    ab_test_group character varying(5),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.api_audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    actor_type character varying(20) NOT NULL,
    actor_id uuid NOT NULL,
    operation character varying(50) NOT NULL,
    resource_type character varying(30) NOT NULL,
    resource_id uuid,
    result character varying(20) NOT NULL,
    error_code character varying(50),
    changes jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.calendar_sync_log (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    mission_id bigint,
    action character varying(30) NOT NULL,
    calendar_event_id character varying(255),
    synced_at timestamp with time zone DEFAULT now() NOT NULL,
    success boolean DEFAULT true NOT NULL,
    error_message text
);

CREATE SEQUENCE public.calendar_sync_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.calendar_sync_log_id_seq OWNED BY public.calendar_sync_log.id;

CREATE TABLE public.child (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    name character varying(120) NOT NULL,
    birth_date date NOT NULL,
    gender character varying(10),
    interests text[],
    challenges text[],
    relationship_quality integer DEFAULT 3,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.child_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.child_id_seq OWNED BY public.child.id;

CREATE TABLE public.communication_endpoints (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    channel character varying(20) NOT NULL,
    channel_identity character varying(50) NOT NULL,
    is_primary boolean DEFAULT true NOT NULL,
    session_opens_at timestamp with time zone,
    session_closes_at timestamp with time zone,
    last_active_at timestamp with time zone,
    registered_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.communication_preferences (
    preference_id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    preferred_coaching_time time without time zone DEFAULT '08:00:00'::time without time zone NOT NULL,
    notification_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    quiet_hours_start time without time zone DEFAULT '21:00:00'::time without time zone NOT NULL,
    quiet_hours_end time without time zone DEFAULT '07:00:00'::time without time zone NOT NULL,
    email_notifications boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.conversation (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    type character varying(30) DEFAULT 'COACHING'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    objective text,
    summary text,
    message_count integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone
);

CREATE SEQUENCE public.conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.conversation_id_seq OWNED BY public.conversation.id;

CREATE TABLE public.delivery_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    message_id uuid NOT NULL,
    father_id uuid NOT NULL,
    channel character varying(20) NOT NULL,
    provider_message_id character varying(100),
    status character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    failure_reason character varying(100),
    retry_count integer DEFAULT 0 NOT NULL,
    sent_at timestamp with time zone,
    delivered_at timestamp with time zone,
    read_at timestamp with time zone,
    failed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.embedding_retry_queue (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    memory_id uuid NOT NULL,
    content text NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_attempt_at timestamp with time zone,
    last_error_type character varying(50),
    last_error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_embedding_retry_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'PERMANENTLY_FAILED'::character varying])::text[]))),
    CONSTRAINT embedding_retry_queue_attempt_count_check CHECK (((attempt_count >= 0) AND (attempt_count <= 3)))
);

COMMENT ON TABLE public.embedding_retry_queue IS 'Queue for retrying failed embedding generation. Max 3 attempts over 24 hours.';

COMMENT ON COLUMN public.embedding_retry_queue.memory_id IS 'References memories.id - the memory that needs embedding';

COMMENT ON COLUMN public.embedding_retry_queue.status IS 'PENDING=waiting, PROCESSING=in progress, COMPLETED=success, PERMANENTLY_FAILED=max attempts reached';

COMMENT ON COLUMN public.embedding_retry_queue.attempt_count IS 'Number of attempts made (0-3). After 3 failed attempts, status becomes PERMANENTLY_FAILED';

COMMENT ON COLUMN public.embedding_retry_queue.next_attempt_at IS 'When the next retry should be attempted. Backoff: 0h -> 4h -> 12h';

CREATE TABLE public.families (
    family_id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    family_name character varying(120),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.father (
    id bigint NOT NULL,
    phone character varying(32) NOT NULL,
    display_name character varying(120),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'NOT_STARTED'::character varying NOT NULL,
    onboarding_state character varying(30) DEFAULT 'NOT_STARTED'::character varying,
    coaching_phase character varying(20) DEFAULT 'FOUNDATION'::character varying,
    coaching_style character varying(20) DEFAULT 'BALANCED'::character varying,
    preferred_coaching_time time without time zone DEFAULT '08:00:00'::time without time zone,
    timezone character varying(64) DEFAULT 'Asia/Jerusalem'::character varying,
    locale character varying(10) DEFAULT 'he'::character varying,
    engagement_score integer DEFAULT 0,
    coaching_streak integer DEFAULT 0,
    longest_streak integer DEFAULT 0,
    activation_date date,
    last_interaction_at timestamp with time zone,
    pause_until date,
    metadata jsonb DEFAULT '{}'::jsonb,
    google_calendar_enabled boolean DEFAULT false,
    google_refresh_token character varying(512),
    google_access_token character varying(2048),
    google_token_expires_at timestamp with time zone,
    google_calendar_id character varying(255),
    weekly_goal_minutes integer DEFAULT 30 NOT NULL,
    monthly_goal_minutes integer DEFAULT 120 NOT NULL,
    goals_started_at date,
    current_streak_weeks integer DEFAULT 0 NOT NULL,
    longest_streak_weeks integer DEFAULT 0 NOT NULL,
    total_quality_minutes integer DEFAULT 0 NOT NULL,
    current_workflow_state character varying(30) DEFAULT 'WELCOME'::character varying,
    previous_workflow_state character varying(30),
    workflow_state_entered_at timestamp with time zone,
    welcomed_at timestamp with time zone,
    quality_time_streak integer DEFAULT 0 NOT NULL,
    quality_time_longest_streak integer DEFAULT 0 NOT NULL,
    total_quality_times_completed integer DEFAULT 0 NOT NULL,
    current_belt character varying(20) DEFAULT 'WHITE'::character varying NOT NULL,
    welcome_step character varying(40) DEFAULT 'INTRO'::character varying
);

COMMENT ON COLUMN public.father.welcome_step IS 'Current step in the welcome onboarding flow: INTRO, CONNECT_CALENDAR, SET_WEEKLY_GOAL, SCHEDULE_FIRST_QUALITY_TIME, DASHBOARD_TOUR, COMPLETED';

CREATE SEQUENCE public.father_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.father_id_seq OWNED BY public.father.id;

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);

CREATE TABLE public.goal (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    category character varying(30) NOT NULL,
    priority integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    progress_percentage integer DEFAULT 0 NOT NULL,
    estimated_total_missions integer DEFAULT 0 NOT NULL,
    completed_related_missions integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone
);

CREATE SEQUENCE public.goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.goal_id_seq OWNED BY public.goal.id;

CREATE TABLE public.invitation_audit_log (
    log_id uuid DEFAULT gen_random_uuid() NOT NULL,
    token_hash character varying(64) NOT NULL,
    action character varying(30) NOT NULL,
    result character varying(20) NOT NULL,
    ip_address character varying(45) NOT NULL,
    user_agent character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.invitations (
    invitation_id uuid DEFAULT gen_random_uuid() NOT NULL,
    token character varying(32) NOT NULL,
    type character varying(15) NOT NULL,
    status character varying(10) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    max_uses integer NOT NULL,
    current_uses integer DEFAULT 0 NOT NULL,
    metadata jsonb
);

CREATE TABLE public.language_preferences (
    preference_id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    language_code character varying(5) DEFAULT 'he'::character varying NOT NULL,
    date_format character varying(20) DEFAULT 'dd/MM/yyyy'::character varying NOT NULL,
    time_format character varying(20) DEFAULT 'HH:mm'::character varying NOT NULL,
    text_direction character varying(3) DEFAULT 'RTL'::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.magic_link (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    token character varying(32) NOT NULL,
    father_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    redirect_path character varying(255),
    context character varying(50)
);

CREATE TABLE public.media_assets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    message_id uuid NOT NULL,
    mime_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    content bytea NOT NULL,
    downloaded_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

CREATE TABLE public.memories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    child_id uuid,
    category character varying(30) NOT NULL,
    subject_type character varying(10) NOT NULL,
    content text NOT NULL,
    importance_score integer NOT NULL,
    confidence_score numeric(3,2) NOT NULL,
    state character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    source_type character varying(30) NOT NULL,
    source_conversation_id uuid,
    superseded_by uuid,
    conflict_group_id uuid,
    needs_user_confirmation boolean DEFAULT false NOT NULL,
    goal_id uuid,
    event_date date,
    event_end_date date,
    is_recurring boolean DEFAULT false NOT NULL,
    embedding text,
    confirmation_count integer DEFAULT 0 NOT NULL,
    access_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    last_confirmed_at timestamp with time zone,
    last_accessed_at timestamp with time zone,
    expires_at timestamp with time zone,
    CONSTRAINT memories_confidence_score_check CHECK (((confidence_score >= 0.0) AND (confidence_score <= 1.0))),
    CONSTRAINT memories_content_check CHECK ((length(content) <= 500)),
    CONSTRAINT memories_importance_score_check CHECK (((importance_score >= 1) AND (importance_score <= 10)))
);

COMMENT ON TABLE public.memories IS 'Memory Knowledge System - stores contextual knowledge about fathers, children, and families with vector embeddings for semantic search';

COMMENT ON COLUMN public.memories.father_id IS 'UUID reference to the father (logical reference, not FK to allow flexibility)';

COMMENT ON COLUMN public.memories.child_id IS 'UUID reference to a specific child (nullable for father-only or family memories)';

COMMENT ON COLUMN public.memories.category IS 'Memory category: IDENTITY, RELATIONSHIP, PREFERENCE, GOAL, CHALLENGE, MILESTONE, CONTEXT, CONVERSATION_SUMMARY, EVENT, HABIT, FAMILY';

COMMENT ON COLUMN public.memories.subject_type IS 'Subject of the memory: FATHER, CHILD, or FAMILY';

COMMENT ON COLUMN public.memories.state IS 'Lifecycle state: ACTIVE, CONFIRMED, SUPERSEDED, ARCHIVED, EXPIRED, DELETED';

COMMENT ON COLUMN public.memories.source_type IS 'Source: CONVERSATION_EXTRACTION, ONBOARDING, FATHER_CORRECTION, SYSTEM_GENERATED, MISSION_OUTCOME';

COMMENT ON COLUMN public.memories.embedding IS 'OpenAI text-embedding-ada-002 vector (1536 dimensions) for semantic similarity search';

CREATE TABLE public.memory (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    child_id bigint,
    category character varying(40) NOT NULL,
    content text NOT NULL,
    importance_score integer DEFAULT 5 NOT NULL,
    confidence_score numeric(3,2) DEFAULT 1.00 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    access_count integer DEFAULT 0 NOT NULL,
    last_accessed_at timestamp with time zone,
    superseded_by bigint,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.memory_audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    memory_id uuid NOT NULL,
    father_id uuid NOT NULL,
    operation_type character varying(30) NOT NULL,
    from_state character varying(20),
    to_state character varying(20),
    trigger_type character varying(30) NOT NULL,
    triggered_by character varying(100) NOT NULL,
    state_before jsonb,
    state_after jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

COMMENT ON TABLE public.memory_audit_log IS 'Append-only audit trail for all memory operations per SPEC-004';

CREATE SEQUENCE public.memory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.memory_id_seq OWNED BY public.memory.id;

CREATE TABLE public.message_log (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    direction character varying(10) NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tool_used character varying(100),
    tool_parameters jsonb,
    previous_state character varying(50),
    new_state character varying(50),
    tool_success boolean,
    error_message text,
    CONSTRAINT message_log_direction_check CHECK (((direction)::text = ANY ((ARRAY['INBOUND'::character varying, 'OUTBOUND'::character varying])::text[])))
);

COMMENT ON COLUMN public.message_log.tool_used IS 'The AI agent tool that was used to process this message (e.g., respond_to_father, start_quality_time)';

COMMENT ON COLUMN public.message_log.tool_parameters IS 'JSON object containing the parameters passed to the tool';

COMMENT ON COLUMN public.message_log.previous_state IS 'Workflow state before AI processing';

COMMENT ON COLUMN public.message_log.new_state IS 'Workflow state after AI processing (null if no transition)';

COMMENT ON COLUMN public.message_log.tool_success IS 'Whether the tool execution succeeded';

COMMENT ON COLUMN public.message_log.error_message IS 'Error message if tool execution failed';

CREATE SEQUENCE public.message_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.message_log_id_seq OWNED BY public.message_log.id;

CREATE TABLE public.message_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    message_type character varying(50) NOT NULL,
    template_text text NOT NULL,
    language character varying(10) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.mission (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    child_id bigint,
    goal_id bigint,
    title character varying(200) NOT NULL,
    description text NOT NULL,
    category character varying(30) NOT NULL,
    difficulty integer DEFAULT 1 NOT NULL,
    estimated_minutes integer DEFAULT 30 NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    outcome_rating integer,
    outcome_notes text,
    prompt_version character varying(50),
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    completed_at timestamp with time zone,
    reschedule_count integer DEFAULT 0 NOT NULL,
    scheduled_for timestamp with time zone,
    reminder_sent_at timestamp with time zone,
    last_reminded_at timestamp with time zone,
    calendar_event_id character varying(255),
    reschedule_reason character varying(100)
);

CREATE SEQUENCE public.mission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mission_id_seq OWNED BY public.mission.id;

CREATE TABLE public.onboarding_sessions (
    session_id uuid DEFAULT gen_random_uuid() NOT NULL,
    invitation_id uuid NOT NULL,
    father_id uuid,
    current_step character varying(20) NOT NULL,
    status character varying(15) NOT NULL,
    wizard_data bytea,
    language character varying(5),
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    last_activity_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    ip_address character varying(45),
    user_agent character varying(500)
);

CREATE TABLE public.platform_person_deletion (
    father_id bigint NOT NULL,
    person_ref character varying(64) NOT NULL,
    external_user_id character varying(64),
    purge_local boolean DEFAULT false NOT NULL,
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    last_error character varying(500),
    completed_at timestamp with time zone,
    outcome character varying(30)
);

CREATE TABLE public.quality_time (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id bigint NOT NULL,
    child_id bigint NOT NULL,
    google_calendar_event_id character varying(255),
    scheduled_start timestamp with time zone NOT NULL,
    scheduled_end timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    completion_notes text,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    reminder_sent boolean DEFAULT false NOT NULL,
    follow_up_sent boolean DEFAULT false NOT NULL,
    pre_qt_reminder_sent boolean DEFAULT false NOT NULL
);

CREATE TABLE public.quality_time_commitment (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    child_id bigint,
    scheduled_date date NOT NULL,
    scheduled_time time without time zone NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    duration_minutes integer,
    activity_type character varying(50),
    activity_note character varying(500),
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    reminder_sent_at timestamp with time zone,
    reminder_message_id character varying(100),
    completed_at timestamp with time zone,
    completion_note character varying(500),
    points_awarded integer,
    created_via character varying(30),
    conversation_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.quality_time_commitment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.quality_time_commitment_id_seq OWNED BY public.quality_time_commitment.id;

CREATE TABLE public.rate_limit_entries (
    entry_id uuid DEFAULT gen_random_uuid() NOT NULL,
    key_type character varying(10) NOT NULL,
    key_value character varying(255) NOT NULL,
    window_start timestamp with time zone NOT NULL,
    attempt_count integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.safety_event_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id uuid NOT NULL,
    event_type character varying(30) NOT NULL,
    severity character varying(20) NOT NULL,
    summary character varying(100) NOT NULL,
    description text,
    conversation_id uuid,
    memory_id uuid,
    metadata jsonb,
    requires_review boolean DEFAULT true NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    review_notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

COMMENT ON TABLE public.safety_event_records IS 'Safety event records - stored separately from memories with 7-year retention for legal compliance per SPEC-004 Req 24';

COMMENT ON COLUMN public.safety_event_records.father_id IS 'Father UUID (no FK intentionally - safety events are isolated and retained independently)';

COMMENT ON COLUMN public.safety_event_records.event_type IS 'Type of safety event: SELF_HARM_MENTION, CHILD_ABUSE_CONCERN, DOMESTIC_VIOLENCE_INDICATOR, SUBSTANCE_ABUSE_MENTION, MENTAL_HEALTH_CRISIS, OTHER_SAFETY_CONCERN';

COMMENT ON COLUMN public.safety_event_records.severity IS 'Severity level: LOW, MEDIUM, HIGH, CRITICAL';

COMMENT ON COLUMN public.safety_event_records.summary IS 'Brief summary for quick scanning (max 100 chars per SPEC-004)';

COMMENT ON COLUMN public.safety_event_records.description IS 'Detailed description for in-depth review (max 500 chars)';

COMMENT ON COLUMN public.safety_event_records.requires_review IS 'Flag indicating whether this event needs human review';

COMMENT ON COLUMN public.safety_event_records.expires_at IS 'When this record can be permanently deleted (default: 7 years from creation)';

CREATE TABLE public.scheduled_response_delivery (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    trigger_id character varying(64) NOT NULL,
    workflow_instance_id character varying(64),
    father_id bigint NOT NULL,
    target_state_key character varying(100),
    status character varying(20) NOT NULL,
    delivery_mode character varying(20),
    failure_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone
);

COMMENT ON TABLE public.scheduled_response_delivery IS 'Workflow Platform proactive messages delivered to fathers, one row per callback idempotency key';

CREATE TABLE public.scheduler_job_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    job_name character varying(100) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    records_processed integer DEFAULT 0 NOT NULL,
    errors_count integer DEFAULT 0 NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.template_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    template_name character varying(100) NOT NULL,
    language character varying(10) NOT NULL,
    category character varying(20) NOT NULL,
    body text NOT NULL,
    status character varying(20) NOT NULL,
    max_variables integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.tool_wishlist (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    suggested_name character varying(100) NOT NULL,
    user_need text NOT NULL,
    suggested_capability text NOT NULL,
    original_message text,
    father_id bigint,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    priority integer,
    review_notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    reviewed_at timestamp with time zone,
    occurrence_count integer DEFAULT 1 NOT NULL
);

COMMENT ON TABLE public.tool_wishlist IS 'AI-suggested tools that do not exist yet - for product feedback loop';

COMMENT ON COLUMN public.tool_wishlist.suggested_name IS 'AI-suggested name for the tool (e.g., send_reminder)';

COMMENT ON COLUMN public.tool_wishlist.user_need IS 'What the user was trying to accomplish';

COMMENT ON COLUMN public.tool_wishlist.suggested_capability IS 'What capability the AI thinks this tool should have';

COMMENT ON COLUMN public.tool_wishlist.occurrence_count IS 'How many times this tool has been wished for';

CREATE TABLE public.weekly_goal (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    week_start_date date NOT NULL,
    target_hours integer NOT NULL,
    actual_minutes integer DEFAULT 0 NOT NULL,
    scheduled_count integer DEFAULT 0 NOT NULL,
    completed_count integer DEFAULT 0 NOT NULL,
    starting_belt character varying(20) NOT NULL,
    ending_belt character varying(20),
    belt_promoted boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT ck_weekly_goal_belt CHECK (((starting_belt)::text = ANY ((ARRAY['WHITE'::character varying, 'YELLOW'::character varying, 'ORANGE'::character varying, 'GREEN'::character varying, 'BLUE'::character varying, 'BROWN'::character varying, 'BLACK'::character varying])::text[]))),
    CONSTRAINT ck_weekly_goal_ending_belt CHECK (((ending_belt IS NULL) OR ((ending_belt)::text = ANY ((ARRAY['WHITE'::character varying, 'YELLOW'::character varying, 'ORANGE'::character varying, 'GREEN'::character varying, 'BLUE'::character varying, 'BROWN'::character varying, 'BLACK'::character varying])::text[])))),
    CONSTRAINT ck_weekly_goal_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'COMPLETED'::character varying, 'MISSED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT weekly_goal_actual_minutes_check CHECK ((actual_minutes >= 0)),
    CONSTRAINT weekly_goal_completed_count_check CHECK ((completed_count >= 0)),
    CONSTRAINT weekly_goal_scheduled_count_check CHECK ((scheduled_count >= 0)),
    CONSTRAINT weekly_goal_target_hours_check CHECK ((target_hours >= 1))
);

CREATE SEQUENCE public.weekly_goal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.weekly_goal_id_seq OWNED BY public.weekly_goal.id;

CREATE TABLE public.workflow_state_transition_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    father_id bigint NOT NULL,
    from_state character varying(30) NOT NULL,
    to_state character varying(30) NOT NULL,
    trigger_reason character varying(50) NOT NULL,
    trigger_message_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.calendar_sync_log ALTER COLUMN id SET DEFAULT nextval('public.calendar_sync_log_id_seq'::regclass);

ALTER TABLE ONLY public.child ALTER COLUMN id SET DEFAULT nextval('public.child_id_seq'::regclass);

ALTER TABLE ONLY public.conversation ALTER COLUMN id SET DEFAULT nextval('public.conversation_id_seq'::regclass);

ALTER TABLE ONLY public.father ALTER COLUMN id SET DEFAULT nextval('public.father_id_seq'::regclass);

ALTER TABLE ONLY public.goal ALTER COLUMN id SET DEFAULT nextval('public.goal_id_seq'::regclass);

ALTER TABLE ONLY public.memory ALTER COLUMN id SET DEFAULT nextval('public.memory_id_seq'::regclass);

ALTER TABLE ONLY public.message_log ALTER COLUMN id SET DEFAULT nextval('public.message_log_id_seq'::regclass);

ALTER TABLE ONLY public.mission ALTER COLUMN id SET DEFAULT nextval('public.mission_id_seq'::regclass);

ALTER TABLE ONLY public.quality_time_commitment ALTER COLUMN id SET DEFAULT nextval('public.quality_time_commitment_id_seq'::regclass);

ALTER TABLE ONLY public.weekly_goal ALTER COLUMN id SET DEFAULT nextval('public.weekly_goal_id_seq'::regclass);

ALTER TABLE ONLY public.activation_records
    ADD CONSTRAINT activation_records_father_id_key UNIQUE (father_id);

ALTER TABLE ONLY public.activation_records
    ADD CONSTRAINT activation_records_pkey PRIMARY KEY (activation_id);

ALTER TABLE ONLY public.ai_profiles
    ADD CONSTRAINT ai_profiles_father_id_key UNIQUE (father_id);

ALTER TABLE ONLY public.ai_profiles
    ADD CONSTRAINT ai_profiles_pkey PRIMARY KEY (profile_id);

ALTER TABLE ONLY public.ai_telemetry
    ADD CONSTRAINT ai_telemetry_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.api_audit_log
    ADD CONSTRAINT api_audit_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.calendar_sync_log
    ADD CONSTRAINT calendar_sync_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.child
    ADD CONSTRAINT child_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.communication_endpoints
    ADD CONSTRAINT communication_endpoints_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.communication_preferences
    ADD CONSTRAINT communication_preferences_father_id_key UNIQUE (father_id);

ALTER TABLE ONLY public.communication_preferences
    ADD CONSTRAINT communication_preferences_pkey PRIMARY KEY (preference_id);

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT conversation_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.delivery_records
    ADD CONSTRAINT delivery_records_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.embedding_retry_queue
    ADD CONSTRAINT embedding_retry_queue_memory_id_key UNIQUE (memory_id);

ALTER TABLE ONLY public.embedding_retry_queue
    ADD CONSTRAINT embedding_retry_queue_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.families
    ADD CONSTRAINT families_father_id_key UNIQUE (father_id);

ALTER TABLE ONLY public.families
    ADD CONSTRAINT families_pkey PRIMARY KEY (family_id);

ALTER TABLE ONLY public.father
    ADD CONSTRAINT father_phone_key UNIQUE (phone);

ALTER TABLE ONLY public.father
    ADD CONSTRAINT father_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT goal_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invitation_audit_log
    ADD CONSTRAINT invitation_audit_log_pkey PRIMARY KEY (log_id);

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_pkey PRIMARY KEY (invitation_id);

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_token_key UNIQUE (token);

ALTER TABLE ONLY public.language_preferences
    ADD CONSTRAINT language_preferences_father_id_key UNIQUE (father_id);

ALTER TABLE ONLY public.language_preferences
    ADD CONSTRAINT language_preferences_pkey PRIMARY KEY (preference_id);

ALTER TABLE ONLY public.magic_link
    ADD CONSTRAINT magic_link_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.magic_link
    ADD CONSTRAINT magic_link_token_key UNIQUE (token);

ALTER TABLE ONLY public.media_assets
    ADD CONSTRAINT media_assets_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.memories
    ADD CONSTRAINT memories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.memory_audit_log
    ADD CONSTRAINT memory_audit_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.memory
    ADD CONSTRAINT memory_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.message_log
    ADD CONSTRAINT message_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.message_templates
    ADD CONSTRAINT message_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.message_templates
    ADD CONSTRAINT message_templates_type_lang_key UNIQUE (message_type, language);

ALTER TABLE ONLY public.mission
    ADD CONSTRAINT mission_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.onboarding_sessions
    ADD CONSTRAINT onboarding_sessions_pkey PRIMARY KEY (session_id);

ALTER TABLE ONLY public.platform_person_deletion
    ADD CONSTRAINT platform_person_deletion_pkey PRIMARY KEY (father_id);

ALTER TABLE ONLY public.quality_time_commitment
    ADD CONSTRAINT quality_time_commitment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rate_limit_entries
    ADD CONSTRAINT rate_limit_entries_key_type_key_value_window_start_key UNIQUE (key_type, key_value, window_start);

ALTER TABLE ONLY public.rate_limit_entries
    ADD CONSTRAINT rate_limit_entries_pkey PRIMARY KEY (entry_id);

ALTER TABLE ONLY public.safety_event_records
    ADD CONSTRAINT safety_event_records_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT scheduled_response_delivery_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scheduler_job_log
    ADD CONSTRAINT scheduler_job_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_messages
    ADD CONSTRAINT template_messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_messages
    ADD CONSTRAINT template_messages_template_name_key UNIQUE (template_name);

ALTER TABLE ONLY public.tool_wishlist
    ADD CONSTRAINT tool_wishlist_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT uk_weekly_goal_father_week UNIQUE (father_id, week_start_date);

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT uq_scheduled_response_delivery_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.workflow_state_transition_log
    ADD CONSTRAINT workflow_state_transition_log_pkey PRIMARY KEY (id);

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);

CREATE INDEX idx_activation_records_father_id ON public.activation_records USING btree (father_id);

CREATE INDEX idx_activation_records_session_id ON public.activation_records USING btree (session_id);

CREATE INDEX idx_activation_records_status ON public.activation_records USING btree (status);

CREATE INDEX idx_ai_profiles_father_id ON public.ai_profiles USING btree (father_id);

CREATE INDEX idx_ai_telemetry_created_at ON public.ai_telemetry USING btree (created_at);

CREATE INDEX idx_ai_telemetry_father_id ON public.ai_telemetry USING btree (father_id);

CREATE INDEX idx_api_audit_actor_id ON public.api_audit_log USING btree (actor_id);

CREATE INDEX idx_api_audit_created_at ON public.api_audit_log USING btree (created_at);

CREATE INDEX idx_calendar_sync_father_id ON public.calendar_sync_log USING btree (father_id);

CREATE INDEX idx_child_father_id ON public.child USING btree (father_id);

CREATE INDEX idx_comm_endpoints_channel_identity ON public.communication_endpoints USING btree (channel_identity);

CREATE INDEX idx_comm_endpoints_father_id ON public.communication_endpoints USING btree (father_id);

CREATE UNIQUE INDEX idx_comm_endpoints_unique ON public.communication_endpoints USING btree (channel, channel_identity);

CREATE INDEX idx_comm_prefs_father_id ON public.communication_preferences USING btree (father_id);

CREATE INDEX idx_conversation_father_id ON public.conversation USING btree (father_id);

CREATE INDEX idx_conversation_status ON public.conversation USING btree (status);

CREATE INDEX idx_delivery_records_father_id ON public.delivery_records USING btree (father_id);

CREATE INDEX idx_delivery_records_message_id ON public.delivery_records USING btree (message_id);

CREATE INDEX idx_delivery_records_status ON public.delivery_records USING btree (status);

CREATE INDEX idx_embedding_retry_memory ON public.embedding_retry_queue USING btree (memory_id);

CREATE INDEX idx_embedding_retry_status_next_attempt ON public.embedding_retry_queue USING btree (status, next_attempt_at) WHERE ((status)::text = 'PENDING'::text);

CREATE INDEX idx_embedding_retry_status_updated ON public.embedding_retry_queue USING btree (status, updated_at);

CREATE INDEX idx_families_father_id ON public.families USING btree (father_id);

CREATE INDEX idx_father_phone ON public.father USING btree (phone);

CREATE INDEX idx_father_status ON public.father USING btree (status);

CREATE INDEX idx_father_welcome_step ON public.father USING btree (welcome_step);

CREATE INDEX idx_goal_father_id ON public.goal USING btree (father_id);

CREATE INDEX idx_goal_status ON public.goal USING btree (status);

CREATE INDEX idx_invitation_audit_created_at ON public.invitation_audit_log USING btree (created_at);

CREATE INDEX idx_invitation_audit_token_hash ON public.invitation_audit_log USING btree (token_hash);

CREATE INDEX idx_invitations_status ON public.invitations USING btree (status);

CREATE INDEX idx_invitations_token ON public.invitations USING btree (token);

CREATE INDEX idx_lang_prefs_father_id ON public.language_preferences USING btree (father_id);

CREATE INDEX idx_magic_link_expires_at ON public.magic_link USING btree (expires_at);

CREATE INDEX idx_magic_link_father_id ON public.magic_link USING btree (father_id);

CREATE INDEX idx_magic_link_token ON public.magic_link USING btree (token);

CREATE INDEX idx_media_assets_father_id ON public.media_assets USING btree (father_id);

CREATE INDEX idx_media_assets_message_id ON public.media_assets USING btree (message_id);

CREATE INDEX idx_memories_conflict_group ON public.memories USING btree (conflict_group_id) WHERE (conflict_group_id IS NOT NULL);

CREATE INDEX idx_memories_expires ON public.memories USING btree (expires_at) WHERE ((state)::text = 'ACTIVE'::text);

CREATE INDEX idx_memories_father_category ON public.memories USING btree (father_id, category, state);

CREATE INDEX idx_memories_father_child ON public.memories USING btree (father_id, subject_type, child_id, state);

CREATE INDEX idx_memories_father_state ON public.memories USING btree (father_id, state);

CREATE INDEX idx_memories_needs_confirmation ON public.memories USING btree (father_id, needs_user_confirmation) WHERE (needs_user_confirmation = true);

CREATE INDEX idx_memory_audit_father ON public.memory_audit_log USING btree (father_id, created_at DESC);

CREATE INDEX idx_memory_audit_memory ON public.memory_audit_log USING btree (memory_id, created_at DESC);

CREATE INDEX idx_memory_category ON public.memory USING btree (category);

CREATE INDEX idx_memory_father_id ON public.memory USING btree (father_id);

CREATE INDEX idx_message_log_father_created ON public.message_log USING btree (father_id, created_at DESC);

CREATE INDEX idx_message_log_tool_used ON public.message_log USING btree (tool_used);

CREATE INDEX idx_message_templates_type ON public.message_templates USING btree (message_type);

CREATE INDEX idx_mission_child_id ON public.mission USING btree (child_id);

CREATE INDEX idx_mission_father_id ON public.mission USING btree (father_id);

CREATE INDEX idx_mission_status ON public.mission USING btree (status);

CREATE INDEX idx_onboarding_sessions_father_id ON public.onboarding_sessions USING btree (father_id);

CREATE INDEX idx_onboarding_sessions_invitation_id ON public.onboarding_sessions USING btree (invitation_id);

CREATE INDEX idx_platform_person_deletion_due ON public.platform_person_deletion USING btree (next_attempt_at) WHERE (completed_at IS NULL);

CREATE INDEX idx_platform_person_deletion_pending_id ON public.platform_person_deletion USING btree (external_user_id) WHERE (completed_at IS NULL);

CREATE INDEX idx_qtc_father_id ON public.quality_time_commitment USING btree (father_id);

CREATE INDEX idx_qtc_scheduled_date ON public.quality_time_commitment USING btree (scheduled_date);

CREATE INDEX idx_quality_time_child_id ON public.quality_time USING btree (child_id);

CREATE INDEX idx_quality_time_father_id ON public.quality_time USING btree (father_id);

CREATE INDEX idx_quality_time_pre_qt_reminder ON public.quality_time USING btree (scheduled_start, pre_qt_reminder_sent) WHERE (((status)::text = 'SCHEDULED'::text) AND (pre_qt_reminder_sent = false));

CREATE INDEX idx_quality_time_scheduled_start ON public.quality_time USING btree (scheduled_start);

CREATE INDEX idx_quality_time_status ON public.quality_time USING btree (status);

CREATE INDEX idx_rate_limit_key_value ON public.rate_limit_entries USING btree (key_value);

CREATE INDEX idx_safety_events_created_at ON public.safety_event_records USING btree (created_at DESC);

CREATE INDEX idx_safety_events_event_type ON public.safety_event_records USING btree (event_type);

CREATE INDEX idx_safety_events_expires_at ON public.safety_event_records USING btree (expires_at);

CREATE INDEX idx_safety_events_father ON public.safety_event_records USING btree (father_id, created_at DESC);

CREATE INDEX idx_safety_events_requires_review ON public.safety_event_records USING btree (requires_review, severity);

CREATE INDEX idx_safety_events_severity ON public.safety_event_records USING btree (severity);

CREATE INDEX idx_scheduled_response_delivery_father ON public.scheduled_response_delivery USING btree (father_id);

CREATE INDEX idx_scheduler_job_name ON public.scheduler_job_log USING btree (job_name);

CREATE INDEX idx_scheduler_job_started_at ON public.scheduler_job_log USING btree (started_at);

CREATE INDEX idx_template_messages_template_name ON public.template_messages USING btree (template_name);

CREATE INDEX idx_tool_wishlist_created_at ON public.tool_wishlist USING btree (created_at);

CREATE INDEX idx_tool_wishlist_status ON public.tool_wishlist USING btree (status);

CREATE INDEX idx_tool_wishlist_suggested_name ON public.tool_wishlist USING btree (suggested_name);

CREATE INDEX idx_weekly_goal_father_status ON public.weekly_goal USING btree (father_id, status);

CREATE INDEX idx_weekly_goal_status ON public.weekly_goal USING btree (status);

CREATE INDEX idx_weekly_goal_week ON public.weekly_goal USING btree (week_start_date, status);

CREATE INDEX idx_wstl_created_at ON public.workflow_state_transition_log USING btree (created_at);

CREATE INDEX idx_wstl_father_id ON public.workflow_state_transition_log USING btree (father_id);

ALTER TABLE ONLY public.calendar_sync_log
    ADD CONSTRAINT calendar_sync_log_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.child
    ADD CONSTRAINT child_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT conversation_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT fk_scheduled_response_delivery_father FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tool_wishlist
    ADD CONSTRAINT fk_tool_wishlist_father FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT goal_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.memories
    ADD CONSTRAINT memories_superseded_by_fkey FOREIGN KEY (superseded_by) REFERENCES public.memories(id);

ALTER TABLE ONLY public.memory
    ADD CONSTRAINT memory_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.child(id);

ALTER TABLE ONLY public.memory
    ADD CONSTRAINT memory_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.message_log
    ADD CONSTRAINT message_log_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mission
    ADD CONSTRAINT mission_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.child(id);

ALTER TABLE ONLY public.mission
    ADD CONSTRAINT mission_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mission
    ADD CONSTRAINT mission_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES public.goal(id);

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.child(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quality_time_commitment
    ADD CONSTRAINT quality_time_commitment_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.child(id);

ALTER TABLE ONLY public.quality_time_commitment
    ADD CONSTRAINT quality_time_commitment_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;
