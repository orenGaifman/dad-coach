package com.dadcoach.ai.safety;

import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SafetyResponseProvider — pre-written safety responses and human review flagging.
 */
class SafetyResponseProviderTest {

    private SafetyResponseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SafetyResponseProvider();
    }

    @Nested
    @DisplayName("Crisis Response")
    class CrisisResponse {

        @Test
        @DisplayName("returns response with 988 hotline number")
        void containsHotlineNumber() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("988");
        }

        @Test
        @DisplayName("returns response with Línea de la Vida")
        void containsLineaDeLaVida() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("Línea de la Vida");
        }

        @Test
        @DisplayName("returns empathetic acknowledgment in Spanish")
        void isInSpanish() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("No estás solo");
        }

        @Test
        @DisplayName("response is non-empty and substantial")
        void isSubstantial() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response.length()).isGreaterThan(100);
        }
    }

    @Nested
    @DisplayName("Child Safety Response")
    class ChildSafetyResponse {

        @Test
        @DisplayName("returns child protection hotline")
        void containsChildHotline() {
            var classification = new SafetyClassification(SafetyCategory.CHILD_SAFETY, 0.90, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("1-800-422-4453");
        }

        @Test
        @DisplayName("response is non-judgmental")
        void isNonJudgmental() {
            var classification = new SafetyClassification(SafetyCategory.CHILD_SAFETY, 0.90, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("valentía");
        }
    }

    @Nested
    @DisplayName("Manipulation Response")
    class ManipulationResponse {

        @Test
        @DisplayName("returns redirect to coaching")
        void redirectsToCoaching() {
            var classification = new SafetyClassification(SafetyCategory.MANIPULATION, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).isEqualTo(
                "Soy tu coach de paternidad. ¿En qué te puedo ayudar con tus hijos hoy?");
        }
    }

    @Nested
    @DisplayName("Medical Response")
    class MedicalResponse {

        @Test
        @DisplayName("states not a health professional")
        void statesNotProfessional() {
            var classification = new SafetyClassification(SafetyCategory.MEDICAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("No soy profesional de salud");
        }

        @Test
        @DisplayName("recommends pediatrician")
        void recommendsPediatrician() {
            var classification = new SafetyClassification(SafetyCategory.MEDICAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("pediatra");
        }
    }

    @Nested
    @DisplayName("Legal Response")
    class LegalResponse {

        @Test
        @DisplayName("states cannot give legal advice")
        void statesNoLegalAdvice() {
            var classification = new SafetyClassification(SafetyCategory.LEGAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("No puedo dar consejos legales");
        }

        @Test
        @DisplayName("recommends family law attorney")
        void recommendsAttorney() {
            var classification = new SafetyClassification(SafetyCategory.LEGAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("abogado");
        }
    }

    @Nested
    @DisplayName("SAFE classification throws exception")
    class SafeClassification {

        @Test
        @DisplayName("throws when SAFE classification is passed")
        void throwsForSafe() {
            var classification = SafetyClassification.safe();

            assertThatThrownBy(() -> provider.getResponse(classification))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAFE");
        }
    }

    @Nested
    @DisplayName("Human Review Requirements")
    class HumanReview {

        @Test
        @DisplayName("CRISIS requires human review")
        void crisisRequiresReview() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            assertThat(provider.requiresHumanReview(classification)).isTrue();
        }

        @Test
        @DisplayName("CHILD_SAFETY requires human review")
        void childSafetyRequiresReview() {
            var classification = new SafetyClassification(SafetyCategory.CHILD_SAFETY, 0.90, "test");
            assertThat(provider.requiresHumanReview(classification)).isTrue();
        }

        @Test
        @DisplayName("MANIPULATION does not require human review")
        void manipulationNoReview() {
            var classification = new SafetyClassification(SafetyCategory.MANIPULATION, 0.95, "test");
            assertThat(provider.requiresHumanReview(classification)).isFalse();
        }

        @Test
        @DisplayName("SAFE does not require human review")
        void safeNoReview() {
            var classification = SafetyClassification.safe();
            assertThat(provider.requiresHumanReview(classification)).isFalse();
        }

        @Test
        @DisplayName("CHILD_SAFETY has 2h SLA")
        void childSafety2hSla() {
            assertThat(provider.getReviewSla(SafetyCategory.CHILD_SAFETY)).isEqualTo("2h");
        }

        @Test
        @DisplayName("CRISIS has 4h SLA")
        void crisis4hSla() {
            assertThat(provider.getReviewSla(SafetyCategory.CRISIS)).isEqualTo("4h");
        }

        @Test
        @DisplayName("logForHumanReview returns event ID")
        void logReturnsEventId() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String eventId = provider.logForHumanReview(classification, "test message", UUID.randomUUID());
            assertThat(eventId).isNotNull().isNotBlank();
        }
    }
}
