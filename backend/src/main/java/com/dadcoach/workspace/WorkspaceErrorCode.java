package com.dadcoach.workspace;

import org.springframework.http.HttpStatus;

/**
 * Error codes specific to the Father Workspace bounded context.
 *
 * <p>Each code maps to an HTTP status, a code string for external consumers,
 * and a human-readable message template.</p>
 */
public enum WorkspaceErrorCode {

    FATHER_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_001", "Father not found with identifier: %s"),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_002", "Child not found with identifier: %s"),
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_003", "Goal not found with identifier: %s"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_004", "Resource not found: %s"),
    GROWTH_SIGNAL_DUPLICATE(HttpStatus.CONFLICT, "WORKSPACE_005", "Growth signal already recorded for source: %s"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "WORKSPACE_006", "Rate limit exceeded. Retry after %s seconds."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "WORKSPACE_007", "Validation failed: %s"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "WORKSPACE_008", "Service temporarily unavailable: %s"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "WORKSPACE_009", "An unexpected error occurred. Please try again later.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String messageTemplate;

    WorkspaceErrorCode(HttpStatus httpStatus, String code, String messageTemplate) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    /**
     * Formats the message template with the given arguments.
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
