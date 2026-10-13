package com.dadcoach.platformjobs;

import com.dadcoach.integration.platform.SessionTimersOwner;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.weeklygoal.WeeklyGoalCompletionJob;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Unified Workflow 5.4 v3 (PHASE5_4_SPEC.md §v3.4, J-9): Dad Coach's scheduled jobs on the platform's product-job
 * scheduler. The weekly completion keeps its Sunday 06:00 UTC slot (review: an hourly run would complete a week right after
 * Israel's Sunday 00:00 - sessions logged late Saturday would miss it, and other zones' weeks would end mid-week); a failed
 * run is retried by the platform's backoff, not a week later. Not here: the Send API reconciles (rewritten by the 6.4 cleanup first), the template
 * refresh and receipt retry (removed by it) and the idempotency purge (per instance / in-process).
 */
// eager even with spring.main.lazy-initialization=true (the Dockerfile sets it): a lazy one never registers its jobs
@org.springframework.context.annotation.Lazy(false)
@Component
public class DadCoachPlatformJobs {

    public DadCoachPlatformJobs(PlatformJobs jobs, WeeklyGoalCompletionJob weeklyGoals, SessionTimersOwner sessionTimers,
                                PlatformPersonDeletions personDeletions,
                                @org.springframework.beans.factory.annotation.Value("${dadcoach.scheduler.weekly-goal-completion-cron:0 0 6 * * SUN}") String weeklyCron) {
        // the tick's own cron (one property, never two schedules); "-" (switched off, as in the tests) registers nothing
        if (!"-".equals(weeklyCron)) {
            registerWeekly(jobs, weeklyGoals, weeklyCron);
        }
        registerOthers(jobs, sessionTimers, personDeletions);
    }

    private static void registerWeekly(PlatformJobs jobs, WeeklyGoalCompletionJob weeklyGoals, String cron) {
        jobs.register(PlatformJobs.Job.cron(WeeklyGoalCompletionJob.PLATFORM_JOB, cron, "UTC", Duration.ofMinutes(5), false,
                "weekly goals and streaks of finished weeks", weeklyGoals::platformRun));
    }

    private static void registerOthers(PlatformJobs jobs, SessionTimersOwner sessionTimers, PlatformPersonDeletions personDeletions) {
        // D-055: no tick left - the platform is their one scheduler
        jobs.register(PlatformJobs.Job.every(SessionTimersOwner.PLATFORM_JOB, Duration.ofMinutes(10), Duration.ofMinutes(5), false,
                "session timer reconcile (D-050)", sessionTimers::platformRun).asPlatformOnly());
        jobs.register(PlatformJobs.Job.every(PlatformPersonDeletions.PLATFORM_JOB, Duration.ofMinutes(5), Duration.ofMinutes(5), false,
                "person deletions to the platform", personDeletions::platformRun).asPlatformOnly());
        QaProbe probe = new QaProbe();
        jobs.registerProbe(probe);
        jobs.register(PlatformJobs.Job.every(QaProbe.KEY, Duration.ofMinutes(5), Duration.ofMinutes(5), false,
                "QA probe: no business effect (controlled scheduler checks)", probe::run));
    }
}
