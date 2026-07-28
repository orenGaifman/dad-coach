package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for the growth score breakdown endpoint (GET /api/v1/workspace/growth/score).
 *
 * <p>Returns the father's total growth score, score breakdown by signal type,
 * signal counts for this week and month, and a list of recent signals.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrowthScoreBreakdownResponse {

    @JsonProperty("total_score")
    private final int totalScore;

    @JsonProperty("score_by_signal_type")
    private final Map<String, Integer> scoreBySignalType;

    @JsonProperty("signals_this_week")
    private final int signalsThisWeek;

    @JsonProperty("signals_this_month")
    private final int signalsThisMonth;

    @JsonProperty("recent_signals")
    private final List<RecentSignalItem> recentSignals;

    private GrowthScoreBreakdownResponse(Builder builder) {
        this.totalScore = builder.totalScore;
        this.scoreBySignalType = builder.scoreBySignalType;
        this.signalsThisWeek = builder.signalsThisWeek;
        this.signalsThisMonth = builder.signalsThisMonth;
        this.recentSignals = builder.recentSignals;
    }

    public int getTotalScore() { return totalScore; }
    public Map<String, Integer> getScoreBySignalType() { return scoreBySignalType; }
    public int getSignalsThisWeek() { return signalsThisWeek; }
    public int getSignalsThisMonth() { return signalsThisMonth; }
    public List<RecentSignalItem> getRecentSignals() { return recentSignals; }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Represents a recent growth signal item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentSignalItem(
            @JsonProperty("signal_id") UUID signalId,
            @JsonProperty("signal_type") String signalType,
            @JsonProperty("points_awarded") int pointsAwarded,
            @JsonProperty("source_entity_type") String sourceEntityType,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    public static final class Builder {
        private int totalScore;
        private Map<String, Integer> scoreBySignalType = Map.of();
        private int signalsThisWeek;
        private int signalsThisMonth;
        private List<RecentSignalItem> recentSignals = List.of();

        private Builder() {}

        public Builder totalScore(int totalScore) { this.totalScore = totalScore; return this; }
        public Builder scoreBySignalType(Map<String, Integer> scoreBySignalType) { this.scoreBySignalType = scoreBySignalType; return this; }
        public Builder signalsThisWeek(int signalsThisWeek) { this.signalsThisWeek = signalsThisWeek; return this; }
        public Builder signalsThisMonth(int signalsThisMonth) { this.signalsThisMonth = signalsThisMonth; return this; }
        public Builder recentSignals(List<RecentSignalItem> recentSignals) { this.recentSignals = recentSignals; return this; }

        public GrowthScoreBreakdownResponse build() {
            return new GrowthScoreBreakdownResponse(this);
        }
    }
}
