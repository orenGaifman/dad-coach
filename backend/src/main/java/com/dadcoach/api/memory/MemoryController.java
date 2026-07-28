package com.dadcoach.api.memory;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.auth.RolePermission;
import com.dadcoach.api.error.ResourceNotFoundException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for memory operations in the Father API.
 * <p>
 * Provides listing, retrieval, and deletion of memories for the authenticated father.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>Memory responses NEVER include: embeddings, raw confidence scores</li>
 *   <li>List endpoint excludes SUPERSEDED, EXPIRED, and ARCHIVED memories (only active)</li>
 *   <li>Ownership is enforced on all endpoints — mismatches return 404 (not 403)</li>
 * </ul>
 * <p>
 * Deletion triggers the SPEC-004 deletion flow. Memories are created exclusively
 * by the extraction pipeline (SPEC-004), not through the Father API.
 */
@RestController
@RequestMapping("/api/v1/fathers/me/memories")
public class MemoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Lists active memories for the authenticated father with cursor-based pagination.
     * <p>
     * Only ACTIVE memories are returned. SUPERSEDED, EXPIRED, and ARCHIVED states
     * are excluded. Results are ordered by importance_score descending.
     * <p>
     * Response fields NEVER include embeddings or raw confidence scores.
     *
     * @param actor    the authenticated actor (injected via @AuthActor)
     * @param cursor   opaque pagination cursor for the next page (null for first page)
     * @param pageSize number of items per page (default: 20, max: 100)
     * @return paginated list of active memories with cursor metadata
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listMemories(
            @AuthActor ActorContext actor,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {

        int effectivePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        MemoryService.MemoryPage page = memoryService.listActiveMemories(
                actor.getActorId(), cursor, effectivePageSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.items());
        response.put("next_cursor", page.nextCursor());
        response.put("has_more", page.hasMore());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single memory by ID.
     * <p>
     * Ownership is verified: if the memory does not belong to the authenticated
     * father, a 404 is returned (not 403) to prevent resource enumeration.
     * <p>
     * Response NEVER includes embeddings or raw confidence scores.
     *
     * @param actor    the authenticated actor (injected via @AuthActor)
     * @param memoryId the UUID of the memory to retrieve
     * @return the memory response DTO
     * @throws ResourceNotFoundException if the memory is not found or not owned by the actor
     */
    @GetMapping("/{memoryId}")
    public ResponseEntity<MemoryResponseDto> getMemory(
            @AuthActor ActorContext actor,
            @PathVariable UUID memoryId) {

        // Verify ownership before returning the resource
        UUID ownerId = memoryService.getMemoryOwnerId(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));

        RolePermission.assertOwnership(actor, ownerId, "Memory", memoryId);

        MemoryResponseDto memory = memoryService.getMemory(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));

        return ResponseEntity.ok(memory);
    }

    /**
     * Requests deletion of a specific memory.
     * <p>
     * The father can request deletion of their own memories. This triggers
     * the SPEC-004 deletion flow. The operation is naturally idempotent.
     * <p>
     * Ownership is verified: if the memory does not belong to the authenticated
     * father, a 404 is returned (not 403) to prevent resource enumeration.
     *
     * @param actor    the authenticated actor (injected via @AuthActor)
     * @param memoryId the UUID of the memory to delete
     * @return 204 No Content on successful deletion
     * @throws ResourceNotFoundException if the memory is not found or not owned by the actor
     */
    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deleteMemory(
            @AuthActor ActorContext actor,
            @PathVariable UUID memoryId) {

        // Verify ownership before performing deletion
        UUID ownerId = memoryService.getMemoryOwnerId(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));

        RolePermission.assertOwnership(actor, ownerId, "Memory", memoryId);

        memoryService.deleteMemory(memoryId);

        return ResponseEntity.noContent().build();
    }
}
