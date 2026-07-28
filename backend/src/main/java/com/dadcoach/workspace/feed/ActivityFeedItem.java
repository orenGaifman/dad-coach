package com.dadcoach.workspace.feed;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an item in the father's activity feed.
 * Maps to the "activity_feed_items" table (V8.008).
 *
 * <p>Activity feed items are append-only event records that expire after 90 days.
 * They provide a chronological timeline of significant events for the father.</p>
 */
@Entity
@Table(name = "activity_feed_items")
public class ActivityFeedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "feed_item_id", updatable = false, nullable = false)
    private UUID feedItemId;

    @Column(name = "father_id", nullable = false, updatable = false)
    private UUID fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false, updatable = false)
    private ActivityFeedEventType eventType;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "related_entity_id", updatable = false)
    private UUID relatedEntityId;

    @Column(name = "related_entity_type", length = 50, updatable = false)
    private String relatedEntityType;

    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private String metadata;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected ActivityFeedItem() {
    }

    private ActivityFeedItem(Builder builder) {
        this.feedItemId = builder.feedItemId;
        this.fatherId = builder.fatherId;
        this.eventType = builder.eventType;
        this.title = builder.title;
        this.description = builder.description;
        this.relatedEntityId = builder.relatedEntityId;
        this.relatedEntityType = builder.relatedEntityType;
        this.metadata = builder.metadata;
        this.eventTimestamp = builder.eventTimestamp;
        this.expiresAt = builder.expiresAt;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getFeedItemId() {
        return feedItemId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public ActivityFeedEventType getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getRelatedEntityId() {
        return relatedEntityId;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    // ─── Builder ─────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID feedItemId;
        private UUID fatherId;
        private ActivityFeedEventType eventType;
        private String title;
        private String description;
        private UUID relatedEntityId;
        private String relatedEntityType;
        private String metadata;
        private Instant eventTimestamp;
        private Instant expiresAt;

        private Builder() {
        }

        public Builder feedItemId(UUID feedItemId) {
            this.feedItemId = feedItemId;
            return this;
        }

        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }

        public Builder eventType(ActivityFeedEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder relatedEntityId(UUID relatedEntityId) {
            this.relatedEntityId = relatedEntityId;
            return this;
        }

        public Builder relatedEntityType(String relatedEntityType) {
            this.relatedEntityType = relatedEntityType;
            return this;
        }

        public Builder metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder eventTimestamp(Instant eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public ActivityFeedItem build() {
            if (fatherId == null) {
                throw new IllegalStateException("fatherId is required");
            }
            if (eventType == null) {
                throw new IllegalStateException("eventType is required");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalStateException("title is required");
            }
            if (eventTimestamp == null) {
                eventTimestamp = Instant.now();
            }
            if (expiresAt == null) {
                expiresAt = eventTimestamp.plusSeconds(90L * 24 * 60 * 60); // 90 days
            }
            return new ActivityFeedItem(this);
        }
    }
}
