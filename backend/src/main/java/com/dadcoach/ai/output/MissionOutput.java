package com.dadcoach.ai.output;

/**
 * Structured output from mission generation.
 * This is a recommendation — the application layer decides whether to assign it.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param title            mission title (max 200 characters)
 * @param description      mission description with 2-4 action steps
 * @param category         the mission category
 * @param difficulty       difficulty level (1-5)
 * @param estimatedMinutes estimated time to complete in minutes
 * @param validationPassed true if the output passed schema validation
 * @param model            the model that generated the mission
 */
public record MissionOutput(
    String title,
    String description,
    String category,
    int difficulty,
    int estimatedMinutes,
    boolean validationPassed,
    String model
) {
    public MissionOutput {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title must not exceed 200 characters");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be null or blank");
        }
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        if (estimatedMinutes < 1) {
            throw new IllegalArgumentException("estimatedMinutes must be >= 1");
        }
    }
}
