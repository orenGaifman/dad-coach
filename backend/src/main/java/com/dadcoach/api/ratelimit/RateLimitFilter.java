package com.dadcoach.api.ratelimit;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Servlet filter that enforces per-actor rate limits using a sliding window algorithm.
 * <p>
 * Rate limits are enforced per {@code actor_id} (not per IP). If no actor context is
 * available (unauthenticated request or context not yet populated), the filter
 * passes the request through without rate limiting.
 * <p>
 * The sliding window tracks request timestamps per actor within a configurable
 * time window (default: 60 seconds). When the number of requests within the window
 * exceeds the configured limit for the actor's type, a {@link RateLimitExceededException}
 * is thrown. The exception is handled by the {@code GlobalExceptionHandler} which
 * returns a 429 response with a {@code Retry-After} header and RFC 9457 Problem Detail body.
 * <p>
 * This filter runs AFTER:
 * <ul>
 *   <li>{@link com.dadcoach.api.auth.ActorContextFilter} (Order 1) — actor identity must be available</li>
 *   <li>{@link com.dadcoach.api.idempotency.IdempotencyFilter} (Order 2) — idempotent replays bypass rate limiting</li>
 * </ul>
 *
 * <p>In-memory implementation using {@link ConcurrentHashMap} with periodic cleanup
 * of expired entries during normal request processing.
 */
@Component
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Maps actor_id → deque of request timestamps (epoch millis) within the sliding window.
     */
    private final ConcurrentHashMap<UUID, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    private final RateLimitConfig config;

    /**
     * Counter to trigger periodic cleanup of stale entries.
     * Cleanup runs approximately every 1000 requests.
     */
    private volatile long requestCounter = 0;
    private static final long CLEANUP_INTERVAL = 1000;

    public RateLimitFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if rate limiting is disabled
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Actor identity is required — skip if no actor context
        ActorContext actor = ActorContext.current();
        if (actor == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID actorId = actor.getActorId();
        int maxRequests = config.getLimitForActorType(actor.getActorType());
        long windowMillis = config.getWindowSeconds() * 1000L;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMillis;

        // Get or create the timestamp deque for this actor
        Deque<Long> timestamps = requestLog.computeIfAbsent(actorId, k -> new ConcurrentLinkedDeque<>());

        // Evict expired timestamps (outside the sliding window)
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }

        // Check if the actor has exceeded their rate limit
        if (timestamps.size() >= maxRequests) {
            // Calculate retry-after: time until the oldest request in the window expires
            long oldestTimestamp = timestamps.peekFirst();
            long retryAfterMillis = (oldestTimestamp + windowMillis) - now;
            long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000); // Round up

            log.warn("Rate limit exceeded for actor {} (type={}, limit={}/{}s, current={})",
                    actorId, actor.getActorType(), maxRequests, config.getWindowSeconds(),
                    timestamps.size());

            throw new RateLimitExceededException(retryAfterSeconds);
        }

        // Record this request
        timestamps.addLast(now);

        // Periodic cleanup of stale entries from actors no longer making requests
        if (++requestCounter % CLEANUP_INTERVAL == 0) {
            cleanupStaleEntries(windowStart);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Removes entries for actors that have no recent requests within the sliding window.
     * Called periodically to prevent unbounded memory growth.
     */
    private void cleanupStaleEntries(long windowStart) {
        requestLog.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            // Remove expired timestamps
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }
            // Remove the entry entirely if no timestamps remain
            return timestamps.isEmpty();
        });
    }
}
