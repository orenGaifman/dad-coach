package com.dadcoach.onboarding;

/**
 * Thrown when the onboarding rate limit is exceeded.
 */
public class OnboardingRateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public OnboardingRateLimitException(int retryAfterSeconds) {
        super("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
