package com.dadcoach.conversation.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Normalized internal format for an inbound message from the Communication Channel.
 * This is the single entry point into the Conversation Engine pipeline.
 * Channel-specific details are abstracted away by the time this DTO is created.
 *
 * @param channelId       Identifier of the communication channel (e.g., WhatsApp number)
 * @param senderId        External sender identifier (channel-specific)
 * @param content         The message text content
 * @param messageType     Type of message (e.g., TEXT, IMAGE, AUDIO)
 * @param idempotencyKey  Unique key for duplicate detection (channel message ID)
 * @param timestamp       When the message was originally sent
 * @param metadata        Additional channel-specific metadata
 */
public record InboundMessageDto(
        String channelId,
        String senderId,
        String content,
        String messageType,
        String idempotencyKey,
        Instant timestamp,
        Map<String, Object> metadata
) {

    public InboundMessageDto {
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channelId is required");
        if (senderId == null || senderId.isBlank()) throw new IllegalArgumentException("senderId is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (messageType == null || messageType.isBlank()) {
            messageType = "TEXT";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
