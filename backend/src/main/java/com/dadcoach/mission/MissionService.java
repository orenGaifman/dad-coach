package com.dadcoach.mission;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract service interface for mission operations.
 * 
 * Each {@link MissionType} has a corresponding implementation of this interface.
 * The workflow engine uses this abstraction to interact with missions, making it
 * easy to add new mission types in the future without changing core workflow logic.
 * 
 * <p><strong>MVP Implementation:</strong></p>
 * For MVP, only {@link QualityTimeMissionService} is implemented, handling
 * {@link MissionType#QUALITY_TIME} missions that are backed by Google Calendar.
 * 
 * <p><strong>Architecture Extensibility:</strong></p>
 * Future mission types (READING_TOGETHER, OUTDOOR_ACTIVITY, etc.) will each have
 * their own MissionService implementation, obtained through the {@code MissionServiceFactory}.
 * 
 * <p><strong>Note:</strong> This interface is part of the new deterministic workflow engine
 * and is separate from the legacy {@code com.dadcoach.domain.mission.MissionService} class.</p>
 * 
 * Requirements: 1.1 (Mission abstraction)
 * 
 * @see Mission
 * @see MissionType
 * @see MissionServiceFactory
 */
public interface MissionService {

    /**
     * Schedules a new mission for a father with a specific child.
     * 
     * <p>The implementation is responsible for creating the mission record and any
     * external integrations (e.g., Google Calendar for Quality Time missions).</p>
     * 
     * @param fatherId  the ID of the father scheduling the mission
     * @param childId   the ID of the child the mission is with
     * @param startTime the scheduled start time
     * @param duration  the duration of the mission
     * @return the created Mission
     * @throws IllegalArgumentException if the father or child is not found
     * @throws IllegalStateException    if there is a conflict or constraint violation
     */
    Mission schedule(Long fatherId, Long childId, Instant startTime, Duration duration);

    /**
     * Marks a mission as completed.
     * 
     * <p>The implementation is responsible for updating status, recording completion
     * time, and any side effects (e.g., incrementing streak, checking belt milestones).</p>
     * 
     * @param missionId the ID of the mission to complete
     * @param notes     optional notes about what was done during the mission (nullable)
     * @return the completed Mission with updated status
     * @throws IllegalArgumentException if the mission is not found
     * @throws IllegalStateException    if the mission is not in a completable state
     */
    Mission complete(UUID missionId, String notes);

    /**
     * Cancels a scheduled mission.
     * 
     * <p>The implementation is responsible for updating status and cleaning up any
     * external integrations (e.g., deleting Google Calendar event).</p>
     * 
     * <p>Cancellation does not affect the father's streak or progression.</p>
     * 
     * @param missionId the ID of the mission to cancel
     * @throws IllegalArgumentException if the mission is not found
     * @throws IllegalStateException    if the mission is not in a cancellable state
     */
    void cancel(UUID missionId);

    /**
     * Gets the next scheduled mission for a father.
     * 
     * <p>Returns the soonest scheduled mission that hasn't ended yet.
     * Used for dashboard display and workflow state decisions.</p>
     * 
     * @param fatherId the ID of the father
     * @return the next scheduled mission, or empty if none scheduled
     */
    Optional<Mission> getNextScheduled(Long fatherId);

    /**
     * Gets recent completed missions for a father.
     * 
     * <p>Returns the most recently completed missions, ordered by completion time descending.
     * Used for dashboard display and history views.</p>
     * 
     * @param fatherId the ID of the father
     * @param limit    the maximum number of missions to return
     * @return list of recently completed missions
     */
    List<Mission> getRecentCompleted(Long fatherId, int limit);

    /**
     * Returns the mission type that this service handles.
     * 
     * <p>Used by {@code MissionServiceFactory} to route requests to the appropriate service.</p>
     * 
     * @return the supported mission type
     */
    MissionType getSupportedType();
}
