package com.dadcoach.memory.extraction;

import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemorySubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates AI-generated memory recommendations before persistence.
 *
 * <p>From SPEC-004 Requirement 25 (AI Output Validation):
 * The Memory System SHALL enforce a validation boundary between AI-generated
 * recommendations and persisted memories. No memory can be persisted without
 * passing through ExtractionValidator. AI recommendations are treated as
 * untrusted input.
 *
 * <p><strong>Validation Rules:</strong>
 * <ol>
 *   <li>Content length must be ≤ 500 characters</li>
 *   <li>importanceScore must be between 1-10</li>
 *   <li>confidenceScore must be between 0.0-1.0</li>
 *   <li>category must be a valid MemoryCategory enum value</li>
 *   <li>subjectType must be a valid MemorySubjectType enum value</li>
 *   <li>sourceType must be a valid MemorySourceType enum value</li>
 *   <li>Content must not be null, empty, or just whitespace</li>
 *   <li>Content must not contain domain entity data (names, birthdays, phone numbers)</li>
 * </ol>
 *
 * <p><strong>Correctness Property:</strong>
 * Memories are NEVER created directly by AI — the ExtractionValidator validates
 * every recommendation before persistence.
 *
 * <p><strong>Design Reference:</strong>
 * From design.md: "Domain entity data (names, birthdays, phone) is NEVER stored
 * as a memory (validated at creation time)".
 *
 * @see AiMemoryRecommendation
 * @see ValidationResult
 */
@Component
public class ExtractionValidator {

    private static final Logger log = LoggerFactory.getLogger(ExtractionValidator.class);

    /**
     * Maximum content length in characters (per SPEC-004 database CHECK constraint).
     */
    public static final int MAX_CONTENT_LENGTH = 500;

    /**
     * Minimum importance score (per SPEC-004 Requirement 4).
     */
    public static final int MIN_IMPORTANCE_SCORE = 1;

    /**
     * Maximum importance score (per SPEC-004 Requirement 4).
     */
    public static final int MAX_IMPORTANCE_SCORE = 10;

    /**
     * Minimum confidence score (per SPEC-004 Requirement 5).
     */
    public static final double MIN_CONFIDENCE_SCORE = 0.0;

    /**
     * Maximum confidence score (per SPEC-004 Requirement 5).
     */
    public static final double MAX_CONFIDENCE_SCORE = 1.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Domain Entity Data Detection Patterns
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pattern to detect phone numbers in various formats.
     * Matches: +1-555-555-5555, (555) 555-5555, 555.555.5555, 5555555555, etc.
     */
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(
            "(?:\\+?\\d{1,3}[-.\\s]?)?" +         // Optional country code
            "(?:\\(?\\d{2,3}\\)?[-.\\s]?)?" +     // Optional area code
            "\\d{3}[-.\\s]?\\d{4}"                // Main number
    );

    /**
     * Pattern to detect date formats that could be birthdays.
     * Matches: MM/DD/YYYY, DD-MM-YYYY, YYYY-MM-DD, Month DD YYYY, etc.
     * Also matches: "born on", "birthday is", "birth date"
     */
    private static final Pattern BIRTHDAY_PATTERN = Pattern.compile(
            "(?i)" +  // Case insensitive
            "(?:" +
            "\\b(?:birth\\s*date|birthday|born\\s+on|date\\s+of\\s+birth|dob)\\b" +  // Birthday phrases
            "|" +
            "\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b" +  // Numeric dates
            "|" +
            "\\b(?:january|february|march|april|may|june|july|august|september|october|november|december)" +
            "\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?\\b" +  // Month name dates
            ")"
    );

    /**
     * Pattern to detect name declarations that suggest storing domain entity names.
     * Matches phrases like "my name is", "his/her name is", "named", etc.
     * Note: This doesn't prevent all name mentions (which would be too restrictive),
     * but catches explicit name declarations that belong in domain entities.
     */
    private static final Pattern NAME_DECLARATION_PATTERN = Pattern.compile(
            "(?i)" +  // Case insensitive
            "(?:" +
            "\\b(?:my|his|her|their|child's|son's|daughter's)\\s+name\\s+is\\b" +
            "|" +
            "\\b(?:named|call(?:ed)?\\s+(?:him|her|them))\\s+[A-Z][a-z]+\\b" +
            "|" +
            "\\bname:\\s*[A-Z][a-z]+" +  // "name: John" format
            ")"
    );

    /**
     * Pattern to detect age declarations that belong in domain entities.
     * Matches phrases like "X is Y years old", "turned Y", "Y-year-old", etc.
     * Age is derived from birth_date in Child entity, not stored as memory.
     */
    private static final Pattern AGE_PATTERN = Pattern.compile(
            "(?i)" +  // Case insensitive
            "(?:" +
            "\\b(?:is|he's|she's|he is|she is)\\s+\\d{1,2}\\s+(?:years?|yrs?)\\s+old\\b" +  // "is 5 years old"
            "|" +
            "\\b(?:just\\s+)?turned\\s+\\d{1,2}\\b" +  // "turned 5" or "just turned 5"
            "|" +
            "\\b\\d{1,2}[-\\s]?(?:year|yr)[-\\s]?old\\b" +  // "5-year-old" or "5 year old"
            "|" +
            "\\bage(?:d)?\\s*(?::|is)?\\s*\\d{1,2}\\b" +  // "age: 5" or "aged 5"
            ")"
    );

    /**
     * Validates an AI memory recommendation.
     *
     * <p>This method checks all validation rules and returns a result indicating
     * whether the recommendation is valid. If invalid, the result includes all
     * validation errors found.
     *
     * @param recommendation the AI-generated memory recommendation to validate
     * @return a ValidationResult indicating success or failure with error messages
     */
    public ValidationResult validate(AiMemoryRecommendation recommendation) {
        if (recommendation == null) {
            log.debug("Validation failed: recommendation is null");
            return ValidationResult.invalid(List.of("Recommendation cannot be null"));
        }

        List<String> errors = new ArrayList<>();

        // Rule 7: Content must not be null, empty, or just whitespace
        validateContent(recommendation.content(), errors);

        // Rule 8: Content must not contain domain entity data
        if (recommendation.content() != null && !recommendation.content().isBlank()) {
            validateNoDomainEntityData(recommendation.content(), errors);
        }

        // Rule 1: Content length must be <= 500 characters
        validateContentLength(recommendation.content(), errors);

        // Rule 2: importanceScore must be between 1-10
        validateImportanceScore(recommendation.importanceScore(), errors);

        // Rule 3: confidenceScore must be between 0.0-1.0
        validateConfidenceScore(recommendation.confidenceScore(), errors);

        // Rule 4: category must be a valid MemoryCategory enum value
        validateCategory(recommendation.category(), errors);

        // Rule 5: subjectType must be a valid MemorySubjectType enum value
        validateSubjectType(recommendation.subjectType(), errors);

        // Rule 6: sourceType must be a valid MemorySourceType enum value
        validateSourceType(recommendation.sourceType(), errors);

        if (errors.isEmpty()) {
            log.debug("Validation passed for recommendation: category={}, subjectType={}",
                    recommendation.category(), recommendation.subjectType());
            return ValidationResult.valid();
        }

        log.debug("Validation failed with {} errors: {}", errors.size(), errors);
        return ValidationResult.invalid(errors);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Individual Validation Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates that content is not null, empty, or just whitespace.
     */
    private void validateContent(String content, List<String> errors) {
        if (content == null) {
            errors.add("Content cannot be null");
        } else if (content.isEmpty()) {
            errors.add("Content cannot be empty");
        } else if (content.isBlank()) {
            errors.add("Content cannot be only whitespace");
        }
    }

    /**
     * Validates content length is within the allowed limit.
     */
    private void validateContentLength(String content, List<String> errors) {
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            errors.add(String.format("Content length %d exceeds maximum of %d characters",
                    content.length(), MAX_CONTENT_LENGTH));
        }
    }

    /**
     * Validates importance score is within the allowed range.
     */
    private void validateImportanceScore(Integer importanceScore, List<String> errors) {
        if (importanceScore == null) {
            errors.add("Importance score cannot be null");
        } else if (importanceScore < MIN_IMPORTANCE_SCORE || importanceScore > MAX_IMPORTANCE_SCORE) {
            errors.add(String.format("Importance score %d must be between %d and %d",
                    importanceScore, MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE));
        }
    }

    /**
     * Validates confidence score is within the allowed range.
     */
    private void validateConfidenceScore(Double confidenceScore, List<String> errors) {
        if (confidenceScore == null) {
            errors.add("Confidence score cannot be null");
        } else if (confidenceScore < MIN_CONFIDENCE_SCORE || confidenceScore > MAX_CONFIDENCE_SCORE) {
            errors.add(String.format("Confidence score %.2f must be between %.1f and %.1f",
                    confidenceScore, MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE));
        }
    }

    /**
     * Validates category is a valid MemoryCategory enum value.
     */
    private void validateCategory(String category, List<String> errors) {
        if (category == null) {
            errors.add("Category cannot be null");
        } else if (category.isBlank()) {
            errors.add("Category cannot be empty");
        } else {
            try {
                MemoryCategory.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(String.format("Invalid category '%s'. Must be one of: %s",
                        category, getEnumValuesString(MemoryCategory.class)));
            }
        }
    }

    /**
     * Validates subjectType is a valid MemorySubjectType enum value.
     */
    private void validateSubjectType(String subjectType, List<String> errors) {
        if (subjectType == null) {
            errors.add("Subject type cannot be null");
        } else if (subjectType.isBlank()) {
            errors.add("Subject type cannot be empty");
        } else {
            try {
                MemorySubjectType.valueOf(subjectType.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(String.format("Invalid subject type '%s'. Must be one of: %s",
                        subjectType, getEnumValuesString(MemorySubjectType.class)));
            }
        }
    }

    /**
     * Validates sourceType is a valid MemorySourceType enum value.
     */
    private void validateSourceType(String sourceType, List<String> errors) {
        if (sourceType == null) {
            errors.add("Source type cannot be null");
        } else if (sourceType.isBlank()) {
            errors.add("Source type cannot be empty");
        } else {
            try {
                MemorySourceType.valueOf(sourceType.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(String.format("Invalid source type '%s'. Must be one of: %s",
                        sourceType, getEnumValuesString(MemorySourceType.class)));
            }
        }
    }

    /**
     * Validates that content does not contain domain entity data.
     *
     * <p>From SPEC-004 Requirement 1 criteria 6 and design.md:
     * The Memory System SHALL NOT store as memories any facts that are authoritative
     * fields on domain entities. Specifically:
     * <ul>
     *   <li>Child name, birth_date → authoritative in Child entity</li>
     *   <li>Father display_name, phone, timezone → authoritative in Father entity</li>
     *   <li>Age (derived from birth_date) → authoritative in Child entity</li>
     * </ul>
     */
    private void validateNoDomainEntityData(String content, List<String> errors) {
        if (PHONE_NUMBER_PATTERN.matcher(content).find()) {
            errors.add("Content contains phone number which belongs in domain entity, not memory");
        }

        if (BIRTHDAY_PATTERN.matcher(content).find()) {
            errors.add("Content contains birthday/date of birth which belongs in domain entity, not memory");
        }

        if (NAME_DECLARATION_PATTERN.matcher(content).find()) {
            errors.add("Content contains explicit name declaration which belongs in domain entity, not memory");
        }

        if (AGE_PATTERN.matcher(content).find()) {
            errors.add("Content contains age information which is derived from birth_date in domain entity, not memory");
        }
    }

    /**
     * Returns a comma-separated string of enum values for error messages.
     */
    private <E extends Enum<E>> String getEnumValuesString(Class<E> enumClass) {
        E[] values = enumClass.getEnumConstants();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i].name());
        }
        return sb.toString();
    }
}
