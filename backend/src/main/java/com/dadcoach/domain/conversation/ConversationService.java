package com.dadcoach.domain.conversation;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.conversation.ConversationStatus;
import com.dadcoach.conversation.ConversationType;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.statemachine.StateMachineEngine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Conversation entity lifecycle management.
 *
 * <p>Handles conversation creation, state transitions, message limit enforcement,
 * and the single-active-conversation-per-father constraint.</p>
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li>Exactly one active conversation per Father (Req 8.2)</li>
 *   <li>DIFFICULT_SITUATION preempts existing active conversation (Req 8.2)</li>
 *   <li>Maximum 8 outbound messages per conversation before auto-completing (Req 8.5)</li>
 * </ul>
 */
@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final FatherRepository fatherRepository;
    private final StateMachineEngine stateMachineEngine;

    public ConversationService(ConversationRepository conversationRepository,
                               FatherRepository fatherRepository,
                               StateMachineEngine stateMachineEngine) {
        this.conversationRepository = conversationRepository;
        this.fatherRepository = fatherRepository;
        this.stateMachineEngine = stateMachineEngine;
    }

    // ─── Creation ────────────────────────────────────────────────────────

    /**
     * Starts a new conversation for a father.
     *
     * <p>Enforces the single-active-conversation constraint:
     * <ul>
     *   <li>If no active conversation exists → creates a new one</li>
     *   <li>If an active conversation exists AND the new type is DIFFICULT_SITUATION →
     *       completes the active one and creates the new one (preemption)</li>
     *   <li>If an active conversation exists AND the new type is NOT DIFFICULT_SITUATION →
     *       throws BusinessRuleViolationException (must queue)</li>
     * </ul>
     *
     * @param fatherId  the ID of the father
     * @param type      the conversation type
     * @param objective the conversation objective
     * @param expiresAt when the conversation expires
     * @return the created Conversation entity
     * @throws ResourceNotFoundException      if the father is not found
     * @throws BusinessRuleViolationException if an active conversation already exists and type is not DIFFICULT_SITUATION
     */
    public Conversation startConversation(Long fatherId, ConversationType type,
                                          String objective, Instant expiresAt) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        Optional<Conversation> existingActive = conversationRepository.findActiveByFatherId(fatherId);

        if (existingActive.isPresent()) {
            if (type == ConversationType.DIFFICULT_SITUATION) {
                // DIFFICULT_SITUATION preempts: close the existing active conversation
                Conversation active = existingActive.get();
                completeConversation(active.getId(), "Preempted by DIFFICULT_SITUATION conversation");
            } else {
                // Cannot start a new conversation while one is active (must queue)
                throw new BusinessRuleViolationException("SINGLE_ACTIVE_CONVERSATION_PER_FATHER",
                        "Father " + fatherId + " already has an active conversation (id="
                                + existingActive.get().getId() + "). New conversations must queue unless type is DIFFICULT_SITUATION.");
            }
        }

        Conversation conversation = new Conversation(father, type, objective, expiresAt);
        return conversationRepository.save(conversation);
    }

    // ─── Message Count and Limit ─────────────────────────────────────────

    /**
     * Records an outbound (system-sent) message in the conversation and enforces the message limit.
     *
     * <p>If after incrementing the count reaches or exceeds MAX_OUTBOUND_MESSAGES (8),
     * the conversation is auto-completed.</p>
     *
     * @param conversationId the conversation ID
     * @return the updated Conversation entity
     * @throws ResourceNotFoundException      if the conversation is not found
     * @throws BusinessRuleViolationException if the conversation is not active
     */
    public Conversation recordOutboundMessage(Long conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleViolationException("CONVERSATION_NOT_ACTIVE",
                    "Cannot record message on conversation " + conversationId
                            + " with status " + conversation.getStatus());
        }

        conversation.incrementMessageCount();

        if (conversation.hasReachedMessageLimit()) {
            // Auto-complete when message limit reached
            stateMachineEngine.transition(
                    "Conversation", conversation.getId(), conversation.getStatus(),
                    ConversationStatus.COMPLETED, "Max outbound messages reached (8)");
            conversation.transitionTo(ConversationStatus.COMPLETED);
        }

        return conversationRepository.save(conversation);
    }

    // ─── State Transitions ───────────────────────────────────────────────

    /**
     * Completes a conversation (ACTIVE → COMPLETED).
     *
     * @param conversationId the conversation ID
     * @param summary        a text summary of the conversation
     * @return the updated Conversation entity
     * @throws ResourceNotFoundException                          if the conversation is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Conversation completeConversation(Long conversationId, String summary) {
        Conversation conversation = findConversationOrThrow(conversationId);

        stateMachineEngine.transition(
                "Conversation", conversation.getId(), conversation.getStatus(),
                ConversationStatus.COMPLETED, "Conversation completed: " + summary);
        conversation.transitionTo(ConversationStatus.COMPLETED);
        conversation.setSummary(summary);

        return conversationRepository.save(conversation);
    }

    /**
     * Expires a conversation (ACTIVE → EXPIRED).
     *
     * @param conversationId the conversation ID
     * @return the updated Conversation entity
     * @throws ResourceNotFoundException                          if the conversation is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Conversation expireConversation(Long conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);

        stateMachineEngine.transition(
                "Conversation", conversation.getId(), conversation.getStatus(),
                ConversationStatus.EXPIRED, "Expiration time reached without completion");
        conversation.transitionTo(ConversationStatus.EXPIRED);

        return conversationRepository.save(conversation);
    }

    /**
     * Abandons a conversation (ACTIVE → ABANDONED).
     *
     * @param conversationId the conversation ID
     * @return the updated Conversation entity
     * @throws ResourceNotFoundException                          if the conversation is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Conversation abandonConversation(Long conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);

        stateMachineEngine.transition(
                "Conversation", conversation.getId(), conversation.getStatus(),
                ConversationStatus.ABANDONED, "Father unresponsive for 48h");
        conversation.transitionTo(ConversationStatus.ABANDONED);

        return conversationRepository.save(conversation);
    }

    // ─── Expiration Job ──────────────────────────────────────────────────

    /**
     * Checks for and expires all active conversations that have passed their expiration time.
     * Intended to be called by a scheduled job.
     *
     * @return the number of conversations expired
     */
    public int expireOverdueConversations() {
        List<Conversation> expired = conversationRepository.findExpired(Instant.now());
        for (Conversation conversation : expired) {
            stateMachineEngine.transition(
                    "Conversation", conversation.getId(), conversation.getStatus(),
                    ConversationStatus.EXPIRED, "Scheduled expiration check");
            conversation.transitionTo(ConversationStatus.EXPIRED);
            conversationRepository.save(conversation);
        }
        return expired.size();
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Gets a conversation by ID.
     *
     * @param conversationId the conversation ID
     * @return the Conversation entity
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Conversation getConversation(Long conversationId) {
        return findConversationOrThrow(conversationId);
    }

    /**
     * Gets the active conversation for a father, if any.
     *
     * @param fatherId the father ID
     * @return the active conversation or empty
     */
    @Transactional(readOnly = true)
    public Optional<Conversation> getActiveConversation(Long fatherId) {
        return conversationRepository.findActiveByFatherId(fatherId);
    }

    /**
     * Counts active conversations for a father (should be 0 or 1).
     *
     * @param fatherId the father ID
     * @return the count of active conversations
     */
    @Transactional(readOnly = true)
    public long countActiveConversations(Long fatherId) {
        return conversationRepository.countActiveByFatherId(fatherId);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private Conversation findConversationOrThrow(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }
}
