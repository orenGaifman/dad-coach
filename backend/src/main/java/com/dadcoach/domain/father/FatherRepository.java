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
}
