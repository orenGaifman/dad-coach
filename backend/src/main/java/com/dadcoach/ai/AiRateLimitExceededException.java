package com.dadcoach.ai;

/**
 * Thrown when a father's daily AI call rate limit (max 20 per day) has been exceeded.
 */
public class AiRateLimitExceededException extends RuntimeException {

    private final Long fatherId;
    private final int currentCount;
    private final int maxAllowed;

    public AiRateLimitExceededException(Long fatherId, int currentCount, int maxAllowed) {
        super(String.format(
            "AI rate limit exceeded for father %d: %d/%d calls today",
            fatherId, currentCount, maxAllowed
        ));
        this.fatherId = fatherId;
        this.currentCount = currentCount;
        this.maxAllowed = maxAllowed;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }
}
