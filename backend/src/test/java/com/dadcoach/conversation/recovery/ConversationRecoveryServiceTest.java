package com.dadcoach.conversation.recovery;

import com.dadcoach.conversation.ConversationService;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.event.ConversationEventPublisher;
import com.dadcoach.conversation.memory.MemoryOrchestrator;
import com.dadcoach.conversation.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationRecoveryService Unit Tests")
class ConversationRecoveryServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MemoryOrchestrator memoryOrchestrator;

    @Mock
    private ConversationEventPublisher eventPublisher;

    private ConversationRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new ConversationRecoveryService(
                conversationRepository,
                conversationService,
                memoryOrchestrator,
                eventPublisher
        );
    }

    // ─── 13.2: Detect ACTIVE conversations past their expires_at ─────────

    @Nested
    @DisplayName("13.2 - Stale conversation detection")
    class StaleDetection {

        @Test
        @DisplayName("No action taken when no stale conversations exist")
        void detectStale_noStaleConversations_noAction() {
            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            recoveryService.detectAndRecoverStaleConversations();

            verifyNoInteractions(conversationService);
            verifyNoInteractions(memoryOrchestrator);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Detects conversations with expires_at in the past")
        void detectStale_expiredConversations_detected() {
            Conversation stale = createStaleConversation(3);
            Conversation expiredResult = createExpiredConversation(stale);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(conversationService.expireConversation(stale.getId())).thenReturn(expiredResult);

            recoveryService.detectAndRecoverStaleConversations();

            verify(conversationService).expireConversation(stale.getId());
        }
    }

    // ─── 13.3: Transition stale to EXPIRED ───────────────────────────────

    @Nested
    @DisplayName("13.3 - EXPIRED transition")
    class ExpiredTransition {

        @Test
        @DisplayName("Transitions each stale conversation to EXPIRED")
        void detectStale_multipleStale_allTransitioned() {
            Conversation stale1 = createStaleConversation(2);
            Conversation stale2 = createStaleConversation(4);
            Conversation expired1 = createExpiredConversation(stale1);
            Conversation expired2 = createExpiredConversation(stale2);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale1, stale2));
            when(conversationService.expireConversation(stale1.getId())).thenReturn(expired1);
            when(conversationService.expireConversation(stale2.getId())).thenReturn(expired2);

            recoveryService.detectAndRecoverStaleConversations();

            verify(conversationService).expireConversation(stale1.getId());
            verify(conversationService).expireConversation(stale2.getId());
        }

        @Test
        @DisplayName("Failure on one conversation does not prevent processing others")
        void detectStale_oneFailure_othersStillProcessed() {
            Conversation stale1 = createStaleConversation(2);
            Conversation stale2 = createStaleConversation(3);
            Conversation expired2 = createExpiredConversation(stale2);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale1, stale2));
            when(conversationService.expireConversation(stale1.getId()))
                    .thenThrow(new IllegalStateException("DB error"));
            when(conversationService.expireConversation(stale2.getId())).thenReturn(expired2);

            recoveryService.detectAndRecoverStaleConversations();

            // Second should still be processed
            verify(conversationService).expireConversation(stale2.getId());
            verify(eventPublisher).publishConversationExpired(expired2);
        }
    }

    // ─── 13.4: Memory extraction if 2+ father messages ──────────────────

    @Nested
    @DisplayName("13.4 - Memory extraction scheduling")
    class MemoryExtraction {

        @Test
        @DisplayName("Schedules extraction when fatherMessageCount >= 2")
        void detectStale_twoOrMoreFatherMessages_schedulesExtraction() {
            Conversation stale = createStaleConversation(3);
            Conversation expired = createExpiredConversation(stale);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(conversationService.expireConversation(stale.getId())).thenReturn(expired);

            recoveryService.detectAndRecoverStaleConversations();

            verify(memoryOrchestrator).scheduleExtractionIfEligible(expired);
        }

        @Test
        @DisplayName("Does NOT schedule extraction when fatherMessageCount < 2")
        void detectStale_lessThanTwoFatherMessages_noExtraction() {
            Conversation stale = createStaleConversation(1);
            Conversation expired = createExpiredConversation(stale);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(conversationService.expireConversation(stale.getId())).thenReturn(expired);

            recoveryService.detectAndRecoverStaleConversations();

            verifyNoInteractions(memoryOrchestrator);
        }

        @Test
        @DisplayName("Does NOT schedule extraction when fatherMessageCount is 0")
        void detectStale_zeroFatherMessages_noExtraction() {
            Conversation stale = createStaleConversation(0);
            Conversation expired = createExpiredConversation(stale);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(conversationService.expireConversation(stale.getId())).thenReturn(expired);

            recoveryService.detectAndRecoverStaleConversations();

            verifyNoInteractions(memoryOrchestrator);
        }
    }

    // ─── 13.5: Publish CONVERSATION_EXPIRED event ────────────────────────

    @Nested
    @DisplayName("13.5 - Event publication")
    class EventPublication {

        @Test
        @DisplayName("Publishes CONVERSATION_EXPIRED event for recovered conversations")
        void detectStale_publishesExpiredEvent() {
            Conversation stale = createStaleConversation(2);
            Conversation expired = createExpiredConversation(stale);

            when(conversationRepository.findByStatusAndExpiresAtBefore(eq("ACTIVE"), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(conversationService.expireConversation(stale.getId())).thenReturn(expired);

            recoveryService.detectAndRecoverStaleConversations();

            verify(eventPublisher).publishConversationExpired(expired);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Conversation createStaleConversation(int fatherMessageCount) {
        Conversation conversation = Conversation.builder()
                .fatherId(UUID.randomUUID())
                .type("DAILY_COACHING")
                .status("ACTIVE")
                .fatherMessageCount(fatherMessageCount)
                .expiresAt(Instant.now().minus(Duration.ofHours(1)))
                .build();
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        return conversation;
    }

    private Conversation createExpiredConversation(Conversation original) {
        Conversation conversation = Conversation.builder()
                .fatherId(original.getFatherId())
                .type(original.getType())
                .status("EXPIRED")
                .fatherMessageCount(original.getFatherMessageCount())
                .completedAt(Instant.now())
                .completionReason("EXPIRATION")
                .build();
        ReflectionTestUtils.setField(conversation, "id", original.getId());
        return conversation;
    }
}
