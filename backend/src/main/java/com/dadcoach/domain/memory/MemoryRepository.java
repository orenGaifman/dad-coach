package com.dadcoach.domain.memory;

import com.dadcoach.memory.MemoryCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Memory} entities.
 * Provides queries for:
 * <ul>
 *   <li>Ranking: top memories by composite score for context retrieval</li>
 *   <li>Capacity: count active memories per father for 500-limit enforcement</li>
 *   <li>Expiration: find expired memories for cleanup</li>
 *   <li>Supersede: find memories by category for contradiction detection</li>
 * </ul>
 *
 * Indexes leveraged:
 * <ul>
 *   <li>idx_memory_father_status ON memory(father_id, status)</li>
 *   <li>idx_memory_father_category ON memory(father_id, category)</li>
 *   <li>idx_memory_expires ON memory(expires_at) WHERE status = 'ACTIVE'</li>
 * </ul>
 */
@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {

    // ─── Capacity Queries ─────────────────────────────────────────────────

    /**
     * Count active memories for a father.
     * Used to enforce the 500-memory capacity limit (Requirement 7.11).
     */
    @Query("SELECT COUNT(m) FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE")
    long countActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Find active memories for a father ordered by combined score (importance × confidence) ascending.
     * Used for capacity enforcement — lowest scores get archived first.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "ORDER BY (m.importanceScore * m.confidenceScore) ASC")
    List<Memory> findActiveByFatherIdOrderByCombinedScoreAsc(@Param("fatherId") Long fatherId);

    // ─── Ranking Queries ──────────────────────────────────────────────────

    /**
     * Find all active memories for a father.
     * Used as the basis for in-memory ranking with the composite formula.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE")
    List<Memory> findActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Find active memories for a father filtered by category.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.category = :category " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE")
    List<Memory> findActiveByFatherIdAndCategory(@Param("fatherId") Long fatherId,
                                                 @Param("category") MemoryCategory category);

    // ─── Expiration Queries ───────────────────────────────────────────────

    /**
     * Find active memories that have passed their expiration time.
     * Used by the scheduled expiration job.
     */
    @Query("SELECT m FROM Memory m WHERE m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "AND m.expiresAt IS NOT NULL AND m.expiresAt < :now")
    List<Memory> findExpiredMemories(@Param("now") Instant now);

    /**
     * Find active memories with low confidence that haven't been accessed recently.
     * Used for auto-expiration (Requirement 7.3): confidence < 0.5 and not accessed in 60 days.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "AND m.confidenceScore < :confidenceThreshold " +
           "AND (m.lastAccessedAt IS NULL OR m.lastAccessedAt < :accessThreshold)")
    List<Memory> findLowConfidenceUnaccessed(@Param("fatherId") Long fatherId,
                                             @Param("confidenceThreshold") java.math.BigDecimal confidenceThreshold,
                                             @Param("accessThreshold") Instant accessThreshold);

    // ─── Consolidation / Global Queries ──────────────────────────────────

    /**
     * Find all active memories with low confidence that haven't been accessed recently,
     * across all fathers. Used by the global expiration job (Requirement 7.3).
     */
    @Query("SELECT m FROM Memory m WHERE m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "AND m.confidenceScore < :confidenceThreshold " +
           "AND (m.lastAccessedAt IS NULL OR m.lastAccessedAt < :accessThreshold)")
    List<Memory> findAllLowConfidenceUnaccessed(
            @Param("confidenceThreshold") java.math.BigDecimal confidenceThreshold,
            @Param("accessThreshold") Instant accessThreshold);

    // ─── Contradiction / Supersede Queries ────────────────────────────────

    /**
     * Find active memories for a father in a specific category.
     * Used for contradiction detection and memory consolidation.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.childId = :childId " +
           "AND m.category = :category " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "ORDER BY m.createdAt DESC")
    List<Memory> findActiveByFatherAndChildAndCategory(@Param("fatherId") Long fatherId,
                                                       @Param("childId") Long childId,
                                                       @Param("category") MemoryCategory category);

    /**
     * Find active memories for a father in a specific category (without child filter).
     * Used when child_id is null.
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.childId IS NULL " +
           "AND m.category = :category " +
           "AND m.status = com.dadcoach.domain.memory.MemoryStatus.ACTIVE " +
           "ORDER BY m.createdAt DESC")
    List<Memory> findActiveByFatherAndCategoryNoChild(@Param("fatherId") Long fatherId,
                                                      @Param("category") MemoryCategory category);

    // ─── Delete Queries ───────────────────────────────────────────────────

    /**
     * Delete all memories for a father.
     * Used when deleting a father account to comply with data deletion requirements.
     */
    void deleteByFatherId(Long fatherId);
}
