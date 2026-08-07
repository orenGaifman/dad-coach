package com.dadcoach.domain.conversation;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status of a conversation with state transition support.
 */
public enum ConversationStatus {
    PENDING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    ABANDONED;

    /**
     * Returns the valid transitions from this status.
     */
    public Set<ConversationStatus> getValidTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(ACTIVE, CANCELLED);
            case ACTIVE -> EnumSet.of(PAUSED, COMPLETED, EXPIRED, ABANDONED);
            case PAUSED -> EnumSet.of(ACTIVE, COMPLETED, EXPIRED, ABANDONED);
            case COMPLETED, EXPIRED, CANCELLED, ABANDONED -> EnumSet.noneOf(ConversationStatus.class);
        };
    }

    /**
     * Checks if a transition to the given status is valid.
     */
    public boolean canTransitionTo(ConversationStatus newStatus) {
        return getValidTransitions().contains(newStatus);
    }
}
