package com.dadcoach.ai.provider;

/**
 * Exception thrown when an AI provider call fails.
 * Covers timeouts, rate limits, server errors, and invalid responses.
 */
public class AiProviderException extends RuntimeException {

    private final String provider;
    private final ErrorType errorType;
    private final int httpStatus;

    public enum ErrorType {
        TIMEOUT,
        RATE_LIMIT,
        SERVER_ERROR,
        AUTHENTICATION_ERROR,
        INVALID_REQUEST,
        INVALID_RESPONSE,
        NETWORK_ERROR,
        CIRCUIT_OPEN
    }

    public AiProviderException(String provider, ErrorType errorType, String message) {
        this(provider, errorType, 0, message, null);
    }

    public AiProviderException(String provider, ErrorType errorType, int httpStatus, String message) {
        this(provider, errorType, httpStatus, message, null);
    }

    public AiProviderException(String provider, ErrorType errorType, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public String getProvider() {
        return provider;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return switch (errorType) {
            case TIMEOUT, RATE_LIMIT, SERVER_ERROR, NETWORK_ERROR -> true;
            case AUTHENTICATION_ERROR, INVALID_REQUEST, INVALID_RESPONSE, CIRCUIT_OPEN -> false;
        };
    }
}
