package com.dadcoach.domain.mission;

import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.mission.LegacyMissionStatus;
import static com.dadcoach.mission.LegacyMissionStatus.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Mission entity covering:
 * - State machine transitions (valid and invalid)
 * - Expiration calculation (weekday vs weekend)
 * - Helper methods (isActive, isTerminal, isExpired)
 */
class MissionTest {

    private Father father;
    private Child child;

    @BeforeEach
    void setUp() {
        father = new Father("+972501234567");
        child = new Child(father, "Test Child", LocalDate.of(2018, 6, 15));
    }

    private Mission createMission() {
        return new Mission(father, child, "Test Mission", "Do something fun",
                "CONNECTION", 2, 20);
    }

    // ─── State Machine Transitions ───────────────────────────────────────

    @Nested
    class StateTransitions {

        @Test
        void newMissionShouldHaveAssignedStatus() {
            Mission mission = createMission();
            assertThat(mission.getStatus()).isEqualTo(ASSIGNED);
        }

        @Test
        void assignedCanTransitionToAccepted() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            assertThat(mission.getStatus()).isEqualTo(ACCEPTED);
            assertThat(mission.getAcceptedAt()).isNotNull();
        }

        @Test
        void assignedCanTransitionToSkipped() {
            Mission mission = createMission();
            mission.transitionTo(SKIPPED);
            assertThat(mission.getStatus()).isEqualTo(SKIPPED);
        }

        @Test
        void assignedCanTransitionToExpired() {
            Mission mission = createMission();
            mission.transitionTo(EXPIRED);
            assertThat(mission.getStatus()).isEqualTo(EXPIRED);
        }

        @Test
        void acceptedCanTransitionToInProgress() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            assertThat(mission.getStatus()).isEqualTo(IN_PROGRESS);
        }

        @Test
        void acceptedCanTransitionToExpired() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(EXPIRED);
            assertThat(mission.getStatus()).isEqualTo(EXPIRED);
        }

        @Test
        void inProgressCanTransitionToCompleted() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            mission.transitionTo(COMPLETED);
            assertThat(mission.getStatus()).isEqualTo(COMPLETED);
            assertThat(mission.getCompletedAt()).isNotNull();
        }

        @Test
        void inProgressCanTransitionToAbandoned() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            mission.transitionTo(ABANDONED);
            assertThat(mission.getStatus()).isEqualTo(ABANDONED);
        }

        @Test
        void completedCanTransitionToReflected() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            mission.transitionTo(COMPLETED);
            mission.transitionTo(REFLECTED);
            assertThat(mission.getStatus()).isEqualTo(REFLECTED);
        }

        @Test
        void invalidTransitionThrowsException() {
            Mission mission = createMission();
            assertThatThrownBy(() -> mission.transitionTo(COMPLETED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void assignedCannotTransitionToInProgress() {
            Mission mission = createMission();
            assertThatThrownBy(() -> mission.transitionTo(IN_PROGRESS))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void skippedCannotTransitionToAnything() {
            Mission mission = createMission();
            mission.transitionTo(SKIPPED);
            assertThatThrownBy(() -> mission.transitionTo(ACCEPTED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void expiredCannotTransitionToAnything() {
            Mission mission = createMission();
            mission.transitionTo(EXPIRED);
            assertThatThrownBy(() -> mission.transitionTo(ACCEPTED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    // ─── Expiration Logic ────────────────────────────────────────────────

    @Nested
    class ExpirationLogic {

        @Test
        void weekdayAssignmentExpires24HoursLater() {
            // Monday
            Instant monday = ZonedDateTime.of(2024, 1, 8, 10, 0, 0, 0, ZoneId.of("UTC"))
                    .toInstant();
            Instant expected = monday.plusSeconds(24 * 3600);
            assertThat(Mission.calculateExpiration(monday)).isEqualTo(expected);
        }

        @Test
        void tuesdayAssignmentExpires24HoursLater() {
            Instant tuesday = ZonedDateTime.of(2024, 1, 9, 14, 30, 0, 0, ZoneId.of("UTC"))
                    .toInstant();
            Instant expected = tuesday.plusSeconds(24 * 3600);
            assertThat(Mission.calculateExpiration(tuesday)).isEqualTo(expected);
        }

        @Test
        void fridayAssignmentExpires24HoursLater() {
            Instant friday = ZonedDateTime.of(2024, 1, 12, 8, 0, 0, 0, ZoneId.of("UTC"))
                    .toInstant();
            Instant expected = friday.plusSeconds(24 * 3600);
            assertThat(Mission.calculateExpiration(friday)).isEqualTo(expected);
        }

        @Test
        void saturdayAssignmentExpires48HoursLater() {
            Instant saturday = ZonedDateTime.of(2024, 1, 13, 10, 0, 0, 0, ZoneId.of("UTC"))
                    .toInstant();
            Instant expected = saturday.plusSeconds(48 * 3600);
            assertThat(Mission.calculateExpiration(saturday)).isEqualTo(expected);
        }

        @Test
        void sundayAssignmentExpires48HoursLater() {
            Instant sunday = ZonedDateTime.of(2024, 1, 14, 9, 0, 0, 0, ZoneId.of("UTC"))
                    .toInstant();
            Instant expected = sunday.plusSeconds(48 * 3600);
            assertThat(Mission.calculateExpiration(sunday)).isEqualTo(expected);
        }

        @Test
        void expirationRespectsTimezone() {
            // A time that is Saturday in Asia/Jerusalem but Friday in UTC
            // Friday 23:00 UTC = Saturday 01:00 Asia/Jerusalem (UTC+2 in winter)
            Instant fridayUtcSaturdayIsrael = ZonedDateTime.of(2024, 1, 12, 23, 0, 0, 0, ZoneId.of("UTC"))
                    .toInstant();

            // In UTC it's Friday → 24h
            Instant utcExpiration = Mission.calculateExpiration(fridayUtcSaturdayIsrael, ZoneId.of("UTC"));
            assertThat(utcExpiration).isEqualTo(fridayUtcSaturdayIsrael.plusSeconds(24 * 3600));

            // In Asia/Jerusalem it's Saturday → 48h
            Instant israelExpiration = Mission.calculateExpiration(fridayUtcSaturdayIsrael, ZoneId.of("Asia/Jerusalem"));
            assertThat(israelExpiration).isEqualTo(fridayUtcSaturdayIsrael.plusSeconds(48 * 3600));
        }

        @Test
        void isWeekendReturnsTrueForSaturdayAndSunday() {
            Instant saturday = ZonedDateTime.of(2024, 1, 13, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            Instant sunday = ZonedDateTime.of(2024, 1, 14, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            assertThat(Mission.isWeekend(saturday, ZoneId.of("UTC"))).isTrue();
            assertThat(Mission.isWeekend(sunday, ZoneId.of("UTC"))).isTrue();
        }

        @Test
        void isWeekendReturnsFalseForWeekdays() {
            Instant monday = ZonedDateTime.of(2024, 1, 8, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            Instant wednesday = ZonedDateTime.of(2024, 1, 10, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            Instant friday = ZonedDateTime.of(2024, 1, 12, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            assertThat(Mission.isWeekend(monday, ZoneId.of("UTC"))).isFalse();
            assertThat(Mission.isWeekend(wednesday, ZoneId.of("UTC"))).isFalse();
            assertThat(Mission.isWeekend(friday, ZoneId.of("UTC"))).isFalse();
        }
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    @Nested
    class HelperMethods {

        @Test
        void isActiveReturnsTrueForAssigned() {
            Mission mission = createMission();
            assertThat(mission.isActive()).isTrue();
        }

        @Test
        void isActiveReturnsTrueForAccepted() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            assertThat(mission.isActive()).isTrue();
        }

        @Test
        void isActiveReturnsTrueForInProgress() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            assertThat(mission.isActive()).isTrue();
        }

        @Test
        void isActiveReturnsFalseForTerminalStates() {
            Mission mission = createMission();
            mission.transitionTo(SKIPPED);
            assertThat(mission.isActive()).isFalse();
        }

        @Test
        void isTerminalReturnsTrueForSkipped() {
            Mission mission = createMission();
            mission.transitionTo(SKIPPED);
            assertThat(mission.isTerminal()).isTrue();
        }

        @Test
        void isTerminalReturnsTrueForReflected() {
            Mission mission = createMission();
            mission.transitionTo(ACCEPTED);
            mission.transitionTo(IN_PROGRESS);
            mission.transitionTo(COMPLETED);
            mission.transitionTo(REFLECTED);
            assertThat(mission.isTerminal()).isTrue();
        }

        @Test
        void isTerminalReturnsFalseForAssigned() {
            Mission mission = createMission();
            assertThat(mission.isTerminal()).isFalse();
        }

        @Test
        void newMissionHasNonNullAssignedAtAndExpiresAt() {
            Mission mission = createMission();
            assertThat(mission.getAssignedAt()).isNotNull();
            assertThat(mission.getExpiresAt()).isNotNull();
        }

        @Test
        void newMissionHasNullAcceptedAtAndCompletedAt() {
            Mission mission = createMission();
            assertThat(mission.getAcceptedAt()).isNull();
            assertThat(mission.getCompletedAt()).isNull();
        }
    }
}
