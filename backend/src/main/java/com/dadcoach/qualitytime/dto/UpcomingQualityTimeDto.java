package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.SessionChildren;

import java.time.Instant;
import java.util.List;
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
         * The names of the children this Quality Time is with, joined in Hebrew ("מטר ונעם").
         */
        String childName,

        /**
         * The session's first child's ID.
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
        boolean reminderSent,

        /**
         * All the session's children's IDs, first child first.
         */
        List<Long> childIds
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
                SessionChildren.hebrew(qualityTime),
                qualityTime.getChildId(),
                qualityTime.getScheduledStart(),
                qualityTime.getScheduledEnd(),
                qualityTime.getStatus(),
                qualityTime.isReminderSent(),
                qualityTime.getChildIds()
        );
    }
}
