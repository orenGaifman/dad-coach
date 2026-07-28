package com.dadcoach.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO for aggregated engagement metrics.
 * <p>
 * Contains ONLY aggregated statistical data without any individual PII.
 * Available to the ANALYTICS role. Shows engagement distributions and
 * trends across the platform.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngagementMetricsDto {

    @JsonProperty("average_engagement_score")
    private double averageEngagementScore;

    @JsonProperty("median_engagement_score")
    private double medianEngagementScore;

    @JsonProperty("engagement_score_distribution")
    private Map<String, Long> engagementScoreDistribution;

    @JsonProperty("average_mission_completion_rate")
    private double averageMissionCompletionRate;

    @JsonProperty("average_coaching_streak")
    private double averageCoachingStreak;

    @JsonProperty("active_users_last_7_days")
    private long activeUsersLast7Days;

    @JsonProperty("active_users_last_30_days")
    private long activeUsersLast30Days;

    @JsonProperty("new_users_last_7_days")
    private long newUsersLast7Days;

    @JsonProperty("churn_rate_last_30_days")
    private double churnRateLast30Days;

    public EngagementMetricsDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public double getAverageEngagementScore() {
        return averageEngagementScore;
    }

    public void setAverageEngagementScore(double averageEngagementScore) {
        this.averageEngagementScore = averageEngagementScore;
    }

    public double getMedianEngagementScore() {
        return medianEngagementScore;
    }

    public void setMedianEngagementScore(double medianEngagementScore) {
        this.medianEngagementScore = medianEngagementScore;
    }

    public Map<String, Long> getEngagementScoreDistribution() {
        return engagementScoreDistribution;
    }

    public void setEngagementScoreDistribution(Map<String, Long> engagementScoreDistribution) {
        this.engagementScoreDistribution = engagementScoreDistribution;
    }

    public double getAverageMissionCompletionRate() {
        return averageMissionCompletionRate;
    }

    public void setAverageMissionCompletionRate(double averageMissionCompletionRate) {
        this.averageMissionCompletionRate = averageMissionCompletionRate;
    }

    public double getAverageCoachingStreak() {
        return averageCoachingStreak;
    }

    public void setAverageCoachingStreak(double averageCoachingStreak) {
        this.averageCoachingStreak = averageCoachingStreak;
    }

    public long getActiveUsersLast7Days() {
        return activeUsersLast7Days;
    }

    public void setActiveUsersLast7Days(long activeUsersLast7Days) {
        this.activeUsersLast7Days = activeUsersLast7Days;
    }

    public long getActiveUsersLast30Days() {
        return activeUsersLast30Days;
    }

    public void setActiveUsersLast30Days(long activeUsersLast30Days) {
        this.activeUsersLast30Days = activeUsersLast30Days;
    }

    public long getNewUsersLast7Days() {
        return newUsersLast7Days;
    }

    public void setNewUsersLast7Days(long newUsersLast7Days) {
        this.newUsersLast7Days = newUsersLast7Days;
    }

    public double getChurnRateLast30Days() {
        return churnRateLast30Days;
    }

    public void setChurnRateLast30Days(double churnRateLast30Days) {
        this.churnRateLast30Days = churnRateLast30Days;
    }
}
