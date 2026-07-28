package com.dadcoach.workspace.aggregation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Stub implementation of {@link ConversationDataService}.
 * Returns empty results until wired to the real Conversation domain layer.
 */
@Service
public class ConversationDataServiceImpl implements ConversationDataService {

    @Override
    public List<ConversationReadModel> getRecentConversations(UUID fatherId, int limit) {
        return List.of();
    }

    @Override
    public int countConversationsByFatherId(UUID fatherId) {
        return 0;
    }
}
