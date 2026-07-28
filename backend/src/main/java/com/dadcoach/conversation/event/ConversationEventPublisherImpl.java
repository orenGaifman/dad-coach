package com.dadcoach.conversation.event;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.sideeffect.SideEffect;
import com.dadcoach.conversation.sideeffect.SideEffectScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link ConversationEventPublisher} that writes business events
 * to the outbox as mandatory side-effects via {@link SideEffectScheduler}.
 *
 * <p>Events are written within the caller's transaction boundary, guaranteeing
 * atomic commit with conversation state changes. The SideEffectProcessor delivers
 * them asynchronously with unlimited retries (mandatory side-effect).
 *
 * <p>Event publication failure (e.g., serialization issue) is logged but does NOT
 * block the conversation response — the father always receives their reply.
 */
@Service
public class ConversationEventPublisherImpl implements ConversationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConversationEventPublisherImpl.class);

    private final SideEffectScheduler sideEffectScheduler;

    public ConversationEventPublisherImpl(SideEffectScheduler sideEffectScheduler) {
        this.sideEffectScheduler = sideEffectScheduler;
    }

    @Override
    public void publishConversationStarted(Conversation conversation) {
        publishEvent(ConversationEvent.CONVERSATION_STARTED, conversation);
    }

    @Override
    public void publishConversationCompleted(Conversation conversation) {
        publishEvent(ConversationEvent.CONVERSATION_COMPLETED, conversation);
    }

    @Override
    public void publishConversationExpired(Conversation conversation) {
        publishEvent(ConversationEvent.CONVERSATION_EXPIRED, conversation);
    }

    /**
     * Builds the event payload and schedules it via the outbox.
     * Failures are caught and logged — never propagated to the caller.
     */
    private void publishEvent(String eventType, Conversation conversation) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_type", eventType);
            payload.put("conversation_id", conversation.getId().toString());
            payload.put("father_id", conversation.getFatherId().toString());
            payload.put("conversation_type", conversation.getType());
            payload.put("completion_reason", conversation.getCompletionReason());
            payload.put("timestamp", Instant.now().toString());

            sideEffectScheduler.schedule(
                    SideEffect.EVENT_PUBLISH,
                    conversation.getFatherId(),
                    conversation.getId(),
                    payload
            );

            log.debug("Scheduled {} event for conversation {} (father={})",
                    eventType, conversation.getId(), conversation.getFatherId());
        } catch (Exception e) {
            // Event publication failure must NOT block conversation response
            log.error("Failed to schedule {} event for conversation {}: {}",
                    eventType, conversation.getId(), e.getMessage(), e);
        }
    }
}
