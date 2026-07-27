package com.dadcoach.father;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Progressive states during the father onboarding flow.
 */
public enum OnboardingState {
    NOT_STARTED,
    NAME_COLLECTED,
    CHILDREN_REGISTERED,
    GOALS_SET,
    SCHEDULE_SET,
    COMPLETED;

    /**
     * Returns the set of valid states this state can transition to.
     */
    public Set<OnboardingState> getValidTransitions() {
        switch (this) {
            case NOT_STARTED:
                return EnumSet.of(NAME_COLLECTED);
            case NAME_COLLECTED:
                return EnumSet.of(CHILDREN_REGISTERED);
            case CHILDREN_REGISTERED:
                return EnumSet.of(GOALS_SET);
            case GOALS_SET:
                return EnumSet.of(SCHEDULE_SET);
            case SCHEDULE_SET:
                return EnumSet.of(COMPLETED);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this state to the target state is valid.
     */
    public boolean canTransitionTo(OnboardingState target) {
        return getValidTransitions().contains(target);
    }
}
