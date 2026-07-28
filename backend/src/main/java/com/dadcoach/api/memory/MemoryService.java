package com.dadcoach.api.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for memory operations in the Father API.
 * <p>
 * Controllers delegate to this service for memory retrieval and deletion.
 * The implementation is responsible for:
 * <ul>
 *   <li>Excluding SUPERSEDED, EXPIRED, and ARCHIVED memories from list results</li>
 *   <li>Never exposing embeddings or raw confidence scores in responses</li>
 *   <li>Triggering the SPEC-004 deletion flow on delete requests</li>
 * </ul>
 */
public interface MemoryService {

    /**
     * Lists active memories for the given father, paginated by cursor.
     * Excludes SUPERSEDED, EXPIRED, and ARCHIVED states.
     *
     * @param fatherId the father's UUID
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize the number of items per page
     * @return a page of active memories
     */
    MemoryPage listActiveMemories(UUID fatherId, String cursor, int pageSize);

    /**
     * Retrieves a single memory by ID.
     *
     * @param memoryId the memory UUID
     * @return the memory response, or empty if not found
     */
    Optional<MemoryResponseDto> getMemory(UUID memoryId);

    /**
     * Returns the fatherId that owns the given memory, for ownership checks.
     *
     * @param memoryId the memory UUID
     * @return the owning father's UUID, or empty if memory not found
     */
    Optional<UUID> getMemoryOwnerId(UUID memoryId);

    /**
     * Requests deletion of a specific memory.
     * Triggers the SPEC-004 deletion flow.
     *
     * @param memoryId the memory UUID to delete
     */
    void deleteMemory(UUID memoryId);

    /**
     * Paginated result for memory listing.
     */
    record MemoryPage(
            List<MemoryResponseDto> items,
            String nextCursor,
            boolean hasMore
    ) {
    }
}
