package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.dto.response.RecentConversationsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates conversation data for the workspace conversations overview.
 *
 * <p>Provides recent conversations for the father, excluding system prompts
 * and AI telemetry. Default limit is 10, maximum 50.</p>
 */
@Service
public class ConversationsOverviewService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ConversationDataService conversationDataService;

    public ConversationsOverviewService(ConversationDataService conversationDataService) {
        this.conversationDataService = conversationDataService;
    }

    /**
     * Retrieves recent conversations for a father.
     *
     * <p>Excludes system prompts and AI telemetry conversations.
     * Default limit is 10, maximum is 50.</p>
     *
     * @param fatherId the father's unique identifier
     * @param limit    maximum number of conversations to return (clamped to 1-50)
     * @return the recent conversations response
     */
    public RecentConversationsResponse getRecentConversations(UUID fatherId, int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<ConversationReadModel> conversations = conversationDataService
                .getRecentConversations(fatherId, effectiveLimit);

        List<RecentConversationsResponse.ConversationItem> items = conversations.stream()
                .map(this::buildConversationItem)
                .toList();

        return new RecentConversationsResponse(items, items.size());
    }

    private RecentConversationsResponse.ConversationItem buildConversationItem(ConversationReadModel conv) {
        return new RecentConversationsResponse.ConversationItem(
                conv.conversationId(),
                conv.type(),
                conv.startedAt(),
                conv.lastMessageAt(),
                conv.messageCount(),
                conv.summary(),
                conv.status()
        );
    }
}
