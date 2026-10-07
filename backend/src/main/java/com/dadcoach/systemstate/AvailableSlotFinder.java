package com.dadcoach.systemstate;

import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Free slots for Quality Time in the father's own timezone, inside family-time windows: Sunday-Thursday evenings
 * 17:00-20:00, Friday 09:00-13:00, Saturday 09:00-12:00 and 16:00-19:00 - minus what is already taken: his booked
 * Dad Coach sessions and, when his Google Calendar is connected, his calendar events. Without a calendar (D-007)
 * the slots are still real: only his own sessions block time. At most 20 slots, each at least 30 minutes.
 */
@Service
public class AvailableSlotFinder {

    private static final Logger log = LoggerFactory.getLogger(AvailableSlotFinder.class);
    /** [startHour, endHour) windows per day; Israel's weekend is Friday-Saturday. */
    static List<int[]> windowsFor(java.time.DayOfWeek day) {
        return switch (day) {
            case FRIDAY -> List.of(new int[]{9, 13});
            case SATURDAY -> List.of(new int[]{9, 12}, new int[]{16, 19});
            default -> List.of(new int[]{17, 20});
        };
    }
    static final int MAX_SLOTS = 20;

    private final QualityTimeRepository qualityTimes;
    private final GoogleCalendarService calendar;
    private final Clock clock;

    public AvailableSlotFinder(QualityTimeRepository qualityTimes, GoogleCalendarService calendar, Clock clock) {
        this.qualityTimes = qualityTimes;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** @param daysAhead 1-14, today included */
    public List<AvailableSlot> find(Father father, ZoneId zone, int daysAhead) {
        if (daysAhead < 1 || daysAhead > 14) {
            throw new IllegalArgumentException("daysAhead must be between 1 and 14");
        }
        Instant now = clock.instant();
        Instant horizon = now.plus(Duration.ofDays(daysAhead));
        List<Busy> busy = new ArrayList<>();
        for (QualityTime qt : qualityTimes.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED)) {
            busy.add(new Busy(qt.getScheduledStart(), qt.getScheduledEnd()));
        }
        if (father.hasGoogleCalendarConfigured()) {
            try {
                for (GoogleCalendarService.CalendarEvent e : calendar.getUpcomingEvents(father, now, horizon, false)) {
                    if (e.startTime() != null && e.endTime() != null) {
                        busy.add(new Busy(e.startTime(), e.endTime()));
                    }
                }
            } catch (RuntimeException e) {
                // the calendar is a refinement, not a requirement: his own sessions still block time
                log.warn("Calendar events unavailable for slots: fatherId={}, error={}", father.getId(), e.getClass().getSimpleName());
            }
        }
        busy.sort(Comparator.comparing(Busy::start));

        List<AvailableSlot> slots = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(now, zone);
        for (int day = 0; day < daysAhead && slots.size() < MAX_SLOTS; day++) {
            LocalDate date = today.plusDays(day);
            for (int[] window : windowsFor(date.getDayOfWeek())) {
                Instant windowStart = date.atTime(window[0], 0).atZone(zone).toInstant();
                Instant windowEnd = date.atTime(window[1], 0).atZone(zone).toInstant();
                if (!windowEnd.isAfter(now)) {
                    continue;
                }
                Instant cursor = windowStart.isBefore(now) ? nextQuarterHour(now) : windowStart;
                for (Busy b : busy) {
                    if (!b.end().isAfter(cursor) || !b.start().isBefore(windowEnd)) {
                        continue;
                    }
                    addIfLongEnough(slots, cursor, b.start());
                    cursor = b.end();
                }
                addIfLongEnough(slots, cursor, windowEnd);
            }
        }
        return slots.size() > MAX_SLOTS ? slots.subList(0, MAX_SLOTS) : slots;
    }

    /** A slot that starts "now" starts at the next quarter hour (17:23 -> 17:30), a time a father can be offered. */
    static Instant nextQuarterHour(Instant instant) {
        long quarter = Duration.ofMinutes(15).getSeconds();
        long seconds = instant.getEpochSecond();
        long rounded = ((seconds + quarter - 1) / quarter) * quarter;
        return Instant.ofEpochSecond(rounded);
    }

    private static void addIfLongEnough(List<AvailableSlot> slots, Instant start, Instant end) {
        if (end.isAfter(start) && Duration.between(start, end).toMinutes() >= AvailableSlot.MINIMUM_DURATION_MINUTES) {
            slots.add(AvailableSlot.of(start, end));
        }
    }

    private record Busy(Instant start, Instant end) {
    }
}
