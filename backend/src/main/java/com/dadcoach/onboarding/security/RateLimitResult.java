package com.dadcoach.onboarding.security;

/**
 * Result of a rate limit check.
 * Contains whether the request is allowed, remaining attempts, and retry-after seconds if blocked.
 */
public record RateLimitResult(
    boolean allowed,
    int remainingAttempts,
    int retryAfterSeconds
) {

    /**
     * Creates a result indicating the request is allowed.
     */
    public static RateLimitResult allowed(int remainingAttempts) {
        return new RateLimitResult(true, remainingAttempts, 0);
    }

    /**
     * Creates a result indicating the request is blocked due to rate limiting.
     */
    public static RateLimitResult blocked(int retryAfterSeconds) {
        return new RateLimitResult(false, 0, retryAfterSeconds);
    }
}
