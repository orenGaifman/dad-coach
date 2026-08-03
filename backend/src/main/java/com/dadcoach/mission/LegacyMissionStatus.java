package com.dadcoach.mission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Legacy lifecycle status of a coaching mission.
 * 
 * <p><strong>DEPRECATED:</strong> This enum is maintained for backward compatibility
 * with the existing mission system in {@code com.dadcoach.domain.mission}. New code
 * should use {@link MissionStatus} which aligns with the deterministic workflow engine.</p>
 * 
 * <p>The new workflow engine uses a simplified status model (SCHEDULED, COMPLETED, MISSED, CANCELLED)
 * that maps directly to the Quality Time lifecycle. This legacy enum supports the older,
 * more complex mission lifecycle.</p>
 * 
 * @see MissionStatus
 * @deprecated Use {@link MissionStatus} for new workflow engine code
 */
@Deprecated
public enum LegacyMissionStatus {
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
    public Set<LegacyMissionStatus> getValidTransitions() {
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
    public boolean canTransitionTo(LegacyMissionStatus target) {
        return getValidTransitions().contains(target);
    }
}
