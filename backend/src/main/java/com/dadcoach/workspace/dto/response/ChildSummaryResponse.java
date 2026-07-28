package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a detailed child summary view.
 *
 * <p>Includes goals, mission history, interests, and an upcoming birthday
 * indicator (true if birthday is within 7 days).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChildSummaryResponse {

    @JsonProperty("child_id")
    private final UUID childId;

    private final String name;

    private final int age;

    @JsonProperty("birth_date")
    private final LocalDate birthDate;

    private final List<String> interests;

    @JsonProperty("active_goals_count")
    private final int activeGoalsCount;

    @JsonProperty("completed_missions_count")
    private final int completedMissionsCount;

    @JsonProperty("recent_mission")
    private final RecentMissionItem recentMission;

    private final List<GoalItem> goals;

    @JsonProperty("mission_history")
    private final List<MissionHistoryItem> missionHistory;

    @JsonProperty("upcoming_birthday")
    private final boolean upcomingBirthday;

    private ChildSummaryResponse(Builder builder) {
        this.childId = builder.childId;
        this.name = builder.name;
        this.age = builder.age;
        this.birthDate = builder.birthDate;
        this.interests = builder.interests;
        this.activeGoalsCount = builder.activeGoalsCount;
        this.completedMissionsCount = builder.completedMissionsCount;
        this.recentMission = builder.recentMission;
        this.goals = builder.goals;
        this.missionHistory = builder.missionHistory;
        this.upcomingBirthday = builder.upcomingBirthday;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public UUID getChildId() {
        return childId;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public List<String> getInterests() {
        return interests;
    }

    public int getActiveGoalsCount() {
        return activeGoalsCount;
    }

    public int getCompletedMissionsCount() {
        return completedMissionsCount;
    }

    public RecentMissionItem getRecentMission() {
        return recentMission;
    }

    public List<GoalItem> getGoals() {
        return goals;
    }

    public List<MissionHistoryItem> getMissionHistory() {
        return missionHistory;
    }

    public boolean isUpcomingBirthday() {
        return upcomingBirthday;
    }

    // Nested DTOs

    /**
     * Brief representation of a recent mission.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentMissionItem(
            @JsonProperty("mission_id") UUID missionId,
            String title,
            String status
    ) {}

    /**
     * Brief representation of a goal.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GoalItem(
            @JsonProperty("goal_id") UUID goalId,
            String title,
            String status
    ) {}

    /**
     * Brief representation of a mission in the history list.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MissionHistoryItem(
            @JsonProperty("mission_id") UUID missionId,
            String title,
            String status,
            @JsonProperty("completed_at") Instant completedAt
    ) {}

    // Builder

    public static class Builder {
        private UUID childId;
        private String name;
        private int age;
        private LocalDate birthDate;
        private List<String> interests;
        private int activeGoalsCount;
        private int completedMissionsCount;
        private RecentMissionItem recentMission;
        private List<GoalItem> goals;
        private List<MissionHistoryItem> missionHistory;
        private boolean upcomingBirthday;

        public Builder childId(UUID childId) {
            this.childId = childId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder birthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder interests(List<String> interests) {
            this.interests = interests;
            return this;
        }

        public Builder activeGoalsCount(int activeGoalsCount) {
            this.activeGoalsCount = activeGoalsCount;
            return this;
        }

        public Builder completedMissionsCount(int completedMissionsCount) {
            this.completedMissionsCount = completedMissionsCount;
            return this;
        }

        public Builder recentMission(RecentMissionItem recentMission) {
            this.recentMission = recentMission;
            return this;
        }

        public Builder goals(List<GoalItem> goals) {
            this.goals = goals;
            return this;
        }

        public Builder missionHistory(List<MissionHistoryItem> missionHistory) {
            this.missionHistory = missionHistory;
            return this;
        }

        public Builder upcomingBirthday(boolean upcomingBirthday) {
            this.upcomingBirthday = upcomingBirthday;
            return this;
        }

        public ChildSummaryResponse build() {
            return new ChildSummaryResponse(this);
        }
    }
}
