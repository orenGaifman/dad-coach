-- V14__create_ai_profiles.sql
-- Creates the ai_profiles table required by the AiProfile entity.
-- This table was referenced by SPEC-007 provisioning but its migration was missing.

CREATE TABLE IF NOT EXISTS ai_profiles (
    profile_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL UNIQUE,
    coaching_style      VARCHAR(30) NOT NULL,
    language            VARCHAR(5) NOT NULL,
    children_context    TEXT,
    goals_context       TEXT,
    personality_brief   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_profiles_father ON ai_profiles(father_id);
