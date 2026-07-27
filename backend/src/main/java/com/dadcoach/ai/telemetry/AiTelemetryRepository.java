package com.dadcoach.ai.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI telemetry records.
 * Provides query methods for alert threshold detection and metrics aggregation.
 */
@Repository
public interface AiTelemetryRepository extends JpaRepository<AiTelemetryRecord, UUID> {

    /**
     * Find all telemetry records for a father within a time range (most recent first).
     */
    List<AiTelemetryRecord> findByFatherIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID fatherId, Instant after);

    /**
     * Count total records within a time window (for error rate calculation).
     */
    @Query("SELECT COUNT(t) FROM AiTelemetryRecord t WHERE t.createdAt >= :since")
    long countRecordsSince(@Param("since") Instant since);

    /**
     * Count records where validation failed within a time window (for error rate).
     */
    @Query("SELECT COUNT(t) FROM AiTelemetryRecord t WHERE t.createdAt >= :since AND t.validationPassed = false")
    long countFailedRecordsSince(@Param("since") Instant since);

    /**
     * Get all latency values within a time window (for p95 calculation).
     */
    @Query("SELECT t.totalLatencyMs FROM AiTelemetryRecord t WHERE t.createdAt >= :since ORDER BY t.totalLatencyMs ASC")
    List<Integer> findLatenciesSince(@Param("since") Instant since);

    /**
     * Find records by model name within a time window.
     */
    List<AiTelemetryRecord> findByModelNameAndCreatedAtAfterOrderByCreatedAtDesc(
            String modelName, Instant after);
}
