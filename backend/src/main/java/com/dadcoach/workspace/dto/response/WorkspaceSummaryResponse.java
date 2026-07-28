package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * DTO representing the workspace summary — the father's dashboard overview.
 *
 * <p>Contains key metrics from multiple sources: father profile, growth system,
 * children, goals, missions, and notifications. Supports partial degradation:
 * fields may be null when their source is unavailable.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkspaceSummaryResponse {

    @JsonProperty("display_name")
    private final String displayName;

    @JsonProperty("coaching_phase")
    private final String coachingPhase;

    @JsonProperty("current_belt")
    private final String currentBelt;

    @JsonProperty("growth_score")
    private final Integer growthScore;

    @JsonProperty("active_children_count")
    private final Integer activeChildrenCount;

    @JsonProperty("active_goals_count")
    private final Integer activeGoalsCount;

    @JsonProperty("current_streak_days")
    private final Integer currentStreakDays;

    @JsonProperty("unread_notifications_count")
    private final Integer unreadNotificationsCount;

    @JsonProperty("active_mission")
    private final ActiveMissionSummary activeMission;

    private WorkspaceSummaryResponse(Builder builder) {
        this.displayName = builder.displayName;
        this.coachingPhase = builder.coachingPhase;
        this.currentBelt = builder.currentBelt;
        this.growthScore = builder.growthScore;
        this.activeChildrenCount = builder.activeChildrenCount;
        this.activeGoalsCount = builder.activeGoalsCount;
        this.currentStreakDays = builder.currentStreakDays;
        this.unreadNotificationsCount = builder.unreadNotificationsCount;
        this.activeMission = builder.activeMission;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getDisplayName() {
        return displayName;
    }

    public String getCoachingPhase() {
        return coachingPhase;
    }

    public String getCurrentBelt() {
        return currentBelt;
    }

    public Integer getGrowthScore() {
        return growthScore;
    }

    public Integer getActiveChildrenCount() {
        return activeChildrenCount;
    }

    public Integer getActiveGoalsCount() {
        return activeGoalsCount;
    }

    public Integer getCurrentStreakDays() {
        return currentStreakDays;
    }

    public Integer getUnreadNotificationsCount() {
        return unreadNotificationsCount;
    }

    public ActiveMissionSummary getActiveMission() {
        return activeMission;
    }

    // Builder

    public static class Builder {
        private String displayName;
        private String coachingPhase;
        private String currentBelt;
        private Integer growthScore;
        private Integer activeChildrenCount;
        private Integer activeGoalsCount;
        private Integer currentStreakDays;
        private Integer unreadNotificationsCount;
        private ActiveMissionSummary activeMission;

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder coachingPhase(String coachingPhase) {
            this.coachingPhase = coachingPhase;
            return this;
        }

        public Builder currentBelt(String currentBelt) {
            this.currentBelt = currentBelt;
            return this;
        }

        public Builder growthScore(Integer growthScore) {
            this.growthScore = growthScore;
            return this;
        }

        public Builder activeChildrenCount(Integer activeChildrenCount) {
            this.activeChildrenCount = activeChildrenCount;
            return this;
        }

        public Builder activeGoalsCount(Integer activeGoalsCount) {
            this.activeGoalsCount = activeGoalsCount;
            return this;
        }

        public Builder currentStreakDays(Integer currentStreakDays) {
            this.currentStreakDays = currentStreakDays;
            return this;
        }

        public Builder unreadNotificationsCount(Integer unreadNotificationsCount) {
            this.unreadNotificationsCount = unreadNotificationsCount;
            return this;
        }

        public Builder activeMission(ActiveMissionSummary activeMission) {
            this.activeMission = activeMission;
            return this;
        }

        public WorkspaceSummaryResponse build() {
            return new WorkspaceSummaryResponse(this);
        }
    }

    /**
     * Nested DTO for the active mission summary shown in the workspace overview.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActiveMissionSummary(
            @JsonProperty("mission_id") UUID missionId,
            String title,
            String status
    ) {}
}
