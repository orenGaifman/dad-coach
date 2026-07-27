package com.dadcoach.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for AI API calls.
 * Enforces a maximum of 20 AI calls per Father per calendar day (Requirement 10.12).
 */
public class AiRateLimiter {

    /**
     * Maximum AI API calls allowed per father per day.
     */
    public static final int MAX_DAILY_CALLS = 20;

    private final Clock clock;

    // Map of fatherId -> (date -> count)
    private final Map<Long, DailyCounter> counters = new ConcurrentHashMap<>();

    public AiRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public AiRateLimiter() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Check if a father can make another AI call today.
     *
     * @param fatherId the father's ID
     * @return true if the father has remaining quota
     */
    public boolean canMakeCall(Long fatherId) {
        LocalDate today = LocalDate.now(clock);
        DailyCounter counter = counters.computeIfAbsent(fatherId, id -> new DailyCounter(today));
        return counter.getCount(today) < MAX_DAILY_CALLS;
    }

    /**
     * Record an AI call for a father.
     *
     * @param fatherId the father's ID
     * @throws AiRateLimitExceededException if the daily limit has been reached
     */
    public void recordCall(Long fatherId) {
        LocalDate today = LocalDate.now(clock);
        DailyCounter counter = counters.computeIfAbsent(fatherId, id -> new DailyCounter(today));
        int currentCount = counter.incrementAndGet(today);
        if (currentCount > MAX_DAILY_CALLS) {
            throw new AiRateLimitExceededException(fatherId, currentCount, MAX_DAILY_CALLS);
        }
    }

    /**
     * Get the current call count for a father today.
     *
     * @param fatherId the father's ID
     * @return the number of AI calls made today
     */
    public int getDailyCount(Long fatherId) {
        LocalDate today = LocalDate.now(clock);
        DailyCounter counter = counters.get(fatherId);
        if (counter == null) {
            return 0;
        }
        return counter.getCount(today);
    }

    /**
     * Get remaining calls available for a father today.
     *
     * @param fatherId the father's ID
     * @return remaining AI calls available
     */
    public int getRemainingCalls(Long fatherId) {
        return Math.max(0, MAX_DAILY_CALLS - getDailyCount(fatherId));
    }

    /**
     * Thread-safe daily counter that resets on a new day.
     */
    private static class DailyCounter {
        private volatile LocalDate date;
        private final AtomicInteger count;

        DailyCounter(LocalDate date) {
            this.date = date;
            this.count = new AtomicInteger(0);
        }

        int getCount(LocalDate today) {
            if (!today.equals(date)) {
                return 0;
            }
            return count.get();
        }

        int incrementAndGet(LocalDate today) {
            synchronized (this) {
                if (!today.equals(date)) {
                    date = today;
                    count.set(0);
                }
                return count.incrementAndGet();
            }
        }
    }
}
