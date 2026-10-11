package com.dadcoach.weeklygoal;

import com.dadcoach.config.SchedulingLanes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sunday 06:00 UTC (09:00 in Israel): every goal of a finished week is completed (status, streaks). Belts come from
 * completed sessions (D-030), so the week never promotes anyone, and the belt notifier that announced promotions here
 * is retired (D-051, Unified Workflow 5.4): this job sends nothing. Idempotent: a completed goal is never completed again.
 * Product-owned on purpose - belts are Dad Coach's rule, not an AI decision (playbook §28).
 */
@Component
public class WeeklyGoalCompletionJob {

    /** Unified Workflow 5.4 v3: this job's key on the platform's product-job scheduler. */
    public static final String PLATFORM_JOB = "weekly_goals";

    private com.dadcoach.platformjobs.PlatformJobs platformJobs;

    /** Optional: hand-built instances in tests tick as before. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPlatformJobs(com.dadcoach.platformjobs.PlatformJobs platformJobs) {
        this.platformJobs = platformJobs;
    }

    /** The body the platform runs for a slot (exceptions reach the platform, which retries). */
    public Object platformRun() throws Exception {
        return goals.completeWeeklyGoals().size();
    }

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalCompletionJob.class);

    private final WeeklyGoalService goals;

    public WeeklyGoalCompletionJob(WeeklyGoalService goals) {
        this.goals = goals;
    }

    // D-043 lane table: the serial messaging lane, as before (since D-051 it sends nothing; the lane is harmless)
    @Scheduled(cron = "${dadcoach.scheduler.weekly-goal-completion-cron:0 0 6 * * SUN}", zone = "UTC",
            scheduler = SchedulingLanes.MESSAGING)
    public void run() {
        // Unified Workflow 5.4 v3: the tick goes through PlatformJobs (its locks; nothing when the platform owns the job)
        if (platformJobs != null) {
            platformJobs.tick(PLATFORM_JOB);
            return;
        }
        runHere();
    }

    /** The tick's own work, as before 5.4 v3 (PlatformJobs runs it under the job's locks). */
    public void runHere() {
        List<WeeklyGoalService.BeltPromotionResult> promotions = goals.completeWeeklyGoals();
        log.atInfo().setMessage("weekly_goal.completion").addKeyValue("promotions", promotions.size()).log();
    }
}
