package com.dadcoach.onboarding.invitation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Enum representing the lifecycle status of an invitation.
 *
 * State transitions follow the invitation lifecycle:
 * CREATED → SENT → OPENED → USED (plus EXPIRED/REVOKED as terminal states).
 *
 * USED → USED is valid for reusable invitations where current_uses < max_uses.
 * EXPIRED and REVOKED are terminal states with no valid outgoing transitions.
 *
 * @see <a href="Requirement 1 criteria 6">Invitation lifecycle state machine</a>
 */
public enum InvitationStatus {

    CREATED {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return EnumSet.of(SENT, EXPIRED, REVOKED);
        }
    },

    SENT {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return EnumSet.of(OPENED, EXPIRED, REVOKED);
        }
    },

    OPENED {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return EnumSet.of(USED, EXPIRED, REVOKED);
        }
    },

    USED {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return EnumSet.of(USED);
        }
    },

    EXPIRED {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return Collections.emptySet();
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    },

    REVOKED {
        @Override
        public Set<InvitationStatus> getAllowedTransitions() {
            return Collections.emptySet();
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    };

    /**
     * Returns the set of states this status can transition to.
     *
     * @return an unmodifiable set of allowed target states
     */
    public abstract Set<InvitationStatus> getAllowedTransitions();

    /**
     * Checks whether this status can transition to the given target status.
     *
     * @param target the target status to transition to
     * @return true if the transition is valid, false otherwise
     */
    public boolean canTransitionTo(InvitationStatus target) {
        return getAllowedTransitions().contains(target);
    }

    /**
     * Returns whether this status is a terminal state (no further transitions allowed).
     * Terminal states are EXPIRED and REVOKED.
     *
     * @return true if this is a terminal state, false otherwise
     */
    public boolean isTerminal() {
        return false;
    }
}
