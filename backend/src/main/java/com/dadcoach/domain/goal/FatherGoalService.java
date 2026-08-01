package com.dadcoach.domain.goal;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Service for managing father weekly/monthly quality time goals.
 * This is separate from GoalService which handles parenting goals.
 */
@Service
@Transactional
public class FatherGoalService {

    private static final Logger log = LoggerFactory.getLogger(FatherGoalService.class);

    private final FatherGoalRepository goalRepository;
    private final FatherRepository fatherRepository;

    public FatherGoalService(FatherGoalRepository goalRepository, FatherRepository fatherRepository) {
        this.goalRepository = goalRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Goal Creation ───────────────────────────────────────────────────

    /**
     * Ensures a father has an active weekly goal. Creates one if missing.
     */
    public FatherGoal ensureWeeklyGoal(Long fatherId) {
        Optional<FatherGoal> existing = goalRepository.findCurrentWeeklyGoal(fatherId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        FatherGoal goal = new FatherGoal(
                father,
                FatherGoal.GoalType.WEEKLY,
                weekStart,
                weekEnd,
                father.getWeeklyGoalMinutes()
        );

        log.info("Created weekly goal for father {}: {} minutes", fatherId, father.getWeeklyGoalMinutes());
        return goalRepository.save(goal);
    }

    /**
     * Ensures a father has an active monthly goal. Creates one if missing.
     */
    public FatherGoal ensureMonthlyGoal(Long fatherId) {
        Optional<FatherGoal> existing = goalRepository.findCurrentMonthlyGoal(fatherId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());

        FatherGoal goal = new FatherGoal(
                father,
                FatherGoal.GoalType.MONTHLY,
                monthStart,
                monthEnd,
                father.getMonthlyGoalMinutes()
        );

        log.info("Created monthly goal for father {}: {} minutes", fatherId, father.getMonthlyGoalMinutes());
        return goalRepository.save(goal);
    }

    // ─── Progress Tracking ───────────────────────────────────────────────

    /**
     * Adds quality time minutes to the father's goals.
     * Updates both weekly and monthly goals, and the father's total.
     *
     * @return GoalProgressResult with status of both goals
     */
    public GoalProgressResult addQualityMinutes(Long fatherId, int minutes) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        // Update father's total
        father.setTotalQualityMinutes(father.getTotalQualityMinutes() + minutes);

        // Update weekly goal
        FatherGoal weeklyGoal = ensureWeeklyGoal(fatherId);
        boolean weeklyCompleted = weeklyGoal.addMinutes(minutes);
        goalRepository.save(weeklyGoal);

        // Update monthly goal
        FatherGoal monthlyGoal = ensureMonthlyGoal(fatherId);
        boolean monthlyCompleted = monthlyGoal.addMinutes(minutes);
        goalRepository.save(monthlyGoal);

        // Update streak if weekly goal completed
        if (weeklyCompleted) {
            updateStreak(father);
        }

        fatherRepository.save(father);

        log.info("Added {} minutes for father {}. Weekly: {}/{}, Monthly: {}/{}",
                minutes, fatherId,
                weeklyGoal.getCompletedMinutes(), weeklyGoal.getTargetMinutes(),
                monthlyGoal.getCompletedMinutes(), monthlyGoal.getTargetMinutes());

        return new GoalProgressResult(
                weeklyGoal,
                monthlyGoal,
                weeklyCompleted,
                monthlyCompleted,
                father.getCurrentStreakWeeks()
        );
    }

    /**
     * Gets the current progress for a father.
     */
    @Transactional(readOnly = true)
    public GoalProgressResult getProgress(Long fatherId) {
        FatherGoal weeklyGoal = ensureWeeklyGoal(fatherId);
        FatherGoal monthlyGoal = ensureMonthlyGoal(fatherId);

        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        return new GoalProgressResult(
                weeklyGoal,
                monthlyGoal,
                weeklyGoal.getStatus() == FatherGoal.GoalStatus.COMPLETED,
                monthlyGoal.getStatus() == FatherGoal.GoalStatus.COMPLETED,
                father.getCurrentStreakWeeks()
        );
    }

    // ─── Streak Management ───────────────────────────────────────────────

    private void updateStreak(Father father) {
        int currentStreak = father.getCurrentStreakWeeks() + 1;
        father.setCurrentStreakWeeks(currentStreak);

        if (currentStreak > father.getLongestStreakWeeks()) {
            father.setLongestStreakWeeks(currentStreak);
        }

        log.info("Father {} streak updated to {} weeks", father.getId(), currentStreak);
    }

    /**
     * Resets streak for fathers who missed their weekly goal.
     * Called by scheduler at end of each week.
     */
    public void resetMissedStreaks() {
        LocalDate today = LocalDate.now();
        var endedGoals = goalRepository.findEndedActiveGoals(today);

        for (FatherGoal goal : endedGoals) {
            goal.markMissed();
            goalRepository.save(goal);

            if (goal.getGoalType() == FatherGoal.GoalType.WEEKLY) {
                Father father = goal.getFather();
                father.setCurrentStreakWeeks(0);
                fatherRepository.save(father);
                log.info("Reset streak for father {} - missed weekly goal", father.getId());
            }
        }
    }

    // ─── Result DTO ──────────────────────────────────────────────────────

    public record GoalProgressResult(
            FatherGoal weeklyGoal,
            FatherGoal monthlyGoal,
            boolean weeklyJustCompleted,
            boolean monthlyJustCompleted,
            int currentStreak
    ) {
        public String getWeeklyProgressText(String locale) {
            int completed = weeklyGoal.getCompletedMinutes();
            int target = weeklyGoal.getTargetMinutes();
            int remaining = weeklyGoal.getRemainingMinutes();

            if ("he".equals(locale)) {
                if (weeklyJustCompleted) {
                    return String.format("🏆 יששש! השגת את היעד השבועי! %d/%d דקות", completed, target);
                } else if (remaining > 0) {
                    return String.format("📊 %d/%d דקות השבוע. נשארו %d דקות ליעד!", completed, target, remaining);
                } else {
                    return String.format("✅ היעד השבועי הושג! %d דקות", completed);
                }
            } else {
                if (weeklyJustCompleted) {
                    return String.format("🏆 Yes! Weekly goal achieved! %d/%d minutes", completed, target);
                } else if (remaining > 0) {
                    return String.format("📊 %d/%d minutes this week. %d minutes to go!", completed, target, remaining);
                } else {
                    return String.format("✅ Weekly goal achieved! %d minutes", completed);
                }
            }
        }

        public String getStreakText(String locale) {
            if (currentStreak == 0) return "";
            if ("he".equals(locale)) {
                return String.format("🔥 רצף של %d שבועות!", currentStreak);
            } else {
                return String.format("🔥 %d week streak!", currentStreak);
            }
        }
    }
}
