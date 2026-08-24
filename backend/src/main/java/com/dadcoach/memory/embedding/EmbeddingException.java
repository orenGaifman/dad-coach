package com.dadcoach.memory.embedding;

/**
 * Exception thrown when embedding generation fails.
 *
 * <p>This exception provides detailed error classification for:
 * <ul>
 *   <li>Network errors (connectivity issues)</li>
 *   <li>Timeout errors (request exceeded time limit)</li>
 *   <li>Rate limiting (API throttling)</li>
 *   <li>Authentication errors (invalid API key)</li>
 *   <li>Invalid request errors (malformed input)</li>
 *   <li>Invalid response errors (unexpected API response format)</li>
 *   <li>Server errors (OpenAI service issues)</li>
 *   <li>Circuit breaker open (service temporarily unavailable)</li>
 * </ul>
 *
 * @see EmbeddingService
 */
public class EmbeddingException extends RuntimeException {

    /**
     * Classification of embedding errors.
     */
    public enum ErrorType {
        /**
         * Network connectivity error.
         */
        NETWORK_ERROR,

        /**
         * Request timed out.
         */
        TIMEOUT,

        /**
         * API rate limit exceeded.
         */
        RATE_LIMIT,

        /**
         * Invalid API key or authentication failure.
         */
        AUTHENTICATION_ERROR,

        /**
         * Invalid request parameters.
         */
        INVALID_REQUEST,

        /**
         * Response from API was malformed or unexpected.
         */
        INVALID_RESPONSE,

        /**
         * OpenAI server error (5xx).
         */
        SERVER_ERROR,

        /**
         * Circuit breaker is open due to recent failures.
         */
        CIRCUIT_OPEN
    }

    private final ErrorType errorType;
    private final int httpStatus;

    /**
     * Creates an EmbeddingException with the given error type and message.
     *
     * @param errorType the type of error
     * @param message   the error message
     */
    public EmbeddingException(ErrorType errorType, String message) {
        this(errorType, 0, message, null);
    }

    /**
     * Creates an EmbeddingException with the given error type, HTTP status, and message.
     *
     * @param errorType  the type of error
     * @param httpStatus the HTTP status code (0 if not applicable)
     * @param message    the error message
     */
    public EmbeddingException(ErrorType errorType, int httpStatus, String message) {
        this(errorType, httpStatus, message, null);
    }

    /**
     * Creates an EmbeddingException with the given error type, HTTP status, message, and cause.
     *
     * @param errorType  the type of error
     * @param httpStatus the HTTP status code (0 if not applicable)
     * @param message    the error message
     * @param cause      the underlying cause
     */
    public EmbeddingException(ErrorType errorType, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the type of error.
     *
     * @return the error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns the HTTP status code if applicable.
     *
     * @return the HTTP status code, or 0 if not applicable
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Checks if this error is potentially retryable.
     *
     * <p>Retryable errors include:
     * <ul>
     *   <li>Network errors (might be transient)</li>
     *   <li>Timeout (might succeed on retry)</li>
     *   <li>Rate limit (should wait then retry)</li>
     *   <li>Server errors (might be transient)</li>
     * </ul>
     *
     * <p>Non-retryable errors include:
     * <ul>
     *   <li>Authentication errors (need config fix)</li>
     *   <li>Invalid request (need input fix)</li>
     *   <li>Invalid response (unexpected API behavior)</li>
     *   <li>Circuit open (should wait for circuit to close)</li>
     * </ul>
     *
     * @return true if the error might be resolved by retrying
     */
    public boolean isRetryable() {
        return switch (errorType) {
            case NETWORK_ERROR, TIMEOUT, RATE_LIMIT, SERVER_ERROR -> true;
            case AUTHENTICATION_ERROR, INVALID_REQUEST, INVALID_RESPONSE, CIRCUIT_OPEN -> false;
        };
    }

    /**
     * Returns whether a delay should be applied before retry.
     *
     * @return true if a backoff delay is recommended before retry
     */
    public boolean shouldBackoff() {
        return errorType == ErrorType.RATE_LIMIT || errorType == ErrorType.SERVER_ERROR;
    }

    @Override
    public String toString() {
        return "EmbeddingException{" +
                "errorType=" + errorType +
                ", httpStatus=" + httpStatus +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
