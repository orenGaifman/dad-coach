package com.dadcoach.ai.output;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Structured output from weekly summary generation.
 * This is a recommendation — the application layer decides how to present it.
 *
 * <p>The AI never directly mutates state; this record is purely advisory output.
 *
 * @param fatherId         the father this summary is for
 * @param periodStart      start date of the summary period
 * @param periodEnd        end date of the summary period
 * @param summary          the generated summary text in the father's preferred language (English or Hebrew)
 * @param highlights       key highlights from the week
 * @param missionsCompleted number of missions completed
 * @param streakDays       current streak at end of period
 * @param model            the model that generated the summary
 * @param validationPassed true if the output passed schema validation
 */
public record WeeklySummaryOutput(
    UUID fatherId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String summary,
    List<String> highlights,
    int missionsCompleted,
    int streakDays,
    String model,
    boolean validationPassed
) {
    public WeeklySummaryOutput {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("period dates must not be null");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be null or blank");
        }
        highlights = highlights != null ? List.copyOf(highlights) : List.of();
    }
}
