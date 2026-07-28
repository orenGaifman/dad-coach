package com.dadcoach.conversation.ai;

import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.conversation.context.ConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates AI-generated coaching responses against schema, language,
 * length, and safety rules before delivery to fathers.
 *
 * <p>Stateless service — all validation is based on the response content
 * and conversation context provided.
 */
@Service
public class ResponseValidatorImpl implements ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidatorImpl.class);

    private static final int MIN_WORD_COUNT = 10;
    private static final int MAX_WORD_COUNT = 500;

    // Common Spanish words used as a heuristic language check
    private static final Set<String> SPANISH_INDICATORS = Set.of(
            "de", "la", "el", "en", "que", "los", "las", "del", "por", "con",
            "una", "para", "es", "se", "un", "su", "al", "no", "lo", "como",
            "más", "pero", "sus", "le", "ya", "o", "fue", "este", "ha", "si",
            "porque", "esta", "entre", "cuando", "muy", "sin", "sobre", "ser",
            "también", "me", "hasta", "hay", "donde", "quien", "desde", "todo",
            "nos", "uno", "ni", "son", "te", "tu", "hijo", "hola", "puedo"
    );

    private static final int SPANISH_WORD_THRESHOLD = 3;

    // Shame language patterns (case-insensitive)
    private static final List<Pattern> SHAME_PATTERNS = List.of(
            Pattern.compile("deberías avergonzarte", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("eres mal padre", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("qué vergüenza", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("mal padre", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("deberías sentir vergüenza", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("eres un fracaso", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    );

    // Diagnostic language patterns (case-insensitive)
    private static final List<Pattern> DIAGNOSTIC_PATTERNS = List.of(
            Pattern.compile("tu hijo tiene", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("padece de", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("síntomas de", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("diagnóstico", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("trastorno de", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("enfermedad mental", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    );

    // PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\+?\\d[\\d\\s\\-()]{7,}\\d"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-]+(\\.[\\w\\-]+)+[/\\w\\-.?=%&#]*"
    );

    @Override
    public ValidationResult validate(CoachingResponse response, ConversationContext context) {
        List<String> failures = new ArrayList<>();

        validateSchema(response, failures);
        if (!failures.isEmpty()) {
            // If schema fails, no point checking further
            return ValidationResult.fail(failures);
        }

        String message = response.message();
        validateLanguage(message, failures);
        validateLength(message, failures);
        validateForbiddenContent(message, failures);

        if (failures.isEmpty()) {
            return ValidationResult.pass();
        }

        log.warn("Response validation failed for conversation {}: {}",
                context.conversationId(), failures);
        return ValidationResult.fail(failures);
    }

    private void validateSchema(CoachingResponse response, List<String> failures) {
        if (response == null) {
            failures.add("Response is null");
            return;
        }
        if (response.message() == null || response.message().isBlank()) {
            failures.add("Response message is null or empty");
        }
    }

    private void validateLanguage(String message, List<String> failures) {
        String[] words = message.toLowerCase().split("\\s+");
        long spanishWordCount = 0;
        for (String word : words) {
            // Strip punctuation for matching
            String clean = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (SPANISH_INDICATORS.contains(clean)) {
                spanishWordCount++;
            }
        }

        if (spanishWordCount < SPANISH_WORD_THRESHOLD) {
            failures.add("Response does not appear to be in Spanish (found " +
                    spanishWordCount + " Spanish indicator words, minimum is " +
                    SPANISH_WORD_THRESHOLD + ")");
        }
    }

    private void validateLength(String message, List<String> failures) {
        String[] words = message.trim().split("\\s+");
        int wordCount = words.length;

        if (wordCount < MIN_WORD_COUNT) {
            failures.add("Response too short: " + wordCount + " words (minimum " + MIN_WORD_COUNT + ")");
        }
        if (wordCount > MAX_WORD_COUNT) {
            failures.add("Response too long: " + wordCount + " words (maximum " + MAX_WORD_COUNT + ")");
        }
    }

    private void validateForbiddenContent(String message, List<String> failures) {
        checkShameLanguage(message, failures);
        checkDiagnosticLanguage(message, failures);
        checkPiiPatterns(message, failures);
    }

    private void checkShameLanguage(String message, List<String> failures) {
        for (Pattern pattern : SHAME_PATTERNS) {
            if (pattern.matcher(message).find()) {
                failures.add("Contains shame language: matched pattern '" + pattern.pattern() + "'");
                return; // Report only once for shame category
            }
        }
    }

    private void checkDiagnosticLanguage(String message, List<String> failures) {
        for (Pattern pattern : DIAGNOSTIC_PATTERNS) {
            if (pattern.matcher(message).find()) {
                failures.add("Contains diagnostic language: matched pattern '" + pattern.pattern() + "'");
                return; // Report only once for diagnostic category
            }
        }
    }

    private void checkPiiPatterns(String message, List<String> failures) {
        if (EMAIL_PATTERN.matcher(message).find()) {
            failures.add("Contains email address (PII detected)");
        }
        if (PHONE_PATTERN.matcher(message).find()) {
            failures.add("Contains phone number (PII detected)");
        }
        if (URL_PATTERN.matcher(message).find()) {
            failures.add("Contains URL (PII detected)");
        }
    }
}
