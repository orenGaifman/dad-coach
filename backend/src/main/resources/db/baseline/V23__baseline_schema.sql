-- =============================================================================
-- Dad Coach - baseline schema at V23 (DECISIONS D-011)
--
-- A fresh, EMPTY database starts here: FlywayBaselineStrategy runs this file and records Flyway baseline 23,
-- then V24+ run as usual. It is production's schema after V1-V23 (pg_dump of production's schema-only dump
-- with V23 applied, 2026-10-07), so a fresh database and production are identical from V24 on. Production
-- itself never runs this file (it has a migration history).
--
-- Never edit this file: a schema change is a new migration (V25+), which both paths run.
-- =============================================================================

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

CREATE TABLE public.message_log (
    id bigint NOT NULL,
    father_id bigint NOT NULL,
    direction character varying(10) NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT message_log_direction_check CHECK (((direction)::text = ANY (ARRAY[('INBOUND'::character varying)::text, ('OUTBOUND'::character varying)::text])))
);

CREATE SEQUENCE public.message_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.message_log_id_seq OWNED BY public.message_log.id;

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
    CONSTRAINT ck_weekly_goal_belt CHECK (((starting_belt)::text = ANY (ARRAY[('WHITE'::character varying)::text, ('YELLOW'::character varying)::text, ('ORANGE'::character varying)::text, ('GREEN'::character varying)::text, ('BLUE'::character varying)::text, ('BROWN'::character varying)::text, ('BLACK'::character varying)::text]))),
    CONSTRAINT ck_weekly_goal_ending_belt CHECK (((ending_belt IS NULL) OR ((ending_belt)::text = ANY (ARRAY[('WHITE'::character varying)::text, ('YELLOW'::character varying)::text, ('ORANGE'::character varying)::text, ('GREEN'::character varying)::text, ('BLUE'::character varying)::text, ('BROWN'::character varying)::text, ('BLACK'::character varying)::text])))),
    CONSTRAINT ck_weekly_goal_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('ACTIVE'::character varying)::text, ('COMPLETED'::character varying)::text, ('MISSED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
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

ALTER TABLE ONLY public.child ALTER COLUMN id SET DEFAULT nextval('public.child_id_seq'::regclass);

ALTER TABLE ONLY public.father ALTER COLUMN id SET DEFAULT nextval('public.father_id_seq'::regclass);

ALTER TABLE ONLY public.goal ALTER COLUMN id SET DEFAULT nextval('public.goal_id_seq'::regclass);

ALTER TABLE ONLY public.message_log ALTER COLUMN id SET DEFAULT nextval('public.message_log_id_seq'::regclass);

ALTER TABLE ONLY public.weekly_goal ALTER COLUMN id SET DEFAULT nextval('public.weekly_goal_id_seq'::regclass);

ALTER TABLE ONLY public.child
    ADD CONSTRAINT child_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.communication_endpoints
    ADD CONSTRAINT communication_endpoints_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.father
    ADD CONSTRAINT father_phone_key UNIQUE (phone);

ALTER TABLE ONLY public.father
    ADD CONSTRAINT father_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT goal_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.message_log
    ADD CONSTRAINT message_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.platform_person_deletion
    ADD CONSTRAINT platform_person_deletion_pkey PRIMARY KEY (father_id);

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT scheduled_response_delivery_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_messages
    ADD CONSTRAINT template_messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_messages
    ADD CONSTRAINT template_messages_template_name_key UNIQUE (template_name);

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT uk_weekly_goal_father_week UNIQUE (father_id, week_start_date);

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT uq_scheduled_response_delivery_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_pkey PRIMARY KEY (id);

CREATE INDEX idx_child_father_id ON public.child USING btree (father_id);

CREATE INDEX idx_comm_endpoints_channel_identity ON public.communication_endpoints USING btree (channel_identity);

CREATE INDEX idx_comm_endpoints_father_id ON public.communication_endpoints USING btree (father_id);

CREATE UNIQUE INDEX idx_comm_endpoints_unique ON public.communication_endpoints USING btree (channel, channel_identity);

CREATE INDEX idx_father_phone ON public.father USING btree (phone);

CREATE INDEX idx_father_status ON public.father USING btree (status);

CREATE INDEX idx_father_welcome_step ON public.father USING btree (welcome_step);

CREATE INDEX idx_goal_father_id ON public.goal USING btree (father_id);

CREATE INDEX idx_goal_status ON public.goal USING btree (status);

CREATE INDEX idx_message_log_father_created ON public.message_log USING btree (father_id, created_at DESC);

CREATE INDEX idx_platform_person_deletion_due ON public.platform_person_deletion USING btree (next_attempt_at) WHERE (completed_at IS NULL);

CREATE INDEX idx_platform_person_deletion_pending_id ON public.platform_person_deletion USING btree (external_user_id) WHERE (completed_at IS NULL);

CREATE INDEX idx_quality_time_child_id ON public.quality_time USING btree (child_id);

CREATE INDEX idx_quality_time_father_id ON public.quality_time USING btree (father_id);

CREATE INDEX idx_quality_time_pre_qt_reminder ON public.quality_time USING btree (scheduled_start, pre_qt_reminder_sent) WHERE (((status)::text = 'SCHEDULED'::text) AND (pre_qt_reminder_sent = false));

CREATE INDEX idx_quality_time_scheduled_start ON public.quality_time USING btree (scheduled_start);

CREATE INDEX idx_quality_time_status ON public.quality_time USING btree (status);

CREATE INDEX idx_scheduled_response_delivery_father ON public.scheduled_response_delivery USING btree (father_id);

CREATE INDEX idx_template_messages_template_name ON public.template_messages USING btree (template_name);

CREATE INDEX idx_weekly_goal_father_status ON public.weekly_goal USING btree (father_id, status);

CREATE INDEX idx_weekly_goal_status ON public.weekly_goal USING btree (status);

CREATE INDEX idx_weekly_goal_week ON public.weekly_goal USING btree (week_start_date, status);

ALTER TABLE ONLY public.child
    ADD CONSTRAINT child_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.scheduled_response_delivery
    ADD CONSTRAINT fk_scheduled_response_delivery_father FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT goal_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.message_log
    ADD CONSTRAINT message_log_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.child(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.quality_time
    ADD CONSTRAINT quality_time_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_father_id_fkey FOREIGN KEY (father_id) REFERENCES public.father(id) ON DELETE CASCADE;
