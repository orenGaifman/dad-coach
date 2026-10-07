package com.dadcoach.whatsapp.inbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 20 turns per WhatsApp number per minute: a loop or a flood never becomes 20 LLM calls a second (playbook §34). */
@Component
public class WhatsAppInboundRateLimiter {

    static final int MAX_PER_MINUTE = 20;

    private final ConcurrentMap<String, Deque<Instant>> seen = new ConcurrentHashMap<>();
    private final Clock clock;

    public WhatsAppInboundRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String sender) {
        Deque<Instant> mine = seen.computeIfAbsent(sender, id -> new ArrayDeque<>());
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
