package com.dadcoach.onboarding.invitation;

/**
 * Enum representing the type of invitation.
 *
 * SINGLE_USE invitations are consumed after one successful registration.
 * REUSABLE invitations can be used up to a configured maximum number of times.
 *
 * Each type defines its own default max uses and expiration policy per Requirement 1 criteria 5.
 */
public enum InvitationType {

    /**
     * A single-use invitation that is consumed after one successful registration.
     * Expires after 7 days. Max uses: 1.
     */
    SINGLE_USE(1, 7),

    /**
     * A reusable invitation that can be used multiple times up to max_uses.
     * Expires after 90 days. Default max uses: 50.
     */
    REUSABLE(50, 90);

    private final int defaultMaxUses;
    private final int expirationDays;

    InvitationType(int defaultMaxUses, int expirationDays) {
        this.defaultMaxUses = defaultMaxUses;
        this.expirationDays = expirationDays;
    }

    /**
     * Returns the default maximum number of uses for this invitation type.
     * SINGLE_USE: 1, REUSABLE: 50.
     *
     * @return the default max uses
     */
    public int getDefaultMaxUses() {
        return defaultMaxUses;
    }

    /**
     * Returns the number of days until this invitation type expires from creation.
     * SINGLE_USE: 7 days, REUSABLE: 90 days.
     *
     * @return the expiration period in days
     */
    public int getExpirationDays() {
        return expirationDays;
    }
}
