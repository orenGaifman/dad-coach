package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an upcoming (scheduled) Quality Time event.
 * 
 * Used for dashboard display and reminder messages.
 * 
 * Requirements: 3.4, 6.4, 13.1
 */
public record UpcomingQualityTimeDto(
        /**
         * The Quality Time ID.
         */
        UUID id,

        /**
         * The name of the child this Quality Time is with.
         */
        String childName,

        /**
         * The child's ID.
         */
        Long childId,

        /**
         * The scheduled start time.
         */
        Instant scheduledStart,

        /**
         * The scheduled end time.
         */
        Instant scheduledEnd,

        /**
         * The status of the Quality Time.
         */
        QualityTimeStatus status,

        /**
         * Whether a reminder has been sent for this Quality Time.
         */
        boolean reminderSent
) {

    /**
     * Creates an UpcomingQualityTimeDto from a QualityTime entity.
     *
     * @param qualityTime the entity to convert
     * @return a new DTO with data from the entity
     */
    public static UpcomingQualityTimeDto from(QualityTime qualityTime) {
        return new UpcomingQualityTimeDto(
                qualityTime.getId(),
                qualityTime.getChild() != null ? qualityTime.getChild().getName() : null,
                qualityTime.getChildId(),
                qualityTime.getScheduledStart(),
                qualityTime.getScheduledEnd(),
                qualityTime.getStatus(),
                qualityTime.isReminderSent()
        );
    }
}
