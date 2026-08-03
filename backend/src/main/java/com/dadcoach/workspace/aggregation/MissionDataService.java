package com.dadcoach.workspace.aggregation;

import com.dadcoach.mission.LegacyMissionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for reading mission data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Mission domain
 * entity and its persistence layer.</p>
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public interface MissionDataService {

    /**
     * Retrieves missions for a father filtered by status.
     *
     * @param fatherId the father's unique identifier
     * @param statuses the set of mission statuses to include
     * @return list of matching mission read models
     */
    List<MissionReadModel> getMissionsByFatherIdAndStatus(UUID fatherId, List<LegacyMissionStatus> statuses);

    /**
     * Retrieves missions associated with a specific child.
     *
     * @param childId the child's unique identifier
     * @return list of mission read models for the child
     */
    List<MissionReadModel> getMissionsByChildId(UUID childId);

    /**
     * Counts completed missions for a specific child.
     *
     * @param childId the child's unique identifier
     * @return the count of completed missions
     */
    int countCompletedMissionsByChildId(UUID childId);

    /**
     * Gets the most recent mission for a child (by creation date).
     *
     * @param childId the child's unique identifier
     * @return the most recent mission, or empty if none
     */
    Optional<MissionReadModel> getMostRecentMissionByChildId(UUID childId);

    /**
     * Gets the active mission for a father (first active by most recent).
     *
     * @param fatherId the father's unique identifier
     * @return the active mission, or empty if none active
     */
    Optional<MissionReadModel> getActiveMission(UUID fatherId);
}
