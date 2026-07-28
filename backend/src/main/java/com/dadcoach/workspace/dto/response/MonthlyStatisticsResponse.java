package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for the monthly statistics endpoint (GET /api/v1/workspace/statistics/monthly).
 *
 * <p>Provides a comprehensive summary of the father's activity and growth for a given month.</p>
 */
public class MonthlyStatisticsResponse {

    @JsonProperty("missions_completed")
    private final int missionsCompleted;

    @JsonProperty("goals_completed")
    private final int goalsCompleted;

    @JsonProperty("conversations_count")
    private final int conversationsCount;

    @JsonProperty("average_daily_engagement")
    private final double averageDailyEngagement;

    @JsonProperty("growth_score_start")
    private final int growthScoreStart;

    @JsonProperty("growth_score_end")
    private final int growthScoreEnd;

    @JsonProperty("achievements_earned")
    private final int achievementsEarned;

    @JsonProperty("longest_streak_in_month")
    private final int longestStreakInMonth;

    private MonthlyStatisticsResponse(Builder builder) {
        this.missionsCompleted = builder.missionsCompleted;
        this.goalsCompleted = builder.goalsCompleted;
        this.conversationsCount = builder.conversationsCount;
        this.averageDailyEngagement = builder.averageDailyEngagement;
        this.growthScoreStart = builder.growthScoreStart;
        this.growthScoreEnd = builder.growthScoreEnd;
        this.achievementsEarned = builder.achievementsEarned;
        this.longestStreakInMonth = builder.longestStreakInMonth;
    }

    public int getMissionsCompleted() {
        return missionsCompleted;
    }

    public int getGoalsCompleted() {
        return goalsCompleted;
    }

    public int getConversationsCount() {
        return conversationsCount;
    }

    public double getAverageDailyEngagement() {
        return averageDailyEngagement;
    }

    public int getGrowthScoreStart() {
        return growthScoreStart;
    }

    public int getGrowthScoreEnd() {
        return growthScoreEnd;
    }

    public int getAchievementsEarned() {
        return achievementsEarned;
    }

    public int getLongestStreakInMonth() {
        return longestStreakInMonth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int missionsCompleted;
        private int goalsCompleted;
        private int conversationsCount;
        private double averageDailyEngagement;
        private int growthScoreStart;
        private int growthScoreEnd;
        private int achievementsEarned;
        private int longestStreakInMonth;

        private Builder() {
        }

        public Builder missionsCompleted(int missionsCompleted) {
            this.missionsCompleted = missionsCompleted;
            return this;
        }

        public Builder goalsCompleted(int goalsCompleted) {
            this.goalsCompleted = goalsCompleted;
            return this;
        }

        public Builder conversationsCount(int conversationsCount) {
            this.conversationsCount = conversationsCount;
            return this;
        }

        public Builder averageDailyEngagement(double averageDailyEngagement) {
            this.averageDailyEngagement = averageDailyEngagement;
            return this;
        }

        public Builder growthScoreStart(int growthScoreStart) {
            this.growthScoreStart = growthScoreStart;
            return this;
        }

        public Builder growthScoreEnd(int growthScoreEnd) {
            this.growthScoreEnd = growthScoreEnd;
            return this;
        }

        public Builder achievementsEarned(int achievementsEarned) {
            this.achievementsEarned = achievementsEarned;
            return this;
        }

        public Builder longestStreakInMonth(int longestStreakInMonth) {
            this.longestStreakInMonth = longestStreakInMonth;
            return this;
        }

        public MonthlyStatisticsResponse build() {
            return new MonthlyStatisticsResponse(this);
        }
    }
}
