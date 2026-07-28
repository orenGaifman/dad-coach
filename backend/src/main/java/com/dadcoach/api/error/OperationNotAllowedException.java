package com.dadcoach.api.error;

/**
 * Thrown when an operation is not allowed due to business rules
 * (e.g., cannot modify a completed goal, cannot pause a CHURNED father).
 */
public class OperationNotAllowedException extends RuntimeException {

    private final String operation;
    private final String reason;

    public OperationNotAllowedException(String operation, String reason) {
        super(String.format("Operation '%s' not allowed: %s", operation, reason));
        this.operation = operation;
        this.reason = reason;
    }

    public String getOperation() {
        return operation;
    }

    public String getReason() {
        return reason;
    }
}
