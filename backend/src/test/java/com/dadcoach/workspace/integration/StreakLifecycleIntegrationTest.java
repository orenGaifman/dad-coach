package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.growth.streak.FatherStreak;
import com.dadcoach.workspace.growth.streak.FatherStreakRepository;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for Streak lifecycle.
 *
 * <p>Verifies: consecutive day interaction → increment, missed day → reset,
 * same day twice → no double increment, milestone detection.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.6 - Streak Lifecycle Integration")
class StreakLifecycleIntegrationTest {

    @Mock
    private FatherStreakRepository fatherStreakRepository;

    private StreakService streakService;

    @BeforeEach
    void setUp() {
        // Use a fixed clock for deterministic tests
        Clock fixedClock = Clock.fixed(
                Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"));
        streakService = new StreakService(fatherStreakRepository, fixedClock);
    }

    @Test
    @DisplayName("Record qualifying interaction on consecutive days → streak incremented")
    void consecutiveDays_streakIncremented() {
        // Given - father has streak from yesterday
        UUID fatherId = UUID.randomUUID();
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(3);
        streak.setLongestStreakDays(3);
        streak.setLastQualifyingDate(LocalDate.of(2024, 1, 14)); // yesterday
        streak.setStreakStartDate(LocalDate.of(2024, 1, 12));

        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
        when(fatherStreakRepository.saveAndFlush(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When - record today (2024-01-15)
        Instant todayMorning = Instant.parse("2024-01-15T08:00:00Z");
        int result = streakService.recordQualifyingInteraction(fatherId, todayMorning);

        // Then - streak incremented from 3 to 4
        assertThat(result).isEqualTo(4);
        assertThat(streak.getCurrentStreakDays()).isEqualTo(4);
        assertThat(streak.getLongestStreakDays()).isEqualTo(4);
        assertThat(streak.getLastQualifyingDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("Miss a day → streak reset to 1 (new streak), longest preserved")
    void missADay_streakResetToOne_longestPreserved() {
        // Given - father has streak, but last interaction was 2 days ago (missed yesterday)
        UUID fatherId = UUID.randomUUID();
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(5);
        streak.setLongestStreakDays(10);
        streak.setLastQualifyingDate(LocalDate.of(2024, 1, 13)); // 2 days ago, yesterday skipped
        streak.setStreakStartDate(LocalDate.of(2024, 1, 9));

        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
        when(fatherStreakRepository.saveAndFlush(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When - record today (2024-01-15), missed yesterday
        Instant todayMorning = Instant.parse("2024-01-15T08:00:00Z");
        int result = streakService.recordQualifyingInteraction(fatherId, todayMorning);

        // Then - streak reset to 1 (starting a new streak), longest preserved at 10
        assertThat(result).isEqualTo(1);
        assertThat(streak.getCurrentStreakDays()).isEqualTo(1);
        assertThat(streak.getLongestStreakDays()).isEqualTo(10); // preserved
        assertThat(streak.getStreakStartDate()).isEqualTo(LocalDate.of(2024, 1, 15)); // new start
    }

    @Test
    @DisplayName("Record interaction on same day twice → no double increment")
    void sameDayTwice_noDoubleIncrement() {
        // Given - father already recorded today
        UUID fatherId = UUID.randomUUID();
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(3);
        streak.setLongestStreakDays(3);
        streak.setLastQualifyingDate(LocalDate.of(2024, 1, 15)); // already today

        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));

        // When - record again today
        Instant todayAfternoon = Instant.parse("2024-01-15T14:00:00Z");
        int result = streakService.recordQualifyingInteraction(fatherId, todayAfternoon);

        // Then - streak stays at 3, no save called
        assertThat(result).isEqualTo(3);
        assertThat(streak.getCurrentStreakDays()).isEqualTo(3);
        verify(fatherStreakRepository, never()).save(any());
    }

    @Test
    @DisplayName("Streak reaches 7 → milestone detected (streak returns 7)")
    void streakReaches7_milestoneDetected() {
        // Given - father has 6-day streak from yesterday
        UUID fatherId = UUID.randomUUID();
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(6);
        streak.setLongestStreakDays(6);
        streak.setLastQualifyingDate(LocalDate.of(2024, 1, 14)); // yesterday
        streak.setStreakStartDate(LocalDate.of(2024, 1, 9));

        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
        when(fatherStreakRepository.saveAndFlush(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When - record today → streak goes from 6 to 7
        Instant todayMorning = Instant.parse("2024-01-15T08:00:00Z");
        int result = streakService.recordQualifyingInteraction(fatherId, todayMorning);

        // Then - streak is now 7 (milestone threshold)
        assertThat(result).isEqualTo(7);
        assertThat(streak.getCurrentStreakDays()).isEqualTo(7);
        assertThat(streak.getLongestStreakDays()).isEqualTo(7);

        // The StreakService itself returns the streak days count.
        // The GrowthSignalProcessorImpl is responsible for detecting this milestone value
        // and recording the STREAK_BONUS_7 signal. That composition is tested in 15.1.
    }

    @Test
    @DisplayName("resetExpiredStreaks resets streaks when last qualifying date is before yesterday")
    void resetExpiredStreaks_resetsOldStreaks() {
        // Given - a streak that hasn't been updated since 2 days ago
        UUID fatherId = UUID.randomUUID();
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(5);
        streak.setLongestStreakDays(8);
        streak.setLastQualifyingDate(LocalDate.of(2024, 1, 13)); // 2 days before clock (Jan 15)

        when(fatherStreakRepository.findAll()).thenReturn(java.util.List.of(streak));
        when(fatherStreakRepository.save(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        streakService.resetExpiredStreaks();

        // Then - current streak reset to 0, longest preserved
        assertThat(streak.getCurrentStreakDays()).isEqualTo(0);
        assertThat(streak.getLongestStreakDays()).isEqualTo(8); // longest preserved
        assertThat(streak.getStreakStartDate()).isNull();
        verify(fatherStreakRepository).save(streak);
    }
}
