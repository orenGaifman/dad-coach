package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the celebrations endpoint (GET /api/v1/workspace/growth/celebrations).
 *
 * <p>Returns a list of celebration events for the father, optionally filtered to
 * only undisplayed celebrations.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CelebrationEventsResponse {

    @JsonProperty("celebrations")
    private final List<CelebrationItem> celebrations;

    public CelebrationEventsResponse(List<CelebrationItem> celebrations) {
        this.celebrations = celebrations;
    }

    public List<CelebrationItem> getCelebrations() {
        return celebrations;
    }

    /**
     * Represents an individual celebration event item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CelebrationItem {

        @JsonProperty("event_id")
        private final UUID eventId;

        @JsonProperty("event_type")
        private final String eventType;

        @JsonProperty("title")
        private final String title;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("related_points")
        private final Integer relatedPoints;

        @JsonProperty("celebration_message")
        private final String celebrationMessage;

        @JsonProperty("motivational_prompt")
        private final String motivationalPrompt;

        @JsonProperty("created_at")
        private final Instant createdAt;

        public CelebrationItem(UUID eventId, String eventType, String title,
                               String description, Integer relatedPoints,
                               String celebrationMessage, String motivationalPrompt,
                               Instant createdAt) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.title = title;
            this.description = description;
            this.relatedPoints = relatedPoints;
            this.celebrationMessage = celebrationMessage;
            this.motivationalPrompt = motivationalPrompt;
            this.createdAt = createdAt;
        }

        public UUID getEventId() {
            return eventId;
        }

        public String getEventType() {
            return eventType;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Integer getRelatedPoints() {
            return relatedPoints;
        }

        public String getCelebrationMessage() {
            return celebrationMessage;
        }

        public String getMotivationalPrompt() {
            return motivationalPrompt;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
