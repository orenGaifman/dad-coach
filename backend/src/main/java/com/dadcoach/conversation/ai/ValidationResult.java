package com.dadcoach.conversation.ai;

import java.util.List;

/**
 * Result of validating an AI-generated coaching response.
 * Contains whether validation passed and any failure reasons for correction context.
 *
 * @param passed   true if the response meets all quality and safety criteria
 * @param failures list of failure descriptions (empty if passed)
 */
public record ValidationResult(
        boolean passed,
        List<String> failures
) {

    public ValidationResult {
        if (failures == null) {
            failures = List.of();
        }
    }

    /**
     * Creates a passing validation result.
     */
    public static ValidationResult pass() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Creates a failing validation result with the specified failure reasons.
     */
    public static ValidationResult fail(List<String> failures) {
        return new ValidationResult(false, failures);
    }

    /**
     * Creates a failing validation result with a single failure reason.
     */
    public static ValidationResult fail(String failure) {
        return new ValidationResult(false, List.of(failure));
    }
}
