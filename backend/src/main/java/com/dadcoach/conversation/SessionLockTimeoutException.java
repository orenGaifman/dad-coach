package com.dadcoach.conversation;

import java.util.UUID;

/**
 * Thrown when a per-father advisory lock cannot be acquired within the configured timeout.
 * Signals that the message should be queued for retry via the side-effect outbox.
 */
public class SessionLockTimeoutException extends RuntimeException {

    private final UUID fatherId;
    private final long timeoutSeconds;

    public SessionLockTimeoutException(UUID fatherId, long timeoutSeconds) {
        super("Could not acquire session lock for father " + fatherId +
                " within " + timeoutSeconds + " seconds. Message will be queued for retry.");
        this.fatherId = fatherId;
        this.timeoutSeconds = timeoutSeconds;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
