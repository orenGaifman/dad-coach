package com.dadcoach.conversation;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link MessageProcessor} that validates inbound messages
 * and routes them to the appropriate conversation.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Content must be non-empty and max 4096 characters</li>
 *   <li>Sender must be identifiable (senderId + channelId required)</li>
 *   <li>Idempotency key must be present</li>
 * </ul>
 *
 * <p>Routing logic:
 * <ol>
 *   <li>Resolve father by channel identity (or create new father)</li>
 *   <li>If active conversation exists and not expired → route to it</li>
 *   <li>If active conversation exists but expired → expire it, create new</li>
 *   <li>If no active conversation → create new (type evaluated)</li>
 * </ol>
 *
 * <p>Message batching (3+ messages in 10s combined after 5s wait) is stubbed
 * for future implementation.
 */
@Service
public class MessageProcessorImpl implements MessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessorImpl.class);

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final String DEFAULT_NEW_CONVERSATION_TYPE = "DAILY_COACHING";
    private static final String ONBOARDING_TYPE = "ONBOARDING";

    private final FatherResolver fatherResolver;
    private final ConversationService conversationService;

    public MessageProcessorImpl(FatherResolver fatherResolver,
                                ConversationService conversationService) {
        this.fatherResolver = fatherResolver;
        this.conversationService = conversationService;
    }

    @Override
    public RoutingResult validateAndRoute(InboundMessageDto message) {
        // Step 1: Validate message format
        validateMessage(message);

        // Step 2: Resolve father
        FatherResolver.ResolvedFather resolvedFather = resolveFather(message);
        boolean isNewFather = "NOT_STARTED".equals(resolvedFather.status());

        // Step 3: Route to conversation
        Conversation conversation = routeToConversation(resolvedFather, isNewFather);

        log.debug("Routed message from sender={} to conversation={} (father={}, newFather={})",
                message.senderId(), conversation.getId(), resolvedFather.fatherId(), isNewFather);

        return new RoutingResult(resolvedFather.fatherId(), conversation, isNewFather);
    }

    /**
     * Validates the inbound message format.
     * Throws MessageValidationException with clear error on failure.
     */
    private void validateMessage(InboundMessageDto message) {
        if (message == null) {
            throw new MessageValidationException("Message cannot be null");
        }

        // Content validation
        if (message.content() == null || message.content().isBlank()) {
            throw new MessageValidationException(
                    "Message content cannot be empty", "content");
        }
        if (message.content().length() > MAX_CONTENT_LENGTH) {
            throw new MessageValidationException(
                    String.format("Message content exceeds maximum length of %d characters (actual: %d)",
                            MAX_CONTENT_LENGTH, message.content().length()),
                    "content");
        }

        // Sender identification
        if (message.senderId() == null || message.senderId().isBlank()) {
            throw new MessageValidationException(
                    "Sender must be identifiable (senderId is required)", "senderId");
        }
        if (message.channelId() == null || message.channelId().isBlank()) {
            throw new MessageValidationException(
                    "Channel must be identifiable (channelId is required)", "channelId");
        }

        // Idempotency key
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()) {
            throw new MessageValidationException(
                    "Idempotency key is required for duplicate detection", "idempotencyKey");
        }
    }

    /**
     * Resolves the father by channel identity. Creates a new father if unknown.
     */
    private FatherResolver.ResolvedFather resolveFather(InboundMessageDto message) {
        Optional<FatherResolver.ResolvedFather> existing =
                fatherResolver.findBySenderIdentity(message.senderId(), message.channelId());

        if (existing.isPresent()) {
            return existing.get();
        }

        // Unknown sender — create new father
        log.info("Unknown sender (senderId={}, channelId={}), creating new father",
                message.senderId(), message.channelId());
        return fatherResolver.createNewFather(message.senderId(), message.channelId());
    }

    /**
     * Routes the message to the appropriate conversation.
     * Handles expiration detection and new conversation creation.
     */
    private Conversation routeToConversation(FatherResolver.ResolvedFather father, boolean isNewFather) {
        UUID fatherId = father.fatherId();

        // New fathers always start with ONBOARDING
        if (isNewFather) {
            return conversationService.createConversation(fatherId, ONBOARDING_TYPE);
        }

        // Check for existing active conversation
        Optional<Conversation> activeConversation = conversationService.findActiveConversation(fatherId);

        if (activeConversation.isPresent()) {
            Conversation conversation = activeConversation.get();

            // Check if expired
            if (conversationService.isExpired(conversation)) {
                log.info("Active conversation {} has expired, transitioning and creating new",
                        conversation.getId());
                conversationService.expireConversation(conversation.getId());
                return conversationService.createConversation(fatherId, evaluateConversationType(father));
            }

            // Route to existing active conversation
            return conversation;
        }

        // No active conversation — create new
        return conversationService.createConversation(fatherId, evaluateConversationType(father));
    }

    /**
     * Evaluates what type of conversation to start for a father.
     * NOT_STARTED fathers get ONBOARDING; all others default to DAILY_COACHING.
     * Additional type evaluation (e.g., scheduled reflections, inactivity checks)
     * is triggered by the Scheduling System via explicit conversation triggers.
     */
    private String evaluateConversationType(FatherResolver.ResolvedFather father) {
        if ("NOT_STARTED".equals(father.status()) || "ONBOARDING".equals(father.status())) {
            return ONBOARDING_TYPE;
        }
        return DEFAULT_NEW_CONVERSATION_TYPE;
    }
}
