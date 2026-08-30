package com.dadcoach.domain.father;

import com.dadcoach.father.FatherStatus;
import com.dadcoach.workflow.WorkflowState;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Father} entities.
 */
@Repository
public interface FatherRepository extends JpaRepository<Father, Long> {

    /**
     * Find a father by phone number.
     */
    Optional<Father> findByPhone(String phone);

    /**
     * Find all fathers with a given status.
     */
    List<Father> findByStatus(FatherStatus status);

    /**
     * Find all fathers with status ACTIVE whose lastInteractionAt is before the given timestamp.
     * Useful for detecting inactive fathers who may need re-engagement.
     */
    @Query("SELECT f FROM Father f WHERE f.status = 'ACTIVE' AND f.lastInteractionAt < :since")
    List<Father> findInactiveSince(@Param("since") Instant since);

    /**
     * Alternative Spring Data query derivation for finding inactive fathers.
     * Equivalent to {@link #findInactiveSince(Instant)} but uses method-name-based query generation.
     */
    List<Father> findByStatusAndLastInteractionAtBefore(FatherStatus status, Instant since);

    // ─── Workflow Query Methods ──────────────────────────────────────────

    /**
     * Find fathers by workflow state who entered that state before a given timestamp.
     * Used for stale state detection by the scheduler to find fathers who have been
     * stuck in a state for too long (e.g., QUALITY_TIME_FOLLOW_UP for over 24 hours).
     * 
     * Requirements: 12.5 - Stale state detection for fathers stuck in states too long
     * 
     * @param state the current workflow state to filter by
     * @param before the timestamp that workflowStateEnteredAt must be before
     * @return list of fathers matching the criteria
     */
    List<Father> findByCurrentWorkflowStateAndWorkflowStateEnteredAtBefore(
            WorkflowState state, 
            Instant before);

    /**
     * Find fathers by workflow state.
     * Useful for analytics and batch processing of fathers in a specific state.
     * 
     * @param state the workflow state to filter by
     * @return list of fathers in the given workflow state
     */
    List<Father> findByCurrentWorkflowState(WorkflowState state);

    /**
     * Count fathers in a specific workflow state.
     * Useful for monitoring and dashboards.
     * 
     * @param state the workflow state to count
     * @return count of fathers in the state
     */
    long countByCurrentWorkflowState(WorkflowState state);

    /**
     * Find inactive fathers who need an inactivity nudge.
     * 
     * <p>Finds fathers who:
     * <ul>
     *   <li>Have status ACTIVE</li>
     *   <li>Haven't interacted since the given cutoff time</li>
     *   <li>Are NOT already in INACTIVITY_NUDGE state</li>
     *   <li>Are in an "active" workflow state (WAITING, SCHEDULE_QUALITY_TIME, etc.)</li>
     * </ul>
     * </p>
     * 
     * <p>Implements LLD Section 9.1: Inactivity detection for re-engagement.</p>
     * 
     * @param lastInteractionBefore the cutoff timestamp for last interaction
     * @return list of fathers who need an inactivity nudge
     */
    @Query("SELECT f FROM Father f WHERE f.status = 'ACTIVE' " +
           "AND f.lastInteractionAt < :cutoffTime " +
           "AND f.currentWorkflowState != 'INACTIVITY_NUDGE' " +
           "AND f.currentWorkflowState IN ('WAITING', 'SCHEDULE_QUALITY_TIME', 'QUALITY_TIME_FOLLOW_UP', 'SET_WEEKLY_GOAL')")
    List<Father> findInactiveFathersForNudge(@Param("cutoffTime") Instant lastInteractionBefore);

    // ─── Weekly Goal Query Methods ───────────────────────────────────────────

    /**
     * Find active fathers who don't have a weekly goal set for the given week.
     * Used by the Sunday 8am weekly goal prompt scheduler.
     * 
     * <p>Finds fathers who:
     * <ul>
     *   <li>Have status ACTIVE</li>
     *   <li>Don't have an existing weekly goal for the specified week start date</li>
     * </ul>
     * </p>
     * 
     * @param weekStart the start date of the week (typically Sunday or Monday)
     * @return list of active fathers without a weekly goal for that week
     */
    @Query("SELECT f FROM Father f WHERE f.status = 'ACTIVE' " +
           "AND NOT EXISTS (SELECT wg FROM WeeklyGoal wg WHERE wg.fatherId = f.id AND wg.weekStartDate = :weekStart)")
    List<Father> findActiveFathersWithoutWeeklyGoal(@Param("weekStart") java.time.LocalDate weekStart);
}
