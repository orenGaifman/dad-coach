package com.dadcoach.channel.session;

import java.time.Instant;

/**
 * Result of a pre-delivery session window check.
 * Either the delivery is allowed, or it is rejected because the session is closed.
 *
 * @param allowed   whether the delivery is permitted
 * @param reason    rejection reason (null when allowed)
 * @param closedAt  when the session closed (null when allowed)
 */
public record SessionCheckResult(
    boolean allowed,
    String reason,
    Instant closedAt
) {

    private static final String SESSION_CLOSED_REASON = "SESSION_CLOSED";

    /**
     * Delivery is allowed (session is open, or message is a template).
     */
    public static SessionCheckResult deliveryAllowed() {
        return new SessionCheckResult(true, null, null);
    }

    /**
     * Delivery is rejected because the session window is closed
     * and the message is not a template.
     */
    public static SessionCheckResult sessionClosed(Instant closedAt) {
        return new SessionCheckResult(false, SESSION_CLOSED_REASON, closedAt);
    }

    public boolean isRejected() {
        return !allowed;
    }
}
