package com.dadcoach.weeklygoal;

import com.dadcoach.config.SchedulingLanes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sunday 06:00 UTC (09:00 in Israel): every goal of a finished week is completed (belts, streaks) and each father
 * who earned a belt is told. Idempotent: a completed goal is never completed again, so a rerun sends nothing new.
 * Product-owned on purpose - belts are Dad Coach's rule, not an AI decision (playbook §28).
 */
@Component
public class WeeklyGoalCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalCompletionJob.class);

    private final WeeklyGoalService goals;
    private final BeltPromotionNotifier notifier;

    public WeeklyGoalCompletionJob(WeeklyGoalService goals, BeltPromotionNotifier notifier) {
        this.goals = goals;
        this.notifier = notifier;
    }

    // D-043: it sends WhatsApp (belt image + text), so it stays on the serial messaging lane
    @Scheduled(cron = "${dadcoach.scheduler.weekly-goal-completion-cron:0 0 6 * * SUN}", zone = "UTC",
            scheduler = SchedulingLanes.MESSAGING)
    public void run() {
        List<WeeklyGoalService.BeltPromotionResult> promotions = goals.completeWeeklyGoals();
        log.atInfo().setMessage("weekly_goal.completion").addKeyValue("promotions", promotions.size()).log();
        notifier.sendBatchPromotionNotifications(promotions);
    }
}
