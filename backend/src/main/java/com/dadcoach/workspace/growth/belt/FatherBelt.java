package com.dadcoach.workspace.growth.belt;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the "father_belts" table (V8.002).
 *
 * <p>Tracks a father's current belt level and cached growth score.
 * The {@code currentScore} field is a read-model cache — the authoritative score
 * is always {@code SUM(growth_signals.points_awarded)} for the father (Design Decision AD-9).</p>
 *
 * <p>Belt progression is monotonic (AD-8): once a father reaches a belt level,
 * they retain it permanently.</p>
 */
@Entity
@Table(name = "father_belts")
public class FatherBelt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "father_id", nullable = false, unique = true, updatable = false)
    private UUID fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "belt_level", length = 10, nullable = false)
    private BeltLevel beltLevel;

    @Column(name = "current_score", nullable = false)
    private int currentScore;

    @Column(name = "belt_earned_at")
    private Instant beltEarnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected FatherBelt() {
    }

    /**
     * Creates a new FatherBelt record with default WHITE belt and score 0.
     *
     * @param fatherId the father's unique identifier
     */
    public FatherBelt(UUID fatherId) {
        this.fatherId = fatherId;
        this.beltLevel = BeltLevel.WHITE;
        this.currentScore = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    /**
     * Returns the current belt level as a {@link BeltLevel} enum value.
     *
     * @return the belt level
     */
    public BeltLevel getBeltLevel() {
        return beltLevel;
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public Instant getBeltEarnedAt() {
        return beltEarnedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ─── Setters (limited — belt mutations go through BeltProgressionService) ──

    /**
     * Sets the belt level.
     *
     * @param beltLevel the new belt level (must not be null)
     */
    public void setBeltLevel(BeltLevel beltLevel) {
        this.beltLevel = beltLevel;
        this.updatedAt = Instant.now();
    }

    public void setCurrentScore(int currentScore) {
        this.currentScore = currentScore;
        this.updatedAt = Instant.now();
    }

    public void setBeltEarnedAt(Instant beltEarnedAt) {
        this.beltEarnedAt = beltEarnedAt;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    private void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    private void onPreUpdate() {
        updatedAt = Instant.now();
    }
}
