package com.dadcoach.qualitytime;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.qualitytime.SessionIntervals.Interval;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionIntervalsTest {

    private static Interval at(String start, String end) {
        return new Interval(Instant.parse("2026-11-06T" + start + ":00Z"), Instant.parse("2026-11-06T" + end + ":00Z"));
    }

    @Test
    void overlappingTimeCountsOnceAndSeparateOrBackToBackTimeCountsInFull() {
        assertThat(SessionIntervals.unionMinutes(List.of())).isZero();
        assertThat(SessionIntervals.unionMinutes(List.of(at("09:00", "10:30"), at("09:00", "10:30")))).isEqualTo(90);
        assertThat(SessionIntervals.unionMinutes(List.of(at("10:00", "11:00"), at("09:00", "10:30")))).isEqualTo(120);
        assertThat(SessionIntervals.unionMinutes(List.of(at("09:00", "10:00"), at("10:00", "10:30")))).isEqualTo(90);
        assertThat(SessionIntervals.unionMinutes(List.of(at("09:00", "12:00"), at("10:00", "10:30"), at("13:00", "13:45"))))
                .isEqualTo(225);
    }

    @Test
    void uncoveredMinutesAreWhatAnIntervalAddsToTheOthers() {
        assertThat(SessionIntervals.uncoveredMinutes(at("09:00", "10:30"), List.of(at("09:00", "10:30")))).isZero();
        assertThat(SessionIntervals.uncoveredMinutes(at("10:00", "11:00"), List.of(at("09:00", "10:30")))).isEqualTo(30);
        assertThat(SessionIntervals.uncoveredMinutes(at("12:00", "13:00"), List.of(at("09:00", "10:30")))).isEqualTo(60);
    }

    @Test
    void namesAreJoinedTheHebrewWayAndTheEnglishWay() {
        assertThat(SessionChildren.joinHebrew(List.of())).isNull();
        assertThat(SessionChildren.joinHebrew(List.of("מטר"))).isEqualTo("מטר");
        assertThat(SessionChildren.joinHebrew(List.of("מטר", "נעם"))).isEqualTo("מטר ונעם");
        assertThat(SessionChildren.joinHebrew(List.of("איתמר", "מטר", "נעם"))).isEqualTo("איתמר, מטר ונעם");
        assertThat(SessionChildren.joinEnglish(List.of("Itamar", "Matar", "Noam"))).isEqualTo("Itamar, Matar and Noam");
    }
}
