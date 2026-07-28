package com.dadcoach.channel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalized internal format for messages received from a communication provider.
 * This is the sole interface between the Communication_Channel and the Conversation_Engine
 * for inbound messages.
 *
 * @param messageId             unique identifier assigned by the Communication_Channel
 * @param idempotencyKey        derived from provider message identifier (for duplicate detection)
 * @param fatherChannelIdentity provider-specific sender identifier (e.g., E.164 phone number)
 * @param channel               identifier of the originating channel (e.g., WHATSAPP, SMS)
 * @param messageType           content classification of the message
 * @param textContent           message text (for TEXT type; null for media-only messages)
 * @param mediaReference        reference to media asset (for media types; null for text-only)
 * @param receivedAt            timestamp when the provider received the message from the father
 * @param ingestedAt            timestamp when the system accepted the message
 */
public record InboundMessageDto(
    UUID messageId,
    String idempotencyKey,
    String fatherChannelIdentity,
    String channel,
    MessageType messageType,
    String textContent,
    UUID mediaReference,
    Instant receivedAt,
    Instant ingestedAt
) {}
