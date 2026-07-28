package com.dadcoach.workspace.aggregation;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only model representing conversation data needed by the workspace aggregation layer.
 *
 * // TODO: Wire to actual implementation from conversation domain when available
 */
public record ConversationReadModel(
        UUID conversationId,
        UUID fatherId,
        String type,
        Instant startedAt,
        Instant lastMessageAt,
        int messageCount,
        String summary,
        String status
) {}
