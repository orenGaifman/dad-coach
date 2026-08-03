package com.dadcoach.workflow.dto;

import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for the GET /api/v1/workspace/summary endpoint.
 * 
 * <p>Provides a complete dashboard overview for the Father Workspace (WEB-SPEC-008).
 * Contains father information, workflow state, belt progression, streaks, 
 * quality time history, achievements, and next milestone.</p>
 * 
 * <p>Uses Java records for immutability and Jackson annotations for snake_case JSON naming.</p>
 * 
 * <p>Implements Requirements 8.2 and 14.1 from the deterministic-workflow-engine spec.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceSummaryDto(
        /**
         * The father's display name.
         */
        @JsonProperty("father_display_name")
        String fatherDisplayName,

        /**
         * The father's current workflow state.
         */
        @JsonProperty("current_workflow_state")
        WorkflowState currentWorkflowState,

        /**
         * The father's current belt level.
         */
        @JsonProperty("current_belt")
        Belt currentBelt,

        /**
         * Progress toward the next belt.
         */
        @JsonProperty("belt_progress")
        BeltProgressDto beltProgress,

        /**
         * Current streak of consecutive Quality Times.
         */
        @JsonProperty("current_streak")
        int currentStreak,

        /**
         * Longest streak ever achieved.
         */
        @JsonProperty("longest_streak")
        int longestStreak,

        /**
         * Total number of Quality Times completed.
         */
        @JsonProperty("total_quality_times_completed")
        int totalQualityTimesCompleted,

        /**
         * Progress toward the weekly Quality Time goal.
         */
        @JsonProperty("weekly_goal_progress")
        WeeklyGoalProgressDto weeklyGoalProgress,

        /**
         * The next scheduled Quality Time, or null if none scheduled.
         */
        @JsonProperty("next_quality_time")
        QualityTimeSummaryDto nextQualityTime,

        /**
         * List of recent completed Quality Times.
         */
        @JsonProperty("recent_quality_times")
        List<RecentQualityTimeDto> recentQualityTimes,

        /**
         * List of recent achievements earned.
         */
        @JsonProperty("recent_achievements")
        List<AchievementDto> recentAchievements,

        /**
         * The next milestone the father is working toward.
         */
        @JsonProperty("next_milestone")
        MilestoneDto nextMilestone
) {

    /**
     * DTO for belt progression information.
     * 
     * <p>Shows current completion count, threshold for next belt, and progress percentage.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BeltProgressDto(
            /**
             * Current count of completed Quality Times.
             */
            @JsonProperty("current_count")
            int currentCount,

            /**
             * Number of Quality Times needed to reach the next belt.
             * Null if at BLACK belt (maximum level).
             */
            @JsonProperty("next_belt_threshold")
            Integer nextBeltThreshold,

            /**
             * Progress percentage toward the next belt (0-100).
             * 100 if at BLACK belt (maximum level).
             */
            @JsonProperty("progress_percentage")
            int progressPercentage
    ) {
        /**
         * Creates a BeltProgressDto from completion count and current belt.
         *
         * @param completionCount the total number of Quality Times completed
         * @param currentBelt the father's current belt level
         * @return a new BeltProgressDto
         */
        public static BeltProgressDto from(int completionCount, Belt currentBelt) {
            Belt nextBelt = currentBelt.getNextBelt();
            
            if (nextBelt == null) {
                // At BLACK belt - maximum level
                return new BeltProgressDto(completionCount, null, 100);
            }
            
            int nextThreshold = nextBelt.getMinCompletions();
            int currentMin = currentBelt.getMinCompletions();
            int range = nextThreshold - currentMin;
            int progress = completionCount - currentMin;
            int percentage = range > 0 ? Math.min(100, (progress * 100) / range) : 100;
            
            return new BeltProgressDto(completionCount, nextThreshold, percentage);
        }
    }

    /**
     * DTO for weekly goal progress.
     * 
     * <p>Shows hours completed, goal hours, and progress percentage.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WeeklyGoalProgressDto(
            /**
             * Hours of Quality Time completed this week.
             */
            @JsonProperty("completed_hours")
            double completedHours,

            /**
             * Target hours for the week.
             */
            @JsonProperty("goal_hours")
            double goalHours,

            /**
             * Progress percentage toward the weekly goal (0-100).
             */
            @JsonProperty("progress_percentage")
            int progressPercentage
    ) {
        /**
         * Creates a WeeklyGoalProgressDto from completed and goal hours.
         *
         * @param completedHours hours of Quality Time completed this week
         * @param goalHours target hours for the week
         * @return a new WeeklyGoalProgressDto
         */
        public static WeeklyGoalProgressDto from(double completedHours, double goalHours) {
            int percentage = goalHours > 0 
                    ? Math.min(100, (int) ((completedHours * 100) / goalHours)) 
                    : 0;
            return new WeeklyGoalProgressDto(completedHours, goalHours, percentage);
        }
    }

    /**
     * DTO for the next scheduled Quality Time.
     * 
     * <p>Contains essential scheduling information for display.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QualityTimeSummaryDto(
            /**
             * The Quality Time ID.
             */
            @JsonProperty("id")
            UUID id,

            /**
             * The name of the child this Quality Time is with.
             */
            @JsonProperty("child_name")
            String childName,

            /**
             * The scheduled start time.
             */
            @JsonProperty("scheduled_start")
            Instant scheduledStart,

            /**
             * The scheduled end time.
             */
            @JsonProperty("scheduled_end")
            Instant scheduledEnd,

            /**
             * The status of the Quality Time.
             */
            @JsonProperty("status")
            QualityTimeStatus status
    ) {}

    /**
     * DTO for a recently completed Quality Time.
     * 
     * <p>Compact representation for history list display.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentQualityTimeDto(
            /**
             * The Quality Time ID.
             */
            @JsonProperty("id")
            UUID id,

            /**
             * The name of the child this Quality Time was with.
             */
            @JsonProperty("child_name")
            String childName,

            /**
             * When the Quality Time was completed.
             */
            @JsonProperty("completed_at")
            Instant completedAt,

            /**
             * Duration of the Quality Time in minutes.
             */
            @JsonProperty("duration_minutes")
            int durationMinutes
    ) {}

    /**
     * DTO for an achievement.
     * 
     * <p>Represents a gamification milestone the father has earned.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AchievementDto(
            /**
             * The unique identifier for this achievement type.
             */
            @JsonProperty("achievement_id")
            String achievementId,

            /**
             * The display name of the achievement.
             */
            @JsonProperty("name")
            String name,

            /**
             * When the achievement was earned.
             */
            @JsonProperty("earned_at")
            Instant earnedAt
    ) {}

    /**
     * DTO for the next milestone.
     * 
     * <p>Shows what the father is working toward next.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MilestoneDto(
            /**
             * The name of the milestone (e.g., "Blue Belt").
             */
            @JsonProperty("name")
            String name,

            /**
             * Number of Quality Times remaining to reach this milestone.
             */
            @JsonProperty("quality_times_remaining")
            int qualityTimesRemaining
    ) {
        /**
         * Creates a MilestoneDto for the next belt.
         *
         * @param currentBelt the father's current belt
         * @param completionCount the total number of Quality Times completed
         * @return a new MilestoneDto, or null if at BLACK belt
         */
        public static MilestoneDto forNextBelt(Belt currentBelt, int completionCount) {
            Belt nextBelt = currentBelt.getNextBelt();
            if (nextBelt == null) {
                return null; // Already at BLACK belt
            }
            int remaining = nextBelt.getMinCompletions() - completionCount;
            return new MilestoneDto(nextBelt.getDisplayName(), Math.max(0, remaining));
        }
    }

    /**
     * Builder for creating WorkspaceSummaryDto instances.
     * 
     * <p>Provides a fluent API for constructing the DTO.</p>
     */
    public static class Builder {
        private String fatherDisplayName;
        private WorkflowState currentWorkflowState;
        private Belt currentBelt;
        private BeltProgressDto beltProgress;
        private int currentStreak;
        private int longestStreak;
        private int totalQualityTimesCompleted;
        private WeeklyGoalProgressDto weeklyGoalProgress;
        private QualityTimeSummaryDto nextQualityTime;
        private List<RecentQualityTimeDto> recentQualityTimes;
        private List<AchievementDto> recentAchievements;
        private MilestoneDto nextMilestone;

        public Builder fatherDisplayName(String fatherDisplayName) {
            this.fatherDisplayName = fatherDisplayName;
            return this;
        }

        public Builder currentWorkflowState(WorkflowState currentWorkflowState) {
            this.currentWorkflowState = currentWorkflowState;
            return this;
        }

        public Builder currentBelt(Belt currentBelt) {
            this.currentBelt = currentBelt;
            return this;
        }

        public Builder beltProgress(BeltProgressDto beltProgress) {
            this.beltProgress = beltProgress;
            return this;
        }

        public Builder currentStreak(int currentStreak) {
            this.currentStreak = currentStreak;
            return this;
        }

        public Builder longestStreak(int longestStreak) {
            this.longestStreak = longestStreak;
            return this;
        }

        public Builder totalQualityTimesCompleted(int totalQualityTimesCompleted) {
            this.totalQualityTimesCompleted = totalQualityTimesCompleted;
            return this;
        }

        public Builder weeklyGoalProgress(WeeklyGoalProgressDto weeklyGoalProgress) {
            this.weeklyGoalProgress = weeklyGoalProgress;
            return this;
        }

        public Builder nextQualityTime(QualityTimeSummaryDto nextQualityTime) {
            this.nextQualityTime = nextQualityTime;
            return this;
        }

        public Builder recentQualityTimes(List<RecentQualityTimeDto> recentQualityTimes) {
            this.recentQualityTimes = recentQualityTimes;
            return this;
        }

        public Builder recentAchievements(List<AchievementDto> recentAchievements) {
            this.recentAchievements = recentAchievements;
            return this;
        }

        public Builder nextMilestone(MilestoneDto nextMilestone) {
            this.nextMilestone = nextMilestone;
            return this;
        }

        public WorkspaceSummaryDto build() {
            return new WorkspaceSummaryDto(
                    fatherDisplayName,
                    currentWorkflowState,
                    currentBelt,
                    beltProgress,
                    currentStreak,
                    longestStreak,
                    totalQualityTimesCompleted,
                    weeklyGoalProgress,
                    nextQualityTime,
                    recentQualityTimes,
                    recentAchievements,
                    nextMilestone
            );
        }
    }

    /**
     * Creates a new Builder instance.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
