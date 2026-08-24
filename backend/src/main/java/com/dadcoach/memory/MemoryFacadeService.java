package com.dadcoach.memory;

import com.dadcoach.memory.dto.MemoryCapacityDto;
import com.dadcoach.memory.dto.RetrievalResultDto;

import java.util.List;
import java.util.UUID;

/**
 * Public interface for the Memory & Knowledge System (SPEC-004).
 *
 * <p>This is the main entry point for other components to interact with the memory system.
 * It acts as a facade that coordinates between the various memory subsystems:
 * <ul>
 *   <li>{@code MemoryRetriever} - for ranked memory retrieval with composite scoring</li>
 *   <li>{@code MemoryExtractionService} - for asynchronous memory extraction from conversations</li>
 *   <li>{@code MemoryLifecycleService} - for state transitions (confirm, supersede, delete)</li>
 *   <li>{@code MemoryRepository} - for persistence and capacity queries</li>
 *   <li>{@code MemoryAuditService} - for injection/reference tracking</li>
 * </ul>
 *
 * <h3>Key Operations</h3>
 * <ul>
 *   <li><b>Retrieval:</b> {@link #retrieveRanked} returns memories ranked by composite score</li>
 *   <li><b>Extraction:</b> {@link #triggerExtraction} starts async memory extraction from transcripts</li>
 *   <li><b>Tracking:</b> {@link #recordInjection} and {@link #recordReference} track memory usage</li>
 *   <li><b>Lifecycle:</b> {@link #confirmMemory}, {@link #supersedeMemory}, {@link #deleteMemory}</li>
 *   <li><b>GDPR:</b> {@link #deleteAllForFather} performs complete erasure for a father</li>
 *   <li><b>Capacity:</b> {@link #getCapacity} returns memory usage statistics</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Autowired
 * private MemoryFacadeService memoryService;
 *
 * // Retrieve memories for a coaching session
 * List<RetrievalResultDto> memories = memoryService.retrieveRanked(
 *     fatherId, "bedtime routine", childId, 15);
 *
 * // Record which memories were injected into the prompt
 * List<UUID> usedMemoryIds = memories.stream()
 *     .map(r -> r.getMemory().getId())
 *     .toList();
 * memoryService.recordInjection(usedMemoryIds, conversationId);
 * }</pre>
 *
 * <p><b>Validates: SPEC-004 Design Document - MemoryService Public Interface</b>
 *
 * @see com.dadcoach.memory.retrieval.MemoryRetriever
 * @see com.dadcoach.memory.extraction.MemoryExtractionService
 * @see com.dadcoach.memory.lifecycle.MemoryLifecycleService
 */
public interface MemoryFacadeService {

    /**
     * Retrieves memories ranked by composite score for a coaching session.
     *
     * <p>The composite score formula (per SPEC-004 Req 16):
     * <pre>
     * (importance/10 × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)
     * </pre>
     *
     * <p>Filtering rules:
     * <ul>
     *   <li>Only ACTIVE and CONFIRMED memories are included</li>
     *   <li>Memories with confidence &lt; 0.3 are excluded</li>
     *   <li>Max 5 memories per category (diversity constraint)</li>
     * </ul>
     *
     * @param fatherId the father's ID (required)
     * @param topic    the topic/query for relevance scoring (optional)
     * @param childId  filter to memories about a specific child (optional, null for all)
     * @param maxCount maximum number of results to return
     * @return list of RetrievalResultDto ordered by descending composite score
     * @throws IllegalArgumentException if fatherId is null or maxCount &lt; 1
     */
    List<RetrievalResultDto> retrieveRanked(UUID fatherId, String topic, UUID childId, int maxCount);

    /**
     * Triggers asynchronous memory extraction from a conversation transcript.
     *
     * <p>This method processes the extraction on a separate thread (via @Async) to ensure
     * the conversation response is never blocked. From SPEC-004 Design AD-2:
     * "Memory extraction is triggered by the Conversation Engine's outbox... processes
     * extraction requests asynchronously, never blocking conversation responses."
     *
     * <p>Extraction pipeline:
     * <ol>
     *   <li>Analyze transcript with AI to generate memory recommendations</li>
     *   <li>Validate each recommendation via ExtractionValidator</li>
     *   <li>Check duplicates via DuplicateDetector before creation</li>
     *   <li>Create memories (max 5 per conversation per Req 3)</li>
     * </ol>
     *
     * @param conversationId the conversation ID (for audit trail)
     * @param fatherId       the father's ID
     * @param transcript     the conversation transcript text to analyze
     */
    void triggerExtraction(UUID conversationId, UUID fatherId, String transcript);

    /**
     * Records that memories were injected into a conversation prompt.
     *
     * <p>Per SPEC-004 Req 6 Criteria 2:
     * "WHEN a memory is meaningfully used (Injected into or Referenced in a coaching session),
     * THE Memory_System SHALL reset its expiration timer to the full tier duration from the use date"
     *
     * <p>Injection means the memory content was included in the AI prompt.
     * This is the primary "meaningful use" event that affects lifecycle.
     *
     * @param memoryIds      list of memory IDs that were injected
     * @param conversationId the conversation where injection occurred
     */
    void recordInjection(List<UUID> memoryIds, UUID conversationId);

    /**
     * Records that memories were referenced during a conversation.
     *
     * <p>Reference means the AI mentioned or acknowledged the memory in its response.
     * This is a secondary tracking mechanism distinct from injection.
     *
     * @param memoryIds      list of memory IDs that were referenced
     * @param conversationId the conversation where reference occurred
     */
    void recordReference(List<UUID> memoryIds, UUID conversationId);

    /**
     * Confirms a memory, transitioning it from ACTIVE to CONFIRMED state.
     *
     * <p>Per SPEC-004 Req 2 Criteria 3:
     * <ul>
     *   <li>Set confidence_score to max(current_confidence, 0.9)</li>
     *   <li>Reset the decay timer (extend expiration)</li>
     *   <li>Increment confirmation_count</li>
     * </ul>
     *
     * @param memoryId the ID of the memory to confirm
     * @throws jakarta.persistence.EntityNotFoundException if memory not found
     * @throws IllegalStateException if memory cannot transition to CONFIRMED
     */
    void confirmMemory(UUID memoryId);

    /**
     * Supersedes a memory with updated content.
     *
     * <p>Per SPEC-004 Req 7:
     * <ul>
     *   <li>Creates a new memory with the updated content</li>
     *   <li>Transitions the old memory to SUPERSEDED state</li>
     *   <li>Records the supersession link (new → old)</li>
     *   <li>Preserves version history</li>
     * </ul>
     *
     * @param oldMemoryId   the ID of the memory to supersede
     * @param newContent    the corrected/updated content
     * @param newConfidence the confidence for the new memory (typically 1.0 for corrections)
     * @throws jakarta.persistence.EntityNotFoundException if memory not found
     * @throws IllegalStateException if memory cannot be superseded
     * @throws IllegalArgumentException if content or confidence is invalid
     */
    void supersedeMemory(UUID oldMemoryId, String newContent, double newConfidence);

    /**
     * Deletes a memory by transitioning it to DELETED state.
     *
     * <p>Per SPEC-004 Req 2 Criteria 7:
     * The memory is marked for deletion immediately. Actual content erasure
     * (nullifying content, embedding, version history) occurs via background
     * job within 72 hours.
     *
     * @param memoryId the ID of the memory to delete
     * @param reason   the reason for deletion (for audit trail)
     * @throws jakarta.persistence.EntityNotFoundException if memory not found
     */
    void deleteMemory(UUID memoryId, String reason);

    /**
     * Performs GDPR erasure: deletes all memories for a father.
     *
     * <p>Per SPEC-004 Req 17 (Privacy):
     * <ul>
     *   <li>Immediately transitions all memories to DELETED state</li>
     *   <li>Background job erases content, embeddings, version history within 72 hours</li>
     *   <li>Audit metadata is retained for 2 years per product policy</li>
     * </ul>
     *
     * @param fatherId the father's ID for complete erasure
     */
    void deleteAllForFather(UUID fatherId);

    /**
     * Returns capacity information for a father's memory store.
     *
     * <p>Per SPEC-004 Req 15:
     * Maximum 500 active memories per father. This method returns current usage
     * and available capacity.
     *
     * @param fatherId the father's ID
     * @return MemoryCapacityDto with current count, max allowed, and available
     */
    MemoryCapacityDto getCapacity(UUID fatherId);
}
