package com.dadcoach.workspace.statistics;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing a pre-computed statistics aggregate.
 * Maps to the "statistics_aggregates" table (V8.009).
 *
 * <p>Aggregates are computed nightly by the StatisticsAggregationJob and stored
 * as JSONB data. They provide fast read access for the statistics endpoints
 * without requiring real-time computation.</p>
 */
@Entity
@Table(name = "statistics_aggregates")
public class StatisticsAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "aggregate_id", updatable = false, nullable = false)
    private UUID aggregateId;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 20, nullable = false)
    private StatisticsPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private String data;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected StatisticsAggregate() {
    }

    private StatisticsAggregate(Builder builder) {
        this.aggregateId = builder.aggregateId;
        this.fatherId = builder.fatherId;
        this.periodType = builder.periodType;
        this.periodStart = builder.periodStart;
        this.periodEnd = builder.periodEnd;
        this.data = builder.data;
        this.computedAt = builder.computedAt;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public StatisticsPeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getData() {
        return data;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    // ─── Setters for update operations ───────────────────────────────────

    public void setData(String data) {
        this.data = data;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    // ─── Builder ─────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID aggregateId;
        private UUID fatherId;
        private StatisticsPeriodType periodType;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String data;
        private Instant computedAt;

        private Builder() {
        }

        public Builder aggregateId(UUID aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }

        public Builder periodType(StatisticsPeriodType periodType) {
            this.periodType = periodType;
            return this;
        }

        public Builder periodStart(LocalDate periodStart) {
            this.periodStart = periodStart;
            return this;
        }

        public Builder periodEnd(LocalDate periodEnd) {
            this.periodEnd = periodEnd;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder computedAt(Instant computedAt) {
            this.computedAt = computedAt;
            return this;
        }

        public StatisticsAggregate build() {
            if (fatherId == null) {
                throw new IllegalStateException("fatherId is required");
            }
            if (periodType == null) {
                throw new IllegalStateException("periodType is required");
            }
            if (periodStart == null) {
                throw new IllegalStateException("periodStart is required");
            }
            if (periodEnd == null) {
                throw new IllegalStateException("periodEnd is required");
            }
            if (data == null || data.isBlank()) {
                throw new IllegalStateException("data is required");
            }
            if (computedAt == null) {
                computedAt = Instant.now();
            }
            return new StatisticsAggregate(this);
        }
    }
}
