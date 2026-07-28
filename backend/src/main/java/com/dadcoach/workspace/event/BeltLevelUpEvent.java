package com.dadcoach.workspace.event;

import com.dadcoach.workspace.growth.belt.BeltLevel;

import java.util.UUID;

/**
 * Domain event published when a father's belt level is promoted.
 *
 * <p>This event is emitted by {@code BeltProgressionService.promoteBelt()} and
 * consumed by downstream listeners (celebration creation, notification dispatch,
 * cache invalidation) without coupling to the belt transition logic.</p>
 *
 * <p>Belt transitions are monotonic (AD-8): this event only represents promotions,
 * never regressions.</p>
 */
public class BeltLevelUpEvent extends WorkspaceDomainEvent {

    private final BeltLevel previousBelt;
    private final BeltLevel newBelt;
    private final int currentScore;

    public BeltLevelUpEvent(UUID fatherId, BeltLevel previousBelt, BeltLevel newBelt, int currentScore) {
        super(fatherId);
        this.previousBelt = previousBelt;
        this.newBelt = newBelt;
        this.currentScore = currentScore;
    }

    public BeltLevel getPreviousBelt() {
        return previousBelt;
    }

    public BeltLevel getNewBelt() {
        return newBelt;
    }

    public int getCurrentScore() {
        return currentScore;
    }
}
