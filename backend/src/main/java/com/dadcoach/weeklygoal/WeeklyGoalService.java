package com.dadcoach.weeklygoal;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.Belt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing weekly quality time goals.
 * 
 * <p>Handles the weekly goal lifecycle:</p>
 * <ol>
 *   <li>Creating new weekly goals</li>
 *   <li>Tracking progress (actual minutes vs target)</li>
 *   <li>Completing goals and determining belt promotion</li>
 *   <li>Generating weekly summaries</li>
 * </ol>
 */
@Service
public class WeeklyGoalService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalService.class);
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");

    private final WeeklyGoalRepository weeklyGoalRepository;
    private final FatherRepository fatherRepository;

    public WeeklyGoalService(WeeklyGoalRepository weeklyGoalRepository, FatherRepository fatherRepository) {
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Goal Creation ──────────────────────────────────────────────────────────

    /**
     * Creates a new weekly goal for a father.
     * 
     * @param fatherId the father's database ID
     * @param targetHours the target hours for the week (minimum 1)
     * @return the created WeeklyGoal
     * @throws IllegalStateException if a goal already exists for the current week
     */
    @Transactional
    public WeeklyGoal createWeeklyGoal(Long fatherId, int targetHours) {
        Father father = fatherRepository.findById(fatherId)
            .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        LocalDate weekStart = getCurrentWeekStart();
        
        // Check if goal already exists for this week
        if (weeklyGoalRepository.findByFatherIdAndWeekStartDate(fatherId, weekStart).isPresent()) {
            throw new IllegalStateException("A weekly goal already exists for week starting " + weekStart);
        }

        // Get current belt from father's metrics or default to WHITE
        Belt currentBelt = getCurrentBelt(father);

        WeeklyGoal goal = new WeeklyGoal(father, weekStart, targetHours, currentBelt);
        goal = weeklyGoalRepository.save(goal);

        log.info("Created weekly goal for father {}: {} hours, starting belt {}", 
                 fatherId, targetHours, currentBelt);

        return goal;
    }

    /**
     * Activates a pending weekly goal (called after scheduling is complete).
     */
    @Transactional
    public WeeklyGoal activateGoal(Long goalId) {
        WeeklyGoal goal = weeklyGoalRepository.findById(goalId)
            .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (goal.getStatus() != WeeklyGoalStatus.PENDING) {
            throw new IllegalStateException("Goal is not in PENDING status: " + goal.getStatus());
        }

        goal.activate();
        goal = weeklyGoalRepository.save(goal);

        log.info("Activated weekly goal {} for father {}", goalId, goal.getFatherId());
        return goal;
    }

    // ─── Progress Tracking ──────────────────────────────────────────────────────

    /**
     * Records completed quality time minutes for the current week's goal.
     * 
     * @param fatherId the father's database ID
     * @param minutes the minutes of quality time completed
     */
    @Transactional
    public void recordCompletedQualityTime(Long fatherId, int minutes) {
        Optional<WeeklyGoal> activeGoal = getActiveGoal(fatherId);
        
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            goal.addCompletedMinutes(minutes);
            weeklyGoalRepository.save(goal);

            log.info("Recorded {} minutes for father {}: now {} minutes (target: {} hours)", 
                     minutes, fatherId, goal.getActualMinutes(), goal.getTargetHours());
        } else {
            log.warn("No active weekly goal found for father {} when recording {} minutes", 
                     fatherId, minutes);
        }
    }

    /**
     * Increments the scheduled count when a quality time is scheduled.
     */
    @Transactional
    public void incrementScheduledCount(Long fatherId) {
        getActiveGoal(fatherId).ifPresent(goal -> {
            goal.incrementScheduled();
            weeklyGoalRepository.save(goal);
        });
    }

    /**
     * Decrements the scheduled count when a quality time is cancelled.
     */
    @Transactional
    public void decrementScheduledCount(Long fatherId) {
        getActiveGoal(fatherId).ifPresent(goal -> {
            goal.decrementScheduled();
            weeklyGoalRepository.save(goal);
        });
    }

    // ─── Goal Completion ────────────────────────────────────────────────────────

    /**
     * Completes all active goals for the past week.
     * Called by the weekly scheduler.
     * 
     * @return list of fathers who were promoted to the next belt
     */
    @Transactional
    public List<BeltPromotionResult> completeWeeklyGoals() {
        LocalDate currentWeekStart = getCurrentWeekStart();
        List<WeeklyGoal> goalsToComplete = weeklyGoalRepository.findGoalsToComplete(currentWeekStart);

        log.info("Completing {} weekly goals for weeks before {}", goalsToComplete.size(), currentWeekStart);

        return goalsToComplete.stream()
            .map(this::completeGoal)
            .filter(BeltPromotionResult::promoted)
            .toList();
    }

    /**
     * Completes a single weekly goal and determines belt promotion.
     */
    @Transactional
    public BeltPromotionResult completeGoal(WeeklyGoal goal) {
        boolean promoted = goal.complete();
        weeklyGoalRepository.save(goal);

        if (promoted) {
            // Update father's belt
            Father father = goal.getFather();
            updateFatherBelt(father, goal.getEndingBelt());

            log.info("Father {} promoted from {} to {} (goal met: {} of {} hours)", 
                     goal.getFatherId(), goal.getStartingBelt(), goal.getEndingBelt(),
                     goal.getActualHours(), goal.getTargetHours());

            return new BeltPromotionResult(
                goal.getFatherId(),
                true,
                goal.getStartingBelt(),
                goal.getEndingBelt(),
                goal.getActualMinutes(),
                goal.getTargetHours() * 60
            );
        }

        log.info("Father {} did not meet goal: {} of {} hours (belt unchanged: {})", 
                 goal.getFatherId(), goal.getActualHours(), goal.getTargetHours(), goal.getStartingBelt());

        return new BeltPromotionResult(
            goal.getFatherId(),
            false,
            goal.getStartingBelt(),
            goal.getStartingBelt(),
            goal.getActualMinutes(),
            goal.getTargetHours() * 60
        );
    }

    // ─── Queries ────────────────────────────────────────────────────────────────

    /**
     * Gets the active weekly goal for a father.
     */
    public Optional<WeeklyGoal> getActiveGoal(Long fatherId) {
        return weeklyGoalRepository.findByFatherIdAndStatus(fatherId, WeeklyGoalStatus.ACTIVE);
    }

    /**
     * Gets the current week's goal (any status) for a father.
     */
    public Optional<WeeklyGoal> getCurrentWeekGoal(Long fatherId) {
        LocalDate weekStart = getCurrentWeekStart();
        return weeklyGoalRepository.findByFatherIdAndWeekStartDate(fatherId, weekStart);
    }

    /**
     * Gets the last week's goal for generating the weekly summary.
     */
    public Optional<WeeklyGoal> getLastWeekGoal(Long fatherId) {
        return weeklyGoalRepository.findLastCompletedOrMissedGoal(fatherId);
    }

    /**
     * Gets recent goals for a father (for dashboard display).
     */
    public List<WeeklyGoal> getRecentGoals(Long fatherId, int limit) {
        return weeklyGoalRepository.findRecentGoals(fatherId, limit);
    }

    /**
     * Generates a weekly summary for a father.
     */
    public WeeklySummary generateWeeklySummary(Long fatherId) {
        Optional<WeeklyGoal> lastGoal = getLastWeekGoal(fatherId);
        
        if (lastGoal.isEmpty()) {
            // First time user - no previous goal
            return new WeeklySummary(
                false,
                0,
                0,
                0,
                0,
                false,
                null,
                null,
                0
            );
        }

        WeeklyGoal goal = lastGoal.get();
        int consecutiveWeeks = weeklyGoalRepository.countConsecutiveCompletedWeeks(
            fatherId, 
            goal.getWeekStartDate()
        );

        return new WeeklySummary(
            true,
            goal.getTargetHours(),
            (int) Math.round(goal.getActualHours()),
            goal.getCompletedCount(),
            goal.getScheduledCount(),
            goal.isGoalMet(),
            goal.getStartingBelt(),
            goal.getEndingBelt(),
            consecutiveWeeks
        );
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────────

    /**
     * Returns the start of the current week (Sunday).
     */
    public LocalDate getCurrentWeekStart() {
        return LocalDate.now(ISRAEL_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * Gets the current belt for a father.
     */
    private Belt getCurrentBelt(Father father) {
        // This would typically come from the father's dashboard metrics
        // For now, default to WHITE if not set
        // TODO: Integrate with MetricsService to get actual belt
        return Belt.WHITE;
    }

    /**
     * Updates the father's belt level.
     */
    private void updateFatherBelt(Father father, Belt newBelt) {
        // TODO: Update father entity with new belt
        // This would update a belt field on the Father entity or in metrics
        log.info("Updating father {} belt to {}", father.getId(), newBelt);
    }

    // ─── Result Records ─────────────────────────────────────────────────────────

    /**
     * Result of completing a weekly goal, including belt promotion info.
     */
    public record BeltPromotionResult(
        Long fatherId,
        boolean promoted,
        Belt previousBelt,
        Belt newBelt,
        int actualMinutes,
        int targetMinutes
    ) {}

    /**
     * Weekly summary for display to the father.
     */
    public record WeeklySummary(
        boolean hasPreviousGoal,
        int targetHours,
        int actualHours,
        int completedCount,
        int scheduledCount,
        boolean goalMet,
        Belt startingBelt,
        Belt endingBelt,
        int consecutiveWeeks
    ) {
        public boolean wasPromoted() {
            return endingBelt != null && startingBelt != null && endingBelt != startingBelt;
        }
    }
}
