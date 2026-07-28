package com.dadcoach.workspace.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for marking celebration events as displayed.
 *
 * <p>Used by POST /api/v1/workspace/growth/celebrations/mark-displayed</p>
 */
public class MarkCelebrationDisplayedRequest {

    @NotEmpty(message = "event_ids must not be empty")
    @JsonProperty("event_ids")
    private List<UUID> eventIds;

    public MarkCelebrationDisplayedRequest() {
    }

    public MarkCelebrationDisplayedRequest(List<UUID> eventIds) {
        this.eventIds = eventIds;
    }

    public List<UUID> getEventIds() {
        return eventIds;
    }

    public void setEventIds(List<UUID> eventIds) {
        this.eventIds = eventIds;
    }
}
