package com.dadcoach.domain.memory;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.memory.MemoryCategory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JPA entity representing a stored piece of contextual information about a father, child, or interaction.
 * Maps to the "memory" table (V2 migration).
 *
 * <p>Business rules:
 * <ul>
 *   <li>Tier classification (Req 7.2): importance 1-3 → 90 days, 4-6 → 180 days, 7-10 → never expires</li>
 *   <li>Confidence decay on contradiction (Req 7.9): reduce by 0.3, min 0.0</li>
 *   <li>Access tracking (Req 7.10): increment access_count and update last_accessed_at on retrieval</li>
 *   <li>Supersede (Req 7.7): old memory gets superseded_by=new_id, new memory gets confidence 1.0</li>
 *   <li>Capacity (Req 7.11): max 500 active memories per Father</li>
 * </ul>
 */
@Entity(name = "LegacyMemory")
@Table(name = "memory")
public class Memory {

    /** Amount to reduce confidence on contradiction (Requirement 7.9). */
    public static final BigDecimal CONFIDENCE_DECAY_AMOUNT = new BigDecimal("0.30");

    /** Maximum active memories per father (Requirement 7.11). */
    public static final int MAX_ACTIVE_MEMORIES_PER_FATHER = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private Child child;

    @Column(name = "child_id", insertable = false, updatable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40, nullable = false)
    private MemoryCategory category;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "importance_score", nullable = false)
    private int importanceScore;

    @Column(name = "confidence_score", precision = 3, scale = 2, nullable = false)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MemoryStatus status = MemoryStatus.ACTIVE;

    @Column(name = "access_count", nullable = false)
    private int accessCount = 0;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Memory() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new memory with tier-based expiration automatically calculated.
     *
     * @param father          the father this memory belongs to
     * @param category        the memory category
     * @param content         the memory content
     * @param importanceScore importance score (1-10)
     * @param confidenceScore confidence score (0.0-1.0)
     */
    public Memory(Father father, MemoryCategory category, String content,
                  int importanceScore, BigDecimal confidenceScore) {
        this.father = father;
        this.category = category;
        this.content = content;
        this.importanceScore = importanceScore;
        this.confidenceScore = confidenceScore;
        this.createdAt = Instant.now();
        this.expiresAt = calculateExpiration(this.createdAt, importanceScore);
    }

    // ─── Tier Classification ─────────────────────────────────────────────

    /**
     * Returns the memory tier based on importance score.
     */
    public MemoryTier getTier() {
        return MemoryTier.fromImportanceScore(importanceScore);
    }

    /**
     * Calculates the expiration instant based on the creation time and importance score.
     * <ul>
     *   <li>Importance 1-3 (SHORT_TERM): created_at + 90 days</li>
     *   <li>Importance 4-6 (MEDIUM_TERM): created_at + 180 days</li>
     *   <li>Importance 7-10 (LONG_TERM): never expires (null)</li>
     * </ul>
     *
     * @param creationTime    the time the memory was created
     * @param importanceScore the importance score (1-10)
     * @return the expiration instant, or null if the memory never expires
     */
    public static Instant calculateExpiration(Instant creationTime, int importanceScore) {
        MemoryTier tier = MemoryTier.fromImportanceScore(importanceScore);
        if (!tier.expires()) {
            return null;
        }
        return creationTime.plus(tier.getExpirationDays(), ChronoUnit.DAYS);
    }

    // ─── Confidence Decay ────────────────────────────────────────────────

    /**
     * Applies confidence decay due to contradiction detection (Requirement 7.9).
     * Reduces confidence_score by 0.3, with a minimum of 0.0.
     */
    public void applyConfidenceDecay() {
        this.confidenceScore = this.confidenceScore
                .subtract(CONFIDENCE_DECAY_AMOUNT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Access Tracking ─────────────────────────────────────────────────

    /**
     * Records an access to this memory (Requirement 7.10).
     * Increments access_count and updates last_accessed_at.
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
    }

    // ─── Supersede ───────────────────────────────────────────────────────

    /**
     * Marks this memory as superseded by another memory (Requirement 7.7).
     *
     * @param newMemoryId the ID of the new memory that replaces this one
     */
    public void markSuperseded(Long newMemoryId) {
        this.supersededBy = newMemoryId;
        this.status = MemoryStatus.SUPERSEDED;
    }

    // ─── Status Transitions ──────────────────────────────────────────────

    /**
     * Expires this memory.
     */
    public void expire() {
        this.status = MemoryStatus.EXPIRED;
    }

    /**
     * Archives this memory (typically due to capacity enforcement).
     */
    public void archive() {
        this.status = MemoryStatus.ARCHIVED;
    }

    /**
     * Checks whether this memory is currently active.
     */
    public boolean isActive() {
        return this.status == MemoryStatus.ACTIVE;
    }

    /**
     * Computes the combined score used for capacity enforcement ranking.
     * Score = importance_score × confidence_score (Requirement 7.11).
     *
     * @return the combined score as a BigDecimal
     */
    public BigDecimal getCombinedScore() {
        return BigDecimal.valueOf(importanceScore).multiply(confidenceScore);
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Father getFather() {
        return father;
    }

    public void setFather(Father father) {
        this.father = father;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public void setFatherId(Long fatherId) {
        this.fatherId = fatherId;
    }

    public Child getChild() {
        return child;
    }

    public void setChild(Child child) {
        this.child = child;
    }

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long childId) {
        this.childId = childId;
    }

    public MemoryCategory getCategory() {
        return category;
    }

    public void setCategory(MemoryCategory category) {
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

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public void setStatus(MemoryStatus status) {
        this.status = status;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Long getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(Long supersededBy) {
        this.supersededBy = supersededBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
