-- V12__create_preference_tables.sql
-- User Onboarding & Activation preference tables for SPEC-007
--
-- Creates:
--   1. language_preferences         - Language and locale settings per father
--   2. communication_preferences    - Coaching time, frequency, and quiet hours per father
--
-- Note: father_id columns use UUID but do NOT have FK constraints to the
-- existing tables. The FK will be added when entity relationships are finalized.

-- ============================================================================
-- 1. language_preferences table
-- ============================================================================
CREATE TABLE language_preferences (
    preference_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL UNIQUE,
    language_code       VARCHAR(5) NOT NULL DEFAULT 'he',
    date_format         VARCHAR(20) NOT NULL DEFAULT 'dd/MM/yyyy',
    time_format         VARCHAR(20) NOT NULL DEFAULT 'HH:mm',
    text_direction      VARCHAR(3) NOT NULL DEFAULT 'RTL',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_lang_direction CHECK (text_direction IN ('RTL', 'LTR')),
    CONSTRAINT chk_lang_code CHECK (language_code IN ('he', 'en'))
);

-- ============================================================================
-- 2. communication_preferences table
-- ============================================================================
CREATE TABLE communication_preferences (
    preference_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id               UUID NOT NULL UNIQUE,
    preferred_coaching_time TIME NOT NULL DEFAULT '08:00',
    notification_frequency  VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    quiet_hours_start       TIME NOT NULL DEFAULT '21:00',
    quiet_hours_end         TIME NOT NULL DEFAULT '07:00',
    email_notifications     BOOLEAN NOT NULL DEFAULT true,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_freq CHECK (notification_frequency IN ('DAILY', 'EVERY_OTHER_DAY', 'TWICE_WEEKLY'))
);
