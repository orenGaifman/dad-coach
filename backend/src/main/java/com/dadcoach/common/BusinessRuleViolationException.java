package com.dadcoach.common;

/** Thrown when a business rule is violated. */
public class BusinessRuleViolationException extends RuntimeException {
    private final String ruleName;
    private final String detail;

    public BusinessRuleViolationException(String ruleName, String message) {
        super("Business rule violated [" + ruleName + "]: " + message);
        this.ruleName = ruleName;
        this.detail = message;
    }

    public String getRuleName() { return ruleName; }
    public String getDetail() { return detail; }
}
