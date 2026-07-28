package com.dadcoach.workspace.growth.streak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that resets expired father streaks daily.
 *
 * <p>Runs at 00:30 UTC every day. A streak is considered expired if the father's
 * last qualifying interaction date is before yesterday in their configured timezone,
 * meaning they missed at least one full calendar day without a qualifying interaction.</p>
 *
 * <p><strong>Idempotency:</strong> This operation IS idempotent. Resetting an already-reset
 * streak (current_streak_days == 0) is a no-op — the inner loop skips such records.
 * Running this job multiple times on the same day produces the same result.
 * Safe for multi-instance deployments without distributed locking.</p>
 *
 * @see StreakService#resetExpiredStreaks()
 */
@Component
public class StreakResetJob {

    private static final Logger log = LoggerFactory.getLogger(StreakResetJob.class);

    private final StreakService streakService;

    public StreakResetJob(StreakService streakService) {
        this.streakService = streakService;
    }

    /**
     * Executes the daily streak reset check at 00:30 UTC.
     *
     * <p>Finds all streaks where the last qualifying date is before yesterday
     * and resets them to zero. This ensures fathers who miss a day lose their
     * current streak (but retain their longest_streak_days record).</p>
     */
    @Scheduled(cron = "0 30 0 * * *")
    public void resetExpiredStreaks() {
        log.info("Starting daily streak reset job");

        long startTime = System.currentTimeMillis();
        streakService.resetExpiredStreaks();
        long duration = System.currentTimeMillis() - startTime;

        log.info("Daily streak reset job completed in {}ms", duration);
    }
}
