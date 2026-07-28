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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationOrchestrator Unit Tests")
class ConversationOrchestratorTest {

    @Mock private IdempotencyService idempotencyService;
    @Mock private SessionLockService sessionLockService;
    @Mock private FatherResolver fatherResolver;
    @Mock private ConversationService conversationService;
    @Mock private ContextAssembler contextAssembler;
    @Mock private AiOrchestrator aiOrchestrator;
    @Mock private MissionOrchestrator missionOrchestrator;
    @Mock private MemoryOrchestrator memoryOrchestrator;
    @Mock private SideEffectScheduler sideEffectScheduler;
    @Mock private ConversationEventPublisher eventPublisher;
    @Mock private FallbackResponseProvider fallbackProvider;
    @Mock private ConversationMessageRepository messageRepository;

    private ConversationOrchestrator orchestrator;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new ConversationOrchestrator(
                idempotencyService,
                sessionLockService,
                fatherResolver,
                conversationService,
                contextAssembler,
                aiOrchestrator,
                missionOrchestrator,
                memoryOrchestrator,
                sideEffectScheduler,
                eventPublisher,
                fallbackProvider,
                messageRepository,
                8 // maxOutboundMessages
        );
    }

    private InboundMessageDto createInboundMessage() {
        return new InboundMessageDto(
                "whatsapp",
                "+5491155551234",
                "Hola, necesito ayuda",
                "TEXT",
                "msg-key-" + UUID.randomUUID(),
                Instant.now(),
                Map.of()
        );
    }

    private Conversation createActiveConversation() {
        Conversation conv = mock(Conversation.class);
        when(conv.getId()).thenReturn(CONVERSATION_ID);
        when(conv.getFatherId()).thenReturn(FATHER_ID);
        when(conv.getType()).thenReturn("DAILY_COACHING");
        when(conv.getStatus()).thenReturn("ACTIVE");
        when(conv.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(conv.getSystemMessageCount()).thenReturn(0);
        when(conv.getFatherMessageCount()).thenReturn(0);
        when(conv.getMessageCount()).thenReturn(0);
        return conv;
    }

    private ConversationContext createConversationContext(Conversation conv) {
        UUID convId = conv.getId() != null ? conv.getId() : CONVERSATION_ID;
        String convType = conv.getType() != null ? conv.getType() : "DAILY_COACHING";
        return new ConversationContext(
                FATHER_ID,
                convId,
                convType,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private AiResult createSuccessfulAiResult() {
        return AiResult.success(
                "¡Hola! ¿Cómo puedo ayudarte hoy?",
                "NONE",
                Map.of("model_used", "gpt-4")
        );
    }

    private void setupHappyPath(InboundMessageDto message, Conversation conversation) {
        // No duplicate
        when(idempotencyService.checkDuplicate(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Father resolved
        when(fatherResolver.findBySenderIdentity(anyString(), anyString()))
                .thenReturn(Optional.of(new FatherResolver.ResolvedFather(FATHER_ID, "ACTIVE")));

        // Active conversation found
        when(conversationService.findActiveConversation(FATHER_ID))
                .thenReturn(Optional.of(conversation));
        when(conversationService.isExpired(conversation)).thenReturn(false);

        // Context assembled
        ConversationContext context = createConversationContext(conversation);
        when(contextAssembler.assembleContext(eq(FATHER_ID), eq(conversation), any()))
                .thenReturn(context);

        // AI returns success
        when(aiOrchestrator.orchestrate(any(), any())).thenReturn(createSuccessfulAiResult());

        // Message persistence
        when(messageRepository.countByConversationId(any())).thenReturn(0);
        ConversationMessage savedMsg = mock(ConversationMessage.class);
        when(savedMsg.getId()).thenReturn(UUID.randomUUID());
        when(messageRepository.save(any())).thenReturn(savedMsg);
    }

    @Nested
    @DisplayName("4.1 — Single @Transactional method coordinates entire pipeline")
    class TransactionalPipeline {

        @Test
        @DisplayName("processMessage executes full pipeline and returns response")
        void processMessage_executesFullPipeline() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("¡Hola! ¿Cómo puedo ayudarte hoy?");
            assertThat(result.recipientId()).isEqualTo(message.senderId());
        }

        @Test
        @DisplayName("pipeline steps execute in correct order")
        void processMessage_stepsExecuteInOrder() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            // Verify order: idempotency → resolve → lock → route → context → AI → persist → side-effects → record
            var inOrder = inOrder(
                    idempotencyService, fatherResolver, sessionLockService,
                    conversationService, contextAssembler, aiOrchestrator,
                    messageRepository, sideEffectScheduler, idempotencyService
            );

            inOrder.verify(idempotencyService).checkDuplicate(anyString(), anyString());
            inOrder.verify(fatherResolver).findBySenderIdentity(anyString(), anyString());
            inOrder.verify(sessionLockService).acquireLock(FATHER_ID);
            inOrder.verify(conversationService).findActiveConversation(FATHER_ID);
            inOrder.verify(contextAssembler).assembleContext(eq(FATHER_ID), any(), any());
            inOrder.verify(aiOrchestrator).orchestrate(any(), any());
            inOrder.verify(messageRepository, times(2)).save(any()); // inbound + outbound
            inOrder.verify(sideEffectScheduler).schedule(anyString(), any(), any(), any());
            inOrder.verify(idempotencyService).recordProcessed(anyString(), eq(FATHER_ID), any());
        }
    }

    @Nested
    @DisplayName("4.2 — Steps execute in order: idempotency, lock, resolve, route, context, AI, persist, evaluate, side-effects")
    class PipelineStepOrder {

        @Test
        @DisplayName("duplicate message returns cached response without executing pipeline")
        void duplicateMessage_returnsCachedResponse() {
            InboundMessageDto message = createInboundMessage();
            OutboundMessageDto cachedResponse = new OutboundMessageDto(
                    message.senderId(), "cached reply", "TEXT", CONVERSATION_ID, Map.of());

            when(idempotencyService.checkDuplicate(message.idempotencyKey(), message.senderId()))
                    .thenReturn(Optional.of(cachedResponse));

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isEqualTo(cachedResponse);
            // No further pipeline steps executed
            verifyNoInteractions(sessionLockService);
            verifyNoInteractions(fatherResolver);
            verifyNoInteractions(conversationService);
            verifyNoInteractions(contextAssembler);
            verifyNoInteractions(aiOrchestrator);
        }

        @Test
        @DisplayName("lock acquired before conversation routing")
        void lockAcquiredBeforeRouting() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            var inOrder = inOrder(sessionLockService, conversationService);
            inOrder.verify(sessionLockService).acquireLock(FATHER_ID);
            inOrder.verify(conversationService).findActiveConversation(FATHER_ID);
        }
    }

    @Nested
    @DisplayName("4.3 — Unknown father triggers create Father + start ONBOARDING conversation")
    class UnknownFatherHandling {

        @Test
        @DisplayName("unknown sender creates new father and starts ONBOARDING")
        void unknownSender_createsNewFatherAndOnboarding() {
            InboundMessageDto message = createInboundMessage();
            UUID onboardingConvId = UUID.randomUUID();

            when(idempotencyService.checkDuplicate(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(fatherResolver.findBySenderIdentity(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(fatherResolver.createNewFather(message.senderId(), message.channelId()))
                    .thenReturn(new FatherResolver.ResolvedFather(FATHER_ID, "NOT_STARTED"));

            Conversation onboardingConv = mock(Conversation.class);
            when(onboardingConv.getId()).thenReturn(onboardingConvId);
            when(onboardingConv.getFatherId()).thenReturn(FATHER_ID);
            when(onboardingConv.getType()).thenReturn("ONBOARDING");
            when(onboardingConv.getStatus()).thenReturn("ACTIVE");
            when(onboardingConv.getSystemMessageCount()).thenReturn(0);

            when(conversationService.findActiveConversation(FATHER_ID))
                    .thenReturn(Optional.empty());
            when(conversationService.createConversation(FATHER_ID, "ONBOARDING"))
                    .thenReturn(onboardingConv);

            ConversationContext context = new ConversationContext(
                    FATHER_ID, onboardingConvId, "ONBOARDING",
                    Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
            when(contextAssembler.assembleContext(eq(FATHER_ID), eq(onboardingConv), any()))
                    .thenReturn(context);
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(createSuccessfulAiResult());
            when(messageRepository.countByConversationId(any())).thenReturn(0);
            ConversationMessage savedMsg = mock(ConversationMessage.class);
            when(savedMsg.getId()).thenReturn(UUID.randomUUID());
            when(messageRepository.save(any())).thenReturn(savedMsg);

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            verify(fatherResolver).createNewFather(message.senderId(), message.channelId());
            verify(conversationService).createConversation(FATHER_ID, "ONBOARDING");
            verify(eventPublisher).publishConversationStarted(onboardingConv);
        }
    }

    @Nested
    @DisplayName("4.4 — Expired conversation triggers transition to EXPIRED and create new")
    class ExpiredConversationHandling {

        @Test
        @DisplayName("expired active conversation is transitioned and new one created")
        void expiredConversation_transitionsAndCreatesNew() {
            InboundMessageDto message = createInboundMessage();
            UUID expiredConvId = UUID.randomUUID();
            UUID newConvId = UUID.randomUUID();

            when(idempotencyService.checkDuplicate(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(fatherResolver.findBySenderIdentity(anyString(), anyString()))
                    .thenReturn(Optional.of(new FatherResolver.ResolvedFather(FATHER_ID, "ACTIVE")));

            // Existing conversation is expired
            Conversation expiredConv = mock(Conversation.class);
            when(expiredConv.getId()).thenReturn(expiredConvId);
            when(expiredConv.getFatherId()).thenReturn(FATHER_ID);
            when(expiredConv.getType()).thenReturn("DAILY_COACHING");
            when(expiredConv.getStatus()).thenReturn("ACTIVE");

            Conversation newConv = mock(Conversation.class);
            when(newConv.getId()).thenReturn(newConvId);
            when(newConv.getFatherId()).thenReturn(FATHER_ID);
            when(newConv.getType()).thenReturn("DAILY_COACHING");
            when(newConv.getStatus()).thenReturn("ACTIVE");
            when(newConv.getSystemMessageCount()).thenReturn(0);

            when(conversationService.findActiveConversation(FATHER_ID))
                    .thenReturn(Optional.of(expiredConv));
            when(conversationService.isExpired(expiredConv)).thenReturn(true);
            when(conversationService.expireConversation(expiredConvId))
                    .thenReturn(expiredConv);
            when(conversationService.createConversation(FATHER_ID, "DAILY_COACHING"))
                    .thenReturn(newConv);

            ConversationContext context = new ConversationContext(
                    FATHER_ID, newConvId, "DAILY_COACHING",
                    Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
            when(contextAssembler.assembleContext(eq(FATHER_ID), eq(newConv), any()))
                    .thenReturn(context);
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(createSuccessfulAiResult());
            when(messageRepository.countByConversationId(any())).thenReturn(0);
            ConversationMessage savedMsg = mock(ConversationMessage.class);
            when(savedMsg.getId()).thenReturn(UUID.randomUUID());
            when(messageRepository.save(any())).thenReturn(savedMsg);

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            verify(conversationService).expireConversation(expiredConvId);
            verify(eventPublisher).publishConversationExpired(expiredConv);
            verify(memoryOrchestrator).scheduleExtraction(expiredConv);
            verify(conversationService).createConversation(FATHER_ID, "DAILY_COACHING");
            verify(eventPublisher).publishConversationStarted(newConv);
        }
    }

    @Nested
    @DisplayName("4.5 — Father always receives a response (never fails silently)")
    class NeverFailSilently {

        @Test
        @DisplayName("unhandled exception in pipeline delivers fallback response")
        void unhandledException_deliversFallback() {
            InboundMessageDto message = createInboundMessage();

            when(idempotencyService.checkDuplicate(anyString(), anyString()))
                    .thenThrow(new RuntimeException("database connection lost"));
            when(fallbackProvider.getGenericFallback())
                    .thenReturn("Lo siento, estamos teniendo dificultades técnicas. Volveré pronto.");

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo(
                    "Lo siento, estamos teniendo dificultades técnicas. Volveré pronto.");
            assertThat(result.recipientId()).isEqualTo(message.senderId());
            assertThat(result.metadata()).containsEntry("fallback_used", true);
            assertThat(result.metadata()).containsEntry("error_recovery", true);
        }

        @Test
        @DisplayName("AI failure still delivers a response via AiOrchestrator's fallback")
        void aiFailure_aiOrchestratorReturnsFallback() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            // AI orchestrator returns a fallback (it never throws)
            AiResult fallbackResult = AiResult.fallback("Respuesta de respaldo");
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(fallbackResult);

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("Respuesta de respaldo");
            assertThat(result.metadata()).containsEntry("fallback_used", true);
        }

        @Test
        @DisplayName("lock timeout exception still delivers fallback")
        void lockTimeout_deliversFallback() {
            InboundMessageDto message = createInboundMessage();

            when(idempotencyService.checkDuplicate(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(fatherResolver.findBySenderIdentity(anyString(), anyString()))
                    .thenReturn(Optional.of(new FatherResolver.ResolvedFather(FATHER_ID, "ACTIVE")));
            doThrow(new SessionLockTimeoutException(FATHER_ID, 45))
                    .when(sessionLockService).acquireLock(FATHER_ID);
            when(fallbackProvider.getGenericFallback())
                    .thenReturn("Fallback response");

            OutboundMessageDto result = orchestrator.processMessage(message);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("Fallback response");
        }
    }

    @Nested
    @DisplayName("4.6 — Transaction commit releases advisory lock")
    class TransactionReleasesLock {

        @Test
        @DisplayName("advisory lock is acquired within the transactional method")
        void advisoryLockAcquiredInTransaction() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            // The lock is acquired — upon commit it will be released
            verify(sessionLockService).acquireLock(FATHER_ID);
            // No explicit unlock call — release is via transaction commit
            verifyNoMoreInteractions(sessionLockService);
        }
    }

    @Nested
    @DisplayName("4.7 — Latency budget: 30 seconds total")
    class LatencyBudget {

        @Test
        @DisplayName("pipeline completes within normal path without timeout enforcement issues")
        void normalPath_completesWithinBudget() {
            // Latency budget enforcement comes from lock timeout (45s) + AI timeout,
            // not from the orchestrator itself. This test verifies the pipeline doesn't
            // add unnecessary delays.
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            long start = System.currentTimeMillis();
            orchestrator.processMessage(message);
            long elapsed = System.currentTimeMillis() - start;

            // Should complete very quickly in unit test (mocked dependencies)
            assertThat(elapsed).isLessThan(5000);
        }
    }

    @Nested
    @DisplayName("Follow-up action processing")
    class FollowUpActions {

        @Test
        @DisplayName("GENERATE_MISSION action triggers MissionOrchestrator")
        void generateMission_triggersMissionOrchestrator() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            AiResult missionResult = AiResult.success(
                    "Aquí tienes una misión nueva", "GENERATE_MISSION", Map.of());
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(missionResult);

            orchestrator.processMessage(message);

            verify(missionOrchestrator).generateMission(FATHER_ID, conversation);
        }

        @Test
        @DisplayName("CLOSE_CONVERSATION action triggers conversation closure")
        void closeConversation_triggersConversationClosure() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            AiResult closeResult = AiResult.success(
                    "¡Fue un placer hablar contigo!", "CLOSE_CONVERSATION", Map.of());
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(closeResult);

            orchestrator.processMessage(message);

            verify(missionOrchestrator).closeConversation(conversation);
        }

        @Test
        @DisplayName("NONE action does not trigger any follow-up")
        void noneAction_noFollowUp() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            verify(missionOrchestrator, never()).generateMission(any(), any());
            verify(missionOrchestrator, never()).closeConversation(any());
        }
    }

    @Nested
    @DisplayName("Evaluate completion")
    class EvaluateCompletion {

        @Test
        @DisplayName("conversation at max outbound messages is completed")
        void maxMessages_completesConversation() {
            InboundMessageDto message = createInboundMessage();
            UUID convId = UUID.randomUUID();

            // Create a conversation that already has max-1 system messages
            Conversation conversation = mock(Conversation.class);
            when(conversation.getId()).thenReturn(convId);
            when(conversation.getFatherId()).thenReturn(FATHER_ID);
            when(conversation.getType()).thenReturn("DAILY_COACHING");
            when(conversation.getStatus()).thenReturn("ACTIVE");
            when(conversation.getSystemMessageCount()).thenReturn(7); // Will become 8 after this message

            // Setup pipeline
            when(idempotencyService.checkDuplicate(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(fatherResolver.findBySenderIdentity(anyString(), anyString()))
                    .thenReturn(Optional.of(new FatherResolver.ResolvedFather(FATHER_ID, "ACTIVE")));
            when(conversationService.findActiveConversation(FATHER_ID))
                    .thenReturn(Optional.of(conversation));
            when(conversationService.isExpired(conversation)).thenReturn(false);

            ConversationContext context = new ConversationContext(
                    FATHER_ID, convId, "DAILY_COACHING",
                    Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
            when(contextAssembler.assembleContext(eq(FATHER_ID), eq(conversation), any()))
                    .thenReturn(context);
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(createSuccessfulAiResult());
            when(messageRepository.countByConversationId(any())).thenReturn(14);
            ConversationMessage savedMsg = mock(ConversationMessage.class);
            when(savedMsg.getId()).thenReturn(UUID.randomUUID());
            when(messageRepository.save(any())).thenReturn(savedMsg);

            // After incrementing outbound, conversation will have 8 system messages
            doAnswer(invocation -> {
                when(conversation.getSystemMessageCount()).thenReturn(8);
                return null;
            }).when(conversationService).incrementMessageCount(convId, "OUTBOUND");

            orchestrator.processMessage(message);

            verify(conversationService).completeConversation(convId, "MAX_MESSAGES");
            verify(eventPublisher).publishConversationCompleted(conversation);
            verify(memoryOrchestrator).scheduleExtraction(conversation);
        }
    }

    @Nested
    @DisplayName("Side-effects scheduling")
    class SideEffectsScheduling {

        @Test
        @DisplayName("side-effects are scheduled after persist")
        void sideEffectsScheduled() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            verify(sideEffectScheduler).schedule(
                    eq("MEMORY_INJECTION_TRACKING"),
                    eq(FATHER_ID),
                    any(),
                    any()
            );
        }

        @Test
        @DisplayName("fallback-used AI result schedules deferred regeneration")
        void fallbackUsed_schedulesDeferredRegeneration() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            AiResult fallbackResult = AiResult.fallback("Respuesta de respaldo");
            when(aiOrchestrator.orchestrate(any(), any())).thenReturn(fallbackResult);

            orchestrator.processMessage(message);

            verify(sideEffectScheduler).schedule(
                    eq("DEFERRED_AI_REGENERATION"),
                    eq(FATHER_ID),
                    any(),
                    argThat(payload -> "fallback_used".equals(payload.get("reason")))
            );
        }
    }

    @Nested
    @DisplayName("Idempotency key recording")
    class IdempotencyRecording {

        @Test
        @DisplayName("idempotency key recorded after successful pipeline execution")
        void recordsIdempotencyKeyOnSuccess() {
            InboundMessageDto message = createInboundMessage();
            Conversation conversation = createActiveConversation();
            setupHappyPath(message, conversation);

            orchestrator.processMessage(message);

            verify(idempotencyService).recordProcessed(
                    eq(message.idempotencyKey()),
                    eq(FATHER_ID),
                    any(UUID.class)
            );
        }
    }
}
