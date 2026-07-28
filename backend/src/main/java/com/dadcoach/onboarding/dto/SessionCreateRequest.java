package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new onboarding session.
 */
@Schema(description = "Request to create a new onboarding session from an invitation token")
public record SessionCreateRequest(

    @NotBlank(message = "Invitation token is required")
    @Size(min = 32, max = 32, message = "Invitation token must be exactly 32 characters")
    @JsonProperty("invitation_token")
    @Schema(description = "The 32-character invitation token", example = "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345")
    String invitationToken
) {
}
