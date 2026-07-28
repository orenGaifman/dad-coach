package com.dadcoach.conversation;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;

import java.util.UUID;

/**
 * Validates inbound messages and routes them to the appropriate conversation.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate message format: non-empty content, identifiable sender, max 4096 chars</li>
 *   <li>Resolve father by channel identity</li>
 *   <li>Route to active conversation if exists and not expired</li>
 *   <li>Create new conversation if none active</li>
 *   <li>Handle message batching (stub)</li>
 *   <li>Reject malformed messages with clear error</li>
 * </ul>
 */
public interface MessageProcessor {

    /**
     * Result of processing and routing an inbound message.
     *
     * @param fatherId     the resolved father UUID
     * @param conversation the conversation the message was routed to (existing or newly created)
     * @param isNewFather  true if a new father was created for this message
     */
    record RoutingResult(UUID fatherId, Conversation conversation, boolean isNewFather) {}

    /**
     * Validates the inbound message and routes it to a conversation.
     *
     * @param message the inbound message to process
     * @return the routing result with resolved father and target conversation
     * @throws MessageValidationException if the message is malformed
     */
    RoutingResult validateAndRoute(InboundMessageDto message);
}
