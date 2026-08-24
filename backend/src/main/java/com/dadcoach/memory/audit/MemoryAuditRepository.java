package com.dadcoach.memory.audit;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only repository for {@link MemoryAuditLog} entities.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * The audit trail is separate from operational memory storage and retained independently.
 * Audit metadata entries for deleted memories SHALL be retained for 2 years as a product policy.
 *
 * <p><strong>Append-Only Design:</strong>
 * This repository intentionally extends {@code Repository<T, ID>} instead of
 * {@code JpaRepository<T, ID>} to avoid exposing update and delete methods.
 * <ul>
 *   <li>Only {@code save()} for new entries is exposed</li>
 *   <li>No {@code delete()}, {@code deleteAll()}, or {@code deleteById()} methods</li>
 *   <li>The entity itself prevents updates via {@code @PreUpdate} callback</li>
 *   <li>Retention cleanup uses a separate administrative interface</li>
 * </ul>
 *
 * <p><strong>Why not JpaRepository?</strong>
 * JpaRepository extends CrudRepository which includes delete operations. By using
 * the base Repository interface and explicitly declaring only the methods we need,
 * we ensure compile-time safety against accidental deletions.
 *
 * @see MemoryAuditLog
 * @see MemoryAuditService
 * @see MemoryAuditRetentionRepository for 2-year retention cleanup
 */
@org.springframework.stereotype.Repository
public interface MemoryAuditRepository extends Repository<MemoryAuditLog, UUID> {

    // ═══════════════════════════════════════════════════════════════════════════
    // Write Operations (Append Only)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Persists a new audit log entry.
     *
     * <p>This is the only write operation supported. The audit log is append-only,
     * so this method should only be used for new entries (never for updates).
     * The entity's {@code @PreUpdate} callback will throw an exception if an
     * update is attempted.
     *
     * @param entity the audit log entry to save
     * @return the saved audit log entry with generated ID
     * @throws UnsupportedOperationException if attempting to update an existing entry
     */
    MemoryAuditLog save(MemoryAuditLog entity);

    /**
     * Persists multiple new audit log entries.
     *
     * @param entities the audit log entries to save
     * @return the saved audit log entries
     */
    List<MemoryAuditLog> saveAll(Iterable<MemoryAuditLog> entities);

    // ═══════════════════════════════════════════════════════════════════════════
    // Read Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find an audit log entry by ID.
     *
     * @param id the audit entry ID
     * @return the audit entry if found
     */
    Optional<MemoryAuditLog> findById(UUID id);

    /**
     * Find all audit entries for a father, ordered by creation time descending.
     *
     * @param fatherId the father's ID
     * @return list of audit entries for the father
     */
    List<MemoryAuditLog> findByFatherIdOrderByCreatedAtDesc(UUID fatherId);

    /**
     * Find audit entries for a father within a time range.
     *
     * @param fatherId  the father's ID
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (exclusive)
     * @return list of audit entries within the time range
     */
    @Query("SELECT a FROM MemoryAuditLog a WHERE a.fatherId = :fatherId " +
            "AND a.createdAt >= :startTime AND a.createdAt < :endTime " +
            "ORDER BY a.createdAt DESC")
    List<MemoryAuditLog> findByFatherIdAndTimeRange(
            @Param("fatherId") UUID fatherId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Find all audit entries within a time range (not filtered by father).
     * Useful for system-wide compliance queries and troubleshooting.
     *
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (exclusive)
     * @return list of audit entries within the time range
     */
    @Query("SELECT a FROM MemoryAuditLog a WHERE a.createdAt >= :startTime AND a.createdAt < :endTime " +
            "ORDER BY a.createdAt DESC")
    List<MemoryAuditLog> findByTimeRange(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    // ─── Queries by Memory ───────────────────────────────────────────────

    /**
     * Find all audit entries for a specific memory, ordered by creation time ascending.
     * Shows the full audit trail/history for a memory.
     *
     * @param memoryId the memory's ID
     * @return list of audit entries for the memory
     */
    List<MemoryAuditLog> findByMemoryIdOrderByCreatedAtAsc(UUID memoryId);

    /**
     * Find the most recent audit entry for a memory.
     *
     * @param memoryId the memory's ID
     * @return the most recent audit entry, or null if none exists
     */
    @Query("SELECT a FROM MemoryAuditLog a WHERE a.memoryId = :memoryId " +
            "ORDER BY a.createdAt DESC LIMIT 1")
    MemoryAuditLog findMostRecentByMemoryId(@Param("memoryId") UUID memoryId);

    // ─── Queries by Event Type ───────────────────────────────────────────

    /**
     * Find audit entries by event type for a father.
     *
     * @param fatherId  the father's ID
     * @param eventType the type of event
     * @return list of audit entries matching the criteria
     */
    List<MemoryAuditLog> findByFatherIdAndEventTypeOrderByCreatedAtDesc(
            UUID fatherId, EventType eventType);

    /**
     * Find all audit entries by event type.
     *
     * @param eventType the type of event
     * @return list of audit entries matching the event type
     */
    List<MemoryAuditLog> findByEventTypeOrderByCreatedAtDesc(EventType eventType);

    // ─── Queries by Actor Type ───────────────────────────────────────────

    /**
     * Find audit entries by actor type for a father.
     *
     * @param fatherId  the father's ID
     * @param actorType the type of actor (AI, USER, SYSTEM)
     * @return list of audit entries matching the criteria
     */
    List<MemoryAuditLog> findByFatherIdAndActorTypeOrderByCreatedAtDesc(
            UUID fatherId, ActorType actorType);

    // ─── Count Queries ───────────────────────────────────────────────────

    /**
     * Count audit entries for a memory.
     * Useful for determining how many lifecycle events a memory has gone through.
     *
     * @param memoryId the memory's ID
     * @return count of audit entries for the memory
     */
    long countByMemoryId(UUID memoryId);

    /**
     * Count audit entries for a father.
     *
     * @param fatherId the father's ID
     * @return count of audit entries for the father
     */
    long countByFatherId(UUID fatherId);

    /**
     * Count all audit entries in the system.
     *
     * @return total count of audit entries
     */
    long count();

    /**
     * Check if an audit entry exists by ID.
     *
     * @param id the audit entry ID
     * @return true if the entry exists
     */
    boolean existsById(UUID id);

    // ═══════════════════════════════════════════════════════════════════════════
    // Retention Queries (Read-Only)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find audit entries older than the retention period (2 years).
     * Used by the retention cleanup job to identify entries for deletion.
     *
     * <p><strong>Note:</strong> Actual deletion is performed through
     * {@link MemoryAuditRetentionRepository} to maintain separation of concerns.
     *
     * @param cutoffTime entries older than this may be eligible for cleanup
     * @return list of audit entries eligible for cleanup review
     */
    List<MemoryAuditLog> findByCreatedAtBefore(Instant cutoffTime);
}
