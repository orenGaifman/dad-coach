package com.dadcoach.memory.sensitive;

/**
 * Severity levels for safety events.
 *
 * <p>Severity determines review priority and handling procedures:
 * <ul>
 *   <li>LOW: Minor concern, logged for pattern analysis</li>
 *   <li>MEDIUM: Moderate concern, requires review within 24 hours</li>
 *   <li>HIGH: Serious concern, requires prompt review</li>
 *   <li>CRITICAL: Immediate danger indicators, requires urgent attention</li>
 * </ul>
 */
public enum SafetyEventSeverity {

    /**
     * Minor concern, logged for pattern analysis.
     */
    LOW(1),

    /**
     * Moderate concern, requires review within 24 hours.
     */
    MEDIUM(2),

    /**
     * Serious concern, requires prompt review.
     */
    HIGH(3),

    /**
     * Immediate danger indicators, requires urgent attention.
     */
    CRITICAL(4);

    private final int level;

    SafetyEventSeverity(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric level of this severity.
     * Higher values indicate more severe concerns.
     *
     * @return the severity level (1-4)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Checks if this severity is at least as severe as the given threshold.
     *
     * @param threshold the threshold severity to compare against
     * @return true if this severity is at or above the threshold
     */
    public boolean isAtLeast(SafetyEventSeverity threshold) {
        return this.level >= threshold.level;
    }
}
