package com.dadcoach.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing an activity idea for Quality Time with a child.
 * 
 * Activity ideas are AI-generated suggestions for meaningful activities
 * that fathers can do with their children during Quality Time sessions.
 * 
 * Requirements: 9.3, 14.1
 */
@Schema(description = "An activity idea suggestion for Quality Time")
public record ActivityIdeaDto(

    @Schema(
        description = "The title of the activity idea",
        example = "Cooking Together"
    )
    String title,

    @Schema(
        description = "A detailed description of the activity with suggestions on how to engage the child",
        example = "Prepare a simple recipe together. Let Sofia measure ingredients and mix them."
    )
    String description,

    @JsonProperty("duration_minutes")
    @Schema(
        description = "Estimated duration of the activity in minutes",
        example = "30"
    )
    int durationMinutes,

    @Schema(
        description = "Whether the activity is suitable for indoor settings",
        example = "true"
    )
    boolean indoor
) {

    /**
     * Creates an ActivityIdeaDto with all fields.
     *
     * @param title           the activity title
     * @param description     detailed description of the activity
     * @param durationMinutes estimated duration in minutes
     * @param indoor          whether the activity is indoor
     * @return a new ActivityIdeaDto
     */
    public static ActivityIdeaDto of(String title, String description, int durationMinutes, boolean indoor) {
        return new ActivityIdeaDto(title, description, durationMinutes, indoor);
    }
}
