package com.dadcoach.conversation.ai;

import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.conversation.context.ConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // Common English words used as a heuristic language check
    private static final Set<String> ENGLISH_INDICATORS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
            "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
            "into", "through", "during", "before", "after", "above", "below",
            "and", "but", "or", "nor", "so", "yet", "both", "either", "neither",
            "not", "only", "own", "same", "than", "too", "very", "just", "also",
            "i", "you", "he", "she", "it", "we", "they", "what", "which", "who",
            "this", "that", "these", "those", "am", "your", "my", "his", "her"
    );

    // Hebrew is detected by presence of Hebrew characters (Unicode block)
    private static final Pattern HEBREW_PATTERN = Pattern.compile("[\\u0590-\\u05FF]");

    private static final int LANGUAGE_WORD_THRESHOLD = 3;

    // Shame language patterns (case-insensitive) - English and Hebrew
    private static final List<Pattern> SHAME_PATTERNS = List.of(
            // English shame patterns
            Pattern.compile("you should be ashamed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you('re| are) a bad (father|dad|parent)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("what a shame", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you('re| are) a failure", Pattern.CASE_INSENSITIVE),
            Pattern.compile("other (dads|fathers) already", Pattern.CASE_INSENSITIVE),
            // Hebrew shame patterns
            Pattern.compile("אתה צריך להתבייש", Pattern.UNICODE_CASE),
            Pattern.compile("אתה אבא רע", Pattern.UNICODE_CASE),
            Pattern.compile("איזה בושה", Pattern.UNICODE_CASE),
            Pattern.compile("אתה כישלון", Pattern.UNICODE_CASE),
            Pattern.compile("אבות אחרים כבר", Pattern.UNICODE_CASE)
    );

    // Diagnostic language patterns (case-insensitive) - English and Hebrew
    private static final List<Pattern> DIAGNOSTIC_PATTERNS = List.of(
            // English diagnostic patterns
            Pattern.compile("your (child|son|daughter) has", Pattern.CASE_INSENSITIVE),
            Pattern.compile("suffers from", Pattern.CASE_INSENSITIVE),
            Pattern.compile("symptoms of", Pattern.CASE_INSENSITIVE),
            Pattern.compile("diagnosis", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disorder", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mental illness", Pattern.CASE_INSENSITIVE),
            // Hebrew diagnostic patterns
            Pattern.compile("לילד שלך יש", Pattern.UNICODE_CASE),
            Pattern.compile("סובל מ", Pattern.UNICODE_CASE),
            Pattern.compile("תסמינים של", Pattern.UNICODE_CASE),
            Pattern.compile("אבחון", Pattern.UNICODE_CASE),
            Pattern.compile("הפרעת", Pattern.UNICODE_CASE),
            Pattern.compile("מחלה נפשית", Pattern.UNICODE_CASE)
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
        validateLanguage(message, context, failures);
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

    private void validateLanguage(String message, ConversationContext context, List<String> failures) {
        // Get the father's language preference from fatherProfile
        String locale = "en"; // default to English
        if (context.fatherProfile() != null && context.fatherProfile().get("locale") != null) {
            locale = context.fatherProfile().get("locale").toString();
        }

        if ("he".equals(locale)) {
            // Hebrew validation: check for Hebrew characters
            if (!HEBREW_PATTERN.matcher(message).find()) {
                failures.add("Response does not appear to be in Hebrew (no Hebrew characters found)");
            }
        } else {
            // English validation: check for common English words
            String[] words = message.toLowerCase().split("\\s+");
            long englishWordCount = 0;
            for (String word : words) {
                String clean = word.replaceAll("[^\\p{L}\\p{N}]", "");
                if (ENGLISH_INDICATORS.contains(clean)) {
                    englishWordCount++;
                }
            }
            if (englishWordCount < LANGUAGE_WORD_THRESHOLD) {
                failures.add("Response does not appear to be in English (found " +
                        englishWordCount + " English indicator words, minimum is " +
                        LANGUAGE_WORD_THRESHOLD + ")");
            }
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
