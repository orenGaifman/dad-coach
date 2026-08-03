package com.dadcoach.conversation.ai;

import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.conversation.context.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseValidatorImpl Unit Tests")
class ResponseValidatorImplTest {

    private ResponseValidatorImpl validator;
    private ConversationContext contextEn;
    private ConversationContext contextHe;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new ResponseValidatorImpl();
        // English locale context (default)
        contextEn = new ConversationContext(
                FATHER_ID, CONVERSATION_ID, "DAILY_COACHING",
                Map.of("locale", "en"), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
        );
        // Hebrew locale context
        contextHe = new ConversationContext(
                FATHER_ID, CONVERSATION_ID, "DAILY_COACHING",
                Map.of("locale", "he"), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
        );
    }

    private CoachingResponse response(String message) {
        return new CoachingResponse(
                message, "gpt-4", "openai",
                100, 50, Duration.ofMillis(500),
                false, true, 0.9
        );
    }

    @Nested
    @DisplayName("8.1 — Validate AI response structure and content rules")
    class SchemaValidation {

        @Test
        @DisplayName("valid English response passes validation")
        void validEnglishResponse_passes() {
            CoachingResponse resp = response(
                    "Hello, I understand you are worried about your son. It is completely " +
                    "normal to feel this way as a father. I suggest you try talking with " +
                    "him calmly and listening to what he has to say about his day.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isTrue();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("valid Hebrew response passes validation")
        void validHebrewResponse_passes() {
            CoachingResponse resp = response(
                    "שלום, אני מבין שאתה מודאג לגבי הבן שלך. זה לגמרי " +
                    "נורמלי להרגיש ככה בתור אבא. אני מציע שתנסה לדבר איתו " +
                    "בצורה רגועה ולהקשיב למה שיש לו להגיד על היום שלו.");

            ValidationResult result = validator.validate(resp, contextHe);

            assertThat(result.passed()).isTrue();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("response that is too short fails length validation")
        void tooShort_fails() {
            // Less than 10 words — will fail length
            CoachingResponse resp = response(
                    "Hello, it's great that you are here to talk.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("too short"));
        }

        @Test
        @DisplayName("response that is too long fails length validation")
        void tooLong_fails() {
            // Build a response > 500 words
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 510; i++) {
                sb.append("word ");
            }
            // Add English content
            String longMsg = "Hello, this is a very long response to your question. " + sb;
            CoachingResponse resp = response(longMsg);

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("too long"));
        }

        @Test
        @DisplayName("response not in expected language fails validation")
        void wrongLanguage_fails() {
            // French response when English expected
            CoachingResponse resp = response(
                    "Bonjour monsieur, je comprends que vous avez des difficultés avec votre enfant. " +
                    "Permettez-moi de vous aider à réfléchir à des stratégies qui pourraient bien fonctionner.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("English"));
        }

        @Test
        @DisplayName("English response fails when Hebrew locale expected")
        void englishWhenHebrewExpected_fails() {
            CoachingResponse resp = response(
                    "Hello there, I understand you are having difficulties with your child. " +
                    "Let me help you think about some strategies that might work well for your family.");

            ValidationResult result = validator.validate(resp, contextHe);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("Hebrew"));
        }
    }

    @Nested
    @DisplayName("8.2 — Check for forbidden content (shame, diagnoses, PII)")
    class ForbiddenContent {

        @Test
        @DisplayName("shame language detected in English: you should be ashamed")
        void shameLanguageEnglish_detected() {
            CoachingResponse resp = response(
                    "Look, the truth is you should be ashamed of how you treated your son " +
                    "yesterday. That is not the right way to act as a responsible father in this situation.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("shame language"));
        }

        @Test
        @DisplayName("shame language detected in English: you are a bad father")
        void shameLanguageBadFather_detected() {
            CoachingResponse resp = response(
                    "Let's be honest, you are a bad father if you cannot dedicate time " +
                    "to your son. You need to change your behavior right now to improve things.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("shame language"));
        }

        @Test
        @DisplayName("shame language detected in Hebrew")
        void shameLanguageHebrew_detected() {
            CoachingResponse resp = response(
                    "אתה צריך להתבייש על הדרך שבה התנהגת עם הבן שלך אתמול. " +
                    "זו לא הדרך הנכונה להתנהג כאבא אחראי במצב הזה ואתה יודע את זה.");

            ValidationResult result = validator.validate(resp, contextHe);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("shame language"));
        }

        @Test
        @DisplayName("diagnostic language detected in English: your child has")
        void diagnosticLanguageEnglish_detected() {
            CoachingResponse resp = response(
                    "From what you're telling me, your child has an attention disorder and needs " +
                    "professional treatment urgently. I recommend you consult with a psychologist about this.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("diagnostic language"));
        }

        @Test
        @DisplayName("diagnostic language detected in English: suffers from")
        void diagnosticSuffersFrom_detected() {
            CoachingResponse resp = response(
                    "I think your child suffers from anxiety based on the symptoms you describe. " +
                    "This is something that needs professional attention from a specialized therapist soon.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("diagnostic language"));
        }

        @Test
        @DisplayName("email address PII detected")
        void emailPii_detected() {
            CoachingResponse resp = response(
                    "Hello, I recommend that you write to this specialist for your child " +
                    "at the address doctor@hospital.com and tell them about the family situation.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("email"));
        }

        @Test
        @DisplayName("phone number PII detected")
        void phonePii_detected() {
            CoachingResponse resp = response(
                    "Hello, for more help with your family situation I recommend calling " +
                    "the number +1 555 123 4567 to speak with a professional who can help you.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("phone"));
        }

        @Test
        @DisplayName("URL PII detected")
        void urlPii_detected() {
            CoachingResponse resp = response(
                    "Hello, I recommend visiting https://www.therapy.com/register to find " +
                    "a professional who can help you with your child's situation at home.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("URL"));
        }

        @Test
        @DisplayName("clean response without forbidden content passes")
        void cleanResponse_passes() {
            CoachingResponse resp = response(
                    "I understand that the situation with your son can be challenging. I suggest " +
                    "that you look for a calm moment to talk with him about how he feels. " +
                    "Patience and active listening are very powerful tools as a father.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("8.3 — Return ValidationResult with pass/fail + failure details")
    class ValidationResultDetails {

        @Test
        @DisplayName("passing result has empty failure list")
        void passingResult_emptyFailures() {
            CoachingResponse resp = response(
                    "Hello, I understand your concern as a father. It is normal to feel this way " +
                    "when our children go through difficult situations at school and with friends.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isTrue();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("failing result lists all detected issues")
        void failingResult_listsAllIssues() {
            // Response with multiple issues: shame + PII email
            CoachingResponse resp = response(
                    "You are a bad father for not paying attention. Write to me at coach@help.com " +
                    "for more advice on how to improve your relationship with your son now.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("failure descriptions are human-readable")
        void failureDescriptions_readable() {
            // French text when English expected
            CoachingResponse resp = response(
                    "Bonjour, je ne parle pas anglais du tout et ceci est entièrement en français.");

            ValidationResult result = validator.validate(resp, contextEn);

            assertThat(result.passed()).isFalse();
            for (String failure : result.failures()) {
                assertThat(failure).isNotBlank();
                assertThat(failure.length()).isGreaterThan(10);
            }
        }
    }
}
