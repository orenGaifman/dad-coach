-- ============================================================================
-- V8.008: Activity Feed Items
-- ============================================================================
-- Table:
--   activity_feed_items — chronological timeline of father activity events
--
-- Requirements: 6.3, 6.5
--
-- Note: father_id column uses UUID but does NOT have a FK constraint to the
-- existing 'father' table (which uses BIGSERIAL). The FK will be added
-- when the father table is migrated to UUID primary keys.

-- ============================================================================
-- 1. activity_feed_items — stores projected domain events for the activity feed
-- ============================================================================
CREATE TABLE activity_feed_items (
    feed_item_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    father_id           UUID NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    related_entity_id   UUID,
    related_entity_type VARCHAR(50),
    metadata            JSONB,
    event_timestamp     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() + INTERVAL '90 days')
);

-- Index for cursor-based pagination feed queries (father's feed ordered by time)
CREATE INDEX idx_feed_items_father_time ON activity_feed_items(father_id, event_timestamp DESC);

-- Index for purge job to find expired items efficiently
CREATE INDEX idx_feed_items_expires ON activity_feed_items(expires_at);
