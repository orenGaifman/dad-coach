package com.dadcoach.ai.routing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks daily per-father token usage with automatic reset at midnight UTC.
 *
 * <p>This class is thread-safe and uses in-memory storage. For production,
 * it would be backed by the {@code ai_daily_summary} table, but the in-memory
 * implementation provides fast reads for budget enforcement decisions.
 */
public class DailyUsageTracker {

    /**
     * Key combining father_id and date for daily tracking.
     */
    private record UsageKey(UUID fatherId, LocalDate date) {}

    private final Map<UsageKey, AtomicInteger> usageMap = new ConcurrentHashMap<>();

    /**
     * Records token usage for a father.
     *
     * @param fatherId    the father's unique identifier
     * @param tokensUsed  the number of tokens consumed (input + output)
     */
    public void recordUsage(UUID fatherId, int tokensUsed) {
        if (tokensUsed < 0) {
            throw new IllegalArgumentException("tokensUsed must be >= 0, was: " + tokensUsed);
        }
        UsageKey key = new UsageKey(fatherId, todayUtc());
        usageMap.computeIfAbsent(key, k -> new AtomicInteger(0))
                .addAndGet(tokensUsed);
    }

    /**
     * Returns the total token usage for a father today (UTC).
     *
     * @param fatherId the father's unique identifier
     * @return total tokens used today, or 0 if no usage recorded
     */
    public int getTodayUsage(UUID fatherId) {
        UsageKey key = new UsageKey(fatherId, todayUtc());
        AtomicInteger usage = usageMap.get(key);
        return usage != null ? usage.get() : 0;
    }

    /**
     * Calculates the percentage of the daily budget consumed.
     *
     * @param fatherId     the father's unique identifier
     * @param dailyBudget  the configured daily token budget
     * @return percentage consumed (0.0 to potentially > 1.0 if over budget)
     */
    public double getUsagePercentage(UUID fatherId, int dailyBudget) {
        if (dailyBudget <= 0) {
            throw new IllegalArgumentException("dailyBudget must be > 0, was: " + dailyBudget);
        }
        int usage = getTodayUsage(fatherId);
        return (double) usage / dailyBudget;
    }

    /**
     * Resets usage for a specific father (primarily for testing).
     *
     * @param fatherId the father's unique identifier
     */
    public void resetUsage(UUID fatherId) {
        UsageKey key = new UsageKey(fatherId, todayUtc());
        usageMap.remove(key);
    }

    /**
     * Cleans up stale entries from previous days to prevent memory leaks.
     * Should be called periodically (e.g., once per hour).
     */
    public void cleanupStaleEntries() {
        LocalDate today = todayUtc();
        usageMap.keySet().removeIf(key -> key.date().isBefore(today));
    }

    private LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
