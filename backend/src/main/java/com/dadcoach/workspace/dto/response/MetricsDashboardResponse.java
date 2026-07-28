package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for the metrics dashboard endpoint (GET /api/v1/workspace/metrics).
 *
 * <p>Provides high-level engagement and progress metrics for the father's workspace.</p>
 */
public class MetricsDashboardResponse {

    @JsonProperty("engagement_score")
    private final double engagementScore;

    @JsonProperty("quality_time_total")
    private final int qualityTimeTotal;

    @JsonProperty("completion_rate")
    private final double completionRate;

    @JsonProperty("week_over_week_growth")
    private final double weekOverWeekGrowth;

    private MetricsDashboardResponse(Builder builder) {
        this.engagementScore = builder.engagementScore;
        this.qualityTimeTotal = builder.qualityTimeTotal;
        this.completionRate = builder.completionRate;
        this.weekOverWeekGrowth = builder.weekOverWeekGrowth;
    }

    public double getEngagementScore() {
        return engagementScore;
    }

    public int getQualityTimeTotal() {
        return qualityTimeTotal;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public double getWeekOverWeekGrowth() {
        return weekOverWeekGrowth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double engagementScore;
        private int qualityTimeTotal;
        private double completionRate;
        private double weekOverWeekGrowth;

        private Builder() {
        }

        public Builder engagementScore(double engagementScore) {
            this.engagementScore = engagementScore;
            return this;
        }

        public Builder qualityTimeTotal(int qualityTimeTotal) {
            this.qualityTimeTotal = qualityTimeTotal;
            return this;
        }

        public Builder completionRate(double completionRate) {
            this.completionRate = completionRate;
            return this;
        }

        public Builder weekOverWeekGrowth(double weekOverWeekGrowth) {
            this.weekOverWeekGrowth = weekOverWeekGrowth;
            return this;
        }

        public MetricsDashboardResponse build() {
            return new MetricsDashboardResponse(this);
        }
    }
}
