package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

// ChildDto and QualityTimeDto are in the same package, no explicit import needed

/**
 * DTO representing detailed father state information for the Dev Dashboard.
 * Includes workflow state, belt info, children, and scheduled quality times.
 *
 * @param id The father's unique identifier
 * @param displayName The father's display name (nullable)
 * @param phone The father's phone number
 * @param status The father's status (uppercase enum string)
 * @param workflow Workflow state information
 * @param belt Belt level and progression information
 * @param children List of associated children
 * @param scheduledQualityTimes List of scheduled quality time entries
 * @param partial Whether only partial data was retrieved (some queries failed)
 * @param errors List of error messages for failed data retrievals
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FatherStateDetailsDto(
    Long id,
    
    @JsonProperty("display_name")
    String displayName,
    
    String phone,
    
    String status,
    
    WorkflowInfo workflow,
    
    BeltInfo belt,
    
    List<ChildDto> children,
    
    @JsonProperty("scheduled_quality_times")
    List<QualityTimeDto> scheduledQualityTimes,
    
    @JsonProperty("_partial")
    boolean partial,
    
    @JsonProperty("_errors")
    List<String> errors
) {
    
    /**
     * Nested record for workflow state information.
     *
     * @param currentState The current workflow state (uppercase enum string)
     * @param previousState The previous workflow state (uppercase enum string, nullable)
     * @param stateEnteredAt When the father entered the current state (ISO 8601 format)
     * @param welcomedAt When the father was welcomed/onboarded (ISO 8601 format, nullable)
     */
    public record WorkflowInfo(
        @JsonProperty("current_state")
        String currentState,
        
        @JsonProperty("previous_state")
        String previousState,
        
        @JsonProperty("state_entered_at")
        Instant stateEnteredAt,
        
        @JsonProperty("welcomed_at")
        Instant welcomedAt
    ) {}
    
    /**
     * Nested record for belt level and progression information.
     *
     * @param current The current belt level (uppercase enum string)
     * @param totalQualityTimesCompleted Total number of quality times completed
     * @param currentStreakWeeks Current weekly streak count
     */
    public record BeltInfo(
        String current,
        
        @JsonProperty("total_quality_times_completed")
        int totalQualityTimesCompleted,
        
        @JsonProperty("current_streak_weeks")
        int currentStreakWeeks
    ) {}
}
