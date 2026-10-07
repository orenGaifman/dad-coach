package com.dadcoach.qualitytime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Wall-clock minutes of a set of sessions, counting time where sessions overlap once (the union of their
 * intervals). One block of time with two children is one session now, but legacy data (and any overlap) must
 * still never count the same minutes twice toward the weekly goal. Sessions that do not overlap - also back to
 * back - count exactly their own minutes, as before.
 */
public final class SessionIntervals {

    private SessionIntervals() {
    }

    public record Interval(Instant start, Instant end) {
    }

    public static Interval of(QualityTime session) {
        return new Interval(session.getScheduledStart(), session.getScheduledEnd());
    }

    /** Minutes covered by the union of these intervals (each merged block counted in whole minutes). */
    public static int unionMinutes(Collection<Interval> intervals) {
        List<Interval> sorted = new ArrayList<>();
        for (Interval i : intervals) {
            if (i != null && i.start() != null && i.end() != null && i.end().isAfter(i.start())) {
                sorted.add(i);
            }
        }
        sorted.sort(Comparator.comparing(Interval::start));
        long minutes = 0;
        Instant blockStart = null;
        Instant blockEnd = null;
        for (Interval i : sorted) {
            if (blockEnd != null && i.start().isBefore(blockEnd)) { // overlaps the current block
                if (i.end().isAfter(blockEnd)) {
                    blockEnd = i.end();
                }
                continue;
            }
            if (blockEnd != null) {
                minutes += Duration.between(blockStart, blockEnd).toMinutes();
            }
            blockStart = i.start();
            blockEnd = i.end();
        }
        if (blockEnd != null) {
            minutes += Duration.between(blockStart, blockEnd).toMinutes();
        }
        return (int) minutes;
    }

    /** Minutes of {@code interval} not already covered by {@code covered}. */
    public static int uncoveredMinutes(Interval interval, Collection<Interval> covered) {
        List<Interval> with = new ArrayList<>(covered);
        with.add(interval);
        return Math.max(0, unionMinutes(with) - unionMinutes(covered));
    }

    public static boolean overlaps(Interval a, Interval b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }
}
