package com.dadcoach.workspace.aggregation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for reading goal data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Goal domain
 * entity and its persistence layer.</p>
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public interface GoalDataService {

    /**
     * Retrieves all active goals for a father.
     *
     * @param fatherId the father's unique identifier
     * @return list of active goal read models
     */
    List<GoalReadModel> getActiveGoalsByFatherId(UUID fatherId);

    /**
     * Retrieves all goals for a father (all statuses).
     *
     * @param fatherId the father's unique identifier
     * @return list of all goal read models for the father
     */
    List<GoalReadModel> getAllGoalsByFatherId(UUID fatherId);

    /**
     * Retrieves a specific goal by ID.
     *
     * @param goalId the goal's unique identifier
     * @return the goal read model, or empty if not found
     */
    Optional<GoalReadModel> getGoalById(UUID goalId);

    /**
     * Retrieves goals for a specific child.
     *
     * @param childId the child's unique identifier
     * @return list of goal read models for the child
     */
    List<GoalReadModel> getGoalsByChildId(UUID childId);

    /**
     * Counts active goals for a father.
     *
     * @param fatherId the father's unique identifier
     * @return count of active goals
     */
    int countActiveGoalsByFatherId(UUID fatherId);
}
