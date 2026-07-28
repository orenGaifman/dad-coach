-- V8.009__create_statistics_aggregates.sql
-- Father Workspace: Statistics Aggregates table (SPEC-008)
--
-- Creates:
--   1. statistics_aggregates — Pre-computed statistics per father per period
--
-- Requirements: 8.1, 8.2
--
-- Note: father_id column uses UUID but does NOT have FK constraint to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. statistics_aggregates — pre-computed statistics per father per period
-- ============================================================================
CREATE TABLE statistics_aggregates (
    aggregate_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id       UUID NOT NULL,
    period_type     VARCHAR(20) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    data            JSONB NOT NULL,
    computed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_statistics_period_type CHECK (
        period_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
    ),
    CONSTRAINT uq_father_period UNIQUE (father_id, period_type, period_start)
);

-- Index on (father_id, period_type) for lookups by father and period type
CREATE INDEX idx_statistics_aggregates_father_period
    ON statistics_aggregates(father_id, period_type);
