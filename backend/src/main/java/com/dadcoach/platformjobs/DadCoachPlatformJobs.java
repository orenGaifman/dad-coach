package com.dadcoach.platformjobs;

import com.dadcoach.integration.platform.SessionTimersOwner;
import com.dadcoach.integration.platform.lifecycle.PlatformPersonDeletions;
import com.dadcoach.weeklygoal.WeeklyGoalCompletionJob;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Unified Workflow 5.4 v3 (PHASE5_4_SPEC.md §v3.4, J-9): Dad Coach's scheduled jobs on the platform's product-job
 * scheduler. The weekly completion runs hourly there (a scan of finished weeks - idempotent; a failed run is caught an
 * hour later, not a week later). Not here: the Send API reconciles (rewritten by the 6.4 cleanup first), the template
 * refresh and receipt retry (removed by it), keep-warm and the idempotency purge (per instance / in-process).
 */
@Component
public class DadCoachPlatformJobs {

    public DadCoachPlatformJobs(PlatformJobs jobs, WeeklyGoalCompletionJob weeklyGoals, SessionTimersOwner sessionTimers,
                                PlatformPersonDeletions personDeletions) {
        jobs.register(PlatformJobs.Job.every(WeeklyGoalCompletionJob.PLATFORM_JOB, Duration.ofHours(1), Duration.ofMinutes(5), false,
                "weekly goals and streaks of finished weeks", weeklyGoals::platformRun));
        jobs.register(PlatformJobs.Job.every(SessionTimersOwner.PLATFORM_JOB, Duration.ofMinutes(10), Duration.ofMinutes(5), false,
                "session timer reconcile (D-050)", sessionTimers::platformRun));
        jobs.register(PlatformJobs.Job.every(PlatformPersonDeletions.PLATFORM_JOB, Duration.ofMinutes(5), Duration.ofMinutes(5), false,
                "person deletions to the platform", personDeletions::platformRun));
        QaProbe probe = new QaProbe();
        jobs.registerProbe(probe);
        jobs.register(PlatformJobs.Job.every(QaProbe.KEY, Duration.ofMinutes(5), Duration.ofMinutes(5), false,
                "QA probe: no business effect (controlled scheduler checks)", probe::run));
    }
}
