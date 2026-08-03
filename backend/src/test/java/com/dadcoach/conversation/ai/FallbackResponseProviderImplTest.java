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
        @DisplayName("DAILY_COACHING returns specific fallback message in English")
        void dailyCoaching_returnsFallback() {
            String result = provider.getForType("DAILY_COACHING");

            assertThat(result).isEqualTo(
                    "Hi there! I'm here to support you on your fatherhood journey. How can I help you today?");
        }

        @Test
        @DisplayName("DAILY_COACHING returns specific fallback message in Hebrew")
        void dailyCoaching_returnsFallbackHebrew() {
            String result = provider.getForType("DAILY_COACHING", "he");

            assertThat(result).isEqualTo(
                    "שלום! אני כאן ללוות אותך במסע האבהות שלך. איך אוכל לעזור לך היום?");
        }

        @Test
        @DisplayName("ONBOARDING returns specific fallback message")
        void onboarding_returnsFallback() {
            String result = provider.getForType("ONBOARDING");

            assertThat(result).isEqualTo(
                    "Welcome! I'm having a small technical issue, but I'll be ready to get to know you better in just a moment.");
        }

        @Test
        @DisplayName("DIFFICULT_SITUATION returns specific fallback message")
        void difficultSituation_returnsFallback() {
            String result = provider.getForType("DIFFICULT_SITUATION");

            assertThat(result).isEqualTo(
                    "I understand you're going through a difficult time. I'm here to listen. Could you tell me a bit more about what's happening?");
        }

        @Test
        @DisplayName("FOLLOW_UP returns specific fallback message")
        void followUp_returnsFallback() {
            String result = provider.getForType("FOLLOW_UP");

            assertThat(result).isNotBlank();
            assertThat(result).contains("see you again");
        }

        @Test
        @DisplayName("REFLECTION returns specific fallback message")
        void reflection_returnsFallback() {
            String result = provider.getForType("REFLECTION");

            assertThat(result).isNotBlank();
            assertThat(result).contains("reflect");
        }

        @Test
        @DisplayName("INACTIVITY_CHECK returns specific fallback message")
        void inactivityCheck_returnsFallback() {
            String result = provider.getForType("INACTIVITY_CHECK");

            assertThat(result).isNotBlank();
            assertThat(result).contains("how you're doing");
        }

        @Test
        @DisplayName("CELEBRATION returns specific fallback message")
        void celebration_returnsFallback() {
            String result = provider.getForType("CELEBRATION");

            assertThat(result).isNotBlank();
            assertThat(result).contains("celebrate");
        }

        @Test
        @DisplayName("unknown type returns generic fallback")
        void unknownType_returnsGeneric() {
            String result = provider.getForType("UNKNOWN_TYPE");

            assertThat(result).isEqualTo(
                    "Sorry, I'm experiencing technical difficulties. I'll get back to you soon.");
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
    @DisplayName("8.6 — Fallback messages support English and Hebrew")
    class LocalizedMessages {

        @Test
        @DisplayName("English messages contain common English words")
        void englishMessages_containEnglishWords() {
            String[] types = {
                "DAILY_COACHING", "ONBOARDING", "DIFFICULT_SITUATION",
                "FOLLOW_UP", "REFLECTION", "INACTIVITY_CHECK", "CELEBRATION"
            };

            for (String type : types) {
                String msg = provider.getForType(type, "en");
                assertThat(msg)
                        .as("Fallback for type '%s' should be in English", type)
                        .containsAnyOf("I'm", "you", "help", "your", "here", "with");
            }
        }

        @Test
        @DisplayName("Hebrew messages contain Hebrew characters")
        void hebrewMessages_containHebrewChars() {
            String[] types = {
                "DAILY_COACHING", "ONBOARDING", "DIFFICULT_SITUATION",
                "FOLLOW_UP", "REFLECTION", "INACTIVITY_CHECK", "CELEBRATION"
            };

            for (String type : types) {
                String msg = provider.getForType(type, "he");
                assertThat(msg)
                        .as("Fallback for type '%s' should be in Hebrew", type)
                        .matches(".*[\\u0590-\\u05FF].*");
            }
        }

        @Test
        @DisplayName("generic fallback in English is correct")
        void genericFallbackEnglish() {
            String msg = provider.getGenericFallback("en");

            assertThat(msg).contains("Sorry");
            assertThat(msg).contains("technical difficulties");
        }

        @Test
        @DisplayName("generic fallback in Hebrew is correct")
        void genericFallbackHebrew() {
            String msg = provider.getGenericFallback("he");

            assertThat(msg).contains("סליחה");
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
