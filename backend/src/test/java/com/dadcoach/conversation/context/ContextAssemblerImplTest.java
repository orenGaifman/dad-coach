package com.dadcoach.conversation.context;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalService;
import com.dadcoach.domain.memory.Memory;
import com.dadcoach.domain.mission.Mission;
import com.dadcoach.domain.mission.MissionService;
import com.dadcoach.memorysystem.MemorySystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContextAssemblerImpl Unit Tests")
class ContextAssemblerImplTest {

    @Mock private FatherService fatherService;
    @Mock private ChildService childService;
    @Mock private GoalService goalService;
    @Mock private MissionService missionService;
    @Mock private MemorySystem memorySystem;
    @Mock private ConversationMessageRepository conversationMessageRepository;

    private ContextAssemblerImpl assembler;

    private UUID fatherId;
    private UUID conversationId;
    private Conversation conversation;
    private InboundMessageDto inboundMessage;

    @BeforeEach
    void setUp() {
        assembler = new ContextAssemblerImpl(
                fatherService, childService, goalService,
                missionService, memorySystem, conversationMessageRepository
        );

        fatherId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        conversation = Conversation.builder()
                .fatherId(fatherId)
                .type("DAILY_COACHING")
                .build();
        // Use reflection to set the ID since it's auto-generated
        setConversationId(conversation, conversationId);

        inboundMessage = new InboundMessageDto(
                "whatsapp", "+972501234567", "Hello, I want to talk about my son",
                "TEXT", "msg-123", Instant.now(), Map.of()
        );
    }

    @Nested
    @DisplayName("6.1 — Assemble father profile, children, goals, missions, memories")
    class AssembleAllSubsystems {

        @Test
        @DisplayName("Should assemble all subsystem data into ConversationContext")
        void shouldAssembleAllData() {
            // Given
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Father father = createFather(domainFatherId);
            Child child = createChild(domainFatherId, 1L);

            when(fatherService.getFather(domainFatherId)).thenReturn(father);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of(child));
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(missionService.getActiveMissionsForChild(1L)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            // When
            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            // Then
            assertThat(context).isNotNull();
            assertThat(context.fatherId()).isEqualTo(fatherId);
            assertThat(context.conversationId()).isEqualTo(conversationId);
            assertThat(context.conversationType()).isEqualTo("DAILY_COACHING");
            assertThat(context.fatherProfile()).isNotEmpty();
            assertThat(context.children()).hasSize(1);
            assertThat(context.temporalContext()).isNotEmpty();
        }

        @Test
        @DisplayName("Should include father profile fields correctly")
        void shouldIncludeFatherProfileFields() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            Father father = createFather(domainFatherId);

            when(fatherService.getFather(domainFatherId)).thenReturn(father);
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            assertThat(context.fatherProfile()).containsKey("status");
            assertThat(context.fatherProfile()).containsKey("coaching_phase");
            assertThat(context.fatherProfile()).containsKey("engagement_score");
            assertThat(context.fatherProfile()).containsKey("coaching_streak");
        }
    }

    @Nested
    @DisplayName("6.2 — Delegate to subsystem services")
    class DelegateToServices {

        @Test
        @DisplayName("Should delegate to FatherService for profile")
        void shouldDelegateToFatherService() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            verify(fatherService).getFather(domainFatherId);
        }

        @Test
        @DisplayName("Should delegate to ChildService for children")
        void shouldDelegateToChildService() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            verify(childService).getChildrenByFather(domainFatherId);
        }

        @Test
        @DisplayName("Should delegate to GoalService for active goals")
        void shouldDelegateToGoalService() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            verify(goalService).getActiveGoals(domainFatherId);
        }

        @Test
        @DisplayName("Should delegate to MemorySystem for ranked memories")
        void shouldDelegateToMemorySystem() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            verify(memorySystem).retrieveTopMemories(eq(domainFatherId), anyString(), eq(10));
        }

        @Test
        @DisplayName("Should delegate to ConversationMessageRepository for history")
        void shouldDelegateToMessageRepository() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            verify(conversationMessageRepository)
                    .findByConversationIdOrderBySequenceNumberAsc(conversationId);
        }
    }

    @Nested
    @DisplayName("6.3 — Handle partial failures gracefully")
    class GracefulDegradation {

        @Test
        @DisplayName("Should return context with empty profile when FatherService fails")
        void shouldHandleFatherServiceFailure() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenThrow(new RuntimeException("DB down"));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            assertThat(context).isNotNull();
            assertThat(context.fatherProfile()).isEmpty();
            // Other fields still assembled
            assertThat(context.conversationType()).isEqualTo("DAILY_COACHING");
        }

        @Test
        @DisplayName("Should return context with empty children when ChildService fails")
        void shouldHandleChildServiceFailure() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenThrow(new RuntimeException("Timeout"));
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            assertThat(context).isNotNull();
            assertThat(context.fatherProfile()).isNotEmpty();
            assertThat(context.children()).isEmpty();
        }

        @Test
        @DisplayName("Should return context with empty memories when MemorySystem fails")
        void shouldHandleMemorySystemFailure() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Memory system unavailable"));
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            assertThat(context).isNotNull();
            assertThat(context.rankedMemories()).isEmpty();
            // Other sections still present
            assertThat(context.fatherProfile()).isNotEmpty();
        }

        @Test
        @DisplayName("Should return context when ALL services fail simultaneously")
        void shouldHandleAllServicesFailure() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenThrow(new RuntimeException("fail"));
            when(childService.getChildrenByFather(domainFatherId)).thenThrow(new RuntimeException("fail"));
            when(goalService.getActiveGoals(domainFatherId)).thenThrow(new RuntimeException("fail"));
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("fail"));
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenThrow(new RuntimeException("fail"));

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            // Should still return a valid context with minimal data
            assertThat(context).isNotNull();
            assertThat(context.fatherId()).isEqualTo(fatherId);
            assertThat(context.conversationId()).isEqualTo(conversationId);
            assertThat(context.fatherProfile()).isEmpty();
            assertThat(context.children()).isEmpty();
            assertThat(context.activeGoals()).isEmpty();
            assertThat(context.activeMissions()).isEmpty();
            assertThat(context.rankedMemories()).isEmpty();
            assertThat(context.conversationHistory()).isEmpty();
            // Temporal context is computed locally, so it should still be present
            assertThat(context.temporalContext()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("6.4 — Include conversation history (last N messages)")
    class ConversationHistory {

        @Test
        @DisplayName("Should include last N messages from conversation history")
        void shouldIncludeRecentMessages() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());

            List<ConversationMessage> messages = List.of(
                    createMessage(conversationId, "INBOUND", "Hello", 1),
                    createMessage(conversationId, "OUTBOUND", "Hello dad!", 2),
                    createMessage(conversationId, "INBOUND", "How do I handle my son?", 3)
            );
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(messages);

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            assertThat(context.conversationHistory()).hasSize(3);
            assertThat(context.conversationHistory().get(0).get("direction")).isEqualTo("INBOUND");
            assertThat(context.conversationHistory().get(0).get("content")).isEqualTo("Hello");
            assertThat(context.conversationHistory().get(2).get("direction")).isEqualTo("INBOUND");
        }

        @Test
        @DisplayName("Should cap conversation history at maximum N messages")
        void shouldCapHistoryAtMaxMessages() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());

            // Create 25 messages (exceeds 20-message limit)
            List<ConversationMessage> messages = new java.util.ArrayList<>();
            for (int i = 1; i <= 25; i++) {
                messages.add(createMessage(conversationId, i % 2 == 0 ? "OUTBOUND" : "INBOUND",
                        "Message " + i, i));
            }
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(messages);

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            // Should contain only the last 20 messages
            assertThat(context.conversationHistory()).hasSize(20);
            // First entry should be message 6 (sequence_number=6, since messages 1-5 are trimmed)
            assertThat(context.conversationHistory().get(0).get("sequence_number")).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("6.5 — Scope memory retrieval by conversation topic/type")
    class MemoryScoping {

        @Test
        @DisplayName("Should pass message content as topic for memory retrieval")
        void shouldPassMessageContentAsTopic() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            assembler.assembleContext(fatherId, conversation, inboundMessage);

            // Verify that the message content is passed as the topic to memory retrieval
            verify(memorySystem).retrieveTopMemories(
                    eq(domainFatherId),
                    eq("Hello, I want to talk about my son"),
                    eq(10)
            );
        }

        @Test
        @DisplayName("Should use conversation type as fallback topic when message is empty")
        void shouldFallbackToConversationTypeAsTopic() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            // Create a message with minimal content (spaces only won't trigger blank since InboundMessageDto validates non-blank)
            InboundMessageDto message = new InboundMessageDto(
                    "whatsapp", "+972501234567", ".",
                    "TEXT", "msg-456", Instant.now(), Map.of()
            );

            assembler.assembleContext(fatherId, conversation, message);

            // "." is not blank, so it's used as the topic
            verify(memorySystem).retrieveTopMemories(
                    eq(domainFatherId),
                    eq("."),
                    eq(10)
            );
        }
    }

    @Nested
    @DisplayName("6.6 — Return structured ConversationContext ready for AI")
    class StructuredContext {

        @Test
        @DisplayName("Should return complete ConversationContext record")
        void shouldReturnCompleteRecord() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            // All required fields present
            assertThat(context.fatherId()).isNotNull();
            assertThat(context.conversationId()).isNotNull();
            assertThat(context.conversationType()).isNotNull();
            assertThat(context.fatherProfile()).isNotNull();
            assertThat(context.children()).isNotNull();
            assertThat(context.activeGoals()).isNotNull();
            assertThat(context.activeMissions()).isNotNull();
            assertThat(context.rankedMemories()).isNotNull();
            assertThat(context.conversationHistory()).isNotNull();
            assertThat(context.temporalContext()).isNotNull();
        }

        @Test
        @DisplayName("Should include temporal context with day, time, and weekend indicator")
        void shouldIncludeTemporalContext() {
            Long domainFatherId = fatherId.getLeastSignificantBits();
            when(fatherService.getFather(domainFatherId)).thenReturn(createFather(domainFatherId));
            when(childService.getChildrenByFather(domainFatherId)).thenReturn(List.of());
            when(goalService.getActiveGoals(domainFatherId)).thenReturn(List.of());
            when(memorySystem.retrieveTopMemories(eq(domainFatherId), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId))
                    .thenReturn(List.of());

            ConversationContext context = assembler.assembleContext(fatherId, conversation, inboundMessage);

            Map<String, Object> temporal = context.temporalContext();
            assertThat(temporal).containsKey("day_of_week");
            assertThat(temporal).containsKey("hour_of_day");
            assertThat(temporal).containsKey("time_of_day");
            assertThat(temporal).containsKey("is_weekend");
            assertThat(temporal).containsKey("timestamp_utc");
            assertThat(temporal.get("is_weekend")).isInstanceOf(Boolean.class);
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private Father createFather(Long id) {
        Father father = new Father("+972501234567");
        father.setId(id);
        father.setDisplayName("Test Father");
        father.setEngagementScore(75);
        father.setCoachingStreak(5);
        return father;
    }

    private Child createChild(Long fatherId, Long childId) {
        Father father = new Father("+972501234567");
        father.setId(fatherId);
        Child child = new Child(father, "Test Child", LocalDate.of(2018, 3, 15));
        child.setId(childId);
        child.setInterests(List.of("fútbol", "dibujar"));
        child.setChallenges(List.of("concentración"));
        return child;
    }

    private ConversationMessage createMessage(UUID conversationId, String direction, String content, int seq) {
        return ConversationMessage.builder()
                .conversationId(conversationId)
                .direction(direction)
                .content(content)
                .messageType("TEXT")
                .sequenceNumber(seq)
                .build();
    }

    /**
     * Sets the ID on a Conversation entity using reflection (since ID is auto-generated).
     */
    private void setConversationId(Conversation conversation, UUID id) {
        try {
            java.lang.reflect.Field idField = Conversation.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(conversation, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set conversation ID via reflection", e);
        }
    }
}
