package com.dadcoach.api.error;

/**
 * Thrown when a rate limit is exceeded for the current actor.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(String.format("Rate limit exceeded. Retry after %d seconds.", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
