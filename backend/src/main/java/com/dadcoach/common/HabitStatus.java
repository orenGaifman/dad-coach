package com.dadcoach.common;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a habit being tracked.
 */
public enum HabitStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED;

    /**
     * Returns the set of valid states this status can transition to.
     */
    public Set<HabitStatus> getValidTransitions() {
        switch (this) {
            case ACTIVE:
                return EnumSet.of(PAUSED, COMPLETED, ARCHIVED);
            case PAUSED:
                return EnumSet.of(ACTIVE, ARCHIVED);
            case COMPLETED:
                return EnumSet.of(ARCHIVED);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this status to the target status is valid.
     */
    public boolean canTransitionTo(HabitStatus target) {
        return getValidTransitions().contains(target);
    }
}
