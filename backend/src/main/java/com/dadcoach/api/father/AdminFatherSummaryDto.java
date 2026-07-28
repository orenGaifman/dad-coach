package com.dadcoach.api.father;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for admin father listing.
 * <p>
 * Contains key identifying information for admin search/list results.
 * Phone numbers are masked (country code + last 2 digits) unless the
 * requesting actor has SUPER_ADMIN role.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFatherSummaryDto {

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

    @JsonProperty("created_at")
    private Instant createdAt;

    public AdminFatherSummaryDto() {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
