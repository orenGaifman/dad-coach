package com.dadcoach.onboarding;

import java.time.Instant;

/**
 * Thrown when an invitation has expired.
 */
public class InvitationExpiredException extends RuntimeException {

    private final Instant expiredAt;

    public InvitationExpiredException(Instant expiredAt) {
        super("Invitation has expired");
        this.expiredAt = expiredAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }
}
