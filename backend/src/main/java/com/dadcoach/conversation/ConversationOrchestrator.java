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
import com.dadcoach.domain.flash.FlashMissionService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender.DashboardLinkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /**
     * Trigger phrases for flash missions (Hebrew and English).
     * When father sends these, immediately return a flash mission.
     */
    private static final Set<String> FLASH_TRIGGERS_EXACT = Set.of(
            "עכשיו", "now", "יש לי דקה", "יש לי רגע", "פנוי", "פנויה",
            "i have a minute", "got a moment", "free now", "בזק"
    );
    private static final Pattern FLASH_TRIGGERS_PATTERN = Pattern.compile(
            "(עכשיו|now|פנוי|free|בזק|יש לי (דקה|רגע|זמן)|got (a )?moment)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

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
    private final FlashMissionService flashMissionService;
    private final FatherRepository fatherRepository;
    private final DashboardLinkAppender dashboardLinkAppender;

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
            FlashMissionService flashMissionService,
            FatherRepository fatherRepository,
            DashboardLinkAppender dashboardLinkAppender,
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
        this.flashMissionService = flashMissionService;
        this.fatherRepository = fatherRepository;
        this.dashboardLinkAppender = dashboardLinkAppender;
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

        // Step 4.5: Check for flash mission trigger BEFORE AI orchestration
        Optional<OutboundMessageDto> flashResponse = checkFlashMissionTrigger(message, fatherId, conversation);
        if (flashResponse.isPresent()) {
            // Record idempotency and return flash mission directly
            idempotencyService.recordProcessed(message.idempotencyKey(), fatherId, null);
            return flashResponse.get();
        }

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
        return buildOutboundMessage(message, aiResult, conversation, outboundMessageId, fatherId);
    }

    /**
     * Checks if the message is a flash mission trigger ("עכשיו", "now", etc.)
     * If so, returns an immediate flash mission response without going through AI.
     */
    private Optional<OutboundMessageDto> checkFlashMissionTrigger(InboundMessageDto message,
                                                                   UUID fatherId,
                                                                   Conversation conversation) {
        String content = message.content().trim().toLowerCase();

        // Check exact matches first
        boolean isFlashTrigger = FLASH_TRIGGERS_EXACT.contains(content);

        // Check pattern match for short messages (< 20 chars to avoid false positives)
        if (!isFlashTrigger && content.length() < 20) {
            isFlashTrigger = FLASH_TRIGGERS_PATTERN.matcher(content).find();
        }

        if (!isFlashTrigger) {
            return Optional.empty();
        }

        // Extract the Long ID from the UUID (reverse of FatherResolverImpl.deriveUuid)
        // UUID is created as new UUID(0L, domainId), so getLeastSignificantBits() returns the domain ID
        Long fatherDomainId = fatherId.getLeastSignificantBits();
        
        // Verify the father exists
        Optional<Father> fatherOpt = fatherRepository.findById(fatherDomainId);
        if (fatherOpt.isEmpty()) {
            log.warn("Flash trigger detected but father not found: UUID={}, domainId={}", fatherId, fatherDomainId);
            return Optional.empty();
        }

        Father fatherEntity = fatherOpt.get();
        log.info("Flash mission trigger detected for father {}: '{}'", fatherEntity.getId(), content);

        try {
            FlashMissionService.FlashMissionSuggestion suggestion =
                    flashMissionService.getFlashMission(fatherEntity.getId(), null);

            String responseContent = suggestion.toMessage();

            return Optional.of(new OutboundMessageDto(
                    message.senderId(),
                    responseContent,
                    "TEXT",
                    conversation.getId(),
                    Map.of("flash_mission", true,
                           "template_id", suggestion.templateId() != null ? suggestion.templateId() : "",
                           "child_id", suggestion.childId() != null ? suggestion.childId() : "")
            ));
        } catch (Exception e) {
            log.error("Failed to get flash mission for father {}", fatherEntity.getId(), e);
            return Optional.empty(); // Fall through to normal AI flow
        }
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
     * Appends a dashboard link for DAILY_COACHING conversations when appropriate.
     */
    private OutboundMessageDto buildOutboundMessage(InboundMessageDto inbound, AiResult aiResult,
                                                     Conversation conversation, UUID outboundMessageId,
                                                     UUID fatherId) {
        Map<String, Object> metadata = new HashMap<>(aiResult.metadata());
        metadata.put("outbound_message_id", outboundMessageId.toString());
        metadata.put("fallback_used", aiResult.fallbackUsed());
        metadata.put("retried", aiResult.retried());

        String responseContent = aiResult.responseContent();

        // Append dashboard link for DAILY_COACHING conversations (not onboarding)
        // Include link when: mission is generated, conversation is closed, or periodically
        if (TYPE_DAILY_COACHING.equals(conversation.getType()) && !aiResult.fallbackUsed()) {
            try {
                responseContent = appendDashboardLinkIfNeeded(responseContent, fatherId, 
                        aiResult.suggestedFollowUpAction(), conversation);
                metadata.put("dashboard_link_included", true);
            } catch (Exception e) {
                // Non-critical: log and continue without dashboard link
                log.warn("Failed to append dashboard link for father {}: {}", fatherId, e.getMessage());
                metadata.put("dashboard_link_included", false);
            }
        }

        return new OutboundMessageDto(
                inbound.senderId(),
                responseContent,
                "TEXT",
                conversation.getId(),
                metadata
        );
    }

    /**
     * Appends a dashboard link to the response content.
     * 
     * Links are added to EVERY DAILY_COACHING message so fathers always
     * have easy access to their dashboard from WhatsApp.
     * 
     * @return The response content with dashboard link appended
     */
    private String appendDashboardLinkIfNeeded(String responseContent, UUID fatherId, 
                                                String followUpAction, Conversation conversation) {
        // Always include a dashboard link for daily coaching messages
        // Choose context based on the follow-up action for more relevant messaging
        DashboardLinkContext linkContext;

        if (FOLLOW_UP_GENERATE_MISSION.equals(followUpAction)) {
            linkContext = DashboardLinkContext.WEEKLY_CHECKIN;
        } else if (FOLLOW_UP_CLOSE_CONVERSATION.equals(followUpAction)) {
            linkContext = DashboardLinkContext.QUALITY_TIME_LOGGED;
        } else {
            // Default: link to dashboard for easy access
            linkContext = DashboardLinkContext.WEEKLY_CHECKIN;
        }

        // Extract the Long ID from the UUID
        Long fatherDomainId = fatherId.getLeastSignificantBits();

        // Generate and append the dashboard link
        String linkMessage = dashboardLinkAppender.generateLinkMessage(fatherDomainId, linkContext);
        
        log.debug("Appending dashboard link for father {} with context {}", fatherDomainId, linkContext);
        
        return responseContent + "\n\n" + linkMessage;
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
