package com.dadcoach.conversation;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ConversationService} managing conversation lifecycle,
 * state transitions, and message counting.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Maximum 1 ACTIVE conversation per father</li>
 *   <li>DIFFICULT_SITUATION preemption of existing active conversations</li>
 *   <li>Valid status transitions only (ACTIVE → COMPLETED/EXPIRED/ABANDONED)</li>
 *   <li>Configurable expiration windows per conversation type</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(ConversationProperties.class)
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_ABANDONED = "ABANDONED";

    private static final String REASON_PREEMPTED = "PREEMPTED";
    private static final String REASON_EXPIRATION = "EXPIRATION";
    private static final String REASON_ABANDONED = "ABANDONED";

    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";

    private static final String TYPE_DIFFICULT_SITUATION = "DIFFICULT_SITUATION";

    private final ConversationRepository conversationRepository;
    private final ConversationProperties conversationProperties;

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   ConversationProperties conversationProperties) {
        this.conversationRepository = conversationRepository;
        this.conversationProperties = conversationProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findActiveConversation(UUID fatherId) {
        return conversationRepository.findActiveByFatherId(fatherId);
    }

    @Override
    @Transactional
    public Conversation createConversation(UUID fatherId, String type) {
        Optional<Conversation> activeConversation = conversationRepository.findActiveByFatherId(fatherId);

        if (activeConversation.isPresent()) {
            if (TYPE_DIFFICULT_SITUATION.equals(type)) {
                // Preempt existing active conversation
                Conversation existing = activeConversation.get();
                log.info("Preempting active conversation {} for father {} due to DIFFICULT_SITUATION",
                        existing.getId(), fatherId);
                transitionToCompleted(existing, REASON_PREEMPTED);
            } else {
                throw new IllegalStateException(
                        String.format("Father %s already has an active conversation (id=%s). " +
                                "Only DIFFICULT_SITUATION can preempt an active conversation.", fatherId,
                                activeConversation.get().getId()));
            }
        }

        // Calculate expiration timestamp
        Duration expirationWindow = conversationProperties.getExpirationWindow(type);
        Instant expiresAt = (expirationWindow != null) ? Instant.now().plus(expirationWindow) : null;

        Conversation conversation = Conversation.builder()
                .fatherId(fatherId)
                .type(type)
                .status(STATUS_ACTIVE)
                .expiresAt(expiresAt)
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("Created conversation {} of type {} for father {} (expires: {})",
                saved.getId(), type, fatherId, expiresAt);
        return saved;
    }

    @Override
    @Transactional
    public Conversation completeConversation(UUID conversationId, String reason) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateTransition(conversation, STATUS_COMPLETED);
        transitionToCompleted(conversation, reason);
        return conversation;
    }

    @Override
    @Transactional
    public Conversation expireConversation(UUID conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateTransition(conversation, STATUS_EXPIRED);

        conversation.setStatus(STATUS_EXPIRED);
        conversation.setCompletedAt(Instant.now());
        conversation.setCompletionReason(REASON_EXPIRATION);

        Conversation saved = conversationRepository.save(conversation);
        log.info("Expired conversation {}", conversationId);
        return saved;
    }

    @Override
    @Transactional
    public Conversation abandonConversation(UUID conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateTransition(conversation, STATUS_ABANDONED);

        conversation.setStatus(STATUS_ABANDONED);
        conversation.setCompletedAt(Instant.now());
        conversation.setCompletionReason(REASON_ABANDONED);

        Conversation saved = conversationRepository.save(conversation);
        log.info("Abandoned conversation {}", conversationId);
        return saved;
    }

    @Override
    @Transactional
    public void incrementMessageCount(UUID conversationId, String direction) {
        Conversation conversation = findConversationOrThrow(conversationId);

        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setLastMessageAt(Instant.now());

        if (DIRECTION_INBOUND.equals(direction)) {
            conversation.setFatherMessageCount(conversation.getFatherMessageCount() + 1);
        } else if (DIRECTION_OUTBOUND.equals(direction)) {
            conversation.setSystemMessageCount(conversation.getSystemMessageCount() + 1);
        } else {
            throw new IllegalArgumentException("Invalid direction: " + direction +
                    ". Must be INBOUND or OUTBOUND.");
        }

        conversationRepository.save(conversation);
    }

    @Override
    public boolean isExpired(Conversation conversation) {
        if (conversation.getExpiresAt() == null) {
            return false;
        }
        return Instant.now().isAfter(conversation.getExpiresAt());
    }

    // --- Private helpers ---

    private Conversation findConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
    }

    private void validateTransition(Conversation conversation, String targetStatus) {
        ConversationStatus currentStatus = ConversationStatus.valueOf(conversation.getStatus());
        ConversationStatus target = ConversationStatus.valueOf(targetStatus);

        if (!currentStatus.canTransitionTo(target)) {
            throw new IllegalStateException(String.format(
                    "Invalid state transition for Conversation[id=%s]: cannot transition from %s to %s",
                    conversation.getId(), conversation.getStatus(), targetStatus));
        }
    }

    private void transitionToCompleted(Conversation conversation, String reason) {
        conversation.setStatus(STATUS_COMPLETED);
        conversation.setCompletedAt(Instant.now());
        conversation.setCompletionReason(reason);
        conversationRepository.save(conversation);
    }
}
