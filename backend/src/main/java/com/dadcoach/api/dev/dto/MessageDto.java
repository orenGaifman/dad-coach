package com.dadcoach.api.dev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO representing a message in the message log for the Dev Dashboard.
 *
 * @param id The message's unique identifier
 * @param direction The message direction (uppercase: INBOUND or OUTBOUND)
 * @param content The message content text
 * @param createdAt When the message was created (ISO 8601 format)
 */
public record MessageDto(
    Long id,
    
    String direction,
    
    String content,
    
    @JsonProperty("created_at")
    Instant createdAt
) {}
