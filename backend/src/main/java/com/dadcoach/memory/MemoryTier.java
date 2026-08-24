package com.dadcoach.memory;

/**
 * Memory tier classification based on importance score.
 *
 * <p>From SPEC-004 Requirement 6:
 * Tier determines base expiration and decay behavior.
 */
public enum MemoryTier {

    /**
     * Short-term memories (importance 1-3).
     * Base expiration: 90 days from creation.
     * Decay starts: 30 days since last access.
     * Decay rate: -0.15 confidence per 30 days.
     */
    SHORT_TERM(90, 30, 0.15),

    /**
     * Medium-term memories (importance 4-6).
     * Base expiration: 180 days from creation.
     * Decay starts: 60 days since last access.
     * Decay rate: -0.10 confidence per 30 days.
     */
    MEDIUM_TERM(180, 60, 0.10),

    /**
     * Long-term memories (importance 7-10).
     * Never expires (unless superseded, corrected, or deleted).
     * Decay starts: 90 days since last access.
     * Decay rate: -0.05 confidence per 30 days.
     */
    LONG_TERM(0, 90, 0.05);

    private final int expirationDays;
    private final int decayStartDays;
    private final double decayRatePer30Days;

    MemoryTier(int expirationDays, int decayStartDays, double decayRatePer30Days) {
        this.expirationDays = expirationDays;
        this.decayStartDays = decayStartDays;
        this.decayRatePer30Days = decayRatePer30Days;
    }

    /**
     * Returns the number of days until expiration.
     *
     * @return expiration days, or 0 for long-term (never expires)
     */
    public int getExpirationDays() {
        return expirationDays;
    }

    /**
     * Returns the number of days after which decay begins.
     *
     * @return decay start threshold in days
     */
    public int getDecayStartDays() {
        return decayStartDays;
    }

    /**
     * Returns the confidence decay rate per 30 days.
     *
     * @return decay rate as a decimal (e.g., 0.15 = 15%)
     */
    public double getDecayRatePer30Days() {
        return decayRatePer30Days;
    }

    /**
     * Checks whether memories in this tier expire.
     *
     * @return true if the tier has a finite expiration
     */
    public boolean expires() {
        return expirationDays > 0;
    }

    /**
     * Returns the tier for a given importance score.
     *
     * @param importanceScore the importance score (1-10)
     * @return the corresponding tier
     */
    public static MemoryTier fromImportanceScore(int importanceScore) {
        if (importanceScore <= 3) {
            return SHORT_TERM;
        } else if (importanceScore <= 6) {
            return MEDIUM_TERM;
        } else {
            return LONG_TERM;
        }
    }
}
