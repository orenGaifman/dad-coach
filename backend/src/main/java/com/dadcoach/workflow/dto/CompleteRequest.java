package com.dadcoach.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for completing a Quality Time event.
 * 
 * POST /api/v1/quality-time/{id}/complete
 * 
 * The notes field is optional and allows the father to describe
 * what they did during the Quality Time session.
 * 
 * Requirements: 14.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to mark a Quality Time as completed")
public record CompleteRequest(

    @Schema(
        description = "Optional notes about the Quality Time session",
        example = "We played soccer together, she scored 3 goals!"
    )
    String notes
) {
}
