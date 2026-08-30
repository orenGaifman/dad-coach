package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a message in the message log for the Dev Dashboard.
 * For outbound (AI) messages, includes AI decision metadata for debugging.
 *
 * @param id The message's unique identifier
 * @param direction The message direction (uppercase: INBOUND or OUTBOUND)
 * @param content The message content text
 * @param createdAt When the message was created (ISO 8601 format)
 * @param toolUsed The AI tool that was used (for outbound messages)
 * @param toolParameters Parameters passed to the tool (for outbound messages)
 * @param previousState Workflow state before AI processing
 * @param newState Workflow state after AI processing (null if no transition)
 * @param toolSuccess Whether the tool execution succeeded
 * @param errorMessage Error message if tool execution failed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageDto(
    Long id,
    
    String direction,
    
    String content,
    
    @JsonProperty("created_at")
    Instant createdAt,
    
    // AI Decision metadata (for outbound messages)
    @JsonProperty("tool_used")
    String toolUsed,
    
    @JsonProperty("tool_parameters")
    Map<String, Object> toolParameters,
    
    @JsonProperty("previous_state")
    String previousState,
    
    @JsonProperty("new_state")
    String newState,
    
    @JsonProperty("tool_success")
    Boolean toolSuccess,
    
    @JsonProperty("error_message")
    String errorMessage
) {
    /**
     * Creates a simple message DTO without AI decision metadata.
     */
    public static MessageDto simple(Long id, String direction, String content, Instant createdAt) {
        return new MessageDto(id, direction, content, createdAt, null, null, null, null, null, null);
    }
}
