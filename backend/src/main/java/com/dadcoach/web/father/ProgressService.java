package com.dadcoach.web.father;

import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import com.dadcoach.workflow.Belt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * התקדמות: the belt ladder (the domain's Belt thresholds), achievements derived from facts already stored (nothing new
 * is recorded - an achievement is a statement about his history), and the weekly history from his weekly goals.
 */
@Service
public class ProgressService {

    public record ProgressView(HomeView.Progress progress, List<BeltStep> belts, List<Achievement> achievements,
                               List<WeekRow> weeks) {
    }

    public record BeltStep(String belt, int from, Integer to, boolean reached, boolean current) {
    }

    /** {@code key} names the picture (/achievements/<image>.webp) and the Hebrew text in the SPA. */
    public record Achievement(String key, String image, boolean earned) {
    }

    public record WeekRow(String weekStart, int targetHours, int creditedMinutes, String status, boolean met) {
    }

    private final QualityTimeRepository qualityTimes;
    private final WeeklyGoalRepository weeklyGoals;
    private final ChildRepository children;

    public ProgressService(QualityTimeRepository qualityTimes, WeeklyGoalRepository weeklyGoals, ChildRepository children) {
        this.qualityTimes = qualityTimes;
        this.weeklyGoals = weeklyGoals;
        this.children = children;
    }

    @Transactional(readOnly = true)
    public ProgressView progress(Father father) {
        HomeView.Progress progress = HomeService.progress(father);
        Belt current = Belt.valueOf(progress.belt());
        List<BeltStep> belts = new ArrayList<>();
        for (Belt belt : Belt.values()) {
            belts.add(new BeltStep(belt.name(), belt.getMinCompletions(),
                    belt == Belt.BLACK ? null : belt.getMaxCompletions(), belt.ordinal() <= current.ordinal(), belt == current));
        }

        List<WeeklyGoal> goals = weeklyGoals.findByFatherIdOrderByWeekStartDateDesc(father.getId());
        List<WeekRow> weeks = goals.stream().map(g -> new WeekRow(g.getWeekStartDate().toString(), g.getTargetHours(),
                g.getActualMinutes(), g.getStatus().name(), met(g))).toList();

        List<QualityTime> completed = qualityTimes.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.COMPLETED);
        Set<Long> childrenWithSession = new HashSet<>();
        boolean noted = false;
        for (QualityTime qt : completed) {
            childrenWithSession.addAll(qt.getChildIds());
            noted |= qt.getCompletionNotes() != null && !qt.getCompletionNotes().isBlank();
        }
        List<Long> activeChildren = children.findByFatherIdAndStatus(father.getId(), "ACTIVE").stream()
                .map(c -> c.getId()).toList();
        int total = father.getTotalQualityTimesCompleted();
        int longestWeeks = father.getLongestStreakWeeks() == null ? 0 : father.getLongestStreakWeeks();
        boolean everyChild = !activeChildren.isEmpty() && childrenWithSession.containsAll(activeChildren);

        List<Achievement> achievements = List.of(
                new Achievement("first-session", "first-mission", total >= 1),
                new Achievement("goal-met", "growth-milestone", goals.stream().anyMatch(ProgressService::met)),
                new Achievement("every-child", "playful-dad", everyChild && activeChildren.size() > 1),
                new Achievement("shared-a-moment", "deep-conversation", noted),
                new Achievement("two-weeks", "streak-7-days", longestWeeks >= 2),
                new Achievement("ten-sessions", "quality-time-champion", total >= 10),
                new Achievement("four-weeks", "streak-30-days", longestWeeks >= 4));
        return new ProgressView(progress, belts, achievements, weeks);
    }

    private static boolean met(WeeklyGoal goal) {
        if (goal.getStatus() == WeeklyGoalStatus.COMPLETED) {
            return true;
        }
        if (goal.getStatus() == WeeklyGoalStatus.MISSED || goal.getStatus() == WeeklyGoalStatus.CANCELLED) {
            return false;
        }
        return goal.getActualMinutes() >= goal.getTargetHours() * 60;
    }
}
