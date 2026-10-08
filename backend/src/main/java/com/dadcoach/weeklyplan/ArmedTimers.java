package com.dadcoach.weeklyplan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The session timers the platform really holds for a father (D-037), read once: session id -> transition key -> when.
 * {@code known=false} when the platform could not be asked - then no reminder time is stated at all.
 */
public record ArmedTimers(boolean known, Map<String, Map<String, Instant>> bySession) {

    public static final ArmedTimers UNKNOWN = new ArmedTimers(false, Map.of());

    public static ArmedTimers of(Map<String, Map<String, Instant>> bySession) {
        return new ArmedTimers(true, Map.copyOf(bySession));
    }

    /** The pending timers of one session (empty when none). */
    public Map<String, Instant> of(UUID sessionId) {
        return bySession.getOrDefault(sessionId.toString(), Map.of());
    }
}
