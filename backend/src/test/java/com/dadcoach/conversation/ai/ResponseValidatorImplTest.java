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
    private ConversationContext context;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new ResponseValidatorImpl();
        context = new ConversationContext(
                FATHER_ID, CONVERSATION_ID, "DAILY_COACHING",
                Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
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
        @DisplayName("valid Spanish response passes validation")
        void validResponse_passes() {
            CoachingResponse resp = response(
                    "Hola, entiendo que estás preocupado por tu hijo. Es completamente " +
                    "normal sentirse así como padre. Te sugiero que intentes hablar con " +
                    "él de manera calmada y escuchar lo que tiene que decir.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isTrue();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("response that is too short fails length validation")
        void tooShort_fails() {
            // Less than 10 words but valid Spanish — will fail length
            CoachingResponse resp = response(
                    "Hola, es muy bueno que estés aquí para conversar.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("too short"));
        }

        @Test
        @DisplayName("response that is too long fails length validation")
        void tooLong_fails() {
            // Build a response > 500 words
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 510; i++) {
                sb.append("palabra ");
            }
            // Add Spanish indicators so it passes language check
            String longMsg = "Hola, esta es una respuesta muy larga para tu pregunta. " + sb;
            CoachingResponse resp = response(longMsg);

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("too long"));
        }

        @Test
        @DisplayName("response not in Spanish fails language validation")
        void notSpanish_fails() {
            CoachingResponse resp = response(
                    "Hello there, I understand you are having difficulties with your child. " +
                    "Let me help you think about some strategies that might work well for your family.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("Spanish"));
        }
    }

    @Nested
    @DisplayName("8.2 — Check for forbidden content (shame, diagnoses, PII)")
    class ForbiddenContent {

        @Test
        @DisplayName("shame language detected: deberías avergonzarte")
        void shameLanguage_detected() {
            CoachingResponse resp = response(
                    "Mira, la verdad es que deberías avergonzarte de cómo trataste a tu hijo " +
                    "ayer. No es la forma correcta de actuar como padre responsable en esta situación.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("shame language"));
        }

        @Test
        @DisplayName("shame language detected: eres mal padre")
        void shameLanguageMalPadre_detected() {
            CoachingResponse resp = response(
                    "Vamos a ser honestos, eres mal padre si no puedes dedicar tiempo " +
                    "a tu hijo. Necesitas cambiar tu comportamiento ahora mismo para mejorar.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("shame language"));
        }

        @Test
        @DisplayName("diagnostic language detected: tu hijo tiene")
        void diagnosticLanguage_detected() {
            CoachingResponse resp = response(
                    "Por lo que me cuentas, tu hijo tiene un trastorno de atención y necesita " +
                    "tratamiento profesional de manera urgente. Te recomiendo consultar un psicólogo.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("diagnostic language"));
        }

        @Test
        @DisplayName("diagnostic language detected: padece de")
        void diagnosticPadeceDe_detected() {
            CoachingResponse resp = response(
                    "Creo que tu hijo padece de ansiedad según los síntomas que me describes. " +
                    "Esto es algo que necesita atención profesional de un terapeuta especializado.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("diagnostic language"));
        }

        @Test
        @DisplayName("email address PII detected")
        void emailPii_detected() {
            CoachingResponse resp = response(
                    "Hola, te recomiendo que le escribas a este especialista para tu hijo " +
                    "en la dirección doctor@hospital.com y le cuentes sobre la situación familiar.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("email"));
        }

        @Test
        @DisplayName("phone number PII detected")
        void phonePii_detected() {
            CoachingResponse resp = response(
                    "Hola, para más ayuda con tu situación familiar te recomiendo llamar " +
                    "al número +54 911 5555 1234 para hablar con un profesional que te pueda ayudar.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("phone"));
        }

        @Test
        @DisplayName("URL PII detected")
        void urlPii_detected() {
            CoachingResponse resp = response(
                    "Hola, te recomiendo visitar https://www.terapia.com/registrarse para encontrar " +
                    "un profesional que te pueda ayudar con la situación de tu hijo en casa.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).anyMatch(f -> f.contains("URL"));
        }

        @Test
        @DisplayName("clean response without forbidden content passes")
        void cleanResponse_passes() {
            CoachingResponse resp = response(
                    "Entiendo que la situación con tu hijo puede ser desafiante. Te sugiero " +
                    "que busques un momento tranquilo para conversar con él sobre cómo se siente. " +
                    "La paciencia y la escucha activa son herramientas muy poderosas como padre.");

            ValidationResult result = validator.validate(resp, context);

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
                    "Hola, entiendo tu preocupación como padre. Es normal sentirse así " +
                    "cuando nuestros hijos atraviesan situaciones difíciles en la escuela.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isTrue();
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("failing result lists all detected issues")
        void failingResult_listsAllIssues() {
            // Response with multiple issues: shame + PII email
            CoachingResponse resp = response(
                    "Eres mal padre por no prestar atención. Escríbeme a coach@help.com " +
                    "para más consejos sobre cómo mejorar tu relación con tu hijo ahora.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            assertThat(result.failures()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("failure descriptions are human-readable")
        void failureDescriptions_readable() {
            CoachingResponse resp = response(
                    "Hello, I don't speak Spanish at all and this is entirely in English.");

            ValidationResult result = validator.validate(resp, context);

            assertThat(result.passed()).isFalse();
            for (String failure : result.failures()) {
                assertThat(failure).isNotBlank();
                assertThat(failure.length()).isGreaterThan(10);
            }
        }
    }
}
