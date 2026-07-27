-- Extend father table with domain columns
ALTER TABLE father
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN onboarding_state VARCHAR(30) DEFAULT 'NOT_STARTED',
    ADD COLUMN coaching_phase VARCHAR(20) DEFAULT 'FOUNDATION',
    ADD COLUMN coaching_style VARCHAR(20) DEFAULT 'BALANCED',
    ADD COLUMN preferred_coaching_time TIME DEFAULT '08:00',
    ADD COLUMN timezone VARCHAR(64) DEFAULT 'Asia/Jerusalem',
    ADD COLUMN locale VARCHAR(10) DEFAULT 'he',
    ADD COLUMN engagement_score INT DEFAULT 0,
    ADD COLUMN coaching_streak INT DEFAULT 0,
    ADD COLUMN longest_streak INT DEFAULT 0,
    ADD COLUMN activation_date DATE,
    ADD COLUMN last_interaction_at TIMESTAMPTZ,
    ADD COLUMN pause_until DATE,
    ADD COLUMN metadata JSONB DEFAULT '{}';
