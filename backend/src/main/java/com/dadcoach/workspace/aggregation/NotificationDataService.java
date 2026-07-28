package com.dadcoach.workspace.aggregation;

import java.util.List;
import java.util.UUID;

/**
 * Interface for reading notification data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Notification
 * domain entity and its persistence layer.</p>
 *
 * // TODO: Wire to actual implementation from SPEC-006 when available
 */
public interface NotificationDataService {

    /**
     * Counts unread notifications for a father.
     *
     * @param fatherId the father's unique identifier
     * @return the count of unread notifications
     */
    int getUnreadCount(UUID fatherId);

    /**
     * Counts total notifications in the last 30 days for a father.
     *
     * @param fatherId the father's unique identifier
     * @return the count of notifications in the last 30 days
     */
    int getTotalCount30Days(UUID fatherId);

    /**
     * Retrieves paginated notifications for a father.
     *
     * @param fatherId the father's unique identifier
     * @param page     page number (0-based)
     * @param pageSize number of notifications per page
     * @return list of notification read models for the requested page
     */
    List<NotificationReadModel> getNotifications(UUID fatherId, int page, int pageSize);

    /**
     * Marks specific notifications as read.
     *
     * @param fatherId        the father's unique identifier (for ownership verification)
     * @param notificationIds list of notification IDs to mark as read
     */
    void markAsRead(UUID fatherId, List<UUID> notificationIds);

    /**
     * Marks all unread notifications as read for a father.
     *
     * @param fatherId the father's unique identifier
     */
    void markAllRead(UUID fatherId);
}
