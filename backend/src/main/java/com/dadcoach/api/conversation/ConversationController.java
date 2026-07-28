package com.dadcoach.api.conversation;

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
 * Read-only REST controller for conversations in the Father API.
 * <p>
 * Provides paginated listing of conversations and retrieval of individual
 * conversations with their message history. System prompts are filtered
 * from the message view — only father-visible messages are returned.
 * <p>
 * All endpoints enforce ownership: a father can only access their own conversations.
 * Ownership mismatches return 404 (not 403) to prevent resource enumeration.
 * <p>
 * This controller is read-only. Conversations are created exclusively by the
 * orchestration pipeline (SPEC-005), not through the Father API.
 */
@RestController
@RequestMapping("/api/v1/fathers/me/conversations")
public class ConversationController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Lists conversations for the authenticated father with cursor-based pagination.
     * <p>
     * Conversations are returned in descending order by creation date (most recent first).
     * Messages are NOT included in list results — use GET /{id} for message details.
     *
     * @param actor    the authenticated actor (injected via @AuthActor)
     * @param cursor   opaque pagination cursor for the next page (null for first page)
     * @param pageSize number of items per page (default: 20, max: 100)
     * @return paginated list of conversations with cursor metadata
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listConversations(
            @AuthActor ActorContext actor,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") int pageSize) {

        int effectivePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        ConversationService.ConversationPage page = conversationService.listConversations(
                actor.getActorId(), cursor, effectivePageSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.items());
        response.put("next_cursor", page.nextCursor());
        response.put("has_more", page.hasMore());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single conversation with its message history.
     * <p>
     * System prompts are filtered from the message list — only messages visible
     * to the father (INBOUND and OUTBOUND with non-system types) are included.
     * <p>
     * Ownership is verified: if the conversation does not belong to the authenticated
     * father, a 404 is returned (not 403) to prevent resource enumeration.
     *
     * @param actor          the authenticated actor (injected via @AuthActor)
     * @param conversationId the UUID of the conversation to retrieve
     * @return the conversation with filtered messages
     * @throws ResourceNotFoundException if the conversation is not found or not owned by the actor
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponseDto> getConversation(
            @AuthActor ActorContext actor,
            @PathVariable UUID conversationId) {

        // Verify ownership before returning the resource
        UUID ownerId = conversationService.getConversationOwnerId(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        RolePermission.assertOwnership(actor, ownerId, "Conversation", conversationId);

        ConversationResponseDto conversation = conversationService.getConversationWithMessages(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        return ResponseEntity.ok(conversation);
    }
}
