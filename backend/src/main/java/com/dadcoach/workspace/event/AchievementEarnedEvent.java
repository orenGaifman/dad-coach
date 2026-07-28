package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a father earns a new achievement.
 *
 * <p>This event is emitted by the {@code GrowthSignalProcessorImpl} after achievement
 * evaluation detects newly earned achievements. Consumed by downstream listeners
 * (celebration creation, notification dispatch, analytics) without coupling to
 * the achievement evaluation logic.</p>
 *
 * @see WorkspaceDomainEvent
 */
public class AchievementEarnedEvent extends WorkspaceDomainEvent {

    private final UUID achievementId;
    private final String achievementName;

    public AchievementEarnedEvent(UUID fatherId, UUID achievementId, String achievementName) {
        super(fatherId);
        this.achievementId = achievementId;
        this.achievementName = achievementName;
    }

    public UUID getAchievementId() {
        return achievementId;
    }

    public String getAchievementName() {
        return achievementName;
    }
}
