package com.dadcoach.api.memory;

import com.dadcoach.api.pagination.CursorPageResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Admin Memory operations.
 * <p>
 * Provides operations for viewing all memories (including archived)
 * and audit history for a father's memories. Admin read operations
 * are automatically audited by {@link com.dadcoach.api.audit.ApiAuditAspect}.
 */
public interface AdminMemoryService {

    /**
     * Lists all memories for a given father, including those in ARCHIVED state.
     * <p>
     * Unlike the Father API which only shows ACTIVE memories, the admin view
     * includes memories in all states: ACTIVE, ARCHIVED, SUPERSEDED, EXPIRED.
     *
     * @param fatherId the father's UUID
     * @param state    optional state filter (null returns all states)
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page
     * @return paginated list of admin memory DTOs
     */
    CursorPageResponse<AdminMemoryDto> listAllMemories(
            UUID fatherId, String state, String cursor, int pageSize);

    /**
     * Retrieves a single memory with full admin context.
     *
     * @param memoryId the memory UUID
     * @return the admin memory DTO, or empty if not found
     */
    Optional<AdminMemoryDto> getMemoryDetail(UUID memoryId);

    /**
     * Retrieves the audit history for a specific memory.
     * <p>
     * Shows all state transitions, modifications, and access events
     * for the memory throughout its lifecycle.
     *
     * @param memoryId the memory UUID
     * @return list of audit entries for the memory, ordered by timestamp descending
     */
    List<MemoryAuditEntryDto> getMemoryAuditHistory(UUID memoryId);
}
