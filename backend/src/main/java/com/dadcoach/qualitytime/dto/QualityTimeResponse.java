package com.dadcoach.qualitytime.dto;

import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a Quality Time event.
 * 
 * Used as a general response for Quality Time operations such as cancel.
 * Contains the Quality Time details including status.
 * 
 * Requirements: 14.1
 */
@Schema(description = "Quality Time event details")
public record QualityTimeResponse(

    @JsonProperty("quality_time_id")
    @Schema(description = "The unique ID of the Quality Time", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID qualityTimeId,

    @JsonProperty("child_id")
    @Schema(description = "The ID of the child this Quality Time is with", example = "123")
    Long childId,

    @JsonProperty("child_name")
    @Schema(description = "The name of the child this Quality Time is with", example = "Maya")
    String childName,

    @JsonProperty("scheduled_start")
    @Schema(description = "The scheduled start time (ISO 8601 format)", example = "2024-01-15T17:00:00Z")
    Instant scheduledStart,

    @JsonProperty("scheduled_end")
    @Schema(description = "The scheduled end time (ISO 8601 format)", example = "2024-01-15T17:30:00Z")
    Instant scheduledEnd,

    @Schema(description = "The status of the Quality Time", example = "CANCELLED")
    QualityTimeStatus status,

    @JsonProperty("google_calendar_event_id")
    @Schema(description = "The Google Calendar event ID, if linked", example = "abc123xyz", nullable = true)
    String googleCalendarEventId
) {

    /**
     * Creates a QualityTimeResponse from a QualityTime entity.
     *
     * @param qualityTime the Quality Time entity
     * @return a new QualityTimeResponse
     */
    public static QualityTimeResponse from(QualityTime qualityTime) {
        return new QualityTimeResponse(
                qualityTime.getId(),
                qualityTime.getChildId(),
                qualityTime.getChild() != null ? qualityTime.getChild().getName() : null,
                qualityTime.getScheduledStart(),
                qualityTime.getScheduledEnd(),
                qualityTime.getStatus(),
                qualityTime.getGoogleCalendarEventId()
        );
    }
}
