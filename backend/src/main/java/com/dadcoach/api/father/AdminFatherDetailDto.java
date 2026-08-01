package com.dadcoach.api.father;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full detail DTO for admin father retrieval.
 * <p>
 * Contains the complete father context including internal metadata
 * that is not exposed through the Father API. Used by admin dashboard
 * and support tools for full visibility.
 * <p>
 * Phone numbers are masked (country code + last 2 digits) unless the
 * requesting actor has SUPER_ADMIN role.
 * <p>
 * Fields that are NEVER returned (even to admins):
 * <ul>
 *   <li>Embeddings (vector data)</li>
 *   <li>AI prompts or internal prompt templates</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFatherDetailDto {

    private UUID id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("phone_number")
    private String phoneNumber;

    private String timezone;

    @JsonProperty("coaching_style")
    private String coachingStyle;

    @JsonProperty("preferred_coaching_time")
    private String preferredCoachingTime;

    private String status;

    @JsonProperty("coaching_phase")
    private String coachingPhase;

    @JsonProperty("engagement_score")
    private int engagementScore;

    @JsonProperty("coaching_streak")
    private int coachingStreak;

    @JsonProperty("mission_completion_rate")
    private double missionCompletionRate;

    @JsonProperty("children_count")
    private int childrenCount;

    @JsonProperty("active_goals_count")
    private int activeGoalsCount;

    @JsonProperty("total_conversations")
    private int totalConversations;

    @JsonProperty("total_memories")
    private int totalMemories;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("last_active_at")
    private Instant lastActiveAt;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("internal_metadata")
    private Map<String, Object> internalMetadata;

    public AdminFatherDetailDto() {
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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCoachingStyle() {
        return coachingStyle;
    }

    public void setCoachingStyle(String coachingStyle) {
        this.coachingStyle = coachingStyle;
    }

    public String getPreferredCoachingTime() {
        return preferredCoachingTime;
    }

    public void setPreferredCoachingTime(String preferredCoachingTime) {
        this.preferredCoachingTime = preferredCoachingTime;
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

    public double getMissionCompletionRate() {
        return missionCompletionRate;
    }

    public void setMissionCompletionRate(double missionCompletionRate) {
        this.missionCompletionRate = missionCompletionRate;
    }

    public int getChildrenCount() {
        return childrenCount;
    }

    public void setChildrenCount(int childrenCount) {
        this.childrenCount = childrenCount;
    }

    public int getActiveGoalsCount() {
        return activeGoalsCount;
    }

    public void setActiveGoalsCount(int activeGoalsCount) {
        this.activeGoalsCount = activeGoalsCount;
    }

    public int getTotalConversations() {
        return totalConversations;
    }

    public void setTotalConversations(int totalConversations) {
        this.totalConversations = totalConversations;
    }

    public int getTotalMemories() {
        return totalMemories;
    }

    public void setTotalMemories(int totalMemories) {
        this.totalMemories = totalMemories;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Map<String, Object> getInternalMetadata() {
        return internalMetadata;
    }

    public void setInternalMetadata(Map<String, Object> internalMetadata) {
        this.internalMetadata = internalMetadata;
    }
}
