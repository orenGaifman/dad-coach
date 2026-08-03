package com.dadcoach.workflow.dto;

import com.dadcoach.workflow.Belt;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for completing a Quality Time event.
 * 
 * POST /api/v1/quality-time/{id}/complete
 * 
 * Contains the completion result including updated streak information
 * and any belt earned from this completion.
 * 
 * Requirements: 14.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after marking a Quality Time as completed")
public record CompleteResponse(

    @JsonProperty("quality_time_id")
    @Schema(description = "The ID of the completed Quality Time", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID qualityTimeId,

    @Schema(description = "The status of the Quality Time after completion", example = "COMPLETED")
    String status,

    @JsonProperty("streak_updated")
    @Schema(description = "Whether the streak counter was updated", example = "true")
    boolean streakUpdated,

    @JsonProperty("new_streak")
    @Schema(description = "The new streak count after completion", example = "6")
    int newStreak,

    @JsonProperty("belt_earned")
    @Schema(
        description = "The belt earned with this completion, or null if no new belt was earned",
        example = "GREEN",
        nullable = true
    )
    Belt beltEarned,

    @JsonProperty("points_awarded")
    @Schema(description = "Points awarded for this completion", example = "10")
    int pointsAwarded
) {

    /**
     * Creates a CompleteResponse from a CompleteQualityTimeResult.
     * 
     * @param result the service layer result object
     * @return a new CompleteResponse for the API
     */
    public static CompleteResponse fromResult(
            com.dadcoach.qualitytime.dto.CompleteQualityTimeResult result
    ) {
        return new CompleteResponse(
                result.qualityTimeId(),
                result.status().name(),
                result.streakUpdated(),
                result.newStreak(),
                result.beltEarned(),
                result.pointsAwarded()
        );
    }
}
