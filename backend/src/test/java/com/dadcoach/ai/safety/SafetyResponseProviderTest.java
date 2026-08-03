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
 * Tests English (default) and Hebrew responses as specified by SPEC-003.
 */
class SafetyResponseProviderTest {

    private SafetyResponseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SafetyResponseProvider();
    }

    @Nested
    @DisplayName("Crisis Response - English")
    class CrisisResponseEnglish {

        @Test
        @DisplayName("returns response with 988 hotline number")
        void containsHotlineNumber() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("988");
        }

        @Test
        @DisplayName("returns response with Crisis Lifeline")
        void containsCrisisLifeline() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("Crisis Lifeline");
        }

        @Test
        @DisplayName("returns empathetic acknowledgment in English")
        void isInEnglish() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("not alone");
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
    @DisplayName("Crisis Response - Hebrew")
    class CrisisResponseHebrew {

        @Test
        @DisplayName("returns response with Israeli crisis hotline")
        void containsIsraeliHotline() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification, "he");

            assertThat(response).containsAnyOf("2784", "1201");
        }

        @Test
        @DisplayName("returns Hebrew acknowledgment")
        void isInHebrew() {
            var classification = new SafetyClassification(SafetyCategory.CRISIS, 0.95, "test");
            String response = provider.getResponse(classification, "he");

            assertThat(response).contains("לא לבד");
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
        @DisplayName("response acknowledges courage")
        void acknowledgesCourage() {
            var classification = new SafetyClassification(SafetyCategory.CHILD_SAFETY, 0.90, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("courage");
        }
    }

    @Nested
    @DisplayName("Manipulation Response")
    class ManipulationResponse {

        @Test
        @DisplayName("returns redirect to coaching in English")
        void redirectsToCoaching() {
            var classification = new SafetyClassification(SafetyCategory.MANIPULATION, 0.95, "test");
            String response = provider.getResponse(classification);

            assertThat(response).isEqualTo(
                "I'm your parenting coach. How can I help you with your kids today?");
        }

        @Test
        @DisplayName("returns redirect to coaching in Hebrew")
        void redirectsToCoachingHebrew() {
            var classification = new SafetyClassification(SafetyCategory.MANIPULATION, 0.95, "test");
            String response = provider.getResponse(classification, "he");

            assertThat(response).contains("מאמן");
            assertThat(response).contains("ילדים");
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

            assertThat(response).contains("not a healthcare professional");
        }

        @Test
        @DisplayName("recommends pediatrician")
        void recommendsPediatrician() {
            var classification = new SafetyClassification(SafetyCategory.MEDICAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("pediatrician");
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

            assertThat(response).containsIgnoringCase("can't give legal advice");
        }

        @Test
        @DisplayName("recommends attorney")
        void recommendsAttorney() {
            var classification = new SafetyClassification(SafetyCategory.LEGAL, 0.85, "test");
            String response = provider.getResponse(classification);

            assertThat(response).contains("attorney");
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
