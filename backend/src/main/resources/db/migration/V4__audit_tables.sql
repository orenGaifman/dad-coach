-- State transition audit log (append-only)
CREATE TABLE state_transition_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    from_state VARCHAR(30) NOT NULL,
    to_state VARCHAR(30) NOT NULL,
    trigger_reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_state_log_entity ON state_transition_log(entity_type, entity_id);

-- Engagement event log (append-only)
CREATE TABLE engagement_event (
    id BIGSERIAL PRIMARY KEY,
    father_id BIGINT NOT NULL REFERENCES father(id),
    event_type VARCHAR(30) NOT NULL,
    event_data JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_engagement_event_father_date ON engagement_event(father_id, created_at);
