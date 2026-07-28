package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the goals overview endpoint (GET /api/v1/workspace/goals).
 *
 * <p>Returns a list of goal items with progress metrics and filtering results.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalsOverviewResponse(
        @JsonProperty("goals") List<GoalItem> goals,
        @JsonProperty("total_count") int totalCount
) {

    /**
     * Represents an individual goal item in the overview.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GoalItem(
            @JsonProperty("goal_id") UUID goalId,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("priority") String priority,
            @JsonProperty("progress_percentage") int progressPercentage,
            @JsonProperty("related_child") String relatedChild,
            @JsonProperty("missions_completed_count") int missionsCompletedCount,
            @JsonProperty("missions_remaining_estimate") int missionsRemainingEstimate
    ) {}
}
