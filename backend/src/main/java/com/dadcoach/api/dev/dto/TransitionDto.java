package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a workflow state transition for the Dev Dashboard.
 *
 * @param id The transition's unique identifier
 * @param fromState The source workflow state (uppercase enum string)
 * @param toState The target workflow state (uppercase enum string)
 * @param triggerReason The reason for the transition (uppercase enum string)
 * @param triggerMessageId The ID of the message that triggered the transition (nullable)
 * @param createdAt When the transition occurred (ISO 8601 format)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransitionDto(
    UUID id,
    
    @JsonProperty("from_state")
    String fromState,
    
    @JsonProperty("to_state")
    String toState,
    
    @JsonProperty("trigger_reason")
    String triggerReason,
    
    @JsonProperty("trigger_message_id")
    UUID triggerMessageId,
    
    @JsonProperty("created_at")
    Instant createdAt
) {}
