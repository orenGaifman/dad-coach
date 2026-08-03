package com.dadcoach.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO containing a list of activity ideas for Quality Time.
 * 
 * Returned by the GET /api/v1/activity-ideas endpoint when a father
 * requests activity suggestions for spending time with their child.
 * 
 * Requirements: 9.3, 14.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing activity idea suggestions for Quality Time")
public record ActivityIdeasResponse(

    @Schema(
        description = "List of activity idea suggestions. Typically returns 3 ideas as per Requirement 9.3.",
        example = """
            [
              {
                "title": "Cooking Together",
                "description": "Prepare a simple recipe together. Let Sofia measure ingredients and mix them.",
                "duration_minutes": 30,
                "indoor": true
              },
              {
                "title": "Nature Walk",
                "description": "Take a walk in the park and identify 5 different plants or animals.",
                "duration_minutes": 45,
                "indoor": false
              },
              {
                "title": "Story Time with Voices",
                "description": "Read a book together using different voices for each character.",
                "duration_minutes": 20,
                "indoor": true
              }
            ]
            """
    )
    List<ActivityIdeaDto> ideas
) {

    /**
     * Creates an ActivityIdeasResponse from a list of ideas.
     *
     * @param ideas the list of activity ideas
     * @return a new ActivityIdeasResponse
     */
    public static ActivityIdeasResponse of(List<ActivityIdeaDto> ideas) {
        return new ActivityIdeasResponse(ideas);
    }

    /**
     * Creates an empty ActivityIdeasResponse.
     *
     * @return a new ActivityIdeasResponse with an empty list
     */
    public static ActivityIdeasResponse empty() {
        return new ActivityIdeasResponse(List.of());
    }
}
