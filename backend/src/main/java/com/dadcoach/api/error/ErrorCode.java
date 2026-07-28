package com.dadcoach.api.error;

import org.springframework.http.HttpStatus;

/**
 * All structured error codes for the Application API (RFC 9457 Problem Details).
 *
 * <p>Each code maps to an HTTP status, a human-readable title, and whether the
 * consumer should retry the request.</p>
 */
public enum ErrorCode {

    // 400 - Structural validation failures
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation Failed", false),
    FIELD_REQUIRED(HttpStatus.BAD_REQUEST, "Field Required", false),
    FIELD_INVALID(HttpStatus.BAD_REQUEST, "Field Invalid", false),

    // 401 - Authentication failures
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", false),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token Expired", false),

    // 404 - Resource not found (also covers ownership mismatch)
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", false),

    // 409 - Conflicts
    STATE_TRANSITION_INVALID(HttpStatus.CONFLICT, "State Transition Invalid", false),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate Resource", false),

    // 422 - Business rule violations
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Business Rule Violation", false),
    OPERATION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Operation Not Allowed", false),

    // 429 - Rate limit
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Rate Limit Exceeded", true),

    // 500 - Internal errors (sanitized, no stack traces)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", true);

    private final HttpStatus httpStatus;
    private final String title;
    private final boolean retryable;

    ErrorCode(HttpStatus httpStatus, String title, boolean retryable) {
        this.httpStatus = httpStatus;
        this.title = title;
        this.retryable = retryable;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Returns the RFC 9457 "type" URI for this error code.
     */
    public String getTypeUri() {
        return "https://dadcoach.app/errors/" + this.name();
    }
}
