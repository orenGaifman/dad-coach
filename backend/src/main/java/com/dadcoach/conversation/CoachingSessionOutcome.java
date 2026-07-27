package com.dadcoach.conversation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Outcome classification for a completed coaching session.
 */
public enum CoachingSessionOutcome {
    ACTIVE,
    OBJECTIVE_MET,
    PARTIALLY_MET,
    NOT_MET,
    FATHER_DISENGAGED,
    ERROR;

    /**
     * Returns the set of valid states this outcome can transition to.
     */
    public Set<CoachingSessionOutcome> getValidTransitions() {
        switch (this) {
            case ACTIVE:
                return EnumSet.of(OBJECTIVE_MET, PARTIALLY_MET, NOT_MET, FATHER_DISENGAGED, ERROR);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this outcome to the target outcome is valid.
     */
    public boolean canTransitionTo(CoachingSessionOutcome target) {
        return getValidTransitions().contains(target);
    }
}
