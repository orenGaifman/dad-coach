package com.dadcoach.channel.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Normalized internal format for messages to be delivered to a communication provider.
 * This is the sole interface between the Conversation_Engine and the Communication_Channel
 * for outbound messages.
 *
 * @param messageId          unique identifier assigned by the Conversation_Engine
 * @param fatherId           internal father identifier (endpoint resolution handled by Communication_Channel)
 * @param channel            optional target delivery channel (null = deliver to primary endpoint)
 * @param messageType        content classification of the message
 * @param textContent        the message text
 * @param mediaReference     reference to media asset (if applicable)
 * @param isTemplate         whether a template message is required
 * @param templateName       template identifier (if isTemplate = true)
 * @param templateParameters key-value pairs for template variable substitution
 * @param priority           IMMEDIATE (conversation reply) or SCHEDULED (proactive notification)
 * @param requestedAt        timestamp when the Conversation_Engine requested delivery
 */
public record OutboundMessageDto(
    UUID messageId,
    UUID fatherId,
    String channel,
    MessageType messageType,
    String textContent,
    UUID mediaReference,
    boolean isTemplate,
    String templateName,
    Map<String, String> templateParameters,
    MessagePriority priority,
    Instant requestedAt
) {}
