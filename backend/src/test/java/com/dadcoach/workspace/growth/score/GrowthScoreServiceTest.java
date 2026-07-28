package com.dadcoach.workspace.growth.score;

import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.dadcoach.workspace.growth.belt.FatherBelt;
import com.dadcoach.workspace.growth.belt.FatherBeltRepository;
import com.dadcoach.workspace.growth.signal.GrowthSignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GrowthScoreService}.
 */
@ExtendWith(MockitoExtension.class)
class GrowthScoreServiceTest {

    @Mock
    private FatherBeltRepository fatherBeltRepository;

    @Mock
    private GrowthSignalRepository growthSignalRepository;

    @InjectMocks
    private GrowthScoreService growthScoreService;

    private UUID fatherId;

    @BeforeEach
    void setUp() {
        fatherId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getTotalScore()")
    class GetTotalScoreTests {

        @Test
        @DisplayName("should return cached score when belt record exists")
        void shouldReturnCachedScoreWhenBeltExists() {
            FatherBelt belt = new FatherBelt(fatherId);
            belt.setCurrentScore(250);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            int score = growthScoreService.getTotalScore(fatherId);

            assertThat(score).isEqualTo(250);
            verify(fatherBeltRepository).findByFatherId(fatherId);
            verify(fatherBeltRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create new belt record and return 0 when none exists")
        void shouldCreateNewBeltAndReturnZeroWhenNoneExists() {
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            int score = growthScoreService.getTotalScore(fatherId);

            assertThat(score).isEqualTo(0);
            ArgumentCaptor<FatherBelt> captor = ArgumentCaptor.forClass(FatherBelt.class);
            verify(fatherBeltRepository).save(captor.capture());
            FatherBelt savedBelt = captor.getValue();
            assertThat(savedBelt.getFatherId()).isEqualTo(fatherId);
            assertThat(savedBelt.getBeltLevel()).isEqualTo(BeltLevel.WHITE);
            assertThat(savedBelt.getCurrentScore()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 for belt record with zero score")
        void shouldReturnZeroForBeltWithZeroScore() {
            FatherBelt belt = new FatherBelt(fatherId);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

            int score = growthScoreService.getTotalScore(fatherId);

            assertThat(score).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("rebuildScore()")
    class RebuildScoreTests {

        @Test
        @DisplayName("should rebuild score from growth_signals SUM and update belt")
        void shouldRebuildScoreFromSignalsSumAndUpdateBelt() {
            FatherBelt belt = new FatherBelt(fatherId);
            belt.setCurrentScore(100); // cached value is stale
            when(growthSignalRepository.sumPointsByFatherId(fatherId)).thenReturn(150);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            int rebuilt = growthScoreService.rebuildScore(fatherId);

            assertThat(rebuilt).isEqualTo(150);
            assertThat(belt.getCurrentScore()).isEqualTo(150);
            verify(fatherBeltRepository).save(belt);
        }

        @Test
        @DisplayName("should create new belt when none exists and set authoritative score")
        void shouldCreateNewBeltWhenNoneExistsAndSetScore() {
            when(growthSignalRepository.sumPointsByFatherId(fatherId)).thenReturn(75);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.empty());
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            int rebuilt = growthScoreService.rebuildScore(fatherId);

            assertThat(rebuilt).isEqualTo(75);
            ArgumentCaptor<FatherBelt> captor = ArgumentCaptor.forClass(FatherBelt.class);
            verify(fatherBeltRepository, times(2)).save(captor.capture());
            // Second save should have the authoritative score
            FatherBelt finalBelt = captor.getAllValues().get(1);
            assertThat(finalBelt.getCurrentScore()).isEqualTo(75);
        }

        @Test
        @DisplayName("should return 0 when no signals exist")
        void shouldReturnZeroWhenNoSignalsExist() {
            FatherBelt belt = new FatherBelt(fatherId);
            belt.setCurrentScore(10); // stale cached value
            when(growthSignalRepository.sumPointsByFatherId(fatherId)).thenReturn(0);
            when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            int rebuilt = growthScoreService.rebuildScore(fatherId);

            assertThat(rebuilt).isEqualTo(0);
            assertThat(belt.getCurrentScore()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("incrementScore()")
    class IncrementScoreTests {

        @Test
        @DisplayName("should atomically increment when belt record exists")
        void shouldAtomicallyIncrementWhenBeltExists() {
            when(fatherBeltRepository.incrementScore(fatherId, 10)).thenReturn(1);

            growthScoreService.incrementScore(fatherId, 10);

            verify(fatherBeltRepository).incrementScore(fatherId, 10);
            verify(fatherBeltRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create belt record with initial points when none exists")
        void shouldCreateBeltWithInitialPointsWhenNoneExists() {
            when(fatherBeltRepository.incrementScore(fatherId, 15)).thenReturn(0);
            when(fatherBeltRepository.save(any(FatherBelt.class))).thenAnswer(inv -> inv.getArgument(0));

            growthScoreService.incrementScore(fatherId, 15);

            ArgumentCaptor<FatherBelt> captor = ArgumentCaptor.forClass(FatherBelt.class);
            verify(fatherBeltRepository).save(captor.capture());
            FatherBelt savedBelt = captor.getValue();
            assertThat(savedBelt.getFatherId()).isEqualTo(fatherId);
            assertThat(savedBelt.getCurrentScore()).isEqualTo(15);
            assertThat(savedBelt.getBeltLevel()).isEqualTo(BeltLevel.WHITE);
        }

        @Test
        @DisplayName("should handle large point increments")
        void shouldHandleLargePointIncrements() {
            when(fatherBeltRepository.incrementScore(fatherId, 300)).thenReturn(1);

            growthScoreService.incrementScore(fatherId, 300);

            verify(fatherBeltRepository).incrementScore(fatherId, 300);
        }
    }
}
