package com.dadcoach.ai.output;

import java.util.List;

/**
 * Structured output from reflection evaluation.
 * This is a recommendation — the application layer decides how to use the insights.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param insights         key insights extracted from the reflection
 * @param growthAreas      areas of growth identified
 * @param suggestedFocus   recommended focus for the coming period
 * @param emotionalTone    detected emotional tone (positive, neutral, negative)
 * @param model            the model that generated the insights
 * @param validationPassed true if the output passed schema validation
 */
public record ReflectionInsightOutput(
    List<String> insights,
    List<String> growthAreas,
    String suggestedFocus,
    String emotionalTone,
    String model,
    boolean validationPassed
) {
    public ReflectionInsightOutput {
        insights = insights != null ? List.copyOf(insights) : List.of();
        growthAreas = growthAreas != null ? List.copyOf(growthAreas) : List.of();
        if (suggestedFocus == null) {
            suggestedFocus = "";
        }
        if (emotionalTone == null) {
            emotionalTone = "neutral";
        }
    }
}
