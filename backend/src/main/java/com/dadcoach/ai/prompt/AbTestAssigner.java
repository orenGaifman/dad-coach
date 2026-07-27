package com.dadcoach.ai.prompt;

import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic A/B test group assigner.
 * Assigns a father to group "A" or "B" based on a stable hash of their ID.
 *
 * <p>The assignment is deterministic: the same father_id always produces
 * the same group, ensuring consistent experience per Requirement 8 criteria 3.
 *
 * <p>Algorithm: {@code Math.abs(father_id.hashCode()) % 2} → 0 = "A", 1 = "B"
 */
public final class AbTestAssigner {

    public static final String GROUP_A = "A";
    public static final String GROUP_B = "B";

    private AbTestAssigner() {
        // Utility class — no instantiation
    }

    /**
     * Assign a father to an A/B test group deterministically.
     *
     * @param fatherId the unique identifier of the father
     * @return "A" or "B" — always the same for the same fatherId
     */
    public static String assignGroup(UUID fatherId) {
        Objects.requireNonNull(fatherId, "fatherId must not be null");
        int hash = fatherId.hashCode();
        // Use Math.abs carefully — Integer.MIN_VALUE edge case handled by bitwise AND
        int bucket = (hash & Integer.MAX_VALUE) % 2;
        return bucket == 0 ? GROUP_A : GROUP_B;
    }

    /**
     * Assign a father to an A/B test group deterministically using string ID.
     *
     * @param fatherId the string representation of the father's unique identifier
     * @return "A" or "B" — always the same for the same fatherId
     */
    public static String assignGroup(String fatherId) {
        Objects.requireNonNull(fatherId, "fatherId must not be null");
        if (fatherId.isBlank()) {
            throw new IllegalArgumentException("fatherId must not be blank");
        }
        int hash = fatherId.hashCode();
        int bucket = (hash & Integer.MAX_VALUE) % 2;
        return bucket == 0 ? GROUP_A : GROUP_B;
    }
}
