package com.dadcoach.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO for aggregated analytics data.
 * <p>
 * Contains ONLY aggregated statistics without any individual PII.
 * This is the ONLY response type available to the ANALYTICS role.
 * No individual father IDs, names, phone numbers, or other PII
 * are included in this response.
 * <p>
 * ANALYTICS role users see this data exclusively — they cannot
 * access individual father records or search results containing PII.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregatedAnalyticsDto {

    @JsonProperty("total_fathers")
    private long totalFathers;

    @JsonProperty("active_fathers")
    private long activeFathers;

    @JsonProperty("paused_fathers")
    private long pausedFathers;

    @JsonProperty("churned_fathers")
    private long churnedFathers;

    @JsonProperty("fathers_by_phase")
    private Map<String, Long> fathersByPhase;

    @JsonProperty("fathers_by_status")
    private Map<String, Long> fathersByStatus;

    @JsonProperty("average_engagement_score")
    private double averageEngagementScore;

    @JsonProperty("average_coaching_streak")
    private double averageCoachingStreak;

    @JsonProperty("average_children_count")
    private double averageChildrenCount;

    @JsonProperty("total_active_goals")
    private long totalActiveGoals;

    @JsonProperty("total_active_memories")
    private long totalActiveMemories;

    @JsonProperty("total_conversations_today")
    private long totalConversationsToday;

    public AggregatedAnalyticsDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public long getTotalFathers() {
        return totalFathers;
    }

    public void setTotalFathers(long totalFathers) {
        this.totalFathers = totalFathers;
    }

    public long getActiveFathers() {
        return activeFathers;
    }

    public void setActiveFathers(long activeFathers) {
        this.activeFathers = activeFathers;
    }

    public long getPausedFathers() {
        return pausedFathers;
    }

    public void setPausedFathers(long pausedFathers) {
        this.pausedFathers = pausedFathers;
    }

    public long getChurnedFathers() {
        return churnedFathers;
    }

    public void setChurnedFathers(long churnedFathers) {
        this.churnedFathers = churnedFathers;
    }

    public Map<String, Long> getFathersByPhase() {
        return fathersByPhase;
    }

    public void setFathersByPhase(Map<String, Long> fathersByPhase) {
        this.fathersByPhase = fathersByPhase;
    }

    public Map<String, Long> getFathersByStatus() {
        return fathersByStatus;
    }

    public void setFathersByStatus(Map<String, Long> fathersByStatus) {
        this.fathersByStatus = fathersByStatus;
    }

    public double getAverageEngagementScore() {
        return averageEngagementScore;
    }

    public void setAverageEngagementScore(double averageEngagementScore) {
        this.averageEngagementScore = averageEngagementScore;
    }

    public double getAverageCoachingStreak() {
        return averageCoachingStreak;
    }

    public void setAverageCoachingStreak(double averageCoachingStreak) {
        this.averageCoachingStreak = averageCoachingStreak;
    }

    public double getAverageChildrenCount() {
        return averageChildrenCount;
    }

    public void setAverageChildrenCount(double averageChildrenCount) {
        this.averageChildrenCount = averageChildrenCount;
    }

    public long getTotalActiveGoals() {
        return totalActiveGoals;
    }

    public void setTotalActiveGoals(long totalActiveGoals) {
        this.totalActiveGoals = totalActiveGoals;
    }

    public long getTotalActiveMemories() {
        return totalActiveMemories;
    }

    public void setTotalActiveMemories(long totalActiveMemories) {
        this.totalActiveMemories = totalActiveMemories;
    }

    public long getTotalConversationsToday() {
        return totalConversationsToday;
    }

    public void setTotalConversationsToday(long totalConversationsToday) {
        this.totalConversationsToday = totalConversationsToday;
    }
}
