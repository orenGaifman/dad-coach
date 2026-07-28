-- V17__create_families_table.sql
-- Creates the families table required by the Family entity.

CREATE TABLE IF NOT EXISTS families (
    family_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL UNIQUE,
    family_name     VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_families_father ON families(father_id);
