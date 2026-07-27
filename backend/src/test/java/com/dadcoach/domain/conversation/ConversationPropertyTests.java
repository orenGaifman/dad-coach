package com.dadcoach.domain.conversation;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.conversation.ConversationStatus;
import com.dadcoach.conversation.ConversationType;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.statemachine.StateMachineEngine;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Conversation domain logic.
 *
 * Tests two correctness properties from the design document:
 * - Property 21: Single active conversation per father
 * - Property 22: Conversation message limit (max 8 outbound)
 */
class ConversationPropertyTests {

    // ─── Property 21: Single Active Conversation Per Father ──────────────────

    /**
     * **Validates: Requirements 8.2**
     *
     * For any Father with an existing active conversation, attempting to start
     * a new non-DIFFICULT_SITUATION conversation should be rejected with a
     * BusinessRuleViolationException.
     */
    @Property
    void nonDifficultSituationConversationShouldBeRejectedWhenActiveExists(
            @ForAll("nonDifficultSituationTypes") ConversationType newType) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Conversation existingActive = new Conversation(father, ConversationType.DAILY_COACHING,
                "Existing session", Instant.now().plus(24, ChronoUnit.HOURS));
        existingActive.setId(100L);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockConvRepo.findActiveByFatherId(1L)).thenReturn(Optional.of(existingActive));

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        try {
            service.startConversation(1L, newType, "New objective",
                    Instant.now().plus(24, ChronoUnit.HOURS));
            throw new AssertionError(
                    "Expected BusinessRuleViolationException when father already has an active conversation "
                            + "and new type is " + newType);
        } catch (BusinessRuleViolationException e) {
            // Expected: single-active-conversation-per-father constraint enforced
            if (!e.getMessage().contains("SINGLE_ACTIVE_CONVERSATION_PER_FATHER")) {
                throw new AssertionError(
                        "Expected SINGLE_ACTIVE_CONVERSATION_PER_FATHER violation but got: " + e.getMessage());
            }
        }
    }

    /**
     * **Validates: Requirements 8.2**
     *
     * For any Father with an existing active conversation, starting a DIFFICULT_SITUATION
     * conversation should succeed by preempting (completing) the existing one.
     */
    @Property
    void difficultSituationShouldPreemptExistingActiveConversation(
            @ForAll("allConversationTypes") ConversationType existingType) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Conversation existingActive = new Conversation(father, existingType,
                "Existing session", Instant.now().plus(24, ChronoUnit.HOURS));
        existingActive.setId(100L);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockConvRepo.findActiveByFatherId(1L)).thenReturn(Optional.of(existingActive));
        when(mockConvRepo.findById(100L)).thenReturn(Optional.of(existingActive));
        when(mockConvRepo.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(200L);
            }
            return c;
        });
        when(mockEngine.<ConversationStatus>transition(anyString(), anyLong(), any(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(3));

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        Conversation result = service.startConversation(1L, ConversationType.DIFFICULT_SITUATION,
                "Urgent situation", Instant.now().plus(48, ChronoUnit.HOURS));

        // The new conversation should be created
        if (result == null) {
            throw new AssertionError("DIFFICULT_SITUATION conversation should be created even when active exists");
        }
        if (result.getType() != ConversationType.DIFFICULT_SITUATION) {
            throw new AssertionError("Expected DIFFICULT_SITUATION type but got " + result.getType());
        }

        // The existing conversation should have been completed (preempted)
        if (existingActive.getStatus() != ConversationStatus.COMPLETED) {
            throw new AssertionError("Existing active conversation should be COMPLETED after preemption, "
                    + "but got " + existingActive.getStatus());
        }
    }

    /**
     * **Validates: Requirements 8.2**
     *
     * For any Father with NO active conversation, starting any type of conversation
     * should succeed.
     */
    @Property
    void newConversationShouldSucceedWhenNoActiveExists(
            @ForAll("allConversationTypes") ConversationType type) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockConvRepo.findActiveByFatherId(1L)).thenReturn(Optional.empty());
        when(mockConvRepo.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        Conversation result = service.startConversation(1L, type, "Objective",
                Instant.now().plus(24, ChronoUnit.HOURS));

        if (result == null) {
            throw new AssertionError("Conversation creation should succeed when no active conversation exists");
        }
        if (result.getStatus() != ConversationStatus.ACTIVE) {
            throw new AssertionError("New conversation should be ACTIVE but got " + result.getStatus());
        }
        if (result.getType() != type) {
            throw new AssertionError("Expected conversation type " + type + " but got " + result.getType());
        }
    }

    // ─── Property 22: Conversation Message Limit ─────────────────────────────

    /**
     * **Validates: Requirements 8.5, 10.3**
     *
     * For any conversation, after recording the 8th outbound message, the conversation
     * should auto-complete (transition to COMPLETED status).
     */
    @Property
    void conversationShouldAutoCompleteAtMessageLimit(
            @ForAll("allConversationTypes") ConversationType type) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Conversation conversation = new Conversation(father, type,
                "Test objective", Instant.now().plus(24, ChronoUnit.HOURS));
        conversation.setId(1L);
        // Set message count to 7 (one below limit)
        conversation.setMessageCount(7);

        when(mockConvRepo.findById(1L)).thenReturn(Optional.of(conversation));
        when(mockConvRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mockEngine.<ConversationStatus>transition(anyString(), anyLong(), any(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(3));

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        Conversation result = service.recordOutboundMessage(1L);

        // Message count should be 8
        if (result.getMessageCount() != 8) {
            throw new AssertionError("Expected message count 8 but got " + result.getMessageCount());
        }
        // Conversation should be auto-completed
        if (result.getStatus() != ConversationStatus.COMPLETED) {
            throw new AssertionError(
                    "Conversation should auto-complete at 8 messages but status is " + result.getStatus());
        }
    }

    /**
     * **Validates: Requirements 8.5, 10.3**
     *
     * For any conversation with message count below the limit (0-6), recording an
     * outbound message should NOT auto-complete the conversation.
     */
    @Property
    void conversationShouldRemainActiveBeforeMessageLimit(
            @ForAll @IntRange(min = 0, max = 6) int initialMessageCount) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Conversation conversation = new Conversation(father, ConversationType.DAILY_COACHING,
                "Test objective", Instant.now().plus(24, ChronoUnit.HOURS));
        conversation.setId(1L);
        conversation.setMessageCount(initialMessageCount);

        when(mockConvRepo.findById(1L)).thenReturn(Optional.of(conversation));
        when(mockConvRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        Conversation result = service.recordOutboundMessage(1L);

        // Message count should be incremented by 1
        if (result.getMessageCount() != initialMessageCount + 1) {
            throw new AssertionError("Expected message count " + (initialMessageCount + 1)
                    + " but got " + result.getMessageCount());
        }
        // Conversation should still be ACTIVE (count is at most 7 after increment)
        if (result.getStatus() != ConversationStatus.ACTIVE) {
            throw new AssertionError(
                    "Conversation with " + result.getMessageCount() + " messages should remain ACTIVE "
                            + "but status is " + result.getStatus());
        }
    }

    /**
     * **Validates: Requirements 8.5, 10.3**
     *
     * The hasReachedMessageLimit() method on Conversation should return true
     * if and only if message_count >= 8.
     */
    @Property
    void messageLimitDetectionIsCorrect(@ForAll @IntRange(min = 0, max = 20) int messageCount) {
        Father father = new Father("+972501234567");
        Conversation conversation = new Conversation(father, ConversationType.DAILY_COACHING,
                "Test", Instant.now().plus(24, ChronoUnit.HOURS));
        conversation.setMessageCount(messageCount);

        boolean atLimit = conversation.hasReachedMessageLimit();
        boolean shouldBeAtLimit = messageCount >= Conversation.MAX_OUTBOUND_MESSAGES;

        if (atLimit != shouldBeAtLimit) {
            throw new AssertionError(
                    "Conversation with " + messageCount + " messages: hasReachedMessageLimit() should be "
                            + shouldBeAtLimit + " but got " + atLimit);
        }
    }

    /**
     * **Validates: Requirements 8.5, 10.3**
     *
     * The outbound message count should never exceed 8 per conversation. When the 8th
     * message is recorded, the conversation must transition to COMPLETED, preventing
     * further messages from being recorded.
     */
    @Property
    void cannotRecordMessageOnNonActiveConversation(
            @ForAll("terminalStatuses") ConversationStatus terminalStatus) {

        // Set up mocks
        ConversationRepository mockConvRepo = mock(ConversationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);
        StateMachineEngine mockEngine = mock(StateMachineEngine.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Conversation conversation = new Conversation(father, ConversationType.DAILY_COACHING,
                "Test", Instant.now().plus(24, ChronoUnit.HOURS));
        conversation.setId(1L);
        // Force the conversation into a terminal state
        conversation.setStatus(terminalStatus);

        when(mockConvRepo.findById(1L)).thenReturn(Optional.of(conversation));

        ConversationService service = new ConversationService(mockConvRepo, mockFatherRepo, mockEngine);

        try {
            service.recordOutboundMessage(1L);
            throw new AssertionError(
                    "Expected BusinessRuleViolationException when recording message on "
                            + terminalStatus + " conversation");
        } catch (BusinessRuleViolationException e) {
            // Expected: cannot record message on non-active conversation
            if (!e.getMessage().contains("CONVERSATION_NOT_ACTIVE")) {
                throw new AssertionError(
                        "Expected CONVERSATION_NOT_ACTIVE violation but got: " + e.getMessage());
            }
        }
    }

    // ─── Providers ───────────────────────────────────────────────────────

    @Provide
    Arbitrary<ConversationType> nonDifficultSituationTypes() {
        return Arbitraries.of(
                ConversationType.ONBOARDING,
                ConversationType.DAILY_COACHING,
                ConversationType.FOLLOW_UP,
                ConversationType.REFLECTION,
                ConversationType.INACTIVITY_CHECK,
                ConversationType.CELEBRATION
        );
    }

    @Provide
    Arbitrary<ConversationType> allConversationTypes() {
        return Arbitraries.of(ConversationType.values());
    }

    @Provide
    Arbitrary<ConversationStatus> terminalStatuses() {
        return Arbitraries.of(
                ConversationStatus.COMPLETED,
                ConversationStatus.EXPIRED,
                ConversationStatus.ABANDONED
        );
    }
}
