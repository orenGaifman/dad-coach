package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO representing a father in the list view for the Dev Dashboard.
 * Contains basic debugging info for father selection.
 *
 * @param id The father's unique identifier
 * @param displayName The father's display name (nullable)
 * @param phone The father's phone number
 * @param status The father's status (uppercase enum string: ACTIVE, PAUSED, etc.)
 * @param currentWorkflowState The current workflow state (uppercase enum string)
 * @param previousWorkflowState The previous workflow state (uppercase enum string, nullable)
 * @param currentBelt The current belt level (uppercase enum string: WHITE, YELLOW, etc.)
 * @param lastInteractionAt The timestamp of last interaction (ISO 8601 format)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FatherListItemDto(
    Long id,
    
    @JsonProperty("display_name")
    String displayName,
    
    String phone,
    
    String status,
    
    @JsonProperty("current_workflow_state")
    String currentWorkflowState,
    
    @JsonProperty("previous_workflow_state")
    String previousWorkflowState,
    
    @JsonProperty("current_belt")
    String currentBelt,
    
    @JsonProperty("last_interaction_at")
    Instant lastInteractionAt
) {}
