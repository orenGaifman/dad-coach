package com.dadcoach.conversation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Status of a coaching conversation.
 */
public enum ConversationStatus {
    ACTIVE,
    COMPLETED,
    EXPIRED,
    ABANDONED;

    /**
     * Returns the set of valid states this status can transition to.
     */
    public Set<ConversationStatus> getValidTransitions() {
        switch (this) {
            case ACTIVE:
                return EnumSet.of(COMPLETED, EXPIRED, ABANDONED);
            default:
                return Collections.emptySet();
        }
    }

    /**
     * Checks whether a transition from this status to the target status is valid.
     */
    public boolean canTransitionTo(ConversationStatus target) {
        return getValidTransitions().contains(target);
    }
}
