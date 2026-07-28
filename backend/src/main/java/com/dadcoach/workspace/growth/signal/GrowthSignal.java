package com.dadcoach.workspace.growth.signal;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable JPA entity representing a growth signal in the Father Growth System.
 * Maps to the "growth_signals" table (V8.001).
 *
 * <p>Growth signals are append-only event records. Once persisted, they are never
 * updated or deleted (Design Decision AD-3). The Growth_Score is the sum of all
 * signals for a father.</p>
 *
 * <p>Duplicate detection is enforced by the database unique constraint
 * (father_id, signal_type, source_entity_id).</p>
 */
@Entity
@Table(name = "growth_signals")
@Immutable
public class GrowthSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "signal_id", updatable = false, nullable = false)
    private UUID signalId;

    @Column(name = "father_id", nullable = false, updatable = false)
    private UUID fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", length = 50, nullable = false, updatable = false)
    private GrowthSignalType signalType;

    @Column(name = "points_awarded", nullable = false, updatable = false)
    private int pointsAwarded;

    @Column(name = "source_entity_id", nullable = false, updatable = false)
    private UUID sourceEntityId;

    @Column(name = "source_entity_type", length = 50, nullable = false, updatable = false)
    private String sourceEntityType;

    @Column(name = "scoring_policy_version", nullable = false, updatable = false)
    private int scoringPolicyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected GrowthSignal() {
    }

    private GrowthSignal(Builder builder) {
        this.signalId = builder.signalId;
        this.fatherId = builder.fatherId;
        this.signalType = builder.signalType;
        this.pointsAwarded = builder.pointsAwarded;
        this.sourceEntityId = builder.sourceEntityId;
        this.sourceEntityType = builder.sourceEntityType;
        this.scoringPolicyVersion = builder.scoringPolicyVersion;
        this.createdAt = builder.createdAt;
    }

    // ─── Getters (no setters — entity is immutable) ─────────────────────

    public UUID getSignalId() {
        return signalId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public GrowthSignalType getSignalType() {
        return signalType;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public String getSourceEntityType() {
        return sourceEntityType;
    }

    public int getScoringPolicyVersion() {
        return scoringPolicyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ─── Builder ─────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID signalId;
        private UUID fatherId;
        private GrowthSignalType signalType;
        private int pointsAwarded;
        private UUID sourceEntityId;
        private String sourceEntityType;
        private int scoringPolicyVersion = 1;
        private Instant createdAt;

        private Builder() {
        }

        public Builder signalId(UUID signalId) {
            this.signalId = signalId;
            return this;
        }

        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }

        public Builder signalType(GrowthSignalType signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder pointsAwarded(int pointsAwarded) {
            this.pointsAwarded = pointsAwarded;
            return this;
        }

        public Builder sourceEntityId(UUID sourceEntityId) {
            this.sourceEntityId = sourceEntityId;
            return this;
        }

        public Builder sourceEntityType(String sourceEntityType) {
            this.sourceEntityType = sourceEntityType;
            return this;
        }

        public Builder scoringPolicyVersion(int scoringPolicyVersion) {
            this.scoringPolicyVersion = scoringPolicyVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public GrowthSignal build() {
            if (fatherId == null) {
                throw new IllegalStateException("fatherId is required");
            }
            if (signalType == null) {
                throw new IllegalStateException("signalType is required");
            }
            if (pointsAwarded <= 0) {
                throw new IllegalStateException("pointsAwarded must be positive");
            }
            if (sourceEntityId == null) {
                throw new IllegalStateException("sourceEntityId is required");
            }
            if (sourceEntityType == null || sourceEntityType.isBlank()) {
                throw new IllegalStateException("sourceEntityType is required");
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new GrowthSignal(this);
        }
    }
}
