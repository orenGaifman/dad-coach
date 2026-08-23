package com.dadcoach.memory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state for Memory entities.
 *
 * <p>From SPEC-004 Requirement 2, memories follow this state machine:
 * <ul>
 *   <li>ACTIVE → CONFIRMED: Father explicitly validates or repeats the information</li>
 *   <li>ACTIVE → SUPERSEDED: Newer contradicting memory created with higher confidence</li>
 *   <li>ACTIVE → ARCHIVED: Memory count exceeds 500 limit OR manual archive</li>
 *   <li>ACTIVE → EXPIRED: Confidence &lt; 0.5 AND not accessed in 60 days</li>
 *   <li>ACTIVE → DELETED: Father requests deletion OR GDPR erasure</li>
 *   <li>CONFIRMED → SUPERSEDED: Father explicitly corrects the information</li>
 *   <li>CONFIRMED → ARCHIVED: Memory count exceeds 500 limit</li>
 *   <li>CONFIRMED → DELETED: Father requests deletion OR GDPR erasure</li>
 *   <li>SUPERSEDED → DELETED: Cleanup job after 90 days</li>
 *   <li>ARCHIVED → ACTIVE: Father re-references the information</li>
 *   <li>ARCHIVED → DELETED: Father requests deletion OR GDPR erasure</li>
 *   <li>EXPIRED → DELETED: Cleanup job after 30 days</li>
 *   <li>EXPIRED → ACTIVE: Father re-references the information</li>
 * </ul>
 */
public enum MemoryState {

    /**
     * Memory is active and available for retrieval.
     * Initial state for all newly created memories.
     */
    ACTIVE,

    /**
     * Memory has been explicitly confirmed by the father.
     * Confidence is set to max(current, 0.9), decay timer reset.
     */
    CONFIRMED,

    /**
     * Memory has been replaced by a newer, corrected version.
     * References the superseding memory via superseded_by field.
     */
    SUPERSEDED,

    /**
     * Memory has been archived (excluded from active retrieval).
     * Can be reactivated if father re-references the information.
     */
    ARCHIVED,

    /**
     * Memory has expired due to low confidence and lack of access.
     * Preserved for 30 days before automatic deletion.
     */
    EXPIRED,

    /**
     * Memory has been deleted (content erasure pending or complete).
     * Content erasure happens within 72 hours of entering this state.
     */
    DELETED;

    /**
     * Returns the set of valid states this state can transition to.
     */
    public Set<MemoryState> getValidTransitions() {
        return switch (this) {
            case ACTIVE -> EnumSet.of(CONFIRMED, SUPERSEDED, ARCHIVED, EXPIRED, DELETED);
            case CONFIRMED -> EnumSet.of(SUPERSEDED, ARCHIVED, DELETED);
            case SUPERSEDED -> EnumSet.of(DELETED);
            case ARCHIVED -> EnumSet.of(ACTIVE, DELETED);
            case EXPIRED -> EnumSet.of(ACTIVE, DELETED);
            case DELETED -> Collections.emptySet();
        };
    }

    /**
     * Checks whether a transition from this state to the target state is valid.
     *
     * @param target the desired target state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(MemoryState target) {
        return getValidTransitions().contains(target);
    }
}
