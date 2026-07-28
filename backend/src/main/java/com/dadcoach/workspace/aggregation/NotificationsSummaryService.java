package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.dto.response.NotificationsSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates notification data for the workspace notifications summary.
 *
 * <p>Provides unread count, total 30-day count, and paginated notification list.
 * Supports marking notifications as read (individually or all at once).</p>
 */
@Service
public class NotificationsSummaryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationDataService notificationDataService;

    public NotificationsSummaryService(NotificationDataService notificationDataService) {
        this.notificationDataService = notificationDataService;
    }

    /**
     * Retrieves a notification summary with unread count, total count,
     * and paginated notification list.
     *
     * @param fatherId the father's unique identifier
     * @param page     page number (0-based)
     * @param pageSize number of notifications per page (clamped to 1-100)
     * @return the notifications summary response
     */
    public NotificationsSummaryResponse getSummary(UUID fatherId, int page, int pageSize) {
        int effectivePage = Math.max(0, page);
        int effectivePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));

        int unreadCount = notificationDataService.getUnreadCount(fatherId);
        int totalCount = notificationDataService.getTotalCount30Days(fatherId);

        List<NotificationReadModel> notifications = notificationDataService
                .getNotifications(fatherId, effectivePage, effectivePageSize);

        List<NotificationsSummaryResponse.NotificationItem> items = notifications.stream()
                .map(this::buildNotificationItem)
                .toList();

        return new NotificationsSummaryResponse(unreadCount, totalCount, items);
    }

    /**
     * Marks specific notifications as read.
     *
     * @param fatherId        the father's unique identifier
     * @param notificationIds list of notification IDs to mark as read
     */
    public void markAsRead(UUID fatherId, List<UUID> notificationIds) {
        if (notificationIds != null && !notificationIds.isEmpty()) {
            notificationDataService.markAsRead(fatherId, notificationIds);
        }
    }

    /**
     * Marks all unread notifications as read for the father.
     *
     * @param fatherId the father's unique identifier
     */
    public void markAllRead(UUID fatherId) {
        notificationDataService.markAllRead(fatherId);
    }

    private NotificationsSummaryResponse.NotificationItem buildNotificationItem(NotificationReadModel notif) {
        return new NotificationsSummaryResponse.NotificationItem(
                notif.notificationId(),
                notif.type(),
                notif.title(),
                notif.body(),
                notif.createdAt(),
                notif.readAt(),
                notif.actionUrl(),
                notif.priority()
        );
    }
}
