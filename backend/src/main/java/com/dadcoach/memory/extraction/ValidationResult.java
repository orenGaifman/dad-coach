package com.dadcoach.memory.extraction;

import java.util.Collections;
import java.util.List;

/**
 * Result of validating an AI memory recommendation.
 *
 * <p>From SPEC-004 Requirement 25 (AI Output Validation):
 * The ExtractionValidator validates every AI recommendation before persistence.
 * This class captures the validation outcome including all error messages.
 *
 * <p>A valid result means the recommendation passed all validation rules and can be
 * persisted to the database. An invalid result includes a list of specific validation
 * errors explaining why the recommendation was rejected.
 *
 * <p><strong>Correctness Property:</strong>
 * No memory can be persisted without passing through ExtractionValidator.
 *
 * @see ExtractionValidator
 * @see AiMemoryRecommendation
 */
public sealed interface ValidationResult {

    /**
     * Returns true if the recommendation is valid and can be persisted.
     */
    boolean isValid();

    /**
     * Returns the list of validation error messages.
     * Empty list if validation passed.
     */
    List<String> errors();

    /**
     * Creates a successful validation result.
     *
     * @return a valid ValidationResult with no errors
     */
    static ValidationResult valid() {
        return Valid.INSTANCE;
    }

    /**
     * Creates a failed validation result with error messages.
     *
     * @param errors the list of validation error messages
     * @return an invalid ValidationResult with the specified errors
     * @throws IllegalArgumentException if errors is null or empty
     */
    static ValidationResult invalid(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid result must have at least one error");
        }
        return new Invalid(Collections.unmodifiableList(errors));
    }

    /**
     * Represents a successful validation.
     */
    record Valid() implements ValidationResult {
        static final Valid INSTANCE = new Valid();

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public List<String> errors() {
            return Collections.emptyList();
        }
    }

    /**
     * Represents a failed validation with error messages.
     */
    record Invalid(List<String> errors) implements ValidationResult {

        @Override
        public boolean isValid() {
            return false;
        }
    }
}
