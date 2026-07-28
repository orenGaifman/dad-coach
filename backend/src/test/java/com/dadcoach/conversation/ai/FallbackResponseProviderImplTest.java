package com.dadcoach.conversation.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackResponseProviderImpl Unit Tests")
class FallbackResponseProviderImplTest {

    private FallbackResponseProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new FallbackResponseProviderImpl();
    }

    @Nested
    @DisplayName("8.4 — FallbackResponseProvider has pre-written messages per conversation type")
    class PreWrittenMessages {

        @Test
        @DisplayName("DAILY_COACHING returns specific fallback message")
        void dailyCoaching_returnsFallback() {
            String result = provider.getForType("DAILY_COACHING");

            assertThat(result).isEqualTo(
                    "¡Hola! Estoy aquí para acompañarte en tu camino como papá. ¿En qué puedo ayudarte hoy?");
        }

        @Test
        @DisplayName("ONBOARDING returns specific fallback message")
        void onboarding_returnsFallback() {
            String result = provider.getForType("ONBOARDING");

            assertThat(result).isEqualTo(
                    "¡Bienvenido! Estoy teniendo un pequeño problema técnico, pero en unos minutos estaré listo para conocerte mejor.");
        }

        @Test
        @DisplayName("DIFFICULT_SITUATION returns specific fallback message")
        void difficultSituation_returnsFallback() {
            String result = provider.getForType("DIFFICULT_SITUATION");

            assertThat(result).isEqualTo(
                    "Entiendo que estás pasando por un momento difícil. Estoy aquí para escucharte. ¿Podrías contarme un poco más?");
        }

        @Test
        @DisplayName("FOLLOW_UP returns specific fallback message")
        void followUp_returnsFallback() {
            String result = provider.getForType("FOLLOW_UP");

            assertThat(result).isNotBlank();
            assertThat(result).contains("verte de vuelta");
        }

        @Test
        @DisplayName("REFLECTION returns specific fallback message")
        void reflection_returnsFallback() {
            String result = provider.getForType("REFLECTION");

            assertThat(result).isNotBlank();
            assertThat(result).contains("reflexionar");
        }

        @Test
        @DisplayName("INACTIVITY_CHECK returns specific fallback message")
        void inactivityCheck_returnsFallback() {
            String result = provider.getForType("INACTIVITY_CHECK");

            assertThat(result).isNotBlank();
            assertThat(result).contains("cómo estás");
        }

        @Test
        @DisplayName("CELEBRATION returns specific fallback message")
        void celebration_returnsFallback() {
            String result = provider.getForType("CELEBRATION");

            assertThat(result).isNotBlank();
            assertThat(result).contains("celebrar");
        }

        @Test
        @DisplayName("unknown type returns generic fallback")
        void unknownType_returnsGeneric() {
            String result = provider.getForType("UNKNOWN_TYPE");

            assertThat(result).isEqualTo(
                    "Disculpa, estoy experimentando dificultades técnicas. Volveré contigo pronto.");
        }

        @Test
        @DisplayName("null type returns generic fallback")
        void nullType_returnsGeneric() {
            String result = provider.getForType(null);

            assertThat(result).isEqualTo(provider.getGenericFallback());
        }

        @Test
        @DisplayName("blank type returns generic fallback")
        void blankType_returnsGeneric() {
            String result = provider.getForType("  ");

            assertThat(result).isEqualTo(provider.getGenericFallback());
        }

        @Test
        @DisplayName("type matching is case-insensitive")
        void caseInsensitiveMatching() {
            String upper = provider.getForType("DAILY_COACHING");
            String lower = provider.getForType("daily_coaching");
            String mixed = provider.getForType("Daily_Coaching");

            assertThat(upper).isEqualTo(lower);
            assertThat(lower).isEqualTo(mixed);
        }
    }

    @Nested
    @DisplayName("8.5 — Fallback messages are static text (never AI-generated)")
    class StaticMessages {

        @Test
        @DisplayName("same type always returns identical message")
        void sameType_identicalMessage() {
            String first = provider.getForType("DAILY_COACHING");
            String second = provider.getForType("DAILY_COACHING");
            String third = provider.getForType("DAILY_COACHING");

            assertThat(first).isEqualTo(second);
            assertThat(second).isEqualTo(third);
        }

        @Test
        @DisplayName("generic fallback is always the same")
        void genericFallback_alwaysTheSame() {
            String first = provider.getGenericFallback();
            String second = provider.getGenericFallback();

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("all fallback messages are non-empty strings")
        void allMessages_nonEmpty() {
            String[] types = {
                "DAILY_COACHING", "ONBOARDING", "DIFFICULT_SITUATION",
                "FOLLOW_UP", "REFLECTION", "INACTIVITY_CHECK", "CELEBRATION"
            };

            for (String type : types) {
                assertThat(provider.getForType(type))
                        .as("Fallback for type: " + type)
                        .isNotNull()
                        .isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("8.6 — Fallback messages in conversational Latin American Spanish")
    class SpanishMessages {

        @Test
        @DisplayName("all messages contain Spanish characters/words")
        void allMessages_inSpanish() {
            String[] types = {
                "DAILY_COACHING", "ONBOARDING", "DIFFICULT_SITUATION",
                "FOLLOW_UP", "REFLECTION", "INACTIVITY_CHECK", "CELEBRATION"
            };

            for (String type : types) {
                String msg = provider.getForType(type);
                // Check for common Spanish patterns (accent marks, Spanish words)
                assertThat(msg)
                        .as("Fallback for type '%s' should be in Spanish", type)
                        .containsAnyOf("á", "é", "í", "ó", "ú", "ñ", "¡", "¿");
            }
        }

        @Test
        @DisplayName("generic fallback is in Spanish")
        void genericFallback_inSpanish() {
            String msg = provider.getGenericFallback();

            assertThat(msg).contains("Disculpa");
            assertThat(msg).containsAnyOf("á", "é", "í", "ó", "ú");
        }
    }

    @Nested
    @DisplayName("8.7 — Max 3 consecutive fallback responses before alerting operations")
    class ConsecutiveFallbackTracking {

        @Test
        @DisplayName("initial count is zero")
        void initialCount_isZero() {
            UUID fatherId = UUID.randomUUID();

            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isZero();
        }

        @Test
        @DisplayName("count increments with each fallback usage")
        void countIncrements() {
            UUID fatherId = UUID.randomUUID();

            provider.recordFallbackUsage(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isEqualTo(1);

            provider.recordFallbackUsage(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isEqualTo(2);
        }

        @Test
        @DisplayName("counter resets after reaching 3 consecutive fallbacks")
        void resetsAfterThree() {
            UUID fatherId = UUID.randomUUID();

            provider.recordFallbackUsage(fatherId);
            provider.recordFallbackUsage(fatherId);
            provider.recordFallbackUsage(fatherId); // should trigger alert and reset

            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isZero();
        }

        @Test
        @DisplayName("resetFallbackCount clears counter for father")
        void resetClears() {
            UUID fatherId = UUID.randomUUID();

            provider.recordFallbackUsage(fatherId);
            provider.recordFallbackUsage(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isEqualTo(2);

            provider.resetFallbackCount(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isZero();
        }

        @Test
        @DisplayName("different fathers tracked independently")
        void differentFathers_independent() {
            UUID father1 = UUID.randomUUID();
            UUID father2 = UUID.randomUUID();

            provider.recordFallbackUsage(father1);
            provider.recordFallbackUsage(father1);
            provider.recordFallbackUsage(father2);

            assertThat(provider.getConsecutiveFallbackCount(father1)).isEqualTo(2);
            assertThat(provider.getConsecutiveFallbackCount(father2)).isEqualTo(1);
        }

        @Test
        @DisplayName("counter continues after reset from 3")
        void counterContinuesAfterReset() {
            UUID fatherId = UUID.randomUUID();

            // First batch of 3 — triggers alert, resets to 0
            provider.recordFallbackUsage(fatherId);
            provider.recordFallbackUsage(fatherId);
            provider.recordFallbackUsage(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isZero();

            // Next batch starts fresh
            provider.recordFallbackUsage(fatherId);
            assertThat(provider.getConsecutiveFallbackCount(fatherId)).isEqualTo(1);
        }
    }
}
