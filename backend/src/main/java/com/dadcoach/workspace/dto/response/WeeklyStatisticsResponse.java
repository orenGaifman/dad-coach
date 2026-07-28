package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for the weekly statistics endpoint (GET /api/v1/workspace/statistics/weekly).
 *
 * <p>Provides a summary of the father's activity and growth for a given week.</p>
 */
public class WeeklyStatisticsResponse {

    @JsonProperty("missions_completed")
    private final int missionsCompleted;

    @JsonProperty("conversations_count")
    private final int conversationsCount;

    @JsonProperty("goals_progressed")
    private final int goalsProgressed;

    @JsonProperty("growth_score_delta")
    private final int growthScoreDelta;

    @JsonProperty("streak_days_this_week")
    private final int streakDaysThisWeek;

    @JsonProperty("quality_time_minutes")
    private final int qualityTimeMinutes;

    private WeeklyStatisticsResponse(Builder builder) {
        this.missionsCompleted = builder.missionsCompleted;
        this.conversationsCount = builder.conversationsCount;
        this.goalsProgressed = builder.goalsProgressed;
        this.growthScoreDelta = builder.growthScoreDelta;
        this.streakDaysThisWeek = builder.streakDaysThisWeek;
        this.qualityTimeMinutes = builder.qualityTimeMinutes;
    }

    public int getMissionsCompleted() {
        return missionsCompleted;
    }

    public int getConversationsCount() {
        return conversationsCount;
    }

    public int getGoalsProgressed() {
        return goalsProgressed;
    }

    public int getGrowthScoreDelta() {
        return growthScoreDelta;
    }

    public int getStreakDaysThisWeek() {
        return streakDaysThisWeek;
    }

    public int getQualityTimeMinutes() {
        return qualityTimeMinutes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int missionsCompleted;
        private int conversationsCount;
        private int goalsProgressed;
        private int growthScoreDelta;
        private int streakDaysThisWeek;
        private int qualityTimeMinutes;

        private Builder() {
        }

        public Builder missionsCompleted(int missionsCompleted) {
            this.missionsCompleted = missionsCompleted;
            return this;
        }

        public Builder conversationsCount(int conversationsCount) {
            this.conversationsCount = conversationsCount;
            return this;
        }

        public Builder goalsProgressed(int goalsProgressed) {
            this.goalsProgressed = goalsProgressed;
            return this;
        }

        public Builder growthScoreDelta(int growthScoreDelta) {
            this.growthScoreDelta = growthScoreDelta;
            return this;
        }

        public Builder streakDaysThisWeek(int streakDaysThisWeek) {
            this.streakDaysThisWeek = streakDaysThisWeek;
            return this;
        }

        public Builder qualityTimeMinutes(int qualityTimeMinutes) {
            this.qualityTimeMinutes = qualityTimeMinutes;
            return this;
        }

        public WeeklyStatisticsResponse build() {
            return new WeeklyStatisticsResponse(this);
        }
    }
}
