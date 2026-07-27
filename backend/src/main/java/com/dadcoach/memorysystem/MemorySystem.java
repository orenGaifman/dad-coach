package com.dadcoach.memorysystem;

import com.dadcoach.domain.memory.Memory;
import com.dadcoach.memory.MemoryCategory;

import java.util.List;

/**
 * High-level memory system interface for retrieval, consolidation, and expiration.
 *
 * <p>Delegates to {@link com.dadcoach.domain.memory.MemoryService} for CRUD operations
 * and adds ranking, consolidation, and intelligent retrieval capabilities.</p>
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Store memories extracted from conversations</li>
 *   <li>Retrieve top-N memories ranked by composite score (Req 7.6)</li>
 *   <li>Run weekly consolidation merging short-term memories (Req 7.5)</li>
 *   <li>Supersede existing memories with corrected information (Req 7.7)</li>
 *   <li>Expire memories below confidence threshold (Req 7.3)</li>
 * </ul>
 */
public interface MemorySystem {

    /**
     * Store a new memory extracted from conversation.
     *
     * @param fatherId        the father this memory belongs to
     * @param category        the memory category
     * @param content         the memory content
     * @param importanceScore importance score (1-10)
     * @param confidenceScore confidence score (0.0-1.0)
     * @return the persisted Memory entity
     */
    Memory createMemory(Long fatherId, MemoryCategory category,
                        String content, int importanceScore, double confidenceScore);

    /**
     * Retrieve top N memories ranked by composite score for context.
     *
     * <p>Ranking formula (Requirement 7.6):
     * {@code (importance_score × 0.5) + (recency_factor × 0.3) + (relevance_to_topic × 0.2)}
     * where {@code recency_factor = max(0, 1.0 - (days_since_creation × 0.05))}</p>
     *
     * <p>Relevance is computed as a simple topic-matching heuristic:
     * if the memory content contains the topic keyword, relevance = 1.0, otherwise 0.0.</p>
     *
     * @param fatherId the father ID
     * @param topic    the topic for relevance scoring
     * @param limit    maximum number of memories to return
     * @return list of memories ordered by descending composite score, at most {@code limit} entries
     */
    List<Memory> retrieveTopMemories(Long fatherId, String topic, int limit);

    /**
     * Run weekly consolidation job (Requirement 7.5).
     *
     * <p>Merges short-term memories (importance 1-3) older than 7 days into summary memories.
     * The summary memory retains the highest importance score found and averages confidence scores.</p>
     *
     * @param fatherId the father ID
     */
    void consolidateMemories(Long fatherId);

    /**
     * Supersede an existing memory with corrected information (Requirement 7.7).
     *
     * @param existingMemoryId the ID of the memory being superseded
     * @param newContent       the corrected content
     * @return the new memory that supersedes the old one
     */
    Memory supersedeMemory(Long existingMemoryId, String newContent);

    /**
     * Expire memories below confidence threshold that haven't been accessed recently (Requirement 7.3).
     * Criteria: confidence_score &lt; 0.5 AND not accessed in 60 days.
     */
    void expireLowConfidenceMemories();
}
