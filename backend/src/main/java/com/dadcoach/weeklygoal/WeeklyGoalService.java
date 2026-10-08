package com.dadcoach.weeklygoal;

import com.dadcoach.common.AppConstants;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.Belt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
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
    private static final ZoneId ISRAEL_ZONE = AppConstants.DEFAULT_ZONE_ID;

    private final WeeklyGoalRepository weeklyGoalRepository;
    private final FatherRepository fatherRepository;
    private final Clock clock;

    public WeeklyGoalService(WeeklyGoalRepository weeklyGoalRepository, FatherRepository fatherRepository, Clock clock) {
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.fatherRepository = fatherRepository;
        this.clock = clock;
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
        WeeklyGoal goal = weeklyGoalRepository.save(newGoalForCurrentWeek(fatherId, targetHours));

        log.info("Created weekly goal for father {}: {} hours, starting belt {}",
                 fatherId, targetHours, goal.getStartingBelt());

        return goal;
    }

    /**
     * Creates the father's goal for his current week and activates it - the lifecycle the
     * {@code set_weekly_goal} tool had in the original in-process engine (createWeeklyGoal, then
     * activateGoal immediately). Built, activated and inserted in a single step inside one
     * transaction, so this path never leaves a goal persisted as PENDING.
     *
     * @throws IllegalStateException if a goal already exists for the father's current week
     */
    @Transactional
    public WeeklyGoal createAndActivateWeeklyGoal(Long fatherId, int targetHours) {
        WeeklyGoal goal = newGoalForCurrentWeek(fatherId, targetHours);
        goal.activate();
        goal = weeklyGoalRepository.save(goal);

        log.info("Created and activated weekly goal {} for father {}: {} hours, week starting {}",
                 goal.getId(), fatherId, targetHours, goal.getWeekStartDate());

        return goal;
    }

    private WeeklyGoal newGoalForCurrentWeek(Long fatherId, int targetHours) {
        Father father = fatherRepository.findById(fatherId)
            .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        LocalDate weekStart = weekStartFor(father, clock.instant());

        // Check if goal already exists for this week
        if (weeklyGoalRepository.findByFatherIdAndWeekStartDate(fatherId, weekStart).isPresent()) {
            throw new IllegalStateException("A weekly goal already exists for week starting " + weekStart);
        }

        // Get current belt from father's metrics or default to WHITE
        Belt currentBelt = getCurrentBelt(father);

        return new WeeklyGoal(father, weekStart, targetHours, currentBelt);
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
     * Records completed quality time minutes against the goal of the week the session belongs to
     * (the father-local Sunday-Saturday week containing its scheduled start), only if that goal
     * is ACTIVE. A session is never credited to a different week's goal, and a week that was
     * already finalized is not modified.
     *
     * @param fatherId the father's database ID
     * @param sessionStart the session's scheduled start (null = now)
     * @param minutes the minutes of quality time completed
     */
    @Transactional
    public void recordCompletedQualityTime(Long fatherId, Instant sessionStart, int minutes) {
        Father father = fatherRepository.findById(fatherId).orElse(null);
        if (father == null) {
            log.warn("Father {} not found when recording {} completed minutes", fatherId, minutes);
            return;
        }

        LocalDate sessionWeek = weekStartFor(father, sessionStart != null ? sessionStart : clock.instant());
        Optional<WeeklyGoal> weekGoal = weeklyGoalRepository.findByFatherIdAndWeekStartDate(fatherId, sessionWeek)
            .filter(goal -> goal.getStatus() == WeeklyGoalStatus.ACTIVE);

        if (weekGoal.isPresent()) {
            WeeklyGoal goal = weekGoal.get();
            goal.addCompletedMinutes(minutes);
            weeklyGoalRepository.save(goal);

            log.info("Recorded {} minutes for father {} to week {}: now {} minutes (target: {} hours)",
                     minutes, fatherId, sessionWeek, goal.getActualMinutes(), goal.getTargetHours());
        } else {
            log.warn("No active weekly goal for father {} in the session's week {} when recording {} minutes",
                     fatherId, sessionWeek, minutes);
        }
    }

    /** Takes back what {@link #recordCompletedQualityTime} added, for a session that turned out not to happen. */
    @Transactional
    public void undoCompletedQualityTime(Long fatherId, Instant sessionStart, int minutes) {
        Father father = fatherRepository.findById(fatherId).orElse(null);
        if (father == null) {
            return;
        }
        LocalDate sessionWeek = weekStartFor(father, sessionStart != null ? sessionStart : clock.instant());
        weeklyGoalRepository.findByFatherIdAndWeekStartDate(fatherId, sessionWeek)
            .filter(goal -> goal.getStatus() == WeeklyGoalStatus.ACTIVE)
            .ifPresent(goal -> {
                goal.removeCompletedMinutes(minutes);
                weeklyGoalRepository.save(goal);
            });
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
        boolean goalMet = goal.isGoalMet();
        boolean promoted = goal.complete();
        weeklyGoalRepository.save(goal);

        Father father = goal.getFather();
        
        // Update streak
        updateStreak(father, goalMet);

        if (promoted) {
            // Update father's belt
            updateFatherBelt(father, goal.getEndingBelt());

            log.info("Father {} promoted from {} to {} (goal met: {} of {} hours, streak: {} weeks)", 
                     goal.getFatherId(), goal.getStartingBelt(), goal.getEndingBelt(),
                     goal.getActualHours(), goal.getTargetHours(), father.getCurrentStreakWeeks());

            return new BeltPromotionResult(
                goal.getFatherId(),
                true,
                goal.getStartingBelt(),
                goal.getEndingBelt(),
                goal.getActualMinutes(),
                goal.getTargetHours() * 60,
                father.getCurrentStreakWeeks(),
                hasProgramCompleted(father)
            );
        }

        log.info("Father {} did not meet goal: {} of {} hours (belt unchanged: {}, streak reset)", 
                 goal.getFatherId(), goal.getActualHours(), goal.getTargetHours(), goal.getStartingBelt());

        return new BeltPromotionResult(
            goal.getFatherId(),
            false,
            goal.getStartingBelt(),
            goal.getStartingBelt(),
            goal.getActualMinutes(),
            goal.getTargetHours() * 60,
            0,
            false
        );
    }

    // ─── Queries ────────────────────────────────────────────────────────────────

    /**
     * Gets the father's ACTIVE goal for his current week.
     *
     * <p>Looked up by (father, current week) - unique - rather than "the father's ACTIVE goal": across
     * a Sunday boundary the previous week's goal stays ACTIVE until the weekly completion job
     * finalizes it, and it must neither be reported as this week's goal nor make the lookup
     * ambiguous.</p>
     */
    public Optional<WeeklyGoal> getActiveGoal(Long fatherId) {
        return getCurrentWeekGoal(fatherId)
            .filter(goal -> goal.getStatus() == WeeklyGoalStatus.ACTIVE);
    }

    /**
     * Gets the current week's goal (any status) for a father.
     */
    public Optional<WeeklyGoal> getCurrentWeekGoal(Long fatherId) {
        return fatherRepository.findById(fatherId)
            .flatMap(father -> weeklyGoalRepository.findByFatherIdAndWeekStartDate(
                fatherId, weekStartFor(father, clock.instant())));
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
     * Returns the start of the current week (Sunday) in the default (Israel) zone. Used by the
     * weekly scheduler jobs; per-father goal logic uses {@link #weekStartFor(Father, Instant)}.
     */
    public LocalDate getCurrentWeekStart() {
        return LocalDate.ofInstant(clock.instant(), ISRAEL_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * The Sunday that starts the father's Sunday-Saturday week containing {@code instant}, in the
     * father's own timezone ({@link Father#getTimezone()}, falling back to the default zone when
     * unset or invalid). This is the single week definition for creating, finding and crediting
     * goals; weekly_goal.week_start_date holds this date.
     */
    public LocalDate weekStartFor(Father father, Instant instant) {
        return instant.atZone(zoneOf(father)).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * The father's own timezone, as used for his Sunday-Saturday week (falls back to the default zone
     * when unset or invalid).
     */
    public ZoneId zoneFor(Father father) {
        return zoneOf(father);
    }

    private static ZoneId zoneOf(Father father) {
        String timezone = father.getTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (Exception e) {
                log.warn("Invalid timezone '{}' for father {}, using default", timezone, father.getId());
            }
        }
        return ISRAEL_ZONE;
    }

    /**
     * Gets the current belt for a father.
     */
    private Belt getCurrentBelt(Father father) {
        return father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
    }

    /**
     * Updates the father's belt level and streak stats.
     */
    private void updateFatherBelt(Father father, Belt newBelt) {
        father.setCurrentBelt(newBelt);
        fatherRepository.save(father);
        log.info("Updated father {} belt to {}", father.getId(), newBelt);
    }

    /**
     * Updates the father's streak when they complete (or miss) a weekly goal.
     * 
     * @param father the father entity
     * @param goalMet true if the weekly goal was met
     */
    @Transactional
    public void updateStreak(Father father, boolean goalMet) {
        if (goalMet) {
            // Increment streak
            int newStreak = father.getCurrentStreakWeeks() + 1;
            father.setCurrentStreakWeeks(newStreak);
            
            // Update longest streak if needed
            if (newStreak > father.getLongestStreakWeeks()) {
                father.setLongestStreakWeeks(newStreak);
            }
            
            log.info("Father {} streak increased to {} weeks (longest: {})", 
                     father.getId(), newStreak, father.getLongestStreakWeeks());
        } else {
            // Reset streak on miss
            int previousStreak = father.getCurrentStreakWeeks();
            father.setCurrentStreakWeeks(0);
            
            log.info("Father {} streak reset from {} to 0 (goal missed)", 
                     father.getId(), previousStreak);
        }
        
        fatherRepository.save(father);
    }

    /**
     * Checks if the father has completed the 7-week program (reached BLACK belt).
     */
    public boolean hasProgramCompleted(Father father) {
        return father.getCurrentBelt() == Belt.BLACK;
    }

    /**
     * Gets the number of weeks until BLACK belt (program completion).
     */
    public int getWeeksUntilBlackBelt(Father father) {
        Belt current = father.getCurrentBelt();
        int weeksRemaining = 0;
        Belt belt = current;
        while (belt != null && belt != Belt.BLACK) {
            belt = belt.getNextBelt();
            weeksRemaining++;
        }
        return weeksRemaining;
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
        int targetMinutes,
        int currentStreak,
        boolean programCompleted
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
