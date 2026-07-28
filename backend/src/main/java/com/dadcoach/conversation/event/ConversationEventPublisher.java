package com.dadcoach.conversation.event;

import com.dadcoach.conversation.entity.Conversation;

/**
 * Emits business events via the outbox as mandatory side-effects.
 * Events are written to the outbox within the same transaction as conversation state changes,
 * ensuring they are committed atomically. The SideEffectProcessor delivers them asynchronously.
 *
 * <p>Events include: CONVERSATION_STARTED, CONVERSATION_COMPLETED, CONVERSATION_EXPIRED.
 */
public interface ConversationEventPublisher {

    /**
     * Publishes a CONVERSATION_STARTED event.
     */
    void publishConversationStarted(Conversation conversation);

    /**
     * Publishes a CONVERSATION_COMPLETED event.
     */
    void publishConversationCompleted(Conversation conversation);

    /**
     * Publishes a CONVERSATION_EXPIRED event.
     */
    void publishConversationExpired(Conversation conversation);
}
