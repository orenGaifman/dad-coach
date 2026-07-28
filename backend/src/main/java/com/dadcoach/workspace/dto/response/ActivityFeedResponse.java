package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the activity feed endpoint (GET /api/v1/workspace/activity-feed).
 *
 * <p>Returns a paginated list of feed items with cursor-based pagination metadata.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityFeedResponse {

    @JsonProperty("items")
    private final List<FeedItem> items;

    @JsonProperty("next_cursor")
    private final String nextCursor;

    @JsonProperty("has_more")
    private final boolean hasMore;

    private ActivityFeedResponse(Builder builder) {
        this.items = builder.items;
        this.nextCursor = builder.nextCursor;
        this.hasMore = builder.hasMore;
    }

    public List<FeedItem> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Represents a single item in the activity feed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedItem(
            @JsonProperty("feed_item_id") UUID feedItemId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("related_entity_id") UUID relatedEntityId,
            @JsonProperty("related_entity_type") String relatedEntityType,
            @JsonProperty("event_timestamp") Instant eventTimestamp
    ) {
    }

    public static final class Builder {
        private List<FeedItem> items = List.of();
        private String nextCursor;
        private boolean hasMore;

        private Builder() {
        }

        public Builder items(List<FeedItem> items) {
            this.items = items;
            return this;
        }

        public Builder nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public Builder hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public ActivityFeedResponse build() {
            return new ActivityFeedResponse(this);
        }
    }
}
