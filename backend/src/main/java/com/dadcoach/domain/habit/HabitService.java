package com.dadcoach.domain.habit;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.HabitStatus;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service layer for Habit entity operations: create, record completion, reset streak.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Maximum of 5 active habits per Father (Requirement 16.5)</li>
 *   <li>Streak rules by frequency: DAILY=every day, WEEKLY=within 7 days, BIWEEKLY=within 14 days</li>
 *   <li>Auto-completion at 66 consecutive completions for DAILY frequency</li>
 *   <li>Streak resets if completion deadline is missed</li>
 * </ul>
 */
@Service
@Transactional
public class HabitService {

    private static final int MAX_ACTIVE_HABITS_PER_FATHER = 5;
    private static final int AUTO_COMPLETE_THRESHOLD = 66;

    private final HabitRepository habitRepository;
    private final FatherRepository fatherRepository;

    public HabitService(HabitRepository habitRepository, FatherRepository fatherRepository) {
        this.habitRepository = habitRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Create ──────────────────────────────────────────────────────────

    /**
     * Creates a new Habit for the specified Father.
     *
     * @param fatherId    the ID of the Father
     * @param title       the habit title
     * @param description the habit description (nullable)
     * @param frequency   the habit frequency (DAILY, WEEKLY, BIWEEKLY)
     * @return the persisted Habit entity
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     * @throws BusinessRuleViolationException if the Father already has 5 active habits
     */
    public Habit createHabit(Long fatherId, String title, String description, String frequency) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        long activeHabitCount = habitRepository.countByFatherIdAndStatus(fatherId, HabitStatus.ACTIVE);
        if (activeHabitCount >= MAX_ACTIVE_HABITS_PER_FATHER) {
            throw new BusinessRuleViolationException(
                    "MAX_HABITS_EXCEEDED",
                    "Father already has " + activeHabitCount + " active habits. Maximum allowed is " + MAX_ACTIVE_HABITS_PER_FATHER
            );
        }

        Habit habit = new Habit(father, title, description, frequency);
        return habitRepository.save(habit);
    }

    // ─── Record Completion ───────────────────────────────────────────────

    /**
     * Records a completion for a habit. Increments total_completions, updates streak,
     * and sets last_completed_at. If the streak was broken (deadline missed), resets
     * streak before incrementing. Auto-completes at 66 consecutive completions
     * for DAILY frequency.
     *
     * @param habitId the ID of the Habit
     * @return the updated Habit entity
     * @throws ResourceNotFoundException      if no Habit exists with the given ID
     * @throws BusinessRuleViolationException if the habit is not in ACTIVE status
     */
    public Habit recordCompletion(Long habitId) {
        Habit habit = getHabit(habitId);

        if (habit.getStatus() != HabitStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "HABIT_NOT_ACTIVE",
                    "Habit with ID " + habitId + " is not in ACTIVE status (current: " + habit.getStatus() + ")"
            );
        }

        Instant now = Instant.now();

        // Check if streak should be reset based on frequency and last completion
        if (habit.getLastCompletedAt() != null && isStreakBroken(habit, now)) {
            habit.setCurrentStreak(0);
        }

        // Increment streak and total completions
        habit.setCurrentStreak(habit.getCurrentStreak() + 1);
        habit.setTotalCompletions(habit.getTotalCompletions() + 1);
        habit.setLastCompletedAt(now);

        // Update longest streak if current exceeds it
        if (habit.getCurrentStreak() > habit.getLongestStreak()) {
            habit.setLongestStreak(habit.getCurrentStreak());
        }

        // Auto-complete at 66 consecutive completions for DAILY frequency
        if ("DAILY".equals(habit.getFrequency()) && habit.getCurrentStreak() >= AUTO_COMPLETE_THRESHOLD) {
            habit.transitionTo(HabitStatus.COMPLETED);
        }

        return habitRepository.save(habit);
    }

    // ─── Reset Streak ────────────────────────────────────────────────────

    /**
     * Resets the current streak to 0.
     *
     * @param habitId the ID of the Habit
     * @return the updated Habit entity
     * @throws ResourceNotFoundException if no Habit exists with the given ID
     */
    public Habit resetStreak(Long habitId) {
        Habit habit = getHabit(habitId);
        habit.setCurrentStreak(0);
        return habitRepository.save(habit);
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Retrieves active habits for a father.
     *
     * @param fatherId the Father ID
     * @return list of active habits
     */
    @Transactional(readOnly = true)
    public List<Habit> getActiveHabits(Long fatherId) {
        return habitRepository.findByFatherIdAndStatus(fatherId, HabitStatus.ACTIVE);
    }

    /**
     * Gets a Habit by ID.
     *
     * @param habitId the Habit ID
     * @return the Habit entity
     * @throws ResourceNotFoundException if no Habit exists with the given ID
     */
    @Transactional(readOnly = true)
    public Habit getHabit(Long habitId) {
        return habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
    }

    // ─── Streak Logic ────────────────────────────────────────────────────

    /**
     * Determines if the streak is broken based on the habit's frequency and the
     * time elapsed since the last completion.
     *
     * <p>Streak rules:
     * <ul>
     *   <li>DAILY: must be completed within 48 hours (allows for timezone variance)</li>
     *   <li>WEEKLY: must be completed within 7 days</li>
     *   <li>BIWEEKLY: must be completed within 14 days</li>
     * </ul>
     *
     * @param habit the habit to check
     * @param now   the current time
     * @return true if the streak is broken
     */
    public static boolean isStreakBroken(Habit habit, Instant now) {
        if (habit.getLastCompletedAt() == null) {
            return false;
        }

        Duration elapsed = Duration.between(habit.getLastCompletedAt(), now);
        long elapsedHours = elapsed.toHours();

        return switch (habit.getFrequency()) {
            case "DAILY" -> elapsedHours > 48; // More than 2 days = missed a day
            case "WEEKLY" -> elapsedHours > (7 * 24); // More than 7 days
            case "BIWEEKLY" -> elapsedHours > (14 * 24); // More than 14 days
            default -> false;
        };
    }
}
