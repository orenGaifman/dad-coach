package com.dadcoach.api.tools;

import com.dadcoach.api.error.ValidationException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.replies.CoachReplies;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The weekly goal (hours per Sunday-Saturday week, in the father's timezone). */
public final class GoalTools {

    static final String NEXT_WEEK_NOTE = "This week's goal did not change (it never changes mid-week). The new number was "
            + "SAVED for next week and Sunday's check-in starts from it. If this turn did not just set this week's goal, "
            + "say this_week_line first, then reply; otherwise only reply.";

    private GoalTools() {
    }

    /**
     * Create-only and idempotent by meaning: the same target for this week's existing goal succeeds without
     * creating anything; a different one never changes this week's goal - it is kept as next week's number (D-036).
     */
    @Component
    public static class SetWeeklyGoal implements ToolHandler {
        private final WeeklyGoalService goals;
        private final SessionViews views;

        public SetWeeklyGoal(WeeklyGoalService goals, SessionViews views) {
            this.goals = goals;
            this.views = views;
        }

        public String toolKey() { return "set_weekly_goal"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            Father father = actor.requireFather();
            Integer target = p.intValue("target_hours");
            if (target == null || target < 1 || target > 40) {
                throw new ValidationException("target_hours must be between 1 and 40");
            }
            Optional<WeeklyGoal> existing = goals.getCurrentWeekGoal(father.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            if (existing.isPresent() && existing.get().getTargetHours() != target) {
                // D-036 (D-4): this week's goal never changes; the new number is kept for next week and offered at
                // Sunday's check-in - so "נתחיל מ-3 שעות" is true, not a promise nothing keeps
                WeeklyGoal current = existing.get();
                current.setNextWeekTargetHours(target);
                goals.save(current);
                data.put("goal_id", current.getId());
                data.put("week_start_date", current.getWeekStartDate().toString());
                data.put("target_hours", current.getTargetHours());
                data.put("status", current.getStatus().name());
                data.put("already_existed", true);
                data.put("next_week_target_hours", target);
                data.put("this_week_line", CoachReplies.goalStays(current.getTargetHours()));
                data.put("reply", CoachReplies.nextWeekGoal(target));
                data.put("note", NEXT_WEEK_NOTE);
                views.putWeekCoverage(data, father);
                data.put("timezone", views.zone(father).getId());
                return data;
            }
            WeeklyGoal goal = existing.orElseGet(() -> goals.createAndActivateWeeklyGoal(father.getId(), target));
            data.put("goal_id", goal.getId());
            data.put("week_start_date", goal.getWeekStartDate().toString());
            data.put("target_hours", goal.getTargetHours());
            data.put("status", goal.getStatus().name());
            data.put("already_existed", existing.isPresent());
            data.put("reply", existing.isPresent() ? CoachReplies.goalAlreadySet(target) : CoachReplies.goalSet(target));
            views.putWeekCoverage(data, father);
            data.put("timezone", views.zone(father).getId());
            return data;
        }
    }
}
