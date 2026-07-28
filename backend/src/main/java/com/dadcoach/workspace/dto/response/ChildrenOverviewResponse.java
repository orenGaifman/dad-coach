package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing the children overview for a father's workspace.
 *
 * <p>Contains a list of child items with key metrics: age, active goals,
 * completed missions, recent mission, and interests.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChildrenOverviewResponse(
        List<ChildItem> children,
        @JsonProperty("total_count") int totalCount
) {

    /**
     * Summary item for each child in the overview list.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChildItem(
            @JsonProperty("child_id") UUID childId,
            String name,
            int age,
            @JsonProperty("active_goals_count") int activeGoalsCount,
            @JsonProperty("completed_missions_count") int completedMissionsCount,
            @JsonProperty("recent_mission") RecentMissionItem recentMission,
            List<String> interests
    ) {}

    /**
     * Brief representation of the most recent mission for a child.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentMissionItem(
            @JsonProperty("mission_id") UUID missionId,
            String title,
            String status
    ) {}
}
