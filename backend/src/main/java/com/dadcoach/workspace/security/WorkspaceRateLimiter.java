package com.dadcoach.workspace.security;

import com.dadcoach.workspace.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-father rate limiter for workspace API endpoints using a sliding window algorithm.
 *
 * <p>Rate limits by role:</p>
 * <ul>
 *   <li>FATHER: 60 requests/minute</li>
 *   <li>ADMIN: 300 requests/minute</li>
 *   <li>SERVICE: 1000 requests/minute</li>
 * </ul>
 *
 * <p>Uses a token-bucket-like approach with a sliding window tracked per father.</p>
 */
@Component
public class WorkspaceRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRateLimiter.class);

    private static final long WINDOW_MS = 60_000; // 1 minute
    private static final long RETRY_AFTER_SECONDS = 60;

    private final int fatherLimit;
    private final int adminLimit;
    private final int serviceLimit;
    private final boolean enabled;

    private final ConcurrentHashMap<String, AtomicReference<SlidingWindow>> windows =
            new ConcurrentHashMap<>();

    public WorkspaceRateLimiter(
            @Value("${api.rate-limit.limits.FATHER:60}") int fatherLimit,
            @Value("${api.rate-limit.limits.ADMIN:300}") int adminLimit,
            @Value("${api.rate-limit.limits.SERVICE:1000}") int serviceLimit,
            @Value("${api.rate-limit.enabled:true}") boolean enabled) {
        this.fatherLimit = fatherLimit;
        this.adminLimit = adminLimit;
        this.serviceLimit = serviceLimit;
        this.enabled = enabled;
    }

    /**
     * Checks and records a request against the rate limit.
     *
     * @param fatherId  the father's identifier (or service principal ID)
     * @param actorType the type of actor making the request
     * @throws RateLimitExceededException if the limit is exceeded
     */
    public void checkRateLimit(UUID fatherId, ActorType actorType) {
        if (!enabled) {
            return;
        }

        int limit = getLimitForActor(actorType);
        String key = fatherId.toString() + ":" + actorType.name();

        AtomicReference<SlidingWindow> windowRef = windows.computeIfAbsent(
                key, k -> new AtomicReference<>(new SlidingWindow(System.currentTimeMillis(), 0)));

        SlidingWindow current;
        SlidingWindow updated;
        do {
            current = windowRef.get();
            long now = System.currentTimeMillis();

            if (now - current.windowStart() >= WINDOW_MS) {
                // Window expired, start a new one
                updated = new SlidingWindow(now, 1);
            } else if (current.requestCount() >= limit) {
                log.warn("Rate limit exceeded for {} (actor={}): {}/{} requests",
                        fatherId, actorType, current.requestCount(), limit);
                throw new RateLimitExceededException(RETRY_AFTER_SECONDS);
            } else {
                updated = new SlidingWindow(current.windowStart(), current.requestCount() + 1);
            }
        } while (!windowRef.compareAndSet(current, updated));
    }

    /**
     * Returns the remaining requests allowed in the current window.
     *
     * @param fatherId  the father's identifier
     * @param actorType the type of actor
     * @return remaining request count, or the full limit if no window exists
     */
    public int getRemainingRequests(UUID fatherId, ActorType actorType) {
        String key = fatherId.toString() + ":" + actorType.name();
        AtomicReference<SlidingWindow> windowRef = windows.get(key);

        if (windowRef == null) {
            return getLimitForActor(actorType);
        }

        SlidingWindow window = windowRef.get();
        long now = System.currentTimeMillis();

        if (now - window.windowStart() >= WINDOW_MS) {
            return getLimitForActor(actorType);
        }

        return Math.max(0, getLimitForActor(actorType) - window.requestCount());
    }

    private int getLimitForActor(ActorType actorType) {
        return switch (actorType) {
            case FATHER -> fatherLimit;
            case ADMIN -> adminLimit;
            case SERVICE -> serviceLimit;
        };
    }

    /**
     * Actor types for rate limiting.
     */
    public enum ActorType {
        FATHER, ADMIN, SERVICE
    }

    /**
     * Represents a sliding window for rate tracking.
     */
    private record SlidingWindow(long windowStart, int requestCount) {
    }
}
