package com.dadcoach.conversation.ai;

import com.dadcoach.ai.AiProviderUnavailableException;
import com.dadcoach.ai.IntelligenceLayer;
import com.dadcoach.ai.output.CoachingContext;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.ai.safety.SafetyClassification;
import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import com.dadcoach.ai.safety.SafetyClassifier;
import com.dadcoach.ai.safety.SafetyResponseProvider;
import com.dadcoach.conversation.context.ConversationContext;
import com.dadcoach.conversation.dto.InboundMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiOrchestratorImpl Unit Tests")
class AiOrchestratorImplTest {

    @Mock private SafetyClassifier safetyClassifier;
    @Mock private SafetyResponseProvider safetyResponseProvider;
    @Mock private IntelligenceLayer intelligenceLayer;
    @Mock private ResponseValidator responseValidator;
    @Mock private FallbackResponseProvider fallbackProvider;

    private AiOrchestratorImpl orchestrator;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new AiOrchestratorImpl(
                safetyClassifier,
                safetyResponseProvider,
                intelligenceLayer,
                responseValidator,
                fallbackProvider
        );
    }

    private ConversationContext createContext() {
        return new ConversationContext(
                FATHER_ID,
                CONVERSATION_ID,
                "DAILY_COACHING",
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private InboundMessageDto createMessage(String content) {
        return new InboundMessageDto(
                "whatsapp",
                "+5491155551234",
                content,
                "TEXT",
                "msg-" + UUID.randomUUID(),
                Instant.now(),
                Map.of()
        );
    }

    private CoachingResponse createCoachingResponse(String message) {
        return new CoachingResponse(
                message, "gpt-4", "openai",
                100, 50, Duration.ofMillis(800),
                false, true, 0.9
        );
    }

    @Nested
    @DisplayName("7.1 — Safety classification runs FIRST (before any coaching generation)")
    class SafetyClassificationFirst {

        @Test
        @DisplayName("safety classification is called before intelligenceLayer")
        void safetyClassificationRunsFirst() {
            InboundMessageDto message = createMessage("Hola, necesito ayuda");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(message.content()))
                    .thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Te ayudo con gusto"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            orchestrator.orchestrate(context, message);

            var inOrder = inOrder(safetyClassifier, intelligenceLayer);
            inOrder.verify(safetyClassifier).classify(message.content());
            inOrder.verify(intelligenceLayer).generateCoachingResponse(any());
        }

        @Test
        @DisplayName("safety classification runs even for normal messages")
        void safetyAlwaysRuns() {
            InboundMessageDto message = createMessage("¿Cómo le enseño a mi hijo?");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(message.content()))
                    .thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Respuesta coaching"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            orchestrator.orchestrate(context, message);

            verify(safetyClassifier).classify(message.content());
        }
    }

    @Nested
    @DisplayName("7.2 — Escalation (CRISIS, CHILD_SAFETY) triggers immediate safety response")
    class EscalationHandling {

        @Test
        @DisplayName("CRISIS classification returns immediate safety response without generation")
        void crisisReturnsImmediateSafetyResponse() {
            InboundMessageDto message = createMessage("No quiero seguir viviendo");
            ConversationContext context = createContext();

            SafetyClassification crisis = new SafetyClassification(
                    SafetyCategory.CRISIS, 0.95, "Self-harm keyword detected");
            when(safetyClassifier.classify(message.content())).thenReturn(crisis);
            when(safetyResponseProvider.getResponse(crisis))
                    .thenReturn("Línea 988: llama al 988");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Línea 988: llama al 988");
            assertThat(result.isSafetyEscalation()).isTrue();
            assertThat(result.safetyClassification()).isEqualTo("CRISIS");
            assertThat(result.fallbackUsed()).isFalse();
            assertThat(result.retried()).isFalse();

            // No coaching generation should have occurred
            verifyNoInteractions(intelligenceLayer);
            verifyNoInteractions(responseValidator);
        }

        @Test
        @DisplayName("CHILD_SAFETY classification returns immediate safety response without generation")
        void childSafetyReturnsImmediateSafetyResponse() {
            InboundMessageDto message = createMessage("Mi hijo tiene moretones");
            ConversationContext context = createContext();

            SafetyClassification childSafety = new SafetyClassification(
                    SafetyCategory.CHILD_SAFETY, 0.90, "Child safety keyword detected");
            when(safetyClassifier.classify(message.content())).thenReturn(childSafety);
            when(safetyResponseProvider.getResponse(childSafety))
                    .thenReturn("Contacta Childhelp: 1-800-422-4453");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Contacta Childhelp: 1-800-422-4453");
            assertThat(result.isSafetyEscalation()).isTrue();
            assertThat(result.safetyClassification()).isEqualTo("CHILD_SAFETY");

            verifyNoInteractions(intelligenceLayer);
        }

        @Test
        @DisplayName("non-escalation safety classification proceeds to generation")
        void nonEscalationProceeds() {
            InboundMessageDto message = createMessage("Tengo preguntas médicas");
            ConversationContext context = createContext();

            // MEDICAL is not an escalation (requiresIntervention = false)
            SafetyClassification medical = new SafetyClassification(
                    SafetyCategory.MEDICAL, 0.85, "Medical keyword detected");
            when(safetyClassifier.classify(message.content())).thenReturn(medical);
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Consulta con tu pediatra"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.isSafetyEscalation()).isFalse();
            assertThat(result.safetyClassification()).isNull();
            verify(intelligenceLayer).generateCoachingResponse(any());
        }
    }

    @Nested
    @DisplayName("7.3 — Generate then validate; if fails then retry once with correction")
    class GenerateValidateRetry {

        @Test
        @DisplayName("successful first attempt returns without retry")
        void successfulFirstAttempt() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Respuesta exitosa"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Respuesta exitosa");
            assertThat(result.retried()).isFalse();
            assertThat(result.fallbackUsed()).isFalse();

            // Only one call to generate
            verify(intelligenceLayer, times(1)).generateCoachingResponse(any());
        }

        @Test
        @DisplayName("failed validation triggers retry with correction and succeeds")
        void failedValidationRetrySucceeds() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            CoachingResponse badResponse = createCoachingResponse("Bad response");
            CoachingResponse goodResponse = createCoachingResponse("Good response after correction");

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(badResponse)
                    .thenReturn(goodResponse);
            when(responseValidator.validate(badResponse, context))
                    .thenReturn(ValidationResult.fail("Response too short"));
            when(responseValidator.validate(goodResponse, context))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Good response after correction");
            assertThat(result.retried()).isTrue();
            assertThat(result.fallbackUsed()).isFalse();

            // Two calls: initial + retry
            verify(intelligenceLayer, times(2)).generateCoachingResponse(any());
        }

        @Test
        @DisplayName("maximum 1 retry (2 total AI calls)")
        void maxOneRetry() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Always bad"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.fail("Still invalid"));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Fallback message");

            orchestrator.orchestrate(context, message);

            // Exactly 2 calls — no more retries
            verify(intelligenceLayer, times(2)).generateCoachingResponse(any());
        }
    }

    @Nested
    @DisplayName("7.4 — If retry fails deliver fallback response")
    class RetryFailsFallback {

        @Test
        @DisplayName("both attempts failing validation delivers fallback")
        void bothAttemptsFail_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Invalid response"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.fail(List.of("too short", "not in Spanish")));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Estoy aquí para ayudarte. ¿En qué puedo apoyarte?");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent())
                    .isEqualTo("Estoy aquí para ayudarte. ¿En qué puedo apoyarte?");
            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.retried()).isFalse(); // fallback result doesn't track retry
        }

        @Test
        @DisplayName("fallback result has correct metadata")
        void fallbackHasCorrectMetadata() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Bad"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.fail("validation error"));
            when(fallbackProvider.getForType("DAILY_COACHING")).thenReturn("Fallback");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.metadata()).containsEntry("fallback_used", true);
            assertThat(result.suggestedFollowUpAction()).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("7.5 — Provider exception triggers fallback response delivery")
    class ProviderExceptionFallback {

        @Test
        @DisplayName("AiProviderUnavailableException delivers fallback")
        void providerUnavailable_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenThrow(new AiProviderUnavailableException("All providers down", 3));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Tenemos dificultades técnicas");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Tenemos dificultades técnicas");
            assertThat(result.fallbackUsed()).isTrue();
        }

        @Test
        @DisplayName("exception during retry still delivers fallback")
        void exceptionDuringRetry_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            CoachingResponse badResponse = createCoachingResponse("Bad");

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(badResponse)
                    .thenThrow(new AiProviderUnavailableException("Timeout on retry", 1));
            when(responseValidator.validate(badResponse, context))
                    .thenReturn(ValidationResult.fail("invalid"));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Fallback after retry exception");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.responseContent()).isEqualTo("Fallback after retry exception");
            assertThat(result.fallbackUsed()).isTrue();
        }
    }

    @Nested
    @DisplayName("7.6 — Never throws - always produces a deliverable response")
    class NeverThrows {

        @Test
        @DisplayName("RuntimeException in generate delivers fallback")
        void runtimeException_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenThrow(new RuntimeException("unexpected NPE"));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Generic fallback");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result).isNotNull();
            assertThat(result.responseContent()).isEqualTo("Generic fallback");
            assertThat(result.fallbackUsed()).isTrue();
        }

        @Test
        @DisplayName("exception in safety classifier delivers fallback")
        void safetyException_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any()))
                    .thenThrow(new RuntimeException("classifier broken"));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Fallback on safety error");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result).isNotNull();
            assertThat(result.responseContent()).isEqualTo("Fallback on safety error");
            assertThat(result.fallbackUsed()).isTrue();
        }

        @Test
        @DisplayName("exception in validator delivers fallback")
        void validatorException_deliversFallback() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Good response"));
            when(responseValidator.validate(any(), any()))
                    .thenThrow(new RuntimeException("validator NPE"));
            when(fallbackProvider.getForType("DAILY_COACHING"))
                    .thenReturn("Fallback on validator error");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result).isNotNull();
            assertThat(result.responseContent()).isEqualTo("Fallback on validator error");
            assertThat(result.fallbackUsed()).isTrue();
        }

        @Test
        @DisplayName("null/error in fallback provider still returns something")
        void fallbackProviderException_stillReturnsResponse() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenThrow(new RuntimeException("all broken"));
            when(fallbackProvider.getForType(any()))
                    .thenThrow(new RuntimeException("fallback also broken"));
            when(fallbackProvider.getGenericFallback())
                    .thenReturn("Lo siento, estoy experimentando dificultades.");

            // This tests the absolute last-resort — if even the typed fallback fails,
            // the orchestrator should try the generic fallback
            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result).isNotNull();
            assertThat(result.responseContent()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("7.7 — AiResult tracks whether fallback or retry was used")
    class ResultTracking {

        @Test
        @DisplayName("successful first attempt: fallbackUsed=false, retried=false")
        void successFirstAttempt_tracking() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Good"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.fallbackUsed()).isFalse();
            assertThat(result.retried()).isFalse();
            assertThat(result.safetyClassification()).isNull();
        }

        @Test
        @DisplayName("successful retry: fallbackUsed=false, retried=true")
        void successRetry_tracking() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            CoachingResponse bad = createCoachingResponse("Bad");
            CoachingResponse good = createCoachingResponse("Good retry");

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(bad).thenReturn(good);
            when(responseValidator.validate(bad, context))
                    .thenReturn(ValidationResult.fail("failed"));
            when(responseValidator.validate(good, context))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.fallbackUsed()).isFalse();
            assertThat(result.retried()).isTrue();
            assertThat(result.safetyClassification()).isNull();
        }

        @Test
        @DisplayName("fallback used: fallbackUsed=true, retried=false")
        void fallbackUsed_tracking() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenThrow(new AiProviderUnavailableException("down", 2));
            when(fallbackProvider.getForType("DAILY_COACHING")).thenReturn("Fallback");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.retried()).isFalse();
            assertThat(result.safetyClassification()).isNull();
        }

        @Test
        @DisplayName("safety escalation: safetyClassification is populated")
        void safetyEscalation_tracking() {
            InboundMessageDto message = createMessage("Quiero matarme");
            ConversationContext context = createContext();

            SafetyClassification crisis = new SafetyClassification(
                    SafetyCategory.CRISIS, 0.95, "crisis");
            when(safetyClassifier.classify(any())).thenReturn(crisis);
            when(safetyResponseProvider.getResponse(crisis)).thenReturn("Call 988");

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.safetyClassification()).isEqualTo("CRISIS");
            assertThat(result.fallbackUsed()).isFalse();
            assertThat(result.retried()).isFalse();
        }

        @Test
        @DisplayName("metadata includes model and latency for successful generation")
        void metadata_includesModelAndLatency() {
            InboundMessageDto message = createMessage("Hola");
            ConversationContext context = createContext();

            when(safetyClassifier.classify(any())).thenReturn(SafetyClassification.safe());
            when(intelligenceLayer.generateCoachingResponse(any()))
                    .thenReturn(createCoachingResponse("Response"));
            when(responseValidator.validate(any(), any()))
                    .thenReturn(ValidationResult.pass());

            AiResult result = orchestrator.orchestrate(context, message);

            assertThat(result.metadata()).containsKey("model_used");
            assertThat(result.metadata()).containsKey("latency_ms");
            assertThat(result.metadata()).containsKey("input_tokens");
            assertThat(result.metadata()).containsKey("output_tokens");
            assertThat(result.metadata().get("model_used")).isEqualTo("gpt-4");
        }
    }
}
