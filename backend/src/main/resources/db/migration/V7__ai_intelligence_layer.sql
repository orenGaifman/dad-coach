-- AI Intelligence Layer tables for telemetry, daily summaries, and prompt versioning

-- AI Telemetry: tracks every AI call with full observability
CREATE TABLE ai_telemetry (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id            UUID NOT NULL,
    father_id             UUID NOT NULL,
    conversation_id       UUID,
    conversation_type     VARCHAR(30),
    interaction_type      VARCHAR(30) NOT NULL,
    prompt_version        VARCHAR(20),
    model_provider        VARCHAR(20) NOT NULL,
    model_name            VARCHAR(50) NOT NULL,
    temperature           REAL,
    input_tokens          INTEGER NOT NULL,
    output_tokens         INTEGER NOT NULL,
    estimated_cost_usd    REAL,
    total_latency_ms      INTEGER NOT NULL,
    llm_latency_ms        INTEGER,
    validation_passed     BOOLEAN NOT NULL,
    fallback_used         BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count           INTEGER NOT NULL DEFAULT 0,
    quality_score         REAL,
    safety_classification VARCHAR(30),
    ab_test_group         VARCHAR(5),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes on ai_telemetry for common query patterns
CREATE INDEX idx_ai_telemetry_father ON ai_telemetry(father_id, created_at DESC);
CREATE INDEX idx_ai_telemetry_model ON ai_telemetry(model_name, created_at DESC);

-- AI Daily Summary: materialized daily aggregates per father (refreshed by scheduler)
CREATE TABLE ai_daily_summary (
    father_id           UUID NOT NULL,
    date                DATE NOT NULL,
    total_calls         INTEGER NOT NULL DEFAULT 0,
    total_input_tokens  INTEGER NOT NULL DEFAULT 0,
    total_output_tokens INTEGER NOT NULL DEFAULT 0,
    total_cost_usd      REAL NOT NULL DEFAULT 0,
    fallback_count      INTEGER NOT NULL DEFAULT 0,
    average_quality     REAL,
    PRIMARY KEY (father_id, date)
);

-- Prompt Versions: versioned prompt templates with A/B test support
CREATE TABLE prompt_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_type     VARCHAR(30) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    ab_test_group   VARCHAR(5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(prompt_type, version)
);
