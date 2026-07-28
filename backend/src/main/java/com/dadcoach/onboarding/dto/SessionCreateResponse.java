package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned after creating a new onboarding session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after creating a new onboarding session")
public record SessionCreateResponse(

    @JsonProperty("session_id")
    @Schema(description = "The unique session ID", example = "019462a8-7b3f-7000-8000-000000000001")
    UUID sessionId,

    @JsonProperty("current_step")
    @Schema(description = "The current wizard step", example = "WELCOME")
    String currentStep,

    @Schema(description = "Session status", example = "IN_PROGRESS")
    String status,

    @Schema(description = "Selected language (null until LANGUAGE step)", example = "he")
    String language,

    @Schema(description = "Progress through the wizard")
    Progress progress,

    @JsonProperty("expires_at")
    @Schema(description = "When the session expires")
    Instant expiresAt,

    @JsonProperty("csrf_token")
    @Schema(description = "CSRF token for state-changing requests")
    String csrfToken
) {

    @Schema(description = "Wizard progress information")
    public record Progress(
        @Schema(description = "Current step number", example = "1")
        int current,
        @Schema(description = "Total number of steps", example = "8")
        int total
    ) {
    }
}
