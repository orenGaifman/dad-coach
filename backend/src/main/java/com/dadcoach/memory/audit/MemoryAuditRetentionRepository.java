package com.dadcoach.memory.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrative repository for audit log retention cleanup.
 *
 * <p><strong>WARNING: This repository provides delete capability and should ONLY
 * be used by the scheduled retention cleanup job.</strong>
 *
 * <p>From SPEC-004 Requirement 24:
 * Audit metadata entries SHALL be retained for 2 years as a product policy,
 * then permanently deleted.
 *
 * <p>This repository is intentionally separate from {@link MemoryAuditRepository}
 * to maintain the append-only principle for normal operations. Only the scheduled
 * retention cleanup job should use this repository.
 *
 * <p>Usage restrictions:
 * <ul>
 *   <li>Only delete entries older than 2 years (730 days)</li>
 *   <li>Only call from the scheduled retention cleanup job</li>
 *   <li>Log all deletion operations for compliance</li>
 * </ul>
 *
 * @see MemoryAuditRepository for normal append-only operations
 */
@Repository
public interface MemoryAuditRetentionRepository extends JpaRepository<MemoryAuditLog, UUID> {

    /**
     * Delete audit entries older than the retention period (2 years).
     *
     * <p><strong>WARNING:</strong> This method permanently deletes audit data.
     * Should only be called by the retention cleanup job with appropriate logging.
     *
     * <p>The cutoff time should be calculated as: {@code Instant.now().minus(730, ChronoUnit.DAYS)}
     *
     * @param cutoffTime entries created before this time will be deleted
     * @return number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM MemoryAuditLog a WHERE a.createdAt < :cutoffTime")
    long deleteByCreatedAtBefore(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Count audit entries older than the retention period.
     * Use this before deletion to log how many records will be affected.
     *
     * @param cutoffTime entries created before this time
     * @return count of entries eligible for deletion
     */
    long countByCreatedAtBefore(Instant cutoffTime);
}
