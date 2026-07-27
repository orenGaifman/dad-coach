package com.dadcoach.domain.notification;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.notification.NotificationType;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Notification domain logic.
 *
 * Tests three correctness properties from the design document:
 * - Property 23: Quiet hours enforcement
 * - Property 24: Daily notification rate limit
 * - Property 25: Priority deconfliction
 */
class NotificationPropertyTests {

    // ─── Property 23: Quiet Hours Enforcement ────────────────────────────

    /**
     * **Validates: Requirements 10.1, 14.1**
     *
     * For any notification with scheduled_for time falling between 21:00 and 07:00
     * in the Father's local timezone, the effective delivery time should be rescheduled
     * to 07:00 the following morning.
     */
    @Property
    void notificationsInQuietHoursShouldBeRescheduledTo0700(
            @ForAll("quietHoursTimes") LocalTime quietTime,
            @ForAll("validTimezones") String timezoneId,
            @ForAll("validDates") LocalDate date) {

        ZoneId zone = ZoneId.of(timezoneId);
        ZonedDateTime scheduledZdt = ZonedDateTime.of(date, quietTime, zone);
        Instant scheduledFor = scheduledZdt.toInstant();

        Instant effective = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        // The effective time should be 07:00 in the father's timezone
        ZonedDateTime effectiveLocal = effective.atZone(zone);
        if (effectiveLocal.toLocalTime().getHour() != 7 || effectiveLocal.toLocalTime().getMinute() != 0) {
            throw new AssertionError(
                    "Notification scheduled at " + quietTime + " in " + timezoneId
                            + " should be rescheduled to 07:00 but got " + effectiveLocal.toLocalTime());
        }

        // If original time was >= 21:00, effective date should be next day
        if (!quietTime.isBefore(LocalTime.of(21, 0))) {
            LocalDate expectedDate = date.plusDays(1);
            if (!effectiveLocal.toLocalDate().equals(expectedDate)) {
                throw new AssertionError(
                        "Notification scheduled at " + quietTime + " on " + date
                                + " should be rescheduled to " + expectedDate
                                + " but got " + effectiveLocal.toLocalDate());
            }
        }

        // If original time was < 07:00 (after midnight), effective date should be same day
        if (quietTime.isBefore(LocalTime.of(7, 0))) {
            if (!effectiveLocal.toLocalDate().equals(date)) {
                throw new AssertionError(
                        "Notification scheduled at " + quietTime + " on " + date
                                + " should be rescheduled to same day but got " + effectiveLocal.toLocalDate());
            }
        }
    }

    /**
     * **Validates: Requirements 10.1, 14.1**
     *
     * For any notification with scheduled_for time outside quiet hours (07:00-21:00),
     * the scheduled time should remain unchanged.
     */
    @Property
    void notificationsOutsideQuietHoursShouldRemainUnchanged(
            @ForAll("outsideQuietHoursTimes") LocalTime normalTime,
            @ForAll("validTimezones") String timezoneId,
            @ForAll("validDates") LocalDate date) {

        ZoneId zone = ZoneId.of(timezoneId);
        ZonedDateTime scheduledZdt = ZonedDateTime.of(date, normalTime, zone);
        Instant scheduledFor = scheduledZdt.toInstant();

        Instant effective = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        if (!effective.equals(scheduledFor)) {
            throw new AssertionError(
                    "Notification scheduled at " + normalTime + " in " + timezoneId
                            + " (outside quiet hours) should remain unchanged but was modified to "
                            + effective.atZone(zone).toLocalTime());
        }
    }

    /**
     * **Validates: Requirements 10.1, 14.1**
     *
     * The isInQuietHours method should return true for any time between 21:00 and 07:00
     * (exclusive at 07:00) and false otherwise.
     */
    @Property
    void isInQuietHoursCorrectness(@ForAll @IntRange(min = 0, max = 23) int hour,
                                    @ForAll @IntRange(min = 0, max = 59) int minute) {
        LocalTime time = LocalTime.of(hour, minute);
        boolean result = NotificationServiceImpl.isInQuietHours(time);

        // Quiet hours: 21:00 <= time OR time < 07:00
        boolean expected = (hour >= 21) || (hour < 7);

        if (result != expected) {
            throw new AssertionError(
                    "isInQuietHours(" + time + ") returned " + result + " but expected " + expected);
        }
    }

    // ─── Property 24: Daily Notification Rate Limit ──────────────────────

    /**
     * **Validates: Requirements 10.2**
     *
     * For any Father on any day, proactive notification count should not exceed 5.
     * When the limit is reached, scheduling a new notification should be rejected.
     */
    @Property
    void dailyLimitShouldRejectWhenAtMax(
            @ForAll("notificationTypes") NotificationType type,
            @ForAll @IntRange(min = 5, max = 20) int existingCount) {

        NotificationRepository mockRepo = mock(NotificationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone("Asia/Jerusalem");

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockRepo.countDailyByFather(eq(1L), any(), any())).thenReturn(existingCount);

        NotificationServiceImpl service = new NotificationServiceImpl(mockRepo, mockFatherRepo);

        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        Instant scheduledFor = LocalDateTime.of(2024, 6, 15, 10, 0).atZone(zone).toInstant();

        try {
            service.scheduleNotification(1L, type, "Test content", scheduledFor);
            throw new AssertionError(
                    "Expected BusinessRuleViolationException when daily count is " + existingCount
                            + " (limit is " + NotificationServiceImpl.MAX_DAILY_NOTIFICATIONS + ")");
        } catch (BusinessRuleViolationException e) {
            if (!e.getMessage().contains("DAILY_NOTIFICATION_LIMIT_EXCEEDED")) {
                throw new AssertionError(
                        "Expected DAILY_NOTIFICATION_LIMIT_EXCEEDED but got: " + e.getMessage());
            }
        }
    }

    /**
     * **Validates: Requirements 10.2**
     *
     * For any Father with fewer than 5 notifications on a day, scheduling should succeed.
     */
    @Property
    void schedulingShouldSucceedWithinDailyLimit(
            @ForAll("notificationTypes") NotificationType type,
            @ForAll @IntRange(min = 0, max = 4) int existingCount) {

        NotificationRepository mockRepo = mock(NotificationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);
        father.setTimezone("Asia/Jerusalem");

        when(mockFatherRepo.findById(1L)).thenReturn(Optional.of(father));
        when(mockRepo.countDailyByFather(eq(1L), any(), any())).thenReturn(existingCount);
        when(mockRepo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });
        when(mockRepo.findById(100L)).thenAnswer(inv -> {
            Notification n = new Notification(father, type, "Test", 5, Instant.now());
            n.setId(100L);
            return Optional.of(n);
        });
        when(mockRepo.findByFatherIdAndScheduledFor(eq(1L), any())).thenReturn(List.of());

        NotificationServiceImpl service = new NotificationServiceImpl(mockRepo, mockFatherRepo);

        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        Instant scheduledFor = LocalDateTime.of(2024, 6, 15, 10, 0).atZone(zone).toInstant();

        Notification result = service.scheduleNotification(1L, type, "Test content", scheduledFor);

        if (result == null) {
            throw new AssertionError(
                    "Scheduling should succeed when daily count is " + existingCount
                            + " (below limit of " + NotificationServiceImpl.MAX_DAILY_NOTIFICATIONS + ")");
        }
    }

    // ─── Property 25: Priority Deconfliction ─────────────────────────────

    /**
     * **Validates: Requirements 14.7**
     *
     * For any set of N notifications queued for the same Father at the same scheduled_for time,
     * only the highest-priority notification should be sent at that time.
     * The remaining N-1 notifications should be rescheduled at 2-hour intervals in priority order.
     */
    @Property
    void highestPriorityShouldKeepOriginalTime(
            @ForAll @IntRange(min = 2, max = 8) int numNotifications) {

        NotificationRepository mockRepo = mock(NotificationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Instant scheduledFor = Instant.parse("2024-06-15T08:00:00Z");

        // Create notifications with distinct priorities
        List<Notification> notifications = new ArrayList<>();
        Random rng = new Random(42);
        List<Integer> priorities = new ArrayList<>();
        for (int i = 0; i < numNotifications; i++) {
            priorities.add(i + 1);
        }
        Collections.shuffle(priorities, rng);

        for (int i = 0; i < numNotifications; i++) {
            Notification n = new Notification(father, NotificationType.DAILY_COACHING,
                    "Content " + i, priorities.get(i), scheduledFor);
            n.setId((long) (i + 1));
            notifications.add(n);
        }

        when(mockRepo.findByFatherIdAndScheduledFor(1L, scheduledFor)).thenReturn(notifications);
        when(mockRepo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationServiceImpl service = new NotificationServiceImpl(mockRepo, mockFatherRepo);
        service.deconflictNotifications(1L, scheduledFor);

        // Find the highest priority notification (lowest number)
        Notification highestPriority = notifications.stream()
                .min(Comparator.comparingInt(Notification::getPriority))
                .orElseThrow();

        // Highest priority should keep original time
        if (!highestPriority.getScheduledFor().equals(scheduledFor)) {
            throw new AssertionError(
                    "Highest priority notification (priority=" + highestPriority.getPriority()
                            + ") should keep original time " + scheduledFor
                            + " but got " + highestPriority.getScheduledFor());
        }

        // All others should be rescheduled at 2h intervals
        List<Notification> sorted = new ArrayList<>(notifications);
        sorted.sort(Comparator.comparingInt(Notification::getPriority));

        for (int i = 1; i < sorted.size(); i++) {
            Instant expectedTime = scheduledFor.plus(Duration.ofHours(2L * i));
            Notification n = sorted.get(i);
            if (!n.getScheduledFor().equals(expectedTime)) {
                throw new AssertionError(
                        "Notification at priority position " + i + " (priority=" + n.getPriority()
                                + ") should be at " + expectedTime + " but got " + n.getScheduledFor());
            }
        }
    }

    /**
     * **Validates: Requirements 14.7**
     *
     * After deconfliction, all notification times should be distinct (no two at the same time).
     */
    @Property
    void afterDeconflictionAllTimesShouldBeDistinct(
            @ForAll @IntRange(min = 2, max = 6) int numNotifications) {

        NotificationRepository mockRepo = mock(NotificationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Instant scheduledFor = Instant.parse("2024-06-15T08:00:00Z");

        List<Notification> notifications = new ArrayList<>();
        for (int i = 0; i < numNotifications; i++) {
            Notification n = new Notification(father, NotificationType.DAILY_COACHING,
                    "Content " + i, i + 1, scheduledFor);
            n.setId((long) (i + 1));
            notifications.add(n);
        }

        when(mockRepo.findByFatherIdAndScheduledFor(1L, scheduledFor)).thenReturn(notifications);
        when(mockRepo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationServiceImpl service = new NotificationServiceImpl(mockRepo, mockFatherRepo);
        service.deconflictNotifications(1L, scheduledFor);

        Set<Instant> times = new HashSet<>();
        for (Notification n : notifications) {
            if (!times.add(n.getScheduledFor())) {
                throw new AssertionError(
                        "After deconfliction, found duplicate time " + n.getScheduledFor()
                                + " for notification with priority " + n.getPriority());
            }
        }
    }

    /**
     * **Validates: Requirements 14.7**
     *
     * Deconfliction should maintain priority ordering — notifications with lower priority
     * numbers (higher priority) should be scheduled earlier.
     */
    @Property
    void deconflictionShouldMaintainPriorityOrder(
            @ForAll @IntRange(min = 2, max = 6) int numNotifications) {

        NotificationRepository mockRepo = mock(NotificationRepository.class);
        FatherRepository mockFatherRepo = mock(FatherRepository.class);

        Father father = new Father("+972501234567");
        father.setId(1L);

        Instant scheduledFor = Instant.parse("2024-06-15T08:00:00Z");

        // Create with shuffled priorities
        List<Notification> notifications = new ArrayList<>();
        List<Integer> priorities = new ArrayList<>();
        for (int i = 0; i < numNotifications; i++) {
            priorities.add(i + 1);
        }
        Collections.shuffle(priorities, new Random(numNotifications));

        for (int i = 0; i < numNotifications; i++) {
            Notification n = new Notification(father, NotificationType.DAILY_COACHING,
                    "Content " + i, priorities.get(i), scheduledFor);
            n.setId((long) (i + 1));
            notifications.add(n);
        }

        when(mockRepo.findByFatherIdAndScheduledFor(1L, scheduledFor)).thenReturn(notifications);
        when(mockRepo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationServiceImpl service = new NotificationServiceImpl(mockRepo, mockFatherRepo);
        service.deconflictNotifications(1L, scheduledFor);

        // Sort by priority (ascending = highest priority first)
        List<Notification> sorted = new ArrayList<>(notifications);
        sorted.sort(Comparator.comparingInt(Notification::getPriority));

        // Verify each successive notification is scheduled later
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (!sorted.get(i).getScheduledFor().isBefore(sorted.get(i + 1).getScheduledFor())) {
                throw new AssertionError(
                        "Notification with priority " + sorted.get(i).getPriority()
                                + " should be scheduled before priority " + sorted.get(i + 1).getPriority()
                                + " but times are " + sorted.get(i).getScheduledFor()
                                + " and " + sorted.get(i + 1).getScheduledFor());
            }
        }
    }

    // ─── Providers ───────────────────────────────────────────────────────

    @Provide
    Arbitrary<LocalTime> quietHoursTimes() {
        // Times between 21:00-23:59 and 00:00-06:59
        return Arbitraries.oneOf(
                // After 21:00
                Arbitraries.integers().between(21, 23).flatMap(hour ->
                        Arbitraries.integers().between(0, 59).map(minute ->
                                LocalTime.of(hour, minute))),
                // Before 07:00
                Arbitraries.integers().between(0, 6).flatMap(hour ->
                        Arbitraries.integers().between(0, 59).map(minute ->
                                LocalTime.of(hour, minute)))
        );
    }

    @Provide
    Arbitrary<LocalTime> outsideQuietHoursTimes() {
        // Times between 07:00-20:59
        return Arbitraries.integers().between(7, 20).flatMap(hour ->
                Arbitraries.integers().between(0, 59).map(minute ->
                        LocalTime.of(hour, minute)));
    }

    @Provide
    Arbitrary<String> validTimezones() {
        return Arbitraries.of(
                "Asia/Jerusalem", "America/New_York", "Europe/London",
                "Asia/Tokyo", "Australia/Sydney", "US/Pacific"
        );
    }

    @Provide
    Arbitrary<LocalDate> validDates() {
        return Arbitraries.integers().between(2024, 2025).flatMap(year ->
                Arbitraries.integers().between(1, 12).flatMap(month ->
                        Arbitraries.integers().between(1, 28).map(day ->
                                LocalDate.of(year, month, day))));
    }

    @Provide
    Arbitrary<NotificationType> notificationTypes() {
        return Arbitraries.of(NotificationType.values());
    }
}
