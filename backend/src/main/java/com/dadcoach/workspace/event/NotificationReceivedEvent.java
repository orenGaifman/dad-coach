package com.dadcoach.workspace.event;

import java.util.UUID;

/**
 * Domain event published when a new notification is received for a father.
 */
public class NotificationReceivedEvent extends WorkspaceDomainEvent {

    private final UUID notificationId;

    public NotificationReceivedEvent(UUID fatherId, UUID notificationId) {
        super(fatherId);
        this.notificationId = notificationId;
    }

    public UUID getNotificationId() {
        return notificationId;
    }
}
