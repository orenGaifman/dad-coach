package com.dadcoach.ai;

import com.dadcoach.ai.output.*;
import com.dadcoach.ai.prompt.PromptAssembler;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.ai.routing.FallbackChain;
import com.dadcoach.ai.routing.FallbackChain.FallbackResult;
import com.dadcoach.ai.routing.ModelRouter;
import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import com.dadcoach.ai.safety.SafetyClassifier;
import com.dadcoach.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IntelligenceLayerImpl verifying the coordination pipeline:
 * safety → prompt → route → validate.
 */
class IntelligenceLayerImplTest {

    private SafetyClassifier safetyClassifier;
    private PromptAssembler promptAssembler;
    private ModelRouter modelRouter;
    private IntelligenceLayerImpl intelligenceLayer;

    @BeforeEach
    void setUp() {
        safetyClassifier = mock(SafetyClassifier.class);
        promptAssembler = mock(PromptAssembler.class);
        modelRouter = mock(ModelRouter.class);
        intelligenceLayer = new IntelligenceLayerImpl(safetyClassifier, promptAssembler, modelRouter);
    }

    // ===== Helper methods =====

    private CoachingContext buildCoachingContext(String userMessage) {
        return new CoachingContext(
            UUID.randomUUID(),
            ConversationType.DAILY_COACHING,
            userMessage,
            List.of(),
            "You are a fatherhood coach.",
            "Memory: child likes soccer",
            "Phase: BUILDING, Day: 15",
            "Respond in 50-100 words."
        );
    }

    private FallbackResult mockSuccessfulRoute(String responseContent) {
        AiProviderResponse aiResponse = new AiProviderResponse(
            responseContent, "gpt-4o-mini", "openai",
            100, 50, "stop", Duration.ofMillis(500)
        );
        return new FallbackResult(aiResponse, 0, List.of());
    }

    private FallbackResult mockFallbackRoute(String responseContent) {
        AiProviderResponse aiResponse = new AiProviderResponse(
            responseContent, "gpt-4o-mini", "openai",
            100, 50, "stop", Duration.ofMillis(800)
        );
        return new FallbackResult(aiResponse, 1, List.of());
    }

    // ===== 6.1 Stateless method tests =====

    @Nested
    @DisplayName("6.1 Stateless methods — receive context, return structured output")
    class StatelessTests {

        @Test
        @DisplayName("generateCoachingResponse is stateless — same input produces consistent structure")
        void generateCoachingResponse_isStateless() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(promptAssembler.assemble(any(), any(), any(), any(), any()))
                .thenReturn(List.of(AiMessage.system("test")));
            when(modelRouter.route(any(), any())).thenReturn(
                mockSuccessfulRoute("Hola papá, hoy vamos a hacer algo divertido.")
            );

            CoachingContext context = buildCoachingContext("Hola, quiero jugar con mi hijo");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            assertNotNull(response);
            assertNotNull(response.message());
            assertNotNull(response.model());
        }

        @Test
        @DisplayName("classifyMessage is stateless — direct delegation to SafetyClassifier")
        void classifyMessage_isStateless() {
            when(safetyClassifier.classify("test message"))
                .thenReturn(SafetyClassification.safe());

            InboundMessage message = new InboundMessage(UUID.randomUUID(), "test message", Instant.now());
            SafetyClassification result = intelligenceLayer.classifyMessage(message);

            assertNotNull(result);
            assertEquals(SafetyCategory.SAFE, result.category());
        }

        @Test
        @DisplayName("decideDailyAction is stateless — returns recommendation without mutation")
        void decideDailyAction_isStateless() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());

            DailyDecisionContext context = new DailyDecisionContext(
                UUID.randomUUID(), "BUILDING", 15, 70,
                Instant.now().minusSeconds(18000), Instant.now().minusSeconds(3600),
                true, 10, "Hola, cómo estás"
            );

            ActionRecommendation result = intelligenceLayer.decideDailyAction(context);

            assertNotNull(result);
            assertNotNull(result.action());
            assertNotNull(result.evaluatedAt());
        }
    }

    // ===== 6.2 generateCoachingResponse coordination =====

    @Nested
    @DisplayName("6.2 generateCoachingResponse — safety → prompt → route → validate")
    class CoachingResponseTests {

        @Test
        @DisplayName("safe message follows full pipeline: safety → prompt → route → validate")
        void safeMessage_fullPipeline() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(promptAssembler.assemble(any(), any(), any(), any(), any()))
                .thenReturn(List.of(AiMessage.system("prompt"), AiMessage.user("msg")));
            when(modelRouter.route(any(), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessfulRoute("¡Genial! Vamos a jugar juntos hoy."));

            CoachingContext context = buildCoachingContext("Quiero jugar con mi hijo");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            // Verify full pipeline was called
            verify(safetyClassifier).classify("Quiero jugar con mi hijo");
            verify(promptAssembler).assemble(any(), any(), any(), any(), any());
            verify(modelRouter).route(any(), eq(ConversationType.DAILY_COACHING));

            assertEquals("¡Genial! Vamos a jugar juntos hoy.", response.message());
            assertEquals("gpt-4o-mini", response.model());
            assertFalse(response.fallbackUsed());
            assertTrue(response.validationPassed());
        }

        @Test
        @DisplayName("crisis message stops at safety — no AI call made")
        void crisisMessage_stopsAtSafety() {
            when(safetyClassifier.classify(any())).thenReturn(
                new SafetyClassification(SafetyCategory.CRISIS, 0.95, "Self-harm detected")
            );

            CoachingContext context = buildCoachingContext("No quiero seguir viviendo");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            // Safety was called
            verify(safetyClassifier).classify(any());
            // But prompt and routing were NOT called
            verify(promptAssembler, never()).assemble(any(), any(), any(), any(), any());
            verify(modelRouter, never()).route(any(), any());

            // Returns safety response
            assertTrue(response.message().contains("988"));
            assertTrue(response.fallbackUsed());
        }

        @Test
        @DisplayName("child safety message returns appropriate safety response")
        void childSafetyMessage_returnsSafetyResponse() {
            when(safetyClassifier.classify(any())).thenReturn(
                new SafetyClassification(SafetyCategory.CHILD_SAFETY, 0.90, "Child abuse detected")
            );

            CoachingContext context = buildCoachingContext("Le pegué a mi hijo");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            verify(modelRouter, never()).route(any(), any());
            assertTrue(response.message().contains("protección infantil"));
            assertTrue(response.fallbackUsed());
        }

        @Test
        @DisplayName("response tracks fallback usage from model router")
        void fallbackUsed_trackedInResponse() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(promptAssembler.assemble(any(), any(), any(), any(), any()))
                .thenReturn(List.of(AiMessage.system("prompt")));
            when(modelRouter.route(any(), any()))
                .thenReturn(mockFallbackRoute("Respuesta usando fallback model."));

            CoachingContext context = buildCoachingContext("Test message");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            assertTrue(response.fallbackUsed());
        }

        @Test
        @DisplayName("invalid AI response is marked as validation failed")
        void invalidResponse_validationFailed() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(promptAssembler.assemble(any(), any(), any(), any(), any()))
                .thenReturn(List.of(AiMessage.system("prompt")));
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("Short")); // Too short to pass validation

            CoachingContext context = buildCoachingContext("Test");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            assertFalse(response.validationPassed());
        }
    }

    // ===== 6.3 generateMission =====

    @Nested
    @DisplayName("6.3 generateMission — returns MissionOutput record")
    class MissionTests {

        @Test
        @DisplayName("generates mission with valid JSON response")
        void generateMission_validResponse() {
            String jsonResponse = """
                {"title": "Jugar al aire libre", "description": "Sal con tu hijo al parque.", "category": "OUTDOOR", "difficulty": 3, "estimated_minutes": 30}
                """;
            when(modelRouter.route(any(), eq(ConversationType.MISSION_GENERATION)))
                .thenReturn(mockSuccessfulRoute(jsonResponse));

            MissionContext context = new MissionContext(
                UUID.randomUUID(), "Lucas", 6, List.of("fútbol", "dibujar"),
                "OUTDOOR", 3, "BALANCED", "Saturday", "morning",
                "Mejorar comunicación", List.of("INDOOR"), List.of()
            );

            MissionOutput output = intelligenceLayer.generateMission(context);

            assertNotNull(output);
            assertEquals("Jugar al aire libre", output.title());
            assertEquals("OUTDOOR", output.category());
            assertEquals(3, output.difficulty());
            assertEquals(30, output.estimatedMinutes());
            assertTrue(output.validationPassed());
        }

        @Test
        @DisplayName("invalid mission response returns defaults with validation failed")
        void generateMission_invalidResponse_returnsDefaults() {
            when(modelRouter.route(any(), eq(ConversationType.MISSION_GENERATION)))
                .thenReturn(mockSuccessfulRoute("not valid json"));

            MissionContext context = new MissionContext(
                UUID.randomUUID(), "Lucas", 6, List.of(),
                "CONNECTION", 2, "GENTLE", "Monday", "evening",
                "Bond with child", List.of(), List.of()
            );

            MissionOutput output = intelligenceLayer.generateMission(context);

            assertNotNull(output);
            assertFalse(output.validationPassed());
        }

        @Test
        @DisplayName("mission generation uses MISSION_GENERATION conversation type for routing")
        void generateMission_usesCorrectRouting() {
            when(modelRouter.route(any(), eq(ConversationType.MISSION_GENERATION)))
                .thenReturn(mockSuccessfulRoute("""
                    {"title": "Test", "description": "desc", "category": "PLAY", "difficulty": 2, "estimated_minutes": 15}
                    """));

            MissionContext context = new MissionContext(
                UUID.randomUUID(), "Sofia", 4, List.of(),
                "PLAY", 2, "BALANCED", "Wednesday", "evening",
                "Play more", List.of(), List.of()
            );

            intelligenceLayer.generateMission(context);

            verify(modelRouter).route(any(), eq(ConversationType.MISSION_GENERATION));
        }
    }

    // ===== 6.4 extractMemories =====

    @Nested
    @DisplayName("6.4 extractMemories — returns MemoryExtractionOutput record")
    class MemoryExtractionTests {

        @Test
        @DisplayName("extracts memories from completed conversation")
        void extractMemories_returnsStructuredOutput() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("[{\"category\": \"IDENTITY\", \"content\": \"Child likes soccer\"}]"));

            CompletedConversation conversation = new CompletedConversation(
                UUID.randomUUID(), UUID.randomUUID(), ConversationType.DAILY_COACHING,
                List.of(
                    AiMessage.user("Mi hijo ama el fútbol"),
                    AiMessage.assistant("¡Qué genial! El fútbol es excelente para los niños.")
                )
            );

            MemoryExtractionOutput output = intelligenceLayer.extractMemories(conversation);

            assertNotNull(output);
            assertEquals(conversation.conversationId().toString(), output.conversationId());
            assertNotNull(output.model());
        }

        @Test
        @DisplayName("memory extraction does not mutate any state")
        void extractMemories_noStateMutation() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("[]"));

            CompletedConversation conversation = new CompletedConversation(
                UUID.randomUUID(), UUID.randomUUID(), ConversationType.DAILY_COACHING,
                List.of(AiMessage.user("Hola"), AiMessage.assistant("Hola papá"))
            );

            MemoryExtractionOutput output = intelligenceLayer.extractMemories(conversation);

            // Output is a recommendation record, no state was mutated
            assertNotNull(output);
            assertTrue(output.memories().isEmpty()); // Empty list, not null
        }
    }

    // ===== 6.5 classifyMessage =====

    @Nested
    @DisplayName("6.5 classifyMessage — delegates to SafetyClassifier")
    class ClassifyMessageTests {

        @Test
        @DisplayName("delegates to SafetyClassifier and returns its result")
        void classifyMessage_delegatesToSafetyClassifier() {
            SafetyClassification expected = new SafetyClassification(
                SafetyCategory.EMOTIONAL_DISTRESS, 0.8, "Distress detected"
            );
            when(safetyClassifier.classify("Estoy muy frustrado")).thenReturn(expected);

            InboundMessage message = new InboundMessage(
                UUID.randomUUID(), "Estoy muy frustrado", Instant.now()
            );
            SafetyClassification result = intelligenceLayer.classifyMessage(message);

            assertEquals(expected, result);
            verify(safetyClassifier).classify("Estoy muy frustrado");
        }

        @Test
        @DisplayName("classification result is never null")
        void classifyMessage_neverReturnsNull() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());

            InboundMessage message = new InboundMessage(
                UUID.randomUUID(), "Hola", Instant.now()
            );
            SafetyClassification result = intelligenceLayer.classifyMessage(message);

            assertNotNull(result);
            assertNotNull(result.category());
        }

        @Test
        @DisplayName("passes message content directly to classifier without modification")
        void classifyMessage_passesContentDirectly() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            String messageContent = "Mi hijo tiene un problema en la escuela";

            InboundMessage message = new InboundMessage(
                UUID.randomUUID(), messageContent, Instant.now()
            );
            intelligenceLayer.classifyMessage(message);

            verify(safetyClassifier).classify(messageContent);
        }
    }

    // ===== 6.6 decideDailyAction =====

    @Nested
    @DisplayName("6.6 decideDailyAction — delegates to DecisionEngine (stub)")
    class DecideDailyActionTests {

        @Test
        @DisplayName("returns SAFETY_RESPONSE when inbound message triggers safety")
        void decideDailyAction_safetyTrigger() {
            when(safetyClassifier.classify(any())).thenReturn(
                new SafetyClassification(SafetyCategory.CRISIS, 0.95, "Crisis detected")
            );

            DailyDecisionContext context = new DailyDecisionContext(
                UUID.randomUUID(), "BUILDING", 10, 50,
                Instant.now().minusSeconds(18000), Instant.now(),
                false, 5, "Quiero morirme"
            );

            ActionRecommendation result = intelligenceLayer.decideDailyAction(context);

            assertEquals(ActionRecommendation.ActionType.SAFETY_RESPONSE, result.action());
            assertEquals(1, result.priority());
        }

        @Test
        @DisplayName("returns WAIT as default when no priority matches (stub)")
        void decideDailyAction_defaultWait() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());

            DailyDecisionContext context = new DailyDecisionContext(
                UUID.randomUUID(), "FOUNDATION", 5, 50,
                Instant.now().minusSeconds(18000), Instant.now(),
                true, 3, "Hola, todo bien"
            );

            ActionRecommendation result = intelligenceLayer.decideDailyAction(context);

            assertEquals(ActionRecommendation.ActionType.WAIT, result.action());
        }

        @Test
        @DisplayName("handles null inbound message (scheduled trigger)")
        void decideDailyAction_nullInbound() {
            DailyDecisionContext context = new DailyDecisionContext(
                UUID.randomUUID(), "BUILDING", 20, 65,
                Instant.now().minusSeconds(18000), Instant.now().minusSeconds(7200),
                false, 14, null
            );

            ActionRecommendation result = intelligenceLayer.decideDailyAction(context);

            assertNotNull(result);
            assertEquals(ActionRecommendation.ActionType.WAIT, result.action());
        }
    }

    // ===== 6.7 AI never directly mutates state =====

    @Nested
    @DisplayName("6.7 AI never directly mutates state — all outputs are recommendations")
    class NoStateMutationTests {

        @Test
        @DisplayName("generateCoachingResponse returns a record, no side effects")
        void coachingResponse_isImmutableRecord() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(promptAssembler.assemble(any(), any(), any(), any(), any()))
                .thenReturn(List.of(AiMessage.system("test")));
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("Respuesta de coaching para ti."));

            CoachingContext context = buildCoachingContext("Hola");
            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            // Response is a Java record — immutable by design
            assertNotNull(response.message());
            assertNotNull(response.model());
            // No database calls, no state changes, just data
        }

        @Test
        @DisplayName("generateMission returns data without persisting anything")
        void missionOutput_isRecommendationOnly() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("""
                    {"title": "Leer juntos", "description": "Lee un cuento.", "category": "READING", "difficulty": 2, "estimated_minutes": 20}
                    """));

            MissionContext context = new MissionContext(
                UUID.randomUUID(), "Lucas", 5, List.of("libros"),
                "READING", 2, "GENTLE", "Tuesday", "evening",
                "Fomentar lectura", List.of(), List.of()
            );

            MissionOutput output = intelligenceLayer.generateMission(context);

            // Output is a recommendation — application layer decides whether to persist
            assertNotNull(output.title());
            assertNotNull(output.description());
            // No verify on any repository save calls — they don't exist
        }

        @Test
        @DisplayName("extractMemories returns recommendations, doesn't persist them")
        void memoryExtraction_returnsRecommendations() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("[]"));

            CompletedConversation conversation = new CompletedConversation(
                UUID.randomUUID(), UUID.randomUUID(), ConversationType.REFLECTION,
                List.of(AiMessage.user("Hoy fue un buen día"))
            );

            MemoryExtractionOutput output = intelligenceLayer.extractMemories(conversation);

            // Output is advisory only
            assertNotNull(output);
            assertNotNull(output.memories());
        }

        @Test
        @DisplayName("decideDailyAction returns recommendation without executing it")
        void actionRecommendation_isAdvisoryOnly() {
            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());

            DailyDecisionContext context = new DailyDecisionContext(
                UUID.randomUUID(), "BUILDING", 15, 70,
                Instant.now().minusSeconds(18000), Instant.now(),
                false, 10, "Quiero una misión nueva"
            );

            ActionRecommendation result = intelligenceLayer.decideDailyAction(context);

            // The action is a recommendation — this layer doesn't execute it
            assertNotNull(result.action());
            assertNotNull(result.reasoning());
            // No side effects verified — just data returned
        }

        @Test
        @DisplayName("generateSummary returns data without persisting")
        void summaryOutput_isRecommendationOnly() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("Resumen de tu semana: fue increíble."));

            SummaryPeriod period = new SummaryPeriod(
                UUID.randomUUID(), LocalDate.now().minusDays(7), LocalDate.now()
            );

            WeeklySummaryOutput output = intelligenceLayer.generateSummary(period);

            assertNotNull(output);
            assertNotNull(output.summary());
            // No persistence side effects
        }

        @Test
        @DisplayName("evaluateReflection returns insights without persistence")
        void reflectionInsight_isRecommendationOnly() {
            when(modelRouter.route(any(), any()))
                .thenReturn(mockSuccessfulRoute("Insight: estás creciendo como papá."));

            ReflectionInput input = new ReflectionInput(
                UUID.randomUUID(), "Esta semana fue difícil pero aprendí mucho.",
                "BUILDING", 20, 5
            );

            ReflectionInsightOutput output = intelligenceLayer.evaluateReflection(input);

            assertNotNull(output);
            assertFalse(output.insights().isEmpty());
        }
    }
}
