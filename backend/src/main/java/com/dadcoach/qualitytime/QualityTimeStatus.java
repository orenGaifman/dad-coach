package com.dadcoach.qualitytime;

/**
 * Status of a Quality Time event in its lifecycle.
 * 
 * Quality Time represents scheduled time where a father spends dedicated time
 * with their child, backed by Google Calendar.
 */
public enum QualityTimeStatus {
    /**
     * Quality Time is scheduled and hasn't occurred yet.
     */
    SCHEDULED,

    /**
     * Father completed the Quality Time.
     */
    COMPLETED,

    /**
     * Father missed the Quality Time (didn't complete after 24h follow-up).
     */
    MISSED,

    /**
     * Quality Time was cancelled by father or sync detected calendar deletion.
     */
    CANCELLED
}
