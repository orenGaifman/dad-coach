package com.dadcoach.mission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a coaching mission.
 */
public enum MissionStatus {
    ASSIGNED,
    ACCEPTED,
    SKIPPED,
    EXPIRED,
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
    REFLECTED;

    /**
     * Returns the set of valid states this status can transition to.
     */
    public Set<MissionStatus> getValidTransitions() {
        switch (this) {
            case ASSIGNED:
                return EnumSet.of(ACCEPTED, SKIPPED, EXPIRED);
            case ACCEPTED:
                return EnumSet.of(IN_PROGRESS, EXPIRED);
            case IN_PROGRESS:
                return EnumSet.of(COMPLETED, ABANDONED);
            case COMPLETED:
                return EnumSet.of(REFLECTED);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this status to the target status is valid.
     */
    public boolean canTransitionTo(MissionStatus target) {
        return getValidTransitions().contains(target);
    }
}
