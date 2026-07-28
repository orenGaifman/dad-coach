package com.dadcoach.channel.session;

import java.time.Instant;

/**
 * Represents the current session window state for a father on a communication channel.
 * Used by the Conversation_Engine to make proactive scheduling decisions.
 *
 * @param open     whether the session window is currently open (free-form messages allowed)
 * @param closesAt timestamp when the session window will close (null if no active session)
 */
public record SessionState(
    boolean open,
    Instant closesAt
) {

    /**
     * Returns a closed session state (no active window).
     */
    public static SessionState closed() {
        return new SessionState(false, null);
    }

    /**
     * Returns an open session state with the given closure time.
     */
    public static SessionState openUntil(Instant closesAt) {
        return new SessionState(true, closesAt);
    }

    public boolean isClosed() {
        return !open;
    }
}
