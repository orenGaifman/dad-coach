package com.dadcoach.domain.notification;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.notification.NotificationType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link NotificationService} with quiet hours enforcement,
 * daily rate limiting, and priority-based deconfliction.
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>Quiet hours 21:00-07:00 in father's local timezone → reschedule to 07:00 next day (Req 10.1)</li>
 *   <li>Max 5 proactive notifications per father per day (Req 10.2)</li>
 *   <li>Priority deconfliction: highest priority (lowest number) wins at same time,
 *       others rescheduled at 2h intervals (Req 14.7)</li>
 * </ul>
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    /** Maximum proactive notifications per father per day. */
    public static final int MAX_DAILY_NOTIFICATIONS = 5;

    /** Maximum retry attempts for failed notifications. */
    public static final int MAX_RETRIES = 5;

    /** Quiet hours start: 21:00 local time. */
    public static final LocalTime QUIET_HOURS_START = LocalTime.of(21, 0);

    /** Quiet hours end: 07:00 local time. */
    public static final LocalTime QUIET_HOURS_END = LocalTime.of(7, 0);

    /** Deconfliction interval between rescheduled notifications. */
    public static final Duration DECONFLICTION_INTERVAL = Duration.ofHours(2);

    private final NotificationRepository notificationRepository;
    private final FatherRepository fatherRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   FatherRepository fatherRepository) {
        this.notificationRepository = notificationRepository;
        this.fatherRepository = fatherRepository;
    }

    @Override
    public Notification scheduleNotification(Long fatherId, NotificationType type,
                                             String content, Instant scheduledFor) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        ZoneId fatherZone = ZoneId.of(father.getTimezone());

        // 1. Apply quiet hours enforcement
        Instant effectiveTime = applyQuietHours(scheduledFor, fatherZone);

        // 2. Check daily rate limit
        LocalDate effectiveDate = effectiveTime.atZone(fatherZone).toLocalDate();
        int dailyCount = getDailyNotificationCount(fatherId, effectiveDate);
        if (dailyCount >= MAX_DAILY_NOTIFICATIONS) {
            throw new BusinessRuleViolationException("DAILY_NOTIFICATION_LIMIT_EXCEEDED",
                    "Father " + fatherId + " has already reached the daily limit of "
                            + MAX_DAILY_NOTIFICATIONS + " proactive notifications for " + effectiveDate);
        }

        // 3. Create the notification
        Notification notification = new Notification(father, type, content,
                getDefaultPriority(type), effectiveTime);
        notification = notificationRepository.save(notification);

        // 4. Apply priority deconfliction if there are conflicting notifications at the same time
        deconflictNotifications(fatherId, effectiveTime);

        return notificationRepository.findById(notification.getId()).orElse(notification);
    }

    @Override
    public void dispatchDueNotifications() {
        List<Notification> dueNotifications = notificationRepository.findDue(Instant.now());
        for (Notification notification : dueNotifications) {
            notification.setStatus(NotificationStatus.DISPATCHED);
            notification.setDeliveredAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    public void retryFailedNotifications() {
        List<Notification> failed = notificationRepository.findFailedForRetry(MAX_RETRIES);
        for (Notification notification : failed) {
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setStatus(NotificationStatus.SCHEDULED);
            // Reschedule with exponential backoff
            long backoffSeconds = (long) Math.pow(2, notification.getRetryCount());
            notification.setScheduledFor(Instant.now().plusSeconds(backoffSeconds));
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int getDailyNotificationCount(Long fatherId, LocalDate date) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        ZoneId fatherZone = ZoneId.of(father.getTimezone());
        Instant dayStart = date.atStartOfDay(fatherZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(fatherZone).toInstant();

        return notificationRepository.countDailyByFather(fatherId, dayStart, dayEnd);
    }

    // ─── Quiet Hours Logic ───────────────────────────────────────────────

    /**
     * Applies quiet hours enforcement. If the scheduled time falls between 21:00 and 07:00
     * in the father's local timezone, the notification is rescheduled to 07:00 the following morning.
     *
     * @param scheduledFor the originally requested delivery time
     * @param fatherZone   the father's timezone
     * @return the effective delivery time (adjusted if in quiet hours)
     */
    public static Instant applyQuietHours(Instant scheduledFor, ZoneId fatherZone) {
        ZonedDateTime localTime = scheduledFor.atZone(fatherZone);
        LocalTime time = localTime.toLocalTime();

        if (isInQuietHours(time)) {
            // Reschedule to 07:00 the following morning
            LocalDate nextMorningDate;
            if (time.isBefore(QUIET_HOURS_END)) {
                // Already past midnight but before 07:00 → same calendar day at 07:00
                nextMorningDate = localTime.toLocalDate();
            } else {
                // After 21:00 → next calendar day at 07:00
                nextMorningDate = localTime.toLocalDate().plusDays(1);
            }
            ZonedDateTime nextMorning = nextMorningDate.atTime(QUIET_HOURS_END).atZone(fatherZone);
            return nextMorning.toInstant();
        }

        return scheduledFor;
    }

    /**
     * Determines if a local time falls within quiet hours (21:00-07:00).
     *
     * @param time the local time to check
     * @return true if in quiet hours
     */
    public static boolean isInQuietHours(LocalTime time) {
        // Quiet hours span midnight: 21:00 <= time < 24:00 OR 00:00 <= time < 07:00
        return !time.isBefore(QUIET_HOURS_START) || time.isBefore(QUIET_HOURS_END);
    }

    // ─── Priority Deconfliction ──────────────────────────────────────────

    /**
     * Applies priority-based deconfliction for notifications at the same time.
     * The highest priority notification (lowest priority number) stays at the original time.
     * Others are rescheduled at 2-hour intervals in priority order.
     *
     * @param fatherId    the father's ID
     * @param scheduledFor the conflicting time slot
     */
    void deconflictNotifications(Long fatherId, Instant scheduledFor) {
        List<Notification> conflicting = new java.util.ArrayList<>(notificationRepository
                .findByFatherIdAndScheduledFor(fatherId, scheduledFor));

        if (conflicting.size() <= 1) {
            return; // No conflict to resolve
        }

        // Sort by priority (ascending = highest priority first)
        conflicting.sort(Comparator.comparingInt(Notification::getPriority));

        // First one (highest priority) keeps its time; rest get rescheduled
        for (int i = 1; i < conflicting.size(); i++) {
            Notification n = conflicting.get(i);
            Instant newTime = scheduledFor.plus(DECONFLICTION_INTERVAL.multipliedBy(i));
            n.setScheduledFor(newTime);
            notificationRepository.save(n);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Returns a default priority for each notification type.
     * Priority: 1 = highest, 10 = lowest.
     */
    private int getDefaultPriority(NotificationType type) {
        return switch (type) {
            case DIFFICULT_SITUATION -> 1;
            case BIRTHDAY_REMINDER -> 2;
            case CELEBRATION -> 3;
            case DAILY_COACHING -> 4;
            case MISSION_REMINDER -> 5;
            case WEEKLY_SUMMARY -> 6;
            case INACTIVITY_CHECK -> 7;
            case REACTIVATION -> 8;
        };
    }
}
