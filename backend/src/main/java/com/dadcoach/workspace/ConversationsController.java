package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.aggregation.ConversationsOverviewService;
import com.dadcoach.workspace.dto.response.RecentConversationsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * @param limit maximum number of conversations to return (default 10, max 50)
     * @param actor the authenticated actor context
     * @return 200 OK with recent conversations response
     */
    @GetMapping
    public ResponseEntity<RecentConversationsResponse> getRecentConversations(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        RecentConversationsResponse response = conversationsOverviewService
                .getRecentConversations(fatherId, limit);
        return ResponseEntity.ok(response);
    }
}
