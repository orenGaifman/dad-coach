package com.dadcoach.mission;

import com.dadcoach.qualitytime.QualityTimeStatus;

/**
 * Status of a Mission in its lifecycle.
 * 
 * <p>This enum mirrors {@link QualityTimeStatus} to maintain consistency across the
 * deterministic workflow engine. For MVP, every Mission is a Quality Time session,
 * so both status enums share the same values.</p>
 * 
 * <p><strong>Status Definitions:</strong></p>
 * <ul>
 *   <li>{@link #SCHEDULED} - Mission is scheduled and hasn't occurred yet</li>
 *   <li>{@link #COMPLETED} - Father completed the mission</li>
 *   <li>{@link #MISSED} - Father missed the mission (didn't complete after 24h follow-up)</li>
 *   <li>{@link #CANCELLED} - Mission was cancelled by father or sync detected calendar deletion</li>
 * </ul>
 * 
 * <p><strong>Conversion:</strong></p>
 * Use {@link #fromQualityTimeStatus(QualityTimeStatus)} and {@link #toQualityTimeStatus()}
 * for interoperability with the Quality Time subsystem.
 * 
 * Requirements: 3.4
 * 
 * @see QualityTimeStatus
 * @see Mission
 */
public enum MissionStatus {
    
    /**
     * Mission is scheduled and hasn't occurred yet.
     */
    SCHEDULED,
    
    /**
     * Father completed the mission.
     */
    COMPLETED,
    
    /**
     * Father missed the mission (didn't complete after 24h follow-up).
     */
    MISSED,
    
    /**
     * Mission was cancelled by father or sync detected calendar deletion.
     */
    CANCELLED;
    
    /**
     * Converts a {@link QualityTimeStatus} to the corresponding {@link MissionStatus}.
     * 
     * @param status the QualityTimeStatus to convert
     * @return the corresponding MissionStatus
     * @throws IllegalArgumentException if status is null
     */
    public static MissionStatus fromQualityTimeStatus(QualityTimeStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("QualityTimeStatus cannot be null");
        }
        return switch (status) {
            case SCHEDULED -> SCHEDULED;
            case COMPLETED -> COMPLETED;
            case MISSED -> MISSED;
            case CANCELLED -> CANCELLED;
        };
    }
    
    /**
     * Converts this {@link MissionStatus} to the corresponding {@link QualityTimeStatus}.
     * 
     * @return the corresponding QualityTimeStatus
     */
    public QualityTimeStatus toQualityTimeStatus() {
        return switch (this) {
            case SCHEDULED -> QualityTimeStatus.SCHEDULED;
            case COMPLETED -> QualityTimeStatus.COMPLETED;
            case MISSED -> QualityTimeStatus.MISSED;
            case CANCELLED -> QualityTimeStatus.CANCELLED;
        };
    }
}
