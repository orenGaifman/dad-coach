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

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalCompletionJob.class);

    private final WeeklyGoalService goals;

    public WeeklyGoalCompletionJob(WeeklyGoalService goals) {
        this.goals = goals;
    }

    // D-043 lane table: the serial messaging lane, as before (since D-051 it sends nothing; the lane is harmless)
    @Scheduled(cron = "${dadcoach.scheduler.weekly-goal-completion-cron:0 0 6 * * SUN}", zone = "UTC",
            scheduler = SchedulingLanes.MESSAGING)
    public void run() {
        List<WeeklyGoalService.BeltPromotionResult> promotions = goals.completeWeeklyGoals();
        log.atInfo().setMessage("weekly_goal.completion").addKeyValue("promotions", promotions.size()).log();
    }
}
