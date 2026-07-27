package com.dadcoach.ai.output;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Defines the period for a weekly summary generation.
 *
 * @param fatherId    the father to summarize
 * @param periodStart start date (inclusive)
 * @param periodEnd   end date (inclusive)
 */
public record SummaryPeriod(
    UUID fatherId,
    LocalDate periodStart,
    LocalDate periodEnd
) {
    public SummaryPeriod {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("period dates must not be null");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
    }
}
