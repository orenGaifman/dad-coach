package com.dadcoach.workspace.growth.streak;

import com.dadcoach.workspace.dto.response.StreakResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StreakService")
class StreakServiceTest {

    @Mock
    private FatherStreakRepository fatherStreakRepository;

    private StreakService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Default clock: 2024-03-15 at 10:00 UTC
        fixedClock = Clock.fixed(
                Instant.parse("2024-03-15T10:00:00Z"),
                ZoneId.of("UTC")
        );
        service = new StreakService(fatherStreakRepository, fixedClock);
    }

    private FatherStreak createStreak(UUID fatherId, int currentDays, int longestDays,
                                      LocalDate lastQualifying, String timezone) {
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(currentDays);
        streak.setLongestStreakDays(longestDays);
        streak.setLastQualifyingDate(lastQualifying);
        if (currentDays > 0 && lastQualifying != null) {
            streak.setStreakStartDate(lastQualifying.minusDays(currentDays - 1));
        }
        streak.setTimezone(timezone);
        return streak;
    }

    @Nested
    @DisplayName("getStreak")
    class GetStreakTests {

        @Test
        @DisplayName("returns existing streak record")
        void returnsExistingStreak() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak existing = createStreak(fatherId, 5, 10,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(existing));

            FatherStreak result = service.getStreak(fatherId);

            assertThat(result).isSameAs(existing);
            verify(fatherStreakRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates default streak if none exists")
        void createsDefaultStreak() {
            UUID fatherId = UUID.randomUUID();
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            FatherStreak result = service.getStreak(fatherId);

            assertThat(result.getFatherId()).isEqualTo(fatherId);
            assertThat(result.getCurrentStreakDays()).isEqualTo(0);
            assertThat(result.getLongestStreakDays()).isEqualTo(0);
            assertThat(result.getStreakStartDate()).isNull();
            assertThat(result.getLastQualifyingDate()).isNull();
            assertThat(result.getTimezone()).isEqualTo("UTC");
            verify(fatherStreakRepository).save(any(FatherStreak.class));
        }
    }

    @Nested
    @DisplayName("recordQualifyingInteraction")
    class RecordQualifyingInteractionTests {

        @Test
        @DisplayName("no change when interaction already recorded today")
        void noChangeWhenAlreadyRecordedToday() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 3, 5,
                    LocalDate.of(2024, 3, 15), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T14:00:00Z"));

            assertThat(result).isEqualTo(3);
            verify(fatherStreakRepository, never()).save(any());
        }

        @Test
        @DisplayName("increments streak when last interaction was yesterday")
        void incrementsOnConsecutiveDay() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 3, 5,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T08:00:00Z"));

            assertThat(result).isEqualTo(4);
            assertThat(streak.getCurrentStreakDays()).isEqualTo(4);
            assertThat(streak.getLastQualifyingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            verify(fatherStreakRepository).save(streak);
        }

        @Test
        @DisplayName("resets streak when gap of more than one day")
        void resetsOnGap() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 10, 15,
                    LocalDate.of(2024, 3, 12), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T08:00:00Z"));

            assertThat(result).isEqualTo(1);
            assertThat(streak.getCurrentStreakDays()).isEqualTo(1);
            assertThat(streak.getStreakStartDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(streak.getLastQualifyingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            // Longest should remain unchanged since new streak (1) < longest (15)
            assertThat(streak.getLongestStreakDays()).isEqualTo(15);
        }

        @Test
        @DisplayName("starts new streak on first ever interaction")
        void startsNewStreakOnFirstInteraction() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 0, 0, null, "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T08:00:00Z"));

            assertThat(result).isEqualTo(1);
            assertThat(streak.getCurrentStreakDays()).isEqualTo(1);
            assertThat(streak.getLongestStreakDays()).isEqualTo(1);
            assertThat(streak.getStreakStartDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(streak.getLastQualifyingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        }

        @Test
        @DisplayName("updates longest streak when current exceeds it")
        void updatesLongestWhenCurrentExceeds() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T08:00:00Z"));

            assertThat(result).isEqualTo(6);
            assertThat(streak.getLongestStreakDays()).isEqualTo(6);
        }

        @Test
        @DisplayName("timezone-aware: UTC midnight boundary respected for Asia/Jerusalem")
        void timezoneAwareDayBoundary() {
            UUID fatherId = UUID.randomUUID();
            // Father in Asia/Jerusalem (UTC+2 in March)
            // Last interaction: 2024-03-14 (in Jerusalem time)
            FatherStreak streak = createStreak(fatherId, 3, 3,
                    LocalDate.of(2024, 3, 14), "Asia/Jerusalem");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            // 2024-03-14T23:00 UTC = 2024-03-15T01:00 Asia/Jerusalem → new day in Jerusalem
            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-14T23:00:00Z"));

            // In Jerusalem timezone, this is March 15 → consecutive with March 14
            assertThat(result).isEqualTo(4);
            assertThat(streak.getLastQualifyingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        }

        @Test
        @DisplayName("timezone-aware: same UTC time is still 'today' in west timezone")
        void timezoneAwareSameDay() {
            UUID fatherId = UUID.randomUUID();
            // Father in America/New_York (UTC-4 in March)
            // Last interaction was March 14 in NY time
            FatherStreak streak = createStreak(fatherId, 2, 2,
                    LocalDate.of(2024, 3, 14), "America/New_York");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            // 2024-03-15T03:00 UTC = 2024-03-14T23:00 America/New_York → still March 14 in NY
            int result = service.recordQualifyingInteraction(fatherId,
                    Instant.parse("2024-03-15T03:00:00Z"));

            // Still same day in NY timezone → no change
            assertThat(result).isEqualTo(2);
            verify(fatherStreakRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("resetExpiredStreaks")
    class ResetExpiredStreaksTests {

        @Test
        @DisplayName("resets streak when last qualifying date is before yesterday")
        void resetsExpiredStreak() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 10,
                    LocalDate.of(2024, 3, 13), "UTC");
            when(fatherStreakRepository.findAll()).thenReturn(List.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            service.resetExpiredStreaks();

            assertThat(streak.getCurrentStreakDays()).isEqualTo(0);
            assertThat(streak.getStreakStartDate()).isNull();
            // Longest should remain unchanged
            assertThat(streak.getLongestStreakDays()).isEqualTo(10);
            verify(fatherStreakRepository).save(streak);
        }

        @Test
        @DisplayName("does not reset streak from yesterday (still valid)")
        void doesNotResetYesterdayStreak() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 10,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findAll()).thenReturn(List.of(streak));

            service.resetExpiredStreaks();

            assertThat(streak.getCurrentStreakDays()).isEqualTo(5);
            verify(fatherStreakRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not reset streak from today")
        void doesNotResetTodayStreak() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 3, 3,
                    LocalDate.of(2024, 3, 15), "UTC");
            when(fatherStreakRepository.findAll()).thenReturn(List.of(streak));

            service.resetExpiredStreaks();

            assertThat(streak.getCurrentStreakDays()).isEqualTo(3);
            verify(fatherStreakRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips already-reset streaks (currentStreakDays == 0)")
        void skipsAlreadyResetStreaks() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 0, 10,
                    LocalDate.of(2024, 3, 1), "UTC");
            when(fatherStreakRepository.findAll()).thenReturn(List.of(streak));

            service.resetExpiredStreaks();

            verify(fatherStreakRepository, never()).save(any());
        }

        @Test
        @DisplayName("timezone-aware: uses father timezone for day boundary")
        void timezoneAwareReset() {
            // Clock is at 2024-03-15T10:00 UTC
            UUID fatherId = UUID.randomUUID();
            // Father in Pacific/Auckland (UTC+13 in March)
            // In Auckland, it's already 2024-03-15T23:00 → today is March 15
            // Yesterday in Auckland is March 14
            // Last qualifying was March 13 in Auckland → that's before yesterday → should reset
            FatherStreak streak = createStreak(fatherId, 4, 8,
                    LocalDate.of(2024, 3, 13), "Pacific/Auckland");
            when(fatherStreakRepository.findAll()).thenReturn(List.of(streak));
            when(fatherStreakRepository.save(any(FatherStreak.class))).thenAnswer(inv -> inv.getArgument(0));

            service.resetExpiredStreaks();

            assertThat(streak.getCurrentStreakDays()).isEqualTo(0);
            assertThat(streak.getStreakStartDate()).isNull();
            verify(fatherStreakRepository).save(streak);
        }
    }

    @Nested
    @DisplayName("isStreakAtRisk")
    class IsStreakAtRiskTests {

        @Test
        @DisplayName("returns false when no active streak")
        void falseWhenNoActiveStreak() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 0, 5, null, "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = service.isStreakAtRisk(fatherId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when no streak record exists")
        void falseWhenNoRecord() {
            UUID fatherId = UUID.randomUUID();
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());

            boolean result = service.isStreakAtRisk(fatherId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when interaction already recorded today")
        void falseWhenAlreadyRecordedToday() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 15), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = service.isStreakAtRisk(fatherId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false before 18:00 even without today's interaction")
        void falseBeforeEvening() {
            // Clock at 10:00 UTC, father in UTC → before 18:00
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = service.isStreakAtRisk(fatherId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true after 18:00 without today's interaction")
        void trueAfterEveningWithoutInteraction() {
            // Use a clock that's past 18:00 in UTC
            Clock eveningClock = Clock.fixed(
                    Instant.parse("2024-03-15T19:00:00Z"),
                    ZoneId.of("UTC")
            );
            StreakService eveningService = new StreakService(fatherStreakRepository, eveningClock);

            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 14), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = eveningService.isStreakAtRisk(fatherId);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("timezone-aware: at-risk based on father's timezone evening")
        void timezoneAwareRisk() {
            // Clock at 2024-03-15T10:00 UTC
            // Father in Asia/Jerusalem (UTC+2): local time is 12:00 → not past 18:00 → not at risk
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 14), "Asia/Jerusalem");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = service.isStreakAtRisk(fatherId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("timezone-aware: at-risk when past 18:00 in father's timezone")
        void timezoneAwareRiskPastEvening() {
            // Clock at 2024-03-15T10:00 UTC
            // Father in Asia/Tokyo (UTC+9): local time is 19:00 → past 18:00 → at risk
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 5, 5,
                    LocalDate.of(2024, 3, 14), "Asia/Tokyo");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            boolean result = service.isStreakAtRisk(fatherId);

            // In Tokyo, it's 19:00 on March 15. Last interaction was March 14.
            // Today (March 15 in Tokyo) has no interaction and it's past 18:00 → at risk
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getStreakResponse")
    class GetStreakResponseTests {

        @Test
        @DisplayName("builds correct response DTO")
        void buildsCorrectResponse() {
            UUID fatherId = UUID.randomUUID();
            FatherStreak streak = createStreak(fatherId, 7, 14,
                    LocalDate.of(2024, 3, 15), "UTC");
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

            StreakResponse response = service.getStreakResponse(fatherId);

            assertThat(response.getCurrentStreakDays()).isEqualTo(7);
            assertThat(response.getLongestStreakDays()).isEqualTo(14);
            assertThat(response.getStreakStartDate()).isEqualTo(LocalDate.of(2024, 3, 9));
            assertThat(response.getLastQualifyingInteractionDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(response.isStreakAtRisk()).isFalse(); // interaction today
        }

        @Test
        @DisplayName("builds response for fresh streak with defaults")
        void buildsResponseForFreshStreak() {
            UUID fatherId = UUID.randomUUID();
            when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());

            StreakResponse response = service.getStreakResponse(fatherId);

            assertThat(response.getCurrentStreakDays()).isEqualTo(0);
            assertThat(response.getLongestStreakDays()).isEqualTo(0);
            assertThat(response.getStreakStartDate()).isNull();
            assertThat(response.getLastQualifyingInteractionDate()).isNull();
            assertThat(response.isStreakAtRisk()).isFalse();
        }
    }
}
