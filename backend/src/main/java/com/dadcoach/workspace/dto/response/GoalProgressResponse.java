package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the goal progress endpoint (GET /api/v1/workspace/goals/{goalId}/progress).
 *
 * <p>Returns detailed goal information including related missions, milestones,
 * and suggested next steps.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalProgressResponse {

    @JsonProperty("goal_id")
    private final UUID goalId;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("category")
    private final String category;

    @JsonProperty("priority")
    private final String priority;

    @JsonProperty("progress_percentage")
    private final int progressPercentage;

    @JsonProperty("related_child")
    private final String relatedChild;

    @JsonProperty("missions_completed_count")
    private final int missionsCompletedCount;

    @JsonProperty("missions_remaining_estimate")
    private final int missionsRemainingEstimate;

    @JsonProperty("missions")
    private final List<MissionItem> missions;

    @JsonProperty("milestones")
    private final List<String> milestones;

    @JsonProperty("suggested_next_steps")
    private final List<String> suggestedNextSteps;

    private GoalProgressResponse(Builder builder) {
        this.goalId = builder.goalId;
        this.description = builder.description;
        this.category = builder.category;
        this.priority = builder.priority;
        this.progressPercentage = builder.progressPercentage;
        this.relatedChild = builder.relatedChild;
        this.missionsCompletedCount = builder.missionsCompletedCount;
        this.missionsRemainingEstimate = builder.missionsRemainingEstimate;
        this.missions = builder.missions;
        this.milestones = builder.milestones;
        this.suggestedNextSteps = builder.suggestedNextSteps;
    }

    public UUID getGoalId() { return goalId; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public int getProgressPercentage() { return progressPercentage; }
    public String getRelatedChild() { return relatedChild; }
    public int getMissionsCompletedCount() { return missionsCompletedCount; }
    public int getMissionsRemainingEstimate() { return missionsRemainingEstimate; }
    public List<MissionItem> getMissions() { return missions; }
    public List<String> getMilestones() { return milestones; }
    public List<String> getSuggestedNextSteps() { return suggestedNextSteps; }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A mission item within a goal progress response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MissionItem(
            @JsonProperty("mission_id") UUID missionId,
            @JsonProperty("title") String title,
            @JsonProperty("status") String status,
            @JsonProperty("completed_at") Instant completedAt
    ) {}

    public static final class Builder {
        private UUID goalId;
        private String description;
        private String category;
        private String priority;
        private int progressPercentage;
        private String relatedChild;
        private int missionsCompletedCount;
        private int missionsRemainingEstimate;
        private List<MissionItem> missions = List.of();
        private List<String> milestones = List.of();
        private List<String> suggestedNextSteps = List.of();

        private Builder() {}

        public Builder goalId(UUID goalId) { this.goalId = goalId; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder priority(String priority) { this.priority = priority; return this; }
        public Builder progressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; return this; }
        public Builder relatedChild(String relatedChild) { this.relatedChild = relatedChild; return this; }
        public Builder missionsCompletedCount(int missionsCompletedCount) { this.missionsCompletedCount = missionsCompletedCount; return this; }
        public Builder missionsRemainingEstimate(int missionsRemainingEstimate) { this.missionsRemainingEstimate = missionsRemainingEstimate; return this; }
        public Builder missions(List<MissionItem> missions) { this.missions = missions; return this; }
        public Builder milestones(List<String> milestones) { this.milestones = milestones; return this; }
        public Builder suggestedNextSteps(List<String> suggestedNextSteps) { this.suggestedNextSteps = suggestedNextSteps; return this; }

        public GoalProgressResponse build() {
            return new GoalProgressResponse(this);
        }
    }
}
