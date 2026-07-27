package com.dadcoach.ai.output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating an AI output against its schema.
 * Contains pass/fail status and a list of failure details when validation fails.
 *
 * @param valid    true if the output passed all validation checks
 * @param failures list of individual field validation failures (empty if valid)
 */
public record ValidationResult(
    boolean valid,
    List<FieldFailure> failures
) {
    /**
     * Describes a single field validation failure.
     *
     * @param field    the field name that failed validation
     * @param expected description of what was expected
     * @param actual   description of the actual value found
     */
    public record FieldFailure(
        String field,
        String expected,
        String actual
    ) {
        public FieldFailure {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field must not be null or blank");
            }
            if (expected == null) {
                expected = "";
            }
            if (actual == null) {
                actual = "";
            }
        }
    }

    public ValidationResult {
        failures = failures != null ? List.copyOf(failures) : List.of();
    }

    /**
     * Creates a passing validation result.
     */
    public static ValidationResult passed() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Creates a failing validation result from the given failures.
     */
    public static ValidationResult failed(List<FieldFailure> failures) {
        return new ValidationResult(false, failures);
    }

    /**
     * Builder to accumulate validation failures.
     */
    public static class Builder {
        private final List<FieldFailure> failures = new ArrayList<>();

        public Builder addFailure(String field, String expected, String actual) {
            failures.add(new FieldFailure(field, expected, actual));
            return this;
        }

        public ValidationResult build() {
            if (failures.isEmpty()) {
                return ValidationResult.passed();
            }
            return ValidationResult.failed(Collections.unmodifiableList(new ArrayList<>(failures)));
        }
    }
}
