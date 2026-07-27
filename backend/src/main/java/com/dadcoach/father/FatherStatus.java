package com.dadcoach.father;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a father in the coaching system.
 */
public enum FatherStatus {
    NOT_STARTED,
    ONBOARDING,
    ACTIVE,
    PAUSED,
    CHURNED,
    REACTIVATED,
    DELETED;

    /**
     * Returns the set of valid states this status can transition to.
     */
    public Set<FatherStatus> getValidTransitions() {
        switch (this) {
            case NOT_STARTED:
                return EnumSet.of(ONBOARDING);
            case ONBOARDING:
                return EnumSet.of(ACTIVE);
            case ACTIVE:
                return EnumSet.of(PAUSED, CHURNED, DELETED);
            case PAUSED:
                return EnumSet.of(ACTIVE, DELETED);
            case CHURNED:
                return EnumSet.of(REACTIVATED, DELETED);
            case REACTIVATED:
                return EnumSet.of(ACTIVE);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this status to the target status is valid.
     */
    public boolean canTransitionTo(FatherStatus target) {
        return getValidTransitions().contains(target);
    }
}
