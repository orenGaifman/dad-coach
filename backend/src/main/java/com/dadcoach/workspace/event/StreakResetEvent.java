package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a father's streak is reset due to inactivity.
 */
public class StreakResetEvent extends WorkspaceDomainEvent {

    private final int previousStreakDays;

    public StreakResetEvent(UUID fatherId, int previousStreakDays) {
        super(fatherId);
        this.previousStreakDays = previousStreakDays;
    }

    public int getPreviousStreakDays() {
        return previousStreakDays;
    }
}
