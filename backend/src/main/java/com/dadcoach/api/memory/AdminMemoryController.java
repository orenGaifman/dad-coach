package com.dadcoach.api.memory;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API controller for memory inspection.
 * <p>
 * Provides endpoints for viewing all memories (including archived) and audit history.
 * All endpoints are under {@code /api/v1/admin/memories} and require ADMIN role
 * (enforced via SecurityConfig).
 * <p>
 * Unlike the Father API which only shows ACTIVE memories, the admin memory view
 * includes memories in ALL states: ACTIVE, ARCHIVED, SUPERSEDED, EXPIRED.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>Admin read operations on memory data are audited (handled by ApiAuditAspect)</li>
 *   <li>Response NEVER includes embeddings or AI prompts (RESTRICTED fields)</li>
 *   <li>Confidence scores ARE visible to admins (INTERNAL sensitivity level)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/memories")
public class AdminMemoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminMemoryService adminMemoryService;

    public AdminMemoryController(AdminMemoryService adminMemoryService) {
        this.adminMemoryService = adminMemoryService;
    }

    /**
     * GET /api/v1/admin/memories?father_id={id} — Lists all memories for a father.
     * <p>
     * Returns memories in ALL states (ACTIVE, ARCHIVED, SUPERSEDED, EXPIRED)
     * unlike the Father API which only returns ACTIVE memories.
     * <p>
     * Supports optional filtering by state.
     * Results are ordered by created_at descending.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param fatherId the UUID of the father whose memories to list
     * @param state    optional state filter (ACTIVE, ARCHIVED, SUPERSEDED, EXPIRED)
     * @param cursor   opaque pagination cursor (null for first page)
     * @param pageSize number of items per page (default: 20, max: 100)
     * @return paginated list of admin memory DTOs
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listMemories(
            @AuthActor ActorContext actor,
            @RequestParam(value = "father_id") UUID fatherId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {

        int effectivePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        CursorPageResponse<AdminMemoryDto> page = adminMemoryService.listAllMemories(
                fatherId, state, cursor, effectivePageSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.getItems());
        response.put("next_cursor", page.getNextCursor());
        response.put("has_more", page.isHasMore());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/memories/{memoryId} — Retrieves full memory detail.
     * <p>
     * Returns the complete memory including state, confidence scores,
     * source conversation reference, and superseded-by reference.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param memoryId the UUID of the memory to retrieve
     * @return the full admin memory detail
     * @throws ResourceNotFoundException if the memory is not found
     */
    @GetMapping("/{memoryId}")
    public ResponseEntity<AdminMemoryDto> getMemoryDetail(
            @AuthActor ActorContext actor,
            @PathVariable UUID memoryId) {

        AdminMemoryDto memory = adminMemoryService.getMemoryDetail(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));

        return ResponseEntity.ok(memory);
    }

    /**
     * GET /api/v1/admin/memories/{memoryId}/audit — Retrieves audit history for a memory.
     * <p>
     * Returns all state transitions, modifications, and access events
     * for the memory throughout its lifecycle, ordered by timestamp descending.
     * This endpoint is audited by ApiAuditAspect.
     *
     * @param actor    the authenticated admin actor (injected via @AuthActor)
     * @param memoryId the UUID of the memory whose audit history to retrieve
     * @return list of audit entries for the memory
     * @throws ResourceNotFoundException if the memory is not found
     */
    @GetMapping("/{memoryId}/audit")
    public ResponseEntity<List<MemoryAuditEntryDto>> getMemoryAuditHistory(
            @AuthActor ActorContext actor,
            @PathVariable UUID memoryId) {

        // Verify memory exists before retrieving audit history
        adminMemoryService.getMemoryDetail(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));

        List<MemoryAuditEntryDto> auditHistory = adminMemoryService.getMemoryAuditHistory(memoryId);

        return ResponseEntity.ok(auditHistory);
    }
}
