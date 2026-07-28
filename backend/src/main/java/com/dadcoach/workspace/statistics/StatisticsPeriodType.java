package com.dadcoach.workspace.statistics;

/**
 * Enumeration of time periods for statistics aggregation.
 *
 * <p>Statistics aggregates are computed at these granularities and stored in
 * the statistics_aggregates table. The aggregation job computes all three
 * period types nightly.</p>
 */
public enum StatisticsPeriodType {

    /** A single calendar day aggregate. */
    DAILY,

    /** A Monday-to-Sunday week aggregate. */
    WEEKLY,

    /** A calendar month aggregate. */
    MONTHLY
}
