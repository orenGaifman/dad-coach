package com.dadcoach.api.error;

/**
 * Thrown when a business rule limit is exceeded (e.g., max 8 children, max 5 active goals).
 */
public class LimitExceededException extends RuntimeException {

    private final String limitName;
    private final int currentCount;
    private final int maxAllowed;

    public LimitExceededException(String limitName, int currentCount, int maxAllowed) {
        super(String.format("Maximum of %d %s exceeded. Current count: %d.", maxAllowed, limitName, currentCount));
        this.limitName = limitName;
        this.currentCount = currentCount;
        this.maxAllowed = maxAllowed;
    }

    public String getLimitName() {
        return limitName;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }
}
