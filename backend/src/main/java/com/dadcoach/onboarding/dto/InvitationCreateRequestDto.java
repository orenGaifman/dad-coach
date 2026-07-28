package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new invitation (admin endpoint).
 */
@Schema(description = "Request to create a new invitation")
public record InvitationCreateRequestDto(

    @NotNull(message = "Invitation type is required")
    @Schema(description = "Type of invitation", example = "REUSABLE")
    String type,

    @JsonProperty("max_uses")
    @Min(value = 1, message = "max_uses must be at least 1")
    @Max(value = 10000, message = "max_uses cannot exceed 10000")
    @Schema(description = "Maximum number of times this invitation can be used", example = "50")
    Integer maxUses
) {
}
