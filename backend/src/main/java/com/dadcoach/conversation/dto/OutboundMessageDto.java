package com.dadcoach.conversation.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Normalized internal format for an outbound message to the Communication Channel.
 * Produced by the Conversation Engine after pipeline execution.
 * The Communication Channel is responsible for delivery via the appropriate adapter.
 *
 * @param recipientId    External recipient identifier (channel-specific, e.g., phone number)
 * @param content        The message text content to deliver
 * @param messageType    Type of message (e.g., TEXT)
 * @param conversationId The conversation this message belongs to
 * @param metadata       Additional metadata (model_used, latency_ms, fallback_used, etc.)
 */
public record OutboundMessageDto(
        String recipientId,
        String content,
        String messageType,
        UUID conversationId,
        Map<String, Object> metadata
) {

    public OutboundMessageDto {
        if (recipientId == null || recipientId.isBlank()) throw new IllegalArgumentException("recipientId is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        if (messageType == null || messageType.isBlank()) {
            messageType = "TEXT";
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
