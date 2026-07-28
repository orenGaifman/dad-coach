package com.dadcoach.workspace;

import com.dadcoach.workspace.aggregation.ConversationsOverviewService;
import com.dadcoach.workspace.dto.response.RecentConversationsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * REST controller for conversation-related workspace endpoints.
 *
 * <p>Provides recent conversations overview for the authenticated father.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/conversations")
public class ConversationsController {

    private final ConversationsOverviewService conversationsOverviewService;

    public ConversationsController(ConversationsOverviewService conversationsOverviewService) {
        this.conversationsOverviewService = conversationsOverviewService;
    }

    /**
     * Returns recent conversations for the authenticated father.
     *
     * <p>Excludes system prompts and AI telemetry. Default limit is 10, max 50.</p>
     *
     * @param limit     maximum number of conversations to return (default 10, max 50)
     * @param principal the authenticated user
     * @return 200 OK with recent conversations response
     */
    @GetMapping
    public ResponseEntity<RecentConversationsResponse> getRecentConversations(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            Principal principal) {
        UUID fatherId = extractFatherId(principal);
        RecentConversationsResponse response = conversationsOverviewService
                .getRecentConversations(fatherId, limit);
        return ResponseEntity.ok(response);
    }

    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
