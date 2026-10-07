package com.dadcoach.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Login links are real WhatsApp messages to real people. At most {@link #PER_SUBJECT} links per person per
 * {@link #WINDOW} - checked only after the phone resolved, so a rate-limited person and an unknown number stay
 * indistinguishable - and at most {@link #PER_CLIENT} requests per client address per window, counted before
 * anything is looked up (the same for every number). In memory: one instance.
 */
@Component
public class LoginLinkRateLimiter {

    static final int PER_SUBJECT = 3;
    static final int PER_CLIENT = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginLinkRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryClient(String clientAddress) {
        return tryAcquire("client:" + (clientAddress == null ? "?" : clientAddress), PER_CLIENT);
    }

    public boolean trySubject(SignInSubject subject) {
        return tryAcquire("subject:" + subject.fatherId() + "/" + subject.staffUserId(), PER_SUBJECT);
    }

    private boolean tryAcquire(String key, int max) {
        Deque<Instant> mine = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant now = clock.instant();
        synchronized (mine) {
            while (!mine.isEmpty() && Duration.between(mine.peekFirst(), now).compareTo(WINDOW) >= 0) {
                mine.pollFirst();
            }
            if (mine.size() >= max) {
                return false;
            }
            mine.addLast(now);
            return true;
        }
    }

    /** Forgets every attempt (tests; also handy after an incident). */
    public void reset() {
        attempts.clear();
    }
}
