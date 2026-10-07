package com.dadcoach.publicsite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * At most {@code dad-coach.public-site.max-per-window} (default 5) signups per client address per 10 minutes.
 * In-memory, per instance (Dad Coach runs one instance); bounded so a flood cannot grow it without limit.
 */
@Component
class SiteSignupRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final ConcurrentMap<String, Deque<Instant>> seen = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;

    SiteSignupRateLimiter(Clock clock, @Value("${dad-coach.public-site.max-per-window:5}") int max) {
        this.clock = clock;
        this.max = max;
    }

    boolean tryAcquire(String client) {
        if (seen.size() > MAX_TRACKED_CLIENTS) {
            seen.clear();
        }
        Deque<Instant> mine = seen.computeIfAbsent(client, k -> new ArrayDeque<>());
        Instant now = clock.instant();
        synchronized (mine) {
            while (!mine.isEmpty() && Duration.between(mine.peekFirst(), now).compareTo(WINDOW) > 0) {
                mine.pollFirst();
            }
            if (mine.size() >= max) {
                return false;
            }
            mine.addLast(now);
            return true;
        }
    }
}
