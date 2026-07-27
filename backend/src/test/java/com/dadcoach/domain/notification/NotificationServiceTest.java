package com.dadcoach.domain.notification;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.notification.NotificationType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FatherRepository fatherRepository;

    private NotificationServiceImpl service;

    private Father testFather;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, fatherRepository);
        testFather = new Father("+972501234567");
        testFather.setId(1L);
        testFather.setTimezone("Asia/Jerusalem");
    }

    // ─── Quiet Hours Tests ───────────────────────────────────────────────

    @Test
    void shouldRescheduleNotificationInQuietHoursAfter2100() {
        // 22:00 Israel time should be rescheduled to 07:00 next day
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 22, 0); // Saturday 22:00
        Instant scheduledFor = ldt.atZone(zone).toInstant();

        Instant result = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        ZonedDateTime resultLocal = result.atZone(zone);
        assertEquals(LocalTime.of(7, 0), resultLocal.toLocalTime());
        assertEquals(LocalDate.of(2024, 6, 16), resultLocal.toLocalDate());
    }

    @Test
    void shouldRescheduleNotificationInQuietHoursBefore0700() {
        // 03:00 Israel time should be rescheduled to 07:00 same day
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 3, 0);
        Instant scheduledFor = ldt.atZone(zone).toInstant();

        Instant result = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        ZonedDateTime resultLocal = result.atZone(zone);
        assertEquals(LocalTime.of(7, 0), resultLocal.toLocalTime());
        assertEquals(LocalDate.of(2024, 6, 15), resultLocal.toLocalDate());
    }

    @Test
    void shouldNotRescheduleNotificationOutsideQuietHours() {
        // 10:00 Israel time - outside quiet hours
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 10, 0);
        Instant scheduledFor = ldt.atZone(zone).toInstant();

        Instant result = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        assertEquals(scheduledFor, result);
    }

    @Test
    void shouldNotRescheduleNotificationAtExactly0700() {
        // 07:00 is NOT in quiet hours (end of quiet hours is exclusive)
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 7, 0);
        Instant scheduledFor = ldt.atZone(zone).toInstant();

        Instant result = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        assertEquals(scheduledFor, result);
    }

    @Test
    void shouldRescheduleNotificationAtExactly2100() {
        // 21:00 IS in quiet hours
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 21, 0);
        Instant scheduledFor = ldt.atZone(zone).toInstant();

        Instant result = NotificationServiceImpl.applyQuietHours(scheduledFor, zone);

        ZonedDateTime resultLocal = result.atZone(zone);
        assertEquals(LocalTime.of(7, 0), resultLocal.toLocalTime());
        assertEquals(LocalDate.of(2024, 6, 16), resultLocal.toLocalDate());
    }

    // ─── Daily Rate Limit Tests ──────────────────────────────────────────

    @Test
    void shouldThrowWhenDailyLimitExceeded() {
        when(fatherRepository.findById(1L)).thenReturn(Optional.of(testFather));
        when(notificationRepository.countDailyByFather(eq(1L), any(), any())).thenReturn(5);

        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        Instant scheduledFor = LocalDateTime.of(2024, 6, 15, 10, 0).atZone(zone).toInstant();

        assertThrows(BusinessRuleViolationException.class, () ->
                service.scheduleNotification(1L, NotificationType.DAILY_COACHING,
                        "Test content", scheduledFor));
    }

    @Test
    void shouldAllowNotificationWithinDailyLimit() {
        when(fatherRepository.findById(1L)).thenReturn(Optional.of(testFather));
        when(notificationRepository.countDailyByFather(eq(1L), any(), any())).thenReturn(3);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });
        when(notificationRepository.findById(100L)).thenAnswer(inv -> {
            Notification n = new Notification(testFather, NotificationType.DAILY_COACHING, "Test", 4,
                    Instant.now());
            n.setId(100L);
            return Optional.of(n);
        });
        when(notificationRepository.findByFatherIdAndScheduledFor(eq(1L), any())).thenReturn(List.of());

        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        Instant scheduledFor = LocalDateTime.of(2024, 6, 15, 10, 0).atZone(zone).toInstant();

        Notification result = service.scheduleNotification(1L, NotificationType.DAILY_COACHING,
                "Test content", scheduledFor);

        assertNotNull(result);
    }

    // ─── Priority Deconfliction Tests ────────────────────────────────────

    @Test
    void shouldDeconflictNotificationsAtSameTime() {
        Instant scheduledFor = Instant.parse("2024-06-15T08:00:00Z");

        Notification high = new Notification(testFather, NotificationType.DIFFICULT_SITUATION,
                "High priority", 1, scheduledFor);
        high.setId(1L);

        Notification mid = new Notification(testFather, NotificationType.DAILY_COACHING,
                "Mid priority", 4, scheduledFor);
        mid.setId(2L);

        Notification low = new Notification(testFather, NotificationType.INACTIVITY_CHECK,
                "Low priority", 7, scheduledFor);
        low.setId(3L);

        when(notificationRepository.findByFatherIdAndScheduledFor(1L, scheduledFor))
                .thenReturn(List.of(high, mid, low));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deconflictNotifications(1L, scheduledFor);

        // Highest priority keeps original time
        assertEquals(scheduledFor, high.getScheduledFor());
        // Second priority → +2h
        assertEquals(scheduledFor.plus(Duration.ofHours(2)), mid.getScheduledFor());
        // Third priority → +4h
        assertEquals(scheduledFor.plus(Duration.ofHours(4)), low.getScheduledFor());
    }

    @Test
    void shouldNotDeconflictSingleNotification() {
        Instant scheduledFor = Instant.parse("2024-06-15T08:00:00Z");

        Notification single = new Notification(testFather, NotificationType.DAILY_COACHING,
                "Only one", 4, scheduledFor);
        single.setId(1L);

        when(notificationRepository.findByFatherIdAndScheduledFor(1L, scheduledFor))
                .thenReturn(List.of(single));

        service.deconflictNotifications(1L, scheduledFor);

        // Should remain unchanged
        assertEquals(scheduledFor, single.getScheduledFor());
        verify(notificationRepository, never()).save(any());
    }

    // ─── Dispatch Tests ──────────────────────────────────────────────────

    @Test
    void shouldDispatchDueNotifications() {
        Notification due = new Notification(testFather, NotificationType.DAILY_COACHING,
                "Due notification", 4, Instant.now().minusSeconds(60));
        due.setId(1L);

        when(notificationRepository.findDue(any())).thenReturn(List.of(due));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.dispatchDueNotifications();

        assertEquals(NotificationStatus.DISPATCHED, due.getStatus());
        assertNotNull(due.getDeliveredAt());
    }

    // ─── isInQuietHours Tests ────────────────────────────────────────────

    @Test
    void isInQuietHours_boundaries() {
        assertTrue(NotificationServiceImpl.isInQuietHours(LocalTime.of(21, 0)));   // Start of quiet
        assertTrue(NotificationServiceImpl.isInQuietHours(LocalTime.of(23, 59)));  // Before midnight
        assertTrue(NotificationServiceImpl.isInQuietHours(LocalTime.of(0, 0)));    // Midnight
        assertTrue(NotificationServiceImpl.isInQuietHours(LocalTime.of(6, 59)));   // Just before end
        assertFalse(NotificationServiceImpl.isInQuietHours(LocalTime.of(7, 0)));   // End of quiet
        assertFalse(NotificationServiceImpl.isInQuietHours(LocalTime.of(12, 0)));  // Midday
        assertFalse(NotificationServiceImpl.isInQuietHours(LocalTime.of(20, 59))); // Just before start
    }
}
