package com.dadcoach.web.father;

import com.dadcoach.auth.DashboardProperties;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.weeklyplan.WeeklyPlanContextBuilder;
import com.dadcoach.workflow.Belt;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The father's home. Every figure and every listed session comes from {@link WeeklyPlanContextBuilder#build} - the
 * weekly_plan_context the coach reasons on - so the dashboard and the WhatsApp coach show the same week. Session
 * rows are loaded only to show their child and times.
 */
@Service
public class HomeService {

    private final WeeklyPlanContextBuilder weeklyPlan;
    private final WeeklyGoalService weeklyGoals;
    private final QualityTimeRepository qualityTimes;
    private final ChildRepository children;
    private final DashboardProperties properties;

    public HomeService(WeeklyPlanContextBuilder weeklyPlan, WeeklyGoalService weeklyGoals, QualityTimeRepository qualityTimes,
                       ChildRepository children, DashboardProperties properties) {
        this.weeklyPlan = weeklyPlan;
        this.weeklyGoals = weeklyGoals;
        this.qualityTimes = qualityTimes;
        this.children = children;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public HomeView home(Father father) {
        Map<String, Object> plan = weeklyPlan.build(father);
        ZoneId zone = weeklyGoals.zoneFor(father);

        List<Child> active = children.findByFatherIdAndStatus(father.getId(), "ACTIVE");
        Map<Long, String> childNames = new HashMap<>();
        List<HomeView.ChildName> childList = new ArrayList<>();
        for (Child child : active) {
            childNames.put(child.getId(), child.getName());
            childList.add(new HomeView.ChildName(child.getId(), child.getName()));
        }

        List<WeekTruth.Ref> thisWeek = WeekTruth.refs(plan.get("sessions_this_week"));
        List<WeekTruth.Ref> awaiting = WeekTruth.refs(plan.get("awaiting_confirmation"));
        List<WeekTruth.Ref> next = WeekTruth.refs(plan.get("next_session"));
        Map<UUID, QualityTime> rows = new LinkedHashMap<>();
        List<UUID> ids = new ArrayList<>();
        thisWeek.forEach(r -> ids.add(r.id()));
        awaiting.forEach(r -> ids.add(r.id()));
        next.forEach(r -> ids.add(r.id()));
        qualityTimes.findAllById(ids).forEach(qt -> rows.put(qt.getId(), qt));

        // The home's week list is what happens (or happened) this week: a cancelled session - also the old half of a
        // reschedule - is noise there ("בוטל" next to the same session at its new time); the sessions page keeps it.
        List<WeekTruth.Ref> shownThisWeek = thisWeek.stream()
                .filter(r -> rows.get(r.id()) == null || rows.get(r.id()).getStatus() != QualityTimeStatus.CANCELLED)
                .toList();

        Map<String, Object> week = WeekTruth.map(plan, "current_week");
        Map<String, Object> goal = WeekTruth.map(plan, "goal");
        Map<String, Object> coverage = WeekTruth.map(plan, "coverage");
        Map<String, Object> now = WeekTruth.map(plan, "now");

        return new HomeView(
                father.getDisplayName() == null ? "" : father.getDisplayName(),
                zone.getId(),
                String.valueOf(now.get("local_date")),
                new HomeView.Week(String.valueOf(week.get("week_start")), String.valueOf(week.get("week_end")),
                        orZero(WeekTruth.integer(week, "days_left_including_today"))),
                new HomeView.Goal(Boolean.TRUE.equals(goal.get("exists")), WeekTruth.integer(goal, "target_hours"),
                        WeekTruth.integer(goal, "target_minutes"), WeekTruth.integer(goal, "credited_minutes"),
                        goal.get("status") == null ? null : String.valueOf(goal.get("status"))),
                new HomeView.Coverage(WeekTruth.integer(coverage, "target_minutes"),
                        orZero(WeekTruth.integer(coverage, "completed_minutes")),
                        orZero(WeekTruth.integer(coverage, "planned_minutes")),
                        WeekTruth.integer(coverage, "uncovered_minutes"),
                        coverage.get("is_covered") instanceof Boolean b ? b : null,
                        orZero(WeekTruth.integer(coverage, "awaiting_confirmation_minutes"))),
                next.isEmpty() ? null : view(next.get(0), rows, zone, childNames),
                views(shownThisWeek, rows, zone, childNames),
                views(awaiting, rows, zone, childNames),
                progress(father),
                childList,
                Boolean.TRUE.equals(plan.get("calendar_connected")),
                blankToNull(properties.getWhatsappPublicNumber()));
    }

    static HomeView.Progress progress(Father father) {
        Belt belt = father.getCurrentBelt() == null ? Belt.WHITE : father.getCurrentBelt();
        Belt next = belt.getNextBelt();
        int total = father.getTotalQualityTimesCompleted();
        Integer toNext = next == null ? null : Math.max(0, next.getMinCompletions() - total);
        int percent = next == null ? 100
                : (int) Math.min(100, Math.max(0, Math.round(100.0 * (total - belt.getMinCompletions())
                        / (next.getMinCompletions() - belt.getMinCompletions()))));
        return new HomeView.Progress(belt.name(), next == null ? null : next.name(), total, belt.getMinCompletions(),
                next == null ? null : next.getMinCompletions(), toNext, percent,
                father.getCurrentStreakWeeks() == null ? 0 : father.getCurrentStreakWeeks(), father.getQualityTimeStreak());
    }

    private static List<SessionView> views(List<WeekTruth.Ref> refs, Map<UUID, QualityTime> rows, ZoneId zone,
                                           Map<Long, String> childNames) {
        List<SessionView> views = new ArrayList<>();
        for (WeekTruth.Ref ref : refs) {
            SessionView view = view(ref, rows, zone, childNames);
            if (view != null) {
                views.add(view);
            }
        }
        return views;
    }

    private static SessionView view(WeekTruth.Ref ref, Map<UUID, QualityTime> rows, ZoneId zone, Map<Long, String> childNames) {
        QualityTime qt = rows.get(ref.id());
        return qt == null ? null : SessionPhases.view(qt, ref.phase(), zone, childNames);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
