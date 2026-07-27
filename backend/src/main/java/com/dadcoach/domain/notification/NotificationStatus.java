package com.dadcoach.domain.notification;

/**
 * Status of a notification in its lifecycle.
 */
public enum NotificationStatus {
    SCHEDULED,
    DISPATCHED,
    DELIVERED,
    FAILED,
    CANCELLED
}
