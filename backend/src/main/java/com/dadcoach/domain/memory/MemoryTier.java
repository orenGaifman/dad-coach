package com.dadcoach.domain.memory;

/**
 * Memory retention tiers based on importance score.
 *
 * <p>Classification rules (Requirement 7.2):
 * <ul>
 *   <li>SHORT_TERM — importance 1-3, expires in 90 days</li>
 *   <li>MEDIUM_TERM — importance 4-6, expires in 180 days</li>
 *   <li>LONG_TERM — importance 7-10, never expires</li>
 * </ul>
 */
public enum MemoryTier {
    SHORT_TERM(90),
    MEDIUM_TERM(180),
    LONG_TERM(0); // 0 means never expires

    private final int expirationDays;

    MemoryTier(int expirationDays) {
        this.expirationDays = expirationDays;
    }

    /**
     * Returns the number of days until expiration, or 0 if the memory never expires.
     */
    public int getExpirationDays() {
        return expirationDays;
    }

    /**
     * Whether this tier has an expiration (SHORT_TERM and MEDIUM_TERM do, LONG_TERM does not).
     */
    public boolean expires() {
        return expirationDays > 0;
    }

    /**
     * Classifies an importance score (1-10) into the appropriate tier.
     *
     * @param importanceScore the importance score (1-10)
     * @return the corresponding memory tier
     * @throws IllegalArgumentException if the score is outside 1-10
     */
    public static MemoryTier fromImportanceScore(int importanceScore) {
        if (importanceScore < 1 || importanceScore > 10) {
            throw new IllegalArgumentException(
                    "Importance score must be between 1 and 10, got: " + importanceScore);
        }
        if (importanceScore <= 3) {
            return SHORT_TERM;
        } else if (importanceScore <= 6) {
            return MEDIUM_TERM;
        } else {
            return LONG_TERM;
        }
    }
}
