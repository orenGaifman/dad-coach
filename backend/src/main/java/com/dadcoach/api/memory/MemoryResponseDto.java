package com.dadcoach.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for memories exposed through the Father API.
 * <p>
 * Security invariants (RESTRICTED fields NEVER included):
 * <ul>
 *   <li>Embeddings — never returned via API</li>
 *   <li>Raw confidence scores — internal/restricted, not exposed to Father API</li>
 *   <li>Internal access tracking metadata — omitted</li>
 * </ul>
 * <p>
 * Only ACTIVE memories are returned through the Father API.
 * SUPERSEDED, EXPIRED, and ARCHIVED memories are excluded from list results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryResponseDto {

    private UUID id;
    private String category;
    private String content;

    @JsonProperty("importance_score")
    private int importanceScore;

    @JsonProperty("child_id")
    private UUID childId;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    private String tier;

    public MemoryResponseDto() {
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public int getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(int importanceScore) {
        this.importanceScore = importanceScore;
    }

    public UUID getChildId() {
        return childId;
    }

    public void setChildId(UUID childId) {
        this.childId = childId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
