package com.dadcoach.onboarding;

/**
 * Thrown when activation retry limit (3) has been exceeded.
 */
public class MaxRetriesExceededException extends RuntimeException {

    private final int retryCount;

    public MaxRetriesExceededException(int retryCount) {
        super("Maximum activation retries (" + retryCount + ") exceeded");
        this.retryCount = retryCount;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
