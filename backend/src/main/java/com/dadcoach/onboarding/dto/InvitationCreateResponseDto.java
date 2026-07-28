package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned after creating an invitation.
 */
@Schema(description = "Response after creating an invitation")
public record InvitationCreateResponseDto(

    @JsonProperty("invitation_id")
    @Schema(description = "The invitation ID")
    UUID invitationId,

    @Schema(description = "The invitation token", example = "aBcDeFgHiJkLmNoPqRsTuVwXyZ012345")
    String token,

    @Schema(description = "Full invitation link", example = "https://dadcoach.app/join/aBcDeFgHiJkLmNoPqRsTuVwXyZ012345")
    String link,

    @Schema(description = "Invitation type", example = "REUSABLE")
    String type,

    @JsonProperty("max_uses")
    @Schema(description = "Maximum number of uses", example = "50")
    int maxUses,

    @JsonProperty("expires_at")
    @Schema(description = "Expiration time")
    Instant expiresAt,

    @Schema(description = "Current status", example = "CREATED")
    String status
) {
}
