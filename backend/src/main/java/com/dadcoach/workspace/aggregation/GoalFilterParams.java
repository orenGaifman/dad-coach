package com.dadcoach.workspace.aggregation;

import java.util.UUID;

/**
 * Filter parameters for querying goals in the workspace overview.
 *
 * <p>All fields are nullable — when null, no filtering is applied for that dimension.</p>
 *
 * @param status   filter by goal status (e.g., "ACTIVE", "COMPLETED")
 * @param category filter by goal category (e.g., "COMMUNICATION", "BONDING")
 * @param childId  filter by associated child
 */
public record GoalFilterParams(
        String status,
        String category,
        UUID childId
) {

    /**
     * Creates an empty filter (no filtering applied).
     */
    public static GoalFilterParams none() {
        return new GoalFilterParams(null, null, null);
    }
}
