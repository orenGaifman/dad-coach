package com.dadcoach.workspace;

/**
 * Thrown when a workspace rate limit is exceeded (e.g., activity reporting daily limits).
 */
public class RateLimitExceededException extends WorkspaceException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                WorkspaceErrorCode.RATE_LIMIT_EXCEEDED,
                WorkspaceErrorCode.RATE_LIMIT_EXCEEDED.formatMessage(retryAfterSeconds)
        );
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
