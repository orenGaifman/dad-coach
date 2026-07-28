package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO returned after submitting a wizard step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after submitting a wizard step")
public record StepSubmissionResponse(

    @JsonProperty("session_id")
    @Schema(description = "The session ID")
    UUID sessionId,

    @JsonProperty("current_step")
    @Schema(description = "The new current step after submission", example = "CHILDREN")
    String currentStep,

    @Schema(description = "Session status", example = "IN_PROGRESS")
    String status,

    @Schema(description = "Progress through the wizard")
    SessionCreateResponse.Progress progress,

    @JsonProperty("completed_steps")
    @Schema(description = "List of completed steps")
    List<String> completedSteps,

    @JsonProperty("wizard_data_summary")
    @Schema(description = "Summary of wizard data collected so far")
    Map<String, Object> wizardDataSummary
) {
}
