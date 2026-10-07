package com.dadcoach.api.tools;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 100 tool calls per actor per minute: defense in depth against a looping caller (playbook §11.2). */
@Component
public class ToolEndpointRateLimiter {

    static final int MAX_PER_MINUTE = 100;

    private final ConcurrentMap<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    public ToolEndpointRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String actorId) {
        Deque<Instant> mine = calls.computeIfAbsent(actorId, id -> new ArrayDeque<>());
        Instant now = clock.instant();
        synchronized (mine) {
            while (!mine.isEmpty() && Duration.between(mine.peekFirst(), now).compareTo(Duration.ofMinutes(1)) > 0) {
                mine.pollFirst();
            }
            if (mine.size() >= MAX_PER_MINUTE) {
                return false;
            }
            mine.addLast(now);
            return true;
        }
    }
}
