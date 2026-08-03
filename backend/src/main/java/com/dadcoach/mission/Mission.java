package com.dadcoach.mission;

import com.dadcoach.qualitytime.QualityTimeStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract representation of a parenting activity mission.
 * 
 * A Mission is a container for different types of parenting activities that a father
 * completes with their child. The Mission abstraction allows for future extensibility
 * while keeping the current MVP focused on Quality Time sessions.
 * 
 * <p><strong>MVP Implementation:</strong></p>
 * For MVP, the only mission type is {@link MissionType#QUALITY_TIME}, which represents
 * a scheduled calendar event where the father spends dedicated time with their child.
 * 
 * <p><strong>Architecture Extensibility:</strong></p>
 * The architecture supports future mission types without changing the core workflow logic:
 * <ul>
 *   <li>READING_TOGETHER - Reading sessions with the child</li>
 *   <li>OUTDOOR_ACTIVITY - Outdoor activities like sports or nature walks</li>
 *   <li>LEARNING_MOMENT - Educational activities</li>
 *   <li>CREATIVE_PLAY - Arts, crafts, and creative activities</li>
 * </ul>
 * 
 * <p>Each mission type will have its own {@link MissionService} implementation,
 * obtained through the {@code MissionServiceFactory}.</p>
 * 
 * <p><strong>Note:</strong> This interface is part of the new deterministic workflow engine
 * and is separate from the legacy {@code com.dadcoach.domain.mission.Mission} entity.</p>
 * 
 * @see MissionType
 * @see MissionService
 * @see com.dadcoach.qualitytime.QualityTime
 * 
 * Requirements: 1.1 (Mission concept as abstract container for parenting activities)
 */
public interface Mission {

    /**
     * Returns the unique identifier for this mission.
     * 
     * @return the mission's UUID
     */
    UUID getId();

    /**
     * Returns the ID of the father who owns this mission.
     * 
     * @return the father's ID
     */
    Long getFatherId();

    /**
     * Returns the ID of the child this mission is associated with.
     * 
     * @return the child's ID
     */
    Long getChildId();

    /**
     * Returns the type of this mission.
     * 
     * For MVP, this will always return {@link MissionType#QUALITY_TIME}.
     * 
     * @return the mission type
     */
    MissionType getType();

    /**
     * Returns the current status of this mission.
     * 
     * Status values: SCHEDULED, COMPLETED, MISSED, or CANCELLED.
     * Uses {@link QualityTimeStatus} for consistency with the MVP implementation.
     * 
     * @return the mission status
     */
    QualityTimeStatus getStatus();

    /**
     * Returns the scheduled start time for this mission.
     * 
     * @return the scheduled start instant
     */
    Instant getScheduledStart();

    /**
     * Returns the scheduled end time for this mission.
     * 
     * @return the scheduled end instant
     */
    Instant getScheduledEnd();

    /**
     * Returns any notes recorded upon completion of this mission.
     * 
     * This may include details about what activities were done during the mission,
     * observations about the child's engagement, or memorable moments.
     * 
     * @return the completion notes, or null if not yet completed or no notes provided
     */
    String getCompletionNotes();

    /**
     * Returns the timestamp when this mission was completed.
     * 
     * @return the completion instant, or null if not yet completed
     */
    Instant getCompletedAt();
}
