package com.dadcoach.conversation;

/**
 * Thrown when an inbound message fails validation checks.
 * Contains a human-readable error description indicating what was wrong.
 */
public class MessageValidationException extends RuntimeException {

    private final String field;

    public MessageValidationException(String message) {
        super(message);
        this.field = null;
    }

    public MessageValidationException(String message, String field) {
        super(message);
        this.field = field;
    }

    /**
     * The field that failed validation, or null if not field-specific.
     */
    public String getField() {
        return field;
    }
}
