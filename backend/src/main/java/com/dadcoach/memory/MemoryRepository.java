package com.dadcoach.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Memory} entities (SPEC-004).
 * 
 * <p>Provides queries for:
 * <ul>
 *   <li>Basic lookups: by father, category, state</li>
 *   <li>Capacity management: count active memories for 500-limit enforcement</li>
 *   <li>Retrieval ranking: find candidates for composite scoring</li>
 *   <li>Similarity search: pgvector cosine similarity for duplicate detection</li>
 *   <li>Lifecycle: expired memories, conflict groups, cleanup candidates</li>
 * </ul>
 *
 * <p>Leverages indexes:
 * <ul>
 *   <li>idx_memories_father_state ON memories(father_id, state)</li>
 *   <li>idx_memories_father_category ON memories(father_id, category, state)</li>
 *   <li>idx_memories_expires ON memories(expires_at) WHERE state = 'ACTIVE'</li>
 *   <li>idx_memories_embedding USING ivfflat ON memories(embedding vector_cosine_ops)</li>
 * </ul>
 *
 * @see Memory
 * @see MemoryCategory
 * @see MemoryState
 */
@Repository("specMemoryRepository")
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    // ─── Basic Queries by Father ──────────────────────────────────────────

    /**
     * Find all memories for a father.
     *
     * @param fatherId the father's ID
     * @return list of all memories for the father
     */
    List<Memory> findByFatherId(UUID fatherId);

    /**
     * Find all memories for a father with a specific category.
     *
     * @param fatherId the father's ID
     * @param category the memory category
     * @return list of memories matching the criteria
     */
    List<Memory> findByFatherIdAndCategory(UUID fatherId, MemoryCategory category);

    /**
     * Find all memories for a father with a specific state.
     *
     * @param fatherId the father's ID
     * @param state    the memory state
     * @return list of memories matching the criteria
     */
    List<Memory> findByFatherIdAndState(UUID fatherId, MemoryState state);

    /**
     * Find all memories for a father with any of the specified states.
     *
     * @param fatherId the father's ID
     * @param states   collection of states to match
     * @return list of memories matching the criteria
     */
    List<Memory> findByFatherIdAndStateIn(UUID fatherId, Collection<MemoryState> states);

    // ─── Capacity Queries ─────────────────────────────────────────────────

    /**
     * Count memories for a father with any of the specified states.
     * Used to enforce the 500-memory capacity limit (Requirement 15).
     *
     * @param fatherId the father's ID
     * @param states   collection of states to count (typically ACTIVE, CONFIRMED)
     * @return count of memories matching the criteria
     */
    long countByFatherIdAndStateIn(UUID fatherId, Collection<MemoryState> states);

    /**
     * Find memories for capacity enforcement, ordered by combined score ascending.
     * Lowest scores (importance × confidence) are archived first when at capacity.
     *
     * @param fatherId the father's ID
     * @param states   states to include (ACTIVE, CONFIRMED)
     * @return memories ordered by combined score ascending (lowest first)
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "ORDER BY (m.importanceScore * m.confidenceScore) ASC")
    List<Memory> findByFatherIdAndStateInOrderByCombinedScoreAsc(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states);

    // ─── Retrieval Queries ────────────────────────────────────────────────

    /**
     * Find retrievable memories for a father (ACTIVE or CONFIRMED with confidence >= threshold).
     * Used as candidates for composite scoring in retrieval.
     *
     * @param fatherId            the father's ID
     * @param states              states to include (typically ACTIVE, CONFIRMED)
     * @param minConfidenceScore  minimum confidence score (typically 0.3)
     * @return list of retrievable memories
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.confidenceScore >= :minConfidenceScore")
    List<Memory> findRetrievableMemories(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states,
            @Param("minConfidenceScore") BigDecimal minConfidenceScore);

    /**
     * Find retrievable memories for a father filtered by category.
     *
     * @param fatherId            the father's ID
     * @param category            the memory category
     * @param states              states to include
     * @param minConfidenceScore  minimum confidence score
     * @return list of retrievable memories in the category
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.category = :category " +
           "AND m.state IN :states " +
           "AND m.confidenceScore >= :minConfidenceScore")
    List<Memory> findRetrievableByCategory(
            @Param("fatherId") UUID fatherId,
            @Param("category") MemoryCategory category,
            @Param("states") Collection<MemoryState> states,
            @Param("minConfidenceScore") BigDecimal minConfidenceScore);

    // ─── Vector Similarity Queries (pgvector) ─────────────────────────────

    /**
     * Find similar memories using pgvector cosine similarity.
     * Used for duplicate detection and semantic search.
     *
     * <p>Returns memories with their cosine similarity score, ordered by similarity descending.
     * The <=> operator computes cosine distance; similarity = 1 - distance.
     *
     * @param fatherId        the father's ID
     * @param states          states to include (typically ACTIVE, CONFIRMED)
     * @param minConfidence   minimum confidence score
     * @param queryEmbedding  the query embedding vector (1536 dimensions)
     * @param maxCandidates   maximum number of results
     * @return list of Object[] where [0] = Memory, [1] = similarity score (Double)
     */
    @Query(value = 
           "SELECT m.*, 1 - (m.embedding <=> CAST(:queryEmbedding AS vector)) AS cosine_similarity " +
           "FROM memories m " +
           "WHERE m.father_id = :fatherId " +
           "AND m.state IN :states " +
           "AND m.confidence_score >= :minConfidence " +
           "AND m.embedding IS NOT NULL " +
           "ORDER BY cosine_similarity DESC " +
           "LIMIT :maxCandidates",
           nativeQuery = true)
    List<Object[]> findBySimilarity(
            @Param("fatherId") UUID fatherId,
            @Param("states") List<String> states,
            @Param("minConfidence") BigDecimal minConfidence,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("maxCandidates") int maxCandidates);

    /**
     * Find similar memories for duplicate detection, scoped by category and subject type.
     * Used by DuplicateDetector before creating new memories.
     *
     * @param fatherId        the father's ID
     * @param category        the memory category
     * @param subjectType     the subject type (FATHER, CHILD, FAMILY)
     * @param states          states to include
     * @param queryEmbedding  the query embedding vector
     * @param minSimilarity   minimum similarity threshold (e.g., 0.70 for potential update)
     * @param maxCandidates   maximum number of results
     * @return list of Object[] where [0] = Memory, [1] = similarity score (Double)
     */
    @Query(value = 
           "SELECT m.*, 1 - (m.embedding <=> CAST(:queryEmbedding AS vector)) AS cosine_similarity " +
           "FROM memories m " +
           "WHERE m.father_id = :fatherId " +
           "AND m.category = :category " +
           "AND m.subject_type = :subjectType " +
           "AND m.state IN :states " +
           "AND m.embedding IS NOT NULL " +
           "AND (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) >= :minSimilarity " +
           "ORDER BY cosine_similarity DESC " +
           "LIMIT :maxCandidates",
           nativeQuery = true)
    List<Object[]> findSimilarForDuplicateDetection(
            @Param("fatherId") UUID fatherId,
            @Param("category") String category,
            @Param("subjectType") String subjectType,
            @Param("states") List<String> states,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("minSimilarity") double minSimilarity,
            @Param("maxCandidates") int maxCandidates);

    // ─── Expiration & Decay Queries ───────────────────────────────────────

    /**
     * Find memories that have passed their expiration time.
     * Used by the scheduled expiration job.
     *
     * @param now         current timestamp
     * @param activeState the ACTIVE state
     * @return list of expired memories
     */
    @Query("SELECT m FROM Memory m WHERE m.state = :activeState " +
           "AND m.expiresAt IS NOT NULL AND m.expiresAt < :now")
    List<Memory> findExpiredMemories(
            @Param("now") Instant now,
            @Param("activeState") MemoryState activeState);

    /**
     * Find memories eligible for decay-based expiration.
     * Criteria: confidence < threshold AND not accessed within access window.
     *
     * @param states              states to check (ACTIVE)
     * @param confidenceThreshold confidence threshold (0.5)
     * @param accessThreshold     access cutoff time (60 days ago)
     * @return list of memories eligible for expiration
     */
    @Query("SELECT m FROM Memory m WHERE m.state IN :states " +
           "AND m.confidenceScore < :confidenceThreshold " +
           "AND (m.lastAccessedAt IS NULL OR m.lastAccessedAt < :accessThreshold)")
    List<Memory> findLowConfidenceUnaccessed(
            @Param("states") Collection<MemoryState> states,
            @Param("confidenceThreshold") BigDecimal confidenceThreshold,
            @Param("accessThreshold") Instant accessThreshold);

    /**
     * Find memories eligible for decay-based expiration for a specific father.
     *
     * @param fatherId            the father's ID
     * @param states              states to check
     * @param confidenceThreshold confidence threshold
     * @param accessThreshold     access cutoff time
     * @return list of memories eligible for expiration
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.confidenceScore < :confidenceThreshold " +
           "AND (m.lastAccessedAt IS NULL OR m.lastAccessedAt < :accessThreshold)")
    List<Memory> findLowConfidenceUnaccessedByFather(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states,
            @Param("confidenceThreshold") BigDecimal confidenceThreshold,
            @Param("accessThreshold") Instant accessThreshold);

    // ─── Conflict Group Queries ───────────────────────────────────────────

    /**
     * Find memories belonging to a conflict group.
     *
     * @param conflictGroupId the conflict group ID
     * @return list of memories in the conflict group
     */
    List<Memory> findByConflictGroupId(UUID conflictGroupId);

    /**
     * Find all memories with unresolved conflicts for a father.
     *
     * @param fatherId the father's ID
     * @param states   states to include (typically ACTIVE, CONFIRMED)
     * @return list of memories with conflict_group_id set
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.conflictGroupId IS NOT NULL")
    List<Memory> findConflictingMemories(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states);

    /**
     * Find all memories that need user confirmation for a father.
     *
     * @param fatherId the father's ID
     * @param states   states to include (typically ACTIVE, CONFIRMED)
     * @return list of memories flagged for user confirmation
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.needsUserConfirmation = true")
    List<Memory> findMemoriesNeedingConfirmation(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states);

    /**
     * Count memories that need user confirmation for a father.
     *
     * @param fatherId the father's ID
     * @param states   states to include (typically ACTIVE, CONFIRMED)
     * @return count of memories flagged for user confirmation
     */
    @Query("SELECT COUNT(m) FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.needsUserConfirmation = true")
    long countMemoriesNeedingConfirmation(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states);

    // ─── Cleanup Queries ──────────────────────────────────────────────────

    /**
     * Find superseded memories older than the retention period.
     * Used by cleanup job to transition SUPERSEDED → DELETED after 90 days.
     *
     * @param supersededState the SUPERSEDED state
     * @param cutoffTime      90 days ago
     * @return list of superseded memories ready for deletion
     */
    @Query("SELECT m FROM Memory m WHERE m.state = :supersededState " +
           "AND m.lastUpdatedAt < :cutoffTime")
    List<Memory> findSupersededForCleanup(
            @Param("supersededState") MemoryState supersededState,
            @Param("cutoffTime") Instant cutoffTime);

    /**
     * Find expired memories older than the retention period.
     * Used by cleanup job to transition EXPIRED → DELETED after 30 days.
     *
     * @param expiredState the EXPIRED state
     * @param cutoffTime   30 days ago
     * @return list of expired memories ready for deletion
     */
    @Query("SELECT m FROM Memory m WHERE m.state = :expiredState " +
           "AND m.lastUpdatedAt < :cutoffTime")
    List<Memory> findExpiredForCleanup(
            @Param("expiredState") MemoryState expiredState,
            @Param("cutoffTime") Instant cutoffTime);

    /**
     * Find deleted memories ready for content erasure.
     * Used by 72-hour erasure job.
     *
     * @param deletedState the DELETED state
     * @param cutoffTime   time after which content should be erased (e.g., 72 hours ago)
     * @return list of deleted memories ready for erasure
     */
    @Query("SELECT m FROM Memory m WHERE m.state = :deletedState " +
           "AND m.lastUpdatedAt < :cutoffTime " +
           "AND m.content IS NOT NULL")
    List<Memory> findDeletedForErasure(
            @Param("deletedState") MemoryState deletedState,
            @Param("cutoffTime") Instant cutoffTime);

    // ─── Consolidation Queries ────────────────────────────────────────────

    /**
     * Find short-term memories (importance 1-3) older than the consolidation threshold.
     * Used by weekly consolidation job.
     *
     * @param fatherId          the father's ID
     * @param states            states to include (ACTIVE)
     * @param maxImportance     maximum importance score (3 for short-term)
     * @param ageThreshold      memories older than this are candidates (14 days ago)
     * @return list of short-term memories ready for consolidation
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.state IN :states " +
           "AND m.importanceScore <= :maxImportance " +
           "AND m.createdAt < :ageThreshold " +
           "ORDER BY m.category, m.createdAt")
    List<Memory> findShortTermForConsolidation(
            @Param("fatherId") UUID fatherId,
            @Param("states") Collection<MemoryState> states,
            @Param("maxImportance") int maxImportance,
            @Param("ageThreshold") Instant ageThreshold);

    /**
     * Find conversation summaries older than the consolidation threshold.
     * Used to create weekly/monthly consolidation summaries.
     *
     * @param fatherId      the father's ID
     * @param category      CONVERSATION_SUMMARY category
     * @param states        states to include (ACTIVE)
     * @param ageThreshold  summaries older than this (30 days ago)
     * @return list of conversation summaries ready for consolidation
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND m.category = :category " +
           "AND m.state IN :states " +
           "AND m.createdAt < :ageThreshold " +
           "ORDER BY m.createdAt")
    List<Memory> findConversationSummariesForConsolidation(
            @Param("fatherId") UUID fatherId,
            @Param("category") MemoryCategory category,
            @Param("states") Collection<MemoryState> states,
            @Param("ageThreshold") Instant ageThreshold);

    // ─── Child-Specific Queries ───────────────────────────────────────────

    /**
     * Find memories about a specific child.
     *
     * @param fatherId the father's ID
     * @param childId  the child's ID
     * @param states   states to include
     * @return list of memories about the child
     */
    List<Memory> findByFatherIdAndChildIdAndStateIn(
            UUID fatherId,
            UUID childId,
            Collection<MemoryState> states);

    /**
     * Find memories for contradiction detection within a category and subject.
     *
     * @param fatherId    the father's ID
     * @param childId     the child's ID (nullable)
     * @param category    the memory category
     * @param subjectType the subject type
     * @param states      states to include
     * @return list of memories ordered by creation date descending
     */
    @Query("SELECT m FROM Memory m WHERE m.fatherId = :fatherId " +
           "AND ((:childId IS NULL AND m.childId IS NULL) OR m.childId = :childId) " +
           "AND m.category = :category " +
           "AND m.subjectType = :subjectType " +
           "AND m.state IN :states " +
           "ORDER BY m.createdAt DESC")
    List<Memory> findForContradictionDetection(
            @Param("fatherId") UUID fatherId,
            @Param("childId") UUID childId,
            @Param("category") MemoryCategory category,
            @Param("subjectType") MemorySubjectType subjectType,
            @Param("states") Collection<MemoryState> states);

    // ─── GDPR Deletion ────────────────────────────────────────────────────

    /**
     * Delete all memories for a father.
     * Used for GDPR erasure requests.
     *
     * @param fatherId the father's ID
     */
    void deleteByFatherId(UUID fatherId);

    // ─── Distinct Father IDs ──────────────────────────────────────────────

    /**
     * Find all distinct father IDs with memories in the specified states.
     * Used by batch processing jobs to iterate over fathers.
     *
     * @param states states to include
     * @return list of distinct father IDs
     */
    @Query("SELECT DISTINCT m.fatherId FROM Memory m WHERE m.state IN :states")
    List<UUID> findDistinctFatherIdsByStateIn(@Param("states") Collection<MemoryState> states);
}
