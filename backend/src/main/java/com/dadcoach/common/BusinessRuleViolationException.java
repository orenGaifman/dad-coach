package com.dadcoach.common;

/**
 * Thrown when a business rule is violated.
 *
 * <p>Examples include exceeding the maximum number of children (8),
 * exceeding the maximum number of active goals (5), or violating
 * any other domain-specific constraint.</p>
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String ruleName;
    private final String detail;

    /**
     * Creates a new BusinessRuleViolationException.
     *
     * @param ruleName the identifier of the violated rule (e.g., "MAX_CHILDREN_EXCEEDED")
     * @param message  a human-readable description of the violation
     */
    public BusinessRuleViolationException(String ruleName, String message) {
        super(formatMessage(ruleName, message));
        this.ruleName = ruleName;
        this.detail = message;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String getMessage() {
        return formatMessage(ruleName, detail);
    }

    private static String formatMessage(String ruleName, String message) {
        return String.format("Business rule violated [%s]: %s", ruleName, message);
    }
}
