package com.dadcoach.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * External domain event representing a coaching conversation being completed.
 *
 * <p>This event is expected to be published by the Conversation Engine (SPEC-005) when
 * a conversation ends. The Growth System only awards a MEANINGFUL_CONVERSATION signal
 * if the quality rating exceeds 0.6 and the conversation has more than 5 exchanges
 * (handled by the GrowthSignalProcessor).</p>
 *
 * <p>This is a placeholder class that will eventually be owned by SPEC-005.
 * It is defined here to decouple the workspace from knowledge of other specs'
 * internal event structures.</p>
 */
public class ConversationCompletedEvent {

    private final UUID fatherId;
    private final UUID conversationId;
    private final String conversationType;
    private final int exchangeCount;
    private final double qualityRating;
    private final Instant completedAt;

    public ConversationCompletedEvent(UUID fatherId, UUID conversationId,
                                      String conversationType, int exchangeCount,
                                      double qualityRating, Instant completedAt) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId is required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }
        this.fatherId = fatherId;
        this.conversationId = conversationId;
        this.conversationType = conversationType;
        this.exchangeCount = exchangeCount;
        this.qualityRating = qualityRating;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getConversationType() {
        return conversationType;
    }

    public int getExchangeCount() {
        return exchangeCount;
    }

    public double getQualityRating() {
        return qualityRating;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
