package com.dadcoach.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkflowTransition} entities.
 * 
 * Provides methods for querying and persisting workflow state transition logs.
 * The save method is inherited from JpaRepository for transition logging.
 * 
 * Implements Requirement 1.4: "WHEN a state transition occurs, THE Workflow_Engine SHALL
 * log the transition to the state_transition_log table with timestamp, from_state, 
 * to_state, and trigger_reason."
 * 
 * Requirements: 1.4, 16.1
 */
@Repository
public interface WorkflowTransitionLogRepository extends JpaRepository<WorkflowTransition, UUID> {

    /**
     * Find all workflow transitions for a father, ordered by creation time descending.
     * Used for debugging, auditing, and viewing transition history.
     * 
     * Requirements: 16.1
     * 
     * @param fatherId the father's database ID (Long)
     * @return list of transitions ordered by most recent first
     */
    List<WorkflowTransition> findByFatherIdOrderByCreatedAtDesc(Long fatherId);

    /**
     * Find the most recent workflow transition for a father.
     * 
     * @param fatherId the father's database ID (Long)
     * @return the most recent transition, or empty list if none
     */
    List<WorkflowTransition> findTop1ByFatherIdOrderByCreatedAtDesc(Long fatherId);

    /**
     * Find all transitions for a father from a specific state.
     * Useful for analyzing workflow patterns.
     * 
     * @param fatherId the father's database ID (Long)
     * @param fromState the source state to filter by
     * @return list of matching transitions
     */
    List<WorkflowTransition> findByFatherIdAndFromState(Long fatherId, WorkflowState fromState);

    /**
     * Find all transitions for a father to a specific state.
     * Useful for analyzing workflow patterns.
     * 
     * @param fatherId the father's database ID (Long)
     * @param toState the target state to filter by
     * @return list of matching transitions
     */
    List<WorkflowTransition> findByFatherIdAndToState(Long fatherId, WorkflowState toState);
}
