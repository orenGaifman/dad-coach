package com.dadcoach.conversation.recovery;

import com.dadcoach.conversation.ConversationService;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.event.ConversationEventPublisher;
import com.dadcoach.conversation.memory.MemoryOrchestrator;
import com.dadcoach.conversation.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Detects and recovers stale ACTIVE conversations that have passed their expiration time.
 *
 * <p>Runs on application startup (to handle any conversations that expired while the service
 * was down) and periodically every 15 minutes. For each stale conversation:
 * <ol>
 *   <li>Transitions to EXPIRED with reason EXPIRATION</li>
 *   <li>Schedules memory extraction if the conversation had 2+ father messages</li>
 *   <li>Publishes a CONVERSATION_EXPIRED event</li>
 * </ol>
 *
 * <p>Cooldown handling after expiration is logged but not yet enforced (per requirements).
 */
@Component
public class ConversationRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationRecoveryService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MIN_FATHER_MESSAGES_FOR_EXTRACTION = 2;

    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final MemoryOrchestrator memoryOrchestrator;
    private final ConversationEventPublisher eventPublisher;

    public ConversationRecoveryService(ConversationRepository conversationRepository,
                                       ConversationService conversationService,
                                       MemoryOrchestrator memoryOrchestrator,
                                       ConversationEventPublisher eventPublisher) {
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.memoryOrchestrator = memoryOrchestrator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Runs on application startup to recover any conversations that expired
     * while the application was down.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("ConversationRecoveryService: running startup recovery check");
        detectAndRecoverStaleConversations();
    }

    /**
     * Runs every 15 minutes to detect and recover stale conversations.
     */
    @Scheduled(fixedRate = 900_000)
    public void scheduledRecoveryCheck() {
        log.debug("ConversationRecoveryService: running scheduled recovery check");
        detectAndRecoverStaleConversations();
    }

    /**
     * Core recovery logic: finds all ACTIVE conversations past their expires_at
     * and transitions them to EXPIRED.
     */
    void detectAndRecoverStaleConversations() {
        List<Conversation> staleConversations = conversationRepository
                .findByStatusAndExpiresAtBefore(STATUS_ACTIVE, Instant.now());

        if (staleConversations.isEmpty()) {
            log.debug("No stale conversations found");
            return;
        }

        log.info("Found {} stale conversations to recover", staleConversations.size());

        for (Conversation conversation : staleConversations) {
            try {
                recoverConversation(conversation);
            } catch (Exception e) {
                // Don't let one failure prevent processing others
                log.error("Failed to recover conversation {}: {}",
                        conversation.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Recovers a single stale conversation:
     * 1. Transitions to EXPIRED
     * 2. Schedules memory extraction if eligible
     * 3. Publishes CONVERSATION_EXPIRED event
     * 4. Logs cooldown info (not enforced yet)
     */
    private void recoverConversation(Conversation conversation) {
        log.info("Recovering stale conversation {} (type={}, father={}, expired_at={})",
                conversation.getId(), conversation.getType(),
                conversation.getFatherId(), conversation.getExpiresAt());

        // 1. Transition to EXPIRED
        Conversation expired = conversationService.expireConversation(conversation.getId());

        // 2. Schedule memory extraction if conversation had meaningful content
        if (conversation.getFatherMessageCount() >= MIN_FATHER_MESSAGES_FOR_EXTRACTION) {
            memoryOrchestrator.scheduleExtractionIfEligible(expired);
            log.debug("Scheduled memory extraction for expired conversation {} (fatherMessages={})",
                    conversation.getId(), conversation.getFatherMessageCount());
        }

        // 3. Publish CONVERSATION_EXPIRED event
        eventPublisher.publishConversationExpired(expired);

        // 4. Log cooldown (not enforced yet per requirements)
        log.debug("Cooldown after expiration for type={} conversation={} (not enforced yet)",
                conversation.getType(), conversation.getId());
    }
}
