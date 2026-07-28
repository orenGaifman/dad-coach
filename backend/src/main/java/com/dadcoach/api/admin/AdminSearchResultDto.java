package com.dadcoach.api.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a single search result for admin father search.
 * <p>
 * Contains enough information for admin users to identify and navigate
 * to specific father records. Phone numbers are masked.
 * <p>
 * This DTO is NOT returned to ANALYTICS role users — they only see
 * aggregated data via {@link AggregatedAnalyticsDto}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminSearchResultDto {

    private UUID id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("phone_number")
    private String phoneNumber;

    private String status;

    @JsonProperty("coaching_phase")
    private String coachingPhase;

    @JsonProperty("engagement_score")
    private int engagementScore;

    @JsonProperty("coaching_streak")
    private int coachingStreak;

    @JsonProperty("children_count")
    private int childrenCount;

    @JsonProperty("last_active_at")
    private Instant lastActiveAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    public AdminSearchResultDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoachingPhase() {
        return coachingPhase;
    }

    public void setCoachingPhase(String coachingPhase) {
        this.coachingPhase = coachingPhase;
    }

    public int getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(int engagementScore) {
        this.engagementScore = engagementScore;
    }

    public int getCoachingStreak() {
        return coachingStreak;
    }

    public void setCoachingStreak(int coachingStreak) {
        this.coachingStreak = coachingStreak;
    }

    public int getChildrenCount() {
        return childrenCount;
    }

    public void setChildrenCount(int childrenCount) {
        this.childrenCount = childrenCount;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
