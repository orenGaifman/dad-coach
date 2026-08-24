package com.dadcoach.memory.dto;

import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.MemoryTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Transfer Object for Memory entities.
 *
 * <p>This DTO represents a memory for API responses and internal service communication.
 * Key design decisions per SPEC-004:
 * <ul>
 *   <li>Excludes the embedding vector (too large for DTOs, 1536 floats)</li>
 *   <li>Includes a computed {@code tier} field derived from importanceScore</li>
 *   <li>Preserves all essential metadata for memory lifecycle management</li>
 * </ul>
 *
 * <p>Tier classification (from importanceScore):
 * <ul>
 *   <li>SHORT_TERM: importance 1-3 (90 days expiration)</li>
 *   <li>MEDIUM_TERM: importance 4-6 (180 days expiration)</li>
 *   <li>LONG_TERM: importance 7-10 (never expires)</li>
 * </ul>
 */
public class MemoryDto {

    // ─── Primary Identifier ──────────────────────────────────────────────

    /**
     * Unique identifier for the memory.
     */
    private UUID id;

    // ─── Relationships ───────────────────────────────────────────────────

    /**
     * The father this memory belongs to.
     */
    private UUID fatherId;

    /**
     * The child this memory is about (nullable for father-only or family memories).
     */
    private UUID childId;

    // ─── Classification ──────────────────────────────────────────────────

    /**
     * The memory category (IDENTITY, RELATIONSHIP, PREFERENCE, etc.).
     */
    private MemoryCategory category;

    /**
     * Who the memory is about (FATHER, CHILD, or FAMILY).
     */
    private MemorySubjectType subjectType;

    // ─── Content ─────────────────────────────────────────────────────────

    /**
     * The memory content (max 500 characters).
     */
    private String content;

    // ─── Scoring ─────────────────────────────────────────────────────────

    /**
     * Importance score (1-10) determining retrieval priority.
     */
    private Integer importanceScore;

    /**
     * Confidence score (0.0-1.0) indicating certainty about the memory's accuracy.
     */
    private BigDecimal confidenceScore;

    /**
     * Computed tier based on importanceScore.
     * This is a derived/computed field, not stored in the entity.
     */
    private MemoryTier tier;

    // ─── Lifecycle State ─────────────────────────────────────────────────

    /**
     * Current lifecycle state (ACTIVE, CONFIRMED, SUPERSEDED, ARCHIVED, EXPIRED, DELETED).
     */
    private MemoryState state;

    // ─── Source Tracking ─────────────────────────────────────────────────

    /**
     * How this memory was created (CONVERSATION_EXTRACTION, ONBOARDING, etc.).
     */
    private MemorySourceType sourceType;

    /**
     * The conversation ID this memory was extracted from (nullable).
     */
    private UUID sourceConversationId;

    // ─── Memory Links ────────────────────────────────────────────────────

    /**
     * Reference to the memory that superseded this one (nullable).
     */
    private UUID supersededBy;

    /**
     * Conflict group identifier for memories with contradictory information (nullable).
     */
    private UUID conflictGroupId;

    /**
     * Reference to an associated goal (for GOAL category memories, nullable).
     */
    private UUID goalId;

    // ─── Event Dates ─────────────────────────────────────────────────────

    /**
     * Event date for EVENT category memories (nullable).
     */
    private LocalDate eventDate;

    /**
     * End date for multi-day events (nullable).
     */
    private LocalDate eventEndDate;

    /**
     * Flag indicating if this is a recurring event.
     */
    private Boolean isRecurring;

    // ─── Counters ────────────────────────────────────────────────────────

    /**
     * Number of times the memory has been confirmed by the father.
     */
    private Integer confirmationCount;

    /**
     * Number of times the memory has been accessed/retrieved.
     */
    private Integer accessCount;

    // ─── Timestamps ──────────────────────────────────────────────────────

    /**
     * When this memory was created.
     */
    private Instant createdAt;

    /**
     * When this memory was last updated.
     */
    private Instant lastUpdatedAt;

    /**
     * When this memory was last confirmed by the father (nullable).
     */
    private Instant lastConfirmedAt;

    /**
     * When this memory was last accessed/retrieved (nullable).
     */
    private Instant lastAccessedAt;

    /**
     * When this memory expires (nullable for long-term tier memories).
     */
    private Instant expiresAt;

    // ─── Constructors ────────────────────────────────────────────────────

    /**
     * Default constructor for serialization/deserialization.
     */
    public MemoryDto() {
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

    public MemoryCategory getCategory() {
        return category;
    }

    public void setCategory(MemoryCategory category) {
        this.category = category;
    }

    public MemorySubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(MemorySubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Integer importanceScore) {
        this.importanceScore = importanceScore;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public MemoryTier getTier() {
        return tier;
    }

    public void setTier(MemoryTier tier) {
        this.tier = tier;
    }

    public MemoryState getState() {
        return state;
    }

    public void setState(MemoryState state) {
        this.state = state;
    }

    public MemorySourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(MemorySourceType sourceType) {
        this.sourceType = sourceType;
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

    public UUID getConflictGroupId() {
        return conflictGroupId;
    }

    public void setConflictGroupId(UUID conflictGroupId) {
        this.conflictGroupId = conflictGroupId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public void setGoalId(UUID goalId) {
        this.goalId = goalId;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalDate getEventEndDate() {
        return eventEndDate;
    }

    public void setEventEndDate(LocalDate eventEndDate) {
        this.eventEndDate = eventEndDate;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    public void setIsRecurring(Boolean recurring) {
        isRecurring = recurring;
    }

    public Integer getConfirmationCount() {
        return confirmationCount;
    }

    public void setConfirmationCount(Integer confirmationCount) {
        this.confirmationCount = confirmationCount;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Instant getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public void setLastConfirmedAt(Instant lastConfirmedAt) {
        this.lastConfirmedAt = lastConfirmedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
