package com.dadcoach.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin memory DTO with full visibility into memory state and metadata.
 * <p>
 * Compared to the Father API's {@link MemoryResponseDto}, this DTO includes:
 * <ul>
 *   <li>Memory state (ACTIVE, ARCHIVED, SUPERSEDED, EXPIRED)</li>
 *   <li>Confidence score (internal metric visible to admins)</li>
 *   <li>Source information (which conversation extracted this memory)</li>
 *   <li>Superseded-by reference (if replaced by a newer memory)</li>
 *   <li>Archived/expired timestamps</li>
 * </ul>
 * <p>
 * Fields that are NEVER returned (even to admins):
 * <ul>
 *   <li>Embeddings (vector data) — RESTRICTED</li>
 *   <li>AI prompts used during extraction — RESTRICTED</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminMemoryDto {

    private UUID id;

    @JsonProperty("father_id")
    private UUID fatherId;

    @JsonProperty("child_id")
    private UUID childId;

    private String category;
    private String content;
    private String state;
    private String tier;

    @JsonProperty("importance_score")
    private int importanceScore;

    @JsonProperty("confidence_score")
    private double confidenceScore;

    @JsonProperty("source_conversation_id")
    private UUID sourceConversationId;

    @JsonProperty("superseded_by")
    private UUID supersededBy;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("archived_at")
    private Instant archivedAt;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    public AdminMemoryDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public void setFatherId(UUID fatherId) {
        this.fatherId = fatherId;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public int getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(int importanceScore) {
        this.importanceScore = importanceScore;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public UUID getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(UUID sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(UUID supersededBy) {
        this.supersededBy = supersededBy;
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

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
