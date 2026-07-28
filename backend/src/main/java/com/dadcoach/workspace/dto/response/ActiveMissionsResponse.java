package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the active missions endpoint (GET /api/v1/workspace/missions/active).
 *
 * <p>Returns a list of currently active missions with their details.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActiveMissionsResponse(
        @JsonProperty("missions") List<MissionItem> missions,
        @JsonProperty("total_count") int totalCount
) {

    /**
     * Represents an individual active mission item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MissionItem(
            @JsonProperty("mission_id") UUID missionId,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("assigned_child") String assignedChild,
            @JsonProperty("category") String category,
            @JsonProperty("difficulty_level") String difficultyLevel,
            @JsonProperty("assigned_at") Instant assignedAt,
            @JsonProperty("status") String status
    ) {}
}
