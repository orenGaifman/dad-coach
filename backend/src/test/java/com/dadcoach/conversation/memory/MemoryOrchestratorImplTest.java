package com.dadcoach.conversation.memory;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.sideeffect.SideEffectScheduler;
import com.dadcoach.domain.memory.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryOrchestratorImpl verifying:
 * - Extraction scheduling only happens when conversation has 2+ father messages
 * - Memory injection tracking records access via MemoryService
 * - Confirmation triggers delegate to MemoryService
 * - SideEffectScheduler is called with correct payload
 */
class MemoryOrchestratorImplTest {

    private SideEffectScheduler sideEffectScheduler;
    private MemoryService memoryService;
    private MemoryOrchestratorImpl memoryOrchestrator;

    @BeforeEach
    void setUp() {
        sideEffectScheduler = mock(SideEffectScheduler.class);
        memoryService = mock(MemoryService.class);
        memoryOrchestrator = new MemoryOrchestratorImpl(sideEffectScheduler, memoryService);
    }

    @Nested
    @DisplayName("scheduleExtraction")
    class ScheduleExtractionTests {

        @Test
        @DisplayName("schedules extraction when conversation has 2+ father messages")
        void schedulesExtractionWhenEligible() {
            Conversation conversation = buildConversation(3);

            memoryOrchestrator.scheduleExtraction(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(sideEffectScheduler).schedule(
                    eq(MemoryOrchestratorImpl.EFFECT_TYPE_MEMORY_EXTRACTION),
                    eq(conversation.getFatherId()),
                    eq(conversation.getId()),
                    payloadCaptor.capture()
            );

            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload).containsEntry("conversationId", conversation.getId().toString());
            assertThat(payload).containsEntry("fatherId", conversation.getFatherId().toString());
            assertThat(payload).containsEntry("conversationType", "DAILY_COACHING");
            assertThat(payload).containsEntry("fatherMessageCount", 3);
        }

        @Test
        @DisplayName("schedules extraction when conversation has exactly 2 father messages")
        void schedulesExtractionAtMinimumThreshold() {
            Conversation conversation = buildConversation(2);

            memoryOrchestrator.scheduleExtraction(conversation);

            verify(sideEffectScheduler).schedule(
                    eq(MemoryOrchestratorImpl.EFFECT_TYPE_MEMORY_EXTRACTION),
                    eq(conversation.getFatherId()),
                    eq(conversation.getId()),
                    any()
            );
        }

        @Test
        @DisplayName("does NOT schedule extraction when conversation has 1 father message")
        void skipsExtractionWhenBelowThreshold() {
            Conversation conversation = buildConversation(1);

            memoryOrchestrator.scheduleExtraction(conversation);

            verifyNoInteractions(sideEffectScheduler);
        }

        @Test
        @DisplayName("does NOT schedule extraction when conversation has 0 father messages")
        void skipsExtractionWhenZeroMessages() {
            Conversation conversation = buildConversation(0);

            memoryOrchestrator.scheduleExtraction(conversation);

            verifyNoInteractions(sideEffectScheduler);
        }

        @Test
        @DisplayName("handles null conversation gracefully")
        void handlesNullConversation() {
            memoryOrchestrator.scheduleExtraction(null);

            verifyNoInteractions(sideEffectScheduler);
        }
    }

    @Nested
    @DisplayName("recordInjectedMemories")
    class RecordInjectedMemoriesTests {

        @Test
        @DisplayName("records access for all injected memories via MemoryService")
        void recordsAccessForInjectedMemories() {
            UUID conversationId = UUID.randomUUID();
            UUID memoryId1 = UUID.randomUUID();
            UUID memoryId2 = UUID.randomUUID();
            List<UUID> memoryIds = List.of(memoryId1, memoryId2);

            memoryOrchestrator.recordInjectedMemories(conversationId, memoryIds);

            List<Long> expectedDomainIds = memoryIds.stream()
                    .map(UUID::getLeastSignificantBits)
                    .toList();
            verify(memoryService).recordAccessBatch(expectedDomainIds);
        }

        @Test
        @DisplayName("does nothing for empty memory list")
        void handlesEmptyMemoryList() {
            UUID conversationId = UUID.randomUUID();

            memoryOrchestrator.recordInjectedMemories(conversationId, List.of());

            verifyNoInteractions(memoryService);
        }

        @Test
        @DisplayName("does nothing for null memory list")
        void handlesNullMemoryList() {
            UUID conversationId = UUID.randomUUID();

            memoryOrchestrator.recordInjectedMemories(conversationId, null);

            verifyNoInteractions(memoryService);
        }

        @Test
        @DisplayName("handles null conversationId gracefully")
        void handlesNullConversationId() {
            memoryOrchestrator.recordInjectedMemories(null, List.of(UUID.randomUUID()));

            verifyNoInteractions(memoryService);
        }

        @Test
        @DisplayName("does not fail pipeline when MemoryService throws exception")
        void continuesOnMemoryServiceFailure() {
            UUID conversationId = UUID.randomUUID();
            List<UUID> memoryIds = List.of(UUID.randomUUID());

            doThrow(new RuntimeException("DB error")).when(memoryService).recordAccessBatch(any());

            // Should not throw
            memoryOrchestrator.recordInjectedMemories(conversationId, memoryIds);
        }
    }

    @Nested
    @DisplayName("triggerConfirmation")
    class TriggerConfirmationTests {

        @Test
        @DisplayName("delegates to MemoryService.recordAccess for confirmation")
        void delegatesToMemoryService() {
            UUID fatherId = UUID.randomUUID();
            UUID memoryId = UUID.randomUUID();

            memoryOrchestrator.triggerConfirmation(fatherId, memoryId);

            Long expectedDomainId = memoryId.getLeastSignificantBits();
            verify(memoryService).recordAccess(expectedDomainId);
        }

        @Test
        @DisplayName("handles null fatherId gracefully")
        void handlesNullFatherId() {
            memoryOrchestrator.triggerConfirmation(null, UUID.randomUUID());

            verifyNoInteractions(memoryService);
        }

        @Test
        @DisplayName("handles null memoryId gracefully")
        void handlesNullMemoryId() {
            memoryOrchestrator.triggerConfirmation(UUID.randomUUID(), null);

            verifyNoInteractions(memoryService);
        }

        @Test
        @DisplayName("does not fail pipeline when MemoryService throws exception")
        void continuesOnMemoryServiceFailure() {
            UUID fatherId = UUID.randomUUID();
            UUID memoryId = UUID.randomUUID();

            doThrow(new RuntimeException("Not found")).when(memoryService).recordAccess(any());

            // Should not throw
            memoryOrchestrator.triggerConfirmation(fatherId, memoryId);
        }
    }

    @Nested
    @DisplayName("scheduleExtractionIfEligible")
    class ScheduleExtractionIfEligibleTests {

        @Test
        @DisplayName("returns true and schedules when conversation has 2+ father messages")
        void returnsTrueWhenEligible() {
            Conversation conversation = buildConversation(4);

            boolean result = memoryOrchestrator.scheduleExtractionIfEligible(conversation);

            assertThat(result).isTrue();
            verify(sideEffectScheduler).schedule(
                    eq(MemoryOrchestratorImpl.EFFECT_TYPE_MEMORY_EXTRACTION),
                    eq(conversation.getFatherId()),
                    eq(conversation.getId()),
                    any()
            );
        }

        @Test
        @DisplayName("returns false when conversation has fewer than 2 father messages")
        void returnsFalseWhenNotEligible() {
            Conversation conversation = buildConversation(1);

            boolean result = memoryOrchestrator.scheduleExtractionIfEligible(conversation);

            assertThat(result).isFalse();
            verifyNoInteractions(sideEffectScheduler);
        }

        @Test
        @DisplayName("returns false for null conversation")
        void returnsFalseForNull() {
            boolean result = memoryOrchestrator.scheduleExtractionIfEligible(null);

            assertThat(result).isFalse();
            verifyNoInteractions(sideEffectScheduler);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Conversation buildConversation(int fatherMessageCount) {
        Conversation conversation = Conversation.builder()
                .fatherId(UUID.randomUUID())
                .type("DAILY_COACHING")
                .status("COMPLETED")
                .fatherMessageCount(fatherMessageCount)
                .messageCount(fatherMessageCount + 2)
                .systemMessageCount(2)
                .build();
        // Set the ID via reflection since it's normally auto-generated by JPA
        setId(conversation, UUID.randomUUID());
        return conversation;
    }

    private void setId(Conversation conversation, UUID id) {
        try {
            var idField = Conversation.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(conversation, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set conversation id for test", e);
        }
    }
}
