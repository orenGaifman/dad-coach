package com.dadcoach.conversation;

import com.dadcoach.conversation.ai.AiOrchestrator;
import com.dadcoach.conversation.ai.AiResult;
import com.dadcoach.conversation.ai.FallbackResponseProvider;
import com.dadcoach.conversation.context.ContextAssembler;
import com.dadcoach.conversation.context.ConversationContext;
import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.dto.OutboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.event.ConversationEventPublisher;
import com.dadcoach.conversation.memory.MemoryOrchestrator;
import com.dadcoach.conversation.mission.MissionOrchestrator;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.conversation.sideeffect.SideEffectScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main pipeline coordinator for the Conversation Engine.
 *
 * <p>Implements the complete orchestration pipeline defined in SPEC-005 Requirement 3:
 * idempotency → lock → resolve father → route conversation → context → AI → follow-up →
 * persist → evaluate → side-effects → record idempotency key.
 *
 * <p>Critical guarantees:
 * <ul>
 *   <li>Father ALWAYS receives a response (never fails silently)</li>
 *   <li>Single {@code @Transactional} method — commit releases the advisory lock</li>
 *   <li>Latency budget: 30 seconds total (enforced by lock timeout + AI timeout)</li>
 *   <li>Per-father serialization via PostgreSQL advisory lock</li>
 * </ul>
 *
 * <p>Dependencies that don't exist yet are declared as interfaces and will be
 * implemented in later tasks.
 */
@Service
public class ConversationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConversationOrchestrator.class);

    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";
    private static final String TYPE_ONBOARDING = "ONBOARDING";
    private static final String TYPE_DAILY_COACHING = "DAILY_COACHING";
    private static final String FOLLOW_UP_GENERATE_MISSION = "GENERATE_MISSION";
    private static final String FOLLOW_UP_CLOSE_CONVERSATION = "CLOSE_CONVERSATION";
    private static final String REASON_MAX_MESSAGES = "MAX_MESSAGES";
    private static final String REASON_OBJECTIVE_MET = "OBJECTIVE_MET";

    private final IdempotencyService idempotencyService;
    private final SessionLockService sessionLockService;
    private final FatherResolver fatherResolver;
    private final ConversationService conversationService;
    private final ContextAssembler contextAssembler;
    private final AiOrchestrator aiOrchestrator;
    private final MissionOrchestrator missionOrchestrator;
    private final MemoryOrchestrator memoryOrchestrator;
    private final SideEffectScheduler sideEffectScheduler;
    private final ConversationEventPublisher eventPublisher;
    private final FallbackResponseProvider fallbackProvider;
    private final ConversationMessageRepository messageRepository;

    /**
     * Maximum outbound messages per conversation before auto-completion.
     * Default: 8 (per SPEC-002 Requirement 10 criteria 3).
     */
    private final int maxOutboundMessages;

    public ConversationOrchestrator(
            IdempotencyService idempotencyService,
            SessionLockService sessionLockService,
            FatherResolver fatherResolver,
            ConversationService conversationService,
            ContextAssembler contextAssembler,
            AiOrchestrator aiOrchestrator,
            MissionOrchestrator missionOrchestrator,
            MemoryOrchestrator memoryOrchestrator,
            SideEffectScheduler sideEffectScheduler,
            ConversationEventPublisher eventPublisher,
            FallbackResponseProvider fallbackProvider,
            ConversationMessageRepository messageRepository,
            @Value("${conversation.pipeline.max-outbound-messages:8}") int maxOutboundMessages) {
        this.idempotencyService = idempotencyService;
        this.sessionLockService = sessionLockService;
        this.fatherResolver = fatherResolver;
        this.conversationService = conversationService;
        this.contextAssembler = contextAssembler;
        this.aiOrchestrator = aiOrchestrator;
        this.missionOrchestrator = missionOrchestrator;
        this.memoryOrchestrator = memoryOrchestrator;
        this.sideEffectScheduler = sideEffectScheduler;
        this.eventPublisher = eventPublisher;
        this.fallbackProvider = fallbackProvider;
        this.messageRepository = messageRepository;
        this.maxOutboundMessages = maxOutboundMessages;
    }

    /**
     * Main entry point for the conversation pipeline.
     * Called by the Communication Channel adapter after message normalization.
     *
     * <p>The entire pipeline executes within a single transaction. The PostgreSQL advisory
     * lock acquired in Step 2 is automatically released when the transaction commits.
     *
     * <p>Latency budget: 30 seconds total. Enforcement comes from the lock timeout (45s)
     * and AI provider timeouts configured in the AiOrchestrator.
     *
     * <p>The father ALWAYS receives a response. Any unhandled exception is caught at the
     * top level and produces a fallback response.
     *
     * @param message the normalized inbound message from the Communication Channel
     * @return the outbound message to deliver to the father
     */
    @Transactional
    public OutboundMessageDto processMessage(InboundMessageDto message) {
        try {
            return executeMainPipeline(message);
        } catch (Exception e) {
            // AD-5: Fallback-First Error Strategy
            // Father ALWAYS receives a response — never fails silently.
            log.error("Unhandled exception in conversation pipeline for sender={}. Delivering fallback response.",
                    message.senderId(), e);
            return buildFallbackResponse(message, null);
        }
    }

    // -----------------------------------------------------------------------
    // Private pipeline implementation
    // -----------------------------------------------------------------------

    private OutboundMessageDto executeMainPipeline(InboundMessageDto message) {
        // Step 1: Check idempotency — if duplicate, return cached response immediately
        Optional<OutboundMessageDto> cached = idempotencyService.checkDuplicate(
                message.idempotencyKey(), message.senderId());
        if (cached.isPresent()) {
            log.debug("Duplicate message detected for key '{}'. Returning cached response.",
                    message.idempotencyKey());
            return cached.get();
        }

        // Step 2: Resolve father — identify who sent this message
        FatherResolver.ResolvedFather father = resolveFather(message);
        UUID fatherId = father.fatherId();

        // Step 3: Acquire session lock — blocks until available or timeout
        // Lock is transaction-scoped: released automatically on COMMIT or ROLLBACK.
        sessionLockService.acquireLock(fatherId);

        // Step 4: Route conversation — find or create the appropriate conversation
        Conversation conversation = routeConversation(fatherId, father.status());

        // Step 5: Load context — assemble all subsystem data for AI
        ConversationContext context = contextAssembler.assembleContext(fatherId, conversation, message);

        // Step 6: AI orchestration — safety → generate → validate → retry/fallback
        AiResult aiResult = aiOrchestrator.orchestrate(context, message);

        // Step 7: Process follow-up action from AI response
        processFollowUpAction(aiResult.suggestedFollowUpAction(), fatherId, conversation);

        // Step 8: Persist state — save inbound + outbound messages, update counters
        UUID outboundMessageId = persistConversationState(message, aiResult, conversation);

        // Step 9: Evaluate completion — check 8-message cap, objective met
        evaluateCompletion(conversation);

        // Step 10: Schedule side-effects — write to outbox (memory tracking, events)
        scheduleSideEffects(fatherId, conversation, aiResult);

        // Step 11: Record idempotency key — marks this message as processed
        idempotencyService.recordProcessed(message.idempotencyKey(), fatherId, outboundMessageId);

        // Build and return the outbound message
        // COMMIT releases the advisory lock automatically.
        return buildOutboundMessage(message, aiResult, conversation, outboundMessageId);
    }

    /**
     * Resolves the father from the inbound message's sender identity.
     * If unknown, creates a new Father with status NOT_STARTED.
     */
    private FatherResolver.ResolvedFather resolveFather(InboundMessageDto message) {
        Optional<FatherResolver.ResolvedFather> existing =
                fatherResolver.findBySenderIdentity(message.senderId(), message.channelId());

        if (existing.isPresent()) {
            return existing.get();
        }

        // Unknown sender — create new Father + will start ONBOARDING
        log.info("Unknown sender '{}' on channel '{}'. Creating new Father.",
                message.senderId(), message.channelId());
        return fatherResolver.createNewFather(message.senderId(), message.channelId());
    }

    /**
     * Routes the message to an appropriate conversation:
     * - If active conversation exists and is not expired → use it
     * - If active conversation exists and is expired → expire it, create new
     * - If no active conversation → create new (ONBOARDING for new fathers, DAILY_COACHING otherwise)
     */
    private Conversation routeConversation(UUID fatherId, String fatherStatus) {
        Optional<Conversation> activeConversation = conversationService.findActiveConversation(fatherId);

        if (activeConversation.isPresent()) {
            Conversation conversation = activeConversation.get();

            // Check if the active conversation has expired
            if (conversationService.isExpired(conversation)) {
                log.info("Active conversation {} for father {} has expired. Transitioning to EXPIRED.",
                        conversation.getId(), fatherId);
                conversationService.expireConversation(conversation.getId());
                eventPublisher.publishConversationExpired(conversation);

                // Schedule memory extraction if enough father messages
                memoryOrchestrator.scheduleExtraction(conversation);

                // Create a new conversation
                return createNewConversation(fatherId, fatherStatus);
            }

            // Active and valid — route to it
            return conversation;
        }

        // No active conversation — create new
        return createNewConversation(fatherId, fatherStatus);
    }

    /**
     * Creates a new conversation based on the father's status.
     * New/NOT_STARTED fathers get ONBOARDING; others get DAILY_COACHING.
     */
    private Conversation createNewConversation(UUID fatherId, String fatherStatus) {
        String conversationType = determineConversationType(fatherStatus);
        Conversation conversation = conversationService.createConversation(fatherId, conversationType);
        eventPublisher.publishConversationStarted(conversation);
        log.info("Created new {} conversation {} for father {}",
                conversationType, conversation.getId(), fatherId);
        return conversation;
    }

    /**
     * Determines the conversation type based on father status.
     * NOT_STARTED fathers get ONBOARDING; all others default to DAILY_COACHING.
     */
    private String determineConversationType(String fatherStatus) {
        if ("NOT_STARTED".equals(fatherStatus)) {
            return TYPE_ONBOARDING;
        }
        return TYPE_DAILY_COACHING;
    }

    /**
     * Processes the AI's suggested follow-up action.
     * The orchestration layer decides — the AI only recommends.
     */
    private void processFollowUpAction(String action, UUID fatherId, Conversation conversation) {
        if (action == null || "NONE".equals(action) || "ASK_QUESTION".equals(action)) {
            return;
        }

        switch (action) {
            case FOLLOW_UP_GENERATE_MISSION -> missionOrchestrator.generateMission(fatherId, conversation);
            case FOLLOW_UP_CLOSE_CONVERSATION -> missionOrchestrator.closeConversation(conversation);
            default -> log.warn("Unknown follow-up action '{}' from AI. Ignoring.", action);
        }
    }

    /**
     * Persists the inbound and outbound messages, updates conversation counters.
     * Returns the UUID of the persisted outbound message.
     */
    private UUID persistConversationState(InboundMessageDto inbound, AiResult aiResult,
                                          Conversation conversation) {
        UUID conversationId = conversation.getId();
        int currentCount = messageRepository.countByConversationId(conversationId);

        // Persist inbound message
        ConversationMessage inboundMsg = ConversationMessage.builder()
                .conversationId(conversationId)
                .direction(DIRECTION_INBOUND)
                .content(inbound.content())
                .messageType(inbound.messageType())
                .metadata(inbound.metadata())
                .sequenceNumber(currentCount + 1)
                .build();
        messageRepository.save(inboundMsg);
        conversationService.incrementMessageCount(conversationId, DIRECTION_INBOUND);

        // Persist outbound message
        Map<String, Object> outboundMetadata = new HashMap<>(aiResult.metadata());
        outboundMetadata.put("fallback_used", aiResult.fallbackUsed());
        outboundMetadata.put("retried", aiResult.retried());

        ConversationMessage outboundMsg = ConversationMessage.builder()
                .conversationId(conversationId)
                .direction(DIRECTION_OUTBOUND)
                .content(aiResult.responseContent())
                .messageType("TEXT")
                .metadata(outboundMetadata)
                .sequenceNumber(currentCount + 2)
                .build();
        ConversationMessage savedOutbound = messageRepository.save(outboundMsg);
        conversationService.incrementMessageCount(conversationId, DIRECTION_OUTBOUND);

        return savedOutbound.getId();
    }

    /**
     * Evaluates whether the conversation should be completed after this exchange.
     * Checks: 8 outbound message cap reached, or objective met (via follow-up action).
     */
    private void evaluateCompletion(Conversation conversation) {
        if (conversation.getSystemMessageCount() >= maxOutboundMessages) {
            log.info("Conversation {} reached max outbound messages ({}). Completing.",
                    conversation.getId(), maxOutboundMessages);
            conversationService.completeConversation(conversation.getId(), REASON_MAX_MESSAGES);
            eventPublisher.publishConversationCompleted(conversation);
            memoryOrchestrator.scheduleExtraction(conversation);
        }
    }

    /**
     * Schedules asynchronous side-effects to be processed after transaction commit.
     * Written to the outbox within the same transaction.
     */
    private void scheduleSideEffects(UUID fatherId, Conversation conversation, AiResult aiResult) {
        UUID conversationId = conversation.getId();

        // Schedule memory injection tracking
        sideEffectScheduler.schedule("MEMORY_INJECTION_TRACKING", fatherId, conversationId,
                Map.of("conversation_type", conversation.getType()));

        // If fallback was used, schedule deferred AI regeneration
        if (aiResult.fallbackUsed()) {
            sideEffectScheduler.schedule("DEFERRED_AI_REGENERATION", fatherId, conversationId,
                    Map.of("reason", "fallback_used"));
        }
    }

    /**
     * Builds the OutboundMessageDto for delivery to the father.
     */
    private OutboundMessageDto buildOutboundMessage(InboundMessageDto inbound, AiResult aiResult,
                                                     Conversation conversation, UUID outboundMessageId) {
        Map<String, Object> metadata = new HashMap<>(aiResult.metadata());
        metadata.put("outbound_message_id", outboundMessageId.toString());
        metadata.put("fallback_used", aiResult.fallbackUsed());
        metadata.put("retried", aiResult.retried());

        return new OutboundMessageDto(
                inbound.senderId(),
                aiResult.responseContent(),
                "TEXT",
                conversation.getId(),
                metadata
        );
    }

    /**
     * Builds a fallback response when an unhandled error occurs.
     * Ensures the father always receives a message.
     */
    private OutboundMessageDto buildFallbackResponse(InboundMessageDto message, Conversation conversation) {
        String fallbackContent;
        try {
            fallbackContent = fallbackProvider.getGenericFallback();
        } catch (Exception e) {
            // Last-resort: if even the fallback provider fails, use a hardcoded message
            log.error("FallbackResponseProvider failed. Using hardcoded last-resort message.", e);
            fallbackContent = "Sorry, we're experiencing technical difficulties. Please try again later.";
        }

        if (fallbackContent == null || fallbackContent.isBlank()) {
            fallbackContent = "Sorry, we're experiencing technical difficulties. Please try again later.";
        }

        UUID conversationId = (conversation != null) ? conversation.getId() : null;

        return new OutboundMessageDto(
                message.senderId(),
                fallbackContent,
                "TEXT",
                conversationId,
                Map.of("fallback_used", true, "error_recovery", true)
        );
    }
}
