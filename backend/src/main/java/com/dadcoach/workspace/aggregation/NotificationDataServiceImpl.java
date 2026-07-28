package com.dadcoach.workspace.aggregation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Stub implementation of {@link NotificationDataService}.
 * Returns empty results until wired to the real Notification domain layer.
 */
@Service
public class NotificationDataServiceImpl implements NotificationDataService {

    @Override
    public int getUnreadCount(UUID fatherId) {
        return 0;
    }

    @Override
    public int getTotalCount30Days(UUID fatherId) {
        return 0;
    }

    @Override
    public List<NotificationReadModel> getNotifications(UUID fatherId, int page, int pageSize) {
        return List.of();
    }

    @Override
    public void markAsRead(UUID fatherId, List<UUID> notificationIds) {
        // no-op
    }

    @Override
    public void markAllRead(UUID fatherId) {
        // no-op
    }
}
