package com.dadcoach.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * External domain event representing a mission being reflected upon by a father.
 *
 * <p>This event is expected to be published by the Mission domain (SPEC-002) when a
 * mission transitions from COMPLETED to REFLECTED status. Reflection is a bonus
 * activity on top of completion.</p>
 *
 * <p>This is a placeholder class that will eventually be owned by SPEC-002.
 * It is defined here to decouple the workspace from knowledge of other specs'
 * internal event structures.</p>
 */
public class MissionReflectedEvent {

    private final UUID fatherId;
    private final UUID missionId;
    private final UUID childId;
    private final Instant reflectedAt;

    public MissionReflectedEvent(UUID fatherId, UUID missionId, UUID childId, Instant reflectedAt) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId is required");
        }
        if (missionId == null) {
            throw new IllegalArgumentException("missionId is required");
        }
        this.fatherId = fatherId;
        this.missionId = missionId;
        this.childId = childId;
        this.reflectedAt = reflectedAt != null ? reflectedAt : Instant.now();
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public UUID getChildId() {
        return childId;
    }

    public Instant getReflectedAt() {
        return reflectedAt;
    }
}
