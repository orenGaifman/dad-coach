package com.dadcoach.workspace.event;

import com.dadcoach.workspace.growth.signal.GrowthSignalType;

import java.util.UUID;

/**
 * Domain event published when a growth signal is successfully recorded.
 *
 * <p>This event is emitted after signal deduplication and persistence, enabling
 * downstream listeners (belt evaluation, achievement checks, cache invalidation)
 * to react to score changes without coupling to the signal recording flow.</p>
 */
public class GrowthSignalRecordedEvent extends WorkspaceDomainEvent {

    private final GrowthSignalType signalType;
    private final int pointsAwarded;
    private final UUID sourceEntityId;
    private final int newTotalScore;

    public GrowthSignalRecordedEvent(UUID fatherId, GrowthSignalType signalType,
                                     int pointsAwarded, UUID sourceEntityId,
                                     int newTotalScore) {
        super(fatherId);
        this.signalType = signalType;
        this.pointsAwarded = pointsAwarded;
        this.sourceEntityId = sourceEntityId;
        this.newTotalScore = newTotalScore;
    }

    public GrowthSignalType getSignalType() {
        return signalType;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public int getNewTotalScore() {
        return newTotalScore;
    }
}
