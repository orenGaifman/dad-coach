package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for invitation token validation.
 * Returned when a token is successfully validated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Invitation validation response containing metadata about the invitation")
public record InvitationValidationResponse(

    @JsonProperty("invitation_type")
    @Schema(description = "Type of invitation", example = "REUSABLE")
    String invitationType,

    @JsonProperty("inviter_name")
    @Schema(description = "Name of the person who created the invitation", example = "David")
    String inviterName,

    @JsonProperty("expires_at")
    @Schema(description = "When the invitation expires", example = "2025-03-15T00:00:00Z")
    Instant expiresAt,

    @JsonProperty("remaining_uses")
    @Schema(description = "Number of remaining uses for this invitation", example = "42")
    int remainingUses
) {
}
