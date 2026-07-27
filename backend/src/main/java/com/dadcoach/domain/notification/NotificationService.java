package com.dadcoach.domain.notification;

import com.dadcoach.notification.NotificationType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Service interface for notification scheduling, quiet hours enforcement, and rate limiting.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li>Quiet hours (21:00-07:00 in father's local timezone): notifications rescheduled to 07:00</li>
 *   <li>Daily rate limit: max 5 proactive notifications per father per day</li>
 *   <li>Priority deconfliction: highest priority wins, rest rescheduled at 2h intervals</li>
 * </ul>
 */
public interface NotificationService {

    /**
     * Schedule a notification respecting quiet hours and rate limits.
     *
     * @param fatherId     the father's ID
     * @param type         the notification type
     * @param content      the notification content
     * @param scheduledFor the desired delivery time
     * @return the created Notification entity (may have adjusted scheduledFor due to quiet hours)
     */
    Notification scheduleNotification(Long fatherId, NotificationType type,
                                      String content, Instant scheduledFor);

    /**
     * Dispatch due notifications (called by scheduler).
     */
    void dispatchDueNotifications();

    /**
     * Retry failed notifications with exponential backoff.
     */
    void retryFailedNotifications();

    /**
     * Check daily notification count for a father.
     *
     * @param fatherId the father's ID
     * @param date     the calendar date to check
     * @return the count of proactive notifications on that day
     */
    int getDailyNotificationCount(Long fatherId, LocalDate date);
}
