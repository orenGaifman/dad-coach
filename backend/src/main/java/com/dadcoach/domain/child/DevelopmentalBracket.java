package com.dadcoach.domain.child;

/**
 * Developmental bracket classification based on child age.
 * Used to tailor coaching missions and content.
 */
public enum DevelopmentalBracket {
    INFANT(0, 2),
    PRESCHOOL(3, 5),
    EARLY_SCHOOL(6, 8),
    PRE_TEEN(9, 11),
    EARLY_TEEN(12, 14),
    TEENAGER(15, 18);

    private final int minAge;
    private final int maxAge;

    DevelopmentalBracket(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    /**
     * Returns the developmental bracket for the given age.
     * Ages above 18 are classified as TEENAGER.
     *
     * @param age the child's age in years
     * @return the matching developmental bracket
     * @throws IllegalArgumentException if age is negative
     */
    public static DevelopmentalBracket fromAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative: " + age);
        }
        for (DevelopmentalBracket bracket : values()) {
            if (age >= bracket.minAge && age <= bracket.maxAge) {
                return bracket;
            }
        }
        // Ages above 18 default to TEENAGER
        return TEENAGER;
    }
}
