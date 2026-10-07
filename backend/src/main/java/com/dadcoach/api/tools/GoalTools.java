package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.api.error.ValidationException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The weekly goal (hours per Sunday-Saturday week, in the father's timezone). */
public final class GoalTools {

    private GoalTools() {
    }

    /**
     * Create-only and idempotent by meaning: the same target for this week's existing goal succeeds without
     * creating anything; a different one is refused (a goal cannot change mid-week).
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
            if (existing.isPresent()) {
                WeeklyGoal current = existing.get();
                if (current.getTargetHours() != target || current.getStatus() != WeeklyGoalStatus.ACTIVE) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                            "This week's goal already exists (" + current.getTargetHours() + " hours, " + current.getStatus()
                                    + ") and cannot be changed this week; a different target can be set for next week in "
                                    + "Sunday's check-in.");
                }
            }
            WeeklyGoal goal = existing.orElseGet(() -> goals.createAndActivateWeeklyGoal(father.getId(), target));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("goal_id", goal.getId());
            data.put("week_start_date", goal.getWeekStartDate().toString());
            data.put("target_hours", goal.getTargetHours());
            data.put("status", goal.getStatus().name());
            data.put("already_existed", existing.isPresent());
            views.putWeekCoverage(data, father);
            data.put("timezone", views.zone(father).getId());
            return data;
        }
    }
}
