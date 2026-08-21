package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a scheduled quality time entry for the Dev Dashboard.
 *
 * @param id The quality time's unique identifier
 * @param childName The name of the child for this quality time
 * @param scheduledStart When the quality time is scheduled to start (ISO 8601 format)
 * @param scheduledEnd When the quality time is scheduled to end (ISO 8601 format)
 * @param status The status of the quality time (uppercase enum string: SCHEDULED, COMPLETED, etc.)
 */
public record QualityTimeDto(
    UUID id,
    
    @JsonProperty("child_name")
    String childName,
    
    @JsonProperty("scheduled_start")
    Instant scheduledStart,
    
    @JsonProperty("scheduled_end")
    Instant scheduledEnd,
    
    String status
) {}
