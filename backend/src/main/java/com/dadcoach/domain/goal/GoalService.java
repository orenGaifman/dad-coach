package com.dadcoach.domain.goal;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.goal.GoalCategory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service layer for Goal entity operations: create, update progress, complete, archive.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Maximum of 5 active goals per Father (Requirement 16.1)</li>
 *   <li>Progress calculation: min(100, (completed_related_missions / estimated_total_missions) × 100)</li>
 *   <li>Completion sets status to COMPLETED and records completedAt timestamp</li>
 *   <li>Archive sets status to ARCHIVED</li>
 * </ul>
 */
@Service
@Transactional
public class GoalService {

    private static final int MAX_ACTIVE_GOALS_PER_FATHER = 5;

    private final GoalRepository goalRepository;
    private final FatherRepository fatherRepository;

    public GoalService(GoalRepository goalRepository, FatherRepository fatherRepository) {
        this.goalRepository = goalRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Create ──────────────────────────────────────────────────────────

    /**
     * Creates a new Goal for the specified Father.
     *
     * @param fatherId    the ID of the Father
     * @param title       the goal title
     * @param description the goal description (nullable)
     * @param category    the goal category (determines estimated missions)
     * @param priority    the goal priority (1-5)
     * @return the persisted Goal entity
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     * @throws BusinessRuleViolationException if the Father already has 5 active goals
     */
    public Goal createGoal(Long fatherId, String title, String description,
                           GoalCategory category, int priority) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        long activeGoalCount = goalRepository.countActiveByFatherId(fatherId);
        if (activeGoalCount >= MAX_ACTIVE_GOALS_PER_FATHER) {
            throw new BusinessRuleViolationException(
                    "MAX_GOALS_EXCEEDED",
                    "Father already has " + activeGoalCount + " active goals. Maximum allowed is " + MAX_ACTIVE_GOALS_PER_FATHER
            );
        }

        Goal goal = new Goal(father, title, description, category, priority);
        return goalRepository.save(goal);
    }

    // ─── Update Progress ─────────────────────────────────────────────────

    /**
     * Recalculates the progress percentage for a goal.
     * Formula: min(100, (completed_related_missions / estimated_total_missions) × 100)
     *
     * @param goalId the ID of the Goal
     * @return the updated Goal entity
     * @throws ResourceNotFoundException if no Goal exists with the given ID
     */
    public Goal updateProgress(Long goalId) {
        Goal goal = getGoal(goalId);
        goal.recalculateProgress();
        return goalRepository.save(goal);
    }

    // ─── Increment Completed Missions ────────────────────────────────────

    /**
     * Increments the completed_related_missions counter and recalculates progress.
     *
     * @param goalId the ID of the Goal
     * @return the updated Goal entity
     * @throws ResourceNotFoundException if no Goal exists with the given ID
     */
    public Goal incrementCompletedMissions(Long goalId) {
        Goal goal = getGoal(goalId);
        goal.incrementCompletedMissions();
        return goalRepository.save(goal);
    }

    // ─── Complete ────────────────────────────────────────────────────────

    /**
     * Marks a goal as COMPLETED with the current timestamp.
     *
     * @param goalId the ID of the Goal
     * @return the updated Goal entity
     * @throws ResourceNotFoundException      if no Goal exists with the given ID
     * @throws BusinessRuleViolationException if the goal is not in ACTIVE status
     */
    public Goal completeGoal(Long goalId) {
        Goal goal = getGoal(goalId);

        if (!"ACTIVE".equals(goal.getStatus())) {
            throw new BusinessRuleViolationException(
                    "GOAL_NOT_ACTIVE",
                    "Goal with ID " + goalId + " is not in ACTIVE status (current: " + goal.getStatus() + ")"
            );
        }

        goal.setStatus("COMPLETED");
        goal.setProgressPercentage(100);
        goal.setCompletedAt(Instant.now());
        return goalRepository.save(goal);
    }

    // ─── Archive ─────────────────────────────────────────────────────────

    /**
     * Archives a goal by setting status to ARCHIVED.
     *
     * @param goalId the ID of the Goal
     * @return the updated Goal entity
     * @throws ResourceNotFoundException      if no Goal exists with the given ID
     * @throws BusinessRuleViolationException if the goal is already archived
     */
    public Goal archiveGoal(Long goalId) {
        Goal goal = getGoal(goalId);

        if ("ARCHIVED".equals(goal.getStatus())) {
            throw new BusinessRuleViolationException(
                    "GOAL_ALREADY_ARCHIVED",
                    "Goal with ID " + goalId + " is already archived"
            );
        }

        goal.setStatus("ARCHIVED");
        return goalRepository.save(goal);
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Retrieves active goals for a father, ordered by priority (ascending = highest priority first).
     *
     * @param fatherId the Father ID
     * @return list of active goals ordered by priority
     */
    @Transactional(readOnly = true)
    public List<Goal> getActiveGoals(Long fatherId) {
        return goalRepository.findTop5ByFatherIdAndStatusOrderByPriorityAsc(fatherId, "ACTIVE");
    }

    /**
     * Gets a Goal by ID.
     *
     * @param goalId the Goal ID
     * @return the Goal entity
     * @throws ResourceNotFoundException if no Goal exists with the given ID
     */
    @Transactional(readOnly = true)
    public Goal getGoal(Long goalId) {
        return goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
    }
}
