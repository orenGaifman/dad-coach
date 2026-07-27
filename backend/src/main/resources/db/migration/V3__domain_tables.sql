-- Child table
CREATE TABLE child (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    name VARCHAR(120) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10),
    interests TEXT[] DEFAULT '{}',
    challenges TEXT[] DEFAULT '{}',
    relationship_quality INT DEFAULT 3 CHECK (relationship_quality BETWEEN 1 AND 5),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Goal table
CREATE TABLE goal (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL,
    priority INT NOT NULL CHECK (priority BETWEEN 1 AND 5),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    progress_percentage INT NOT NULL DEFAULT 0,
    estimated_total_missions INT NOT NULL,
    completed_related_missions INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Habit table
CREATE TABLE habit (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    total_completions INT NOT NULL DEFAULT 0,
    last_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Mission table
CREATE TABLE mission (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    child_id BIGINT NOT NULL REFERENCES child(id),
    goal_id BIGINT REFERENCES goal(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    difficulty INT NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    outcome_rating INT CHECK (outcome_rating BETWEEN 1 AND 5),
    outcome_notes TEXT,
    prompt_version VARCHAR(50),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

-- Memory table
CREATE TABLE memory (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    child_id BIGINT REFERENCES child(id),
    category VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    importance_score INT NOT NULL CHECK (importance_score BETWEEN 1 AND 10),
    confidence_score NUMERIC(3,2) NOT NULL CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    access_count INT NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMPTZ,
    superseded_by BIGINT REFERENCES memory(id),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Conversation table
CREATE TABLE conversation (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    objective TEXT,
    summary TEXT,
    message_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Coaching session (outcome metadata for completed conversations)
CREATE TABLE coaching_session (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversation(id),
    father_id BIGINT NOT NULL REFERENCES father(id),
    outcome VARCHAR(30) NOT NULL,
    model_used VARCHAR(30) NOT NULL,
    total_tokens INT NOT NULL DEFAULT 0,
    context_memories_used BIGINT[] DEFAULT '{}',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notification table
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    type VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    content TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 5,
    scheduled_for TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Reflection table
CREATE TABLE reflection (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    conversation_id BIGINT REFERENCES conversation(id),
    mission_id BIGINT REFERENCES mission(id),
    type VARCHAR(20) NOT NULL,
    emotional_tone VARCHAR(20),
    insights TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Weekly summary table
CREATE TABLE weekly_summary (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    missions_assigned INT NOT NULL DEFAULT 0,
    missions_completed INT NOT NULL DEFAULT 0,
    missions_skipped INT NOT NULL DEFAULT 0,
    engagement_score INT NOT NULL DEFAULT 0,
    coaching_streak INT NOT NULL DEFAULT 0,
    highlights TEXT,
    focus_areas TEXT,
    content TEXT NOT NULL,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(father_id, week_start)
);
