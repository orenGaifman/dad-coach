package com.dadcoach.mission;

/**
 * Types of parenting activity missions available in Dad Coach.
 * 
 * For MVP, only {@link #QUALITY_TIME} is implemented. The architecture
 * supports future mission types without changing core workflow logic.
 * 
 * <p><strong>MVP:</strong></p>
 * <ul>
 *   <li>{@link #QUALITY_TIME} - Calendar-backed quality time with child</li>
 * </ul>
 * 
 * <p><strong>Future Types (not implemented in MVP):</strong></p>
 * <ul>
 *   <li>READING_TOGETHER - Reading sessions with the child</li>
 *   <li>OUTDOOR_ACTIVITY - Outdoor activities like sports or nature walks</li>
 *   <li>LEARNING_MOMENT - Educational activities</li>
 *   <li>CREATIVE_PLAY - Arts, crafts, and creative activities</li>
 * </ul>
 * 
 * Requirements: 1.1 (Mission extensibility)
 * 
 * @see Mission
 * @see MissionService
 */
public enum MissionType {
    
    /**
     * Quality Time mission - the MVP mission type.
     * 
     * Represents a scheduled calendar event where the father spends
     * dedicated time with their child. Backed by Google Calendar.
     */
    QUALITY_TIME
    
    // Future mission types (not implemented in MVP):
    // READING_TOGETHER,
    // OUTDOOR_ACTIVITY,
    // LEARNING_MOMENT,
    // CREATIVE_PLAY
}
