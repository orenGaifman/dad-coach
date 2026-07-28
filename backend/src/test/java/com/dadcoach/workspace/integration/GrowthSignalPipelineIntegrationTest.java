package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.event.GrowthSignalRecordedEvent;
import com.dadcoach.workspace.event.MissionCompletedEvent;
import com.dadcoach.workspace.growth.achievement.AchievementEvaluator;
import com.dadcoach.workspace.growth.achievement.AchievementRepository;
import com.dadcoach.workspace.growth.achievement.FatherAchievement;
import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.belt.FatherBelt;
import com.dadcoach.workspace.growth.milestone.FatherMilestone;
import com.dadcoach.workspace.growth.milestone.MilestoneEvaluator;
import com.dadcoach.workspace.growth.milestone.MilestoneRepository;
import com.dadcoach.workspace.growth.score.GrowthScoreService;
import com.dadcoach.workspace.growth.signal.*;
import com.dadcoach.workspace.growth.streak.FatherStreak;
import com.dadcoach.workspace.growth.streak.FatherStreakRepository;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the full Growth Signal processing pipeline.
 *
 * <p>Verifies the chain: MissionCompletedEvent → GrowthSignalProcessor processes →
 * signal recorded → score updated → belt evaluated → achievement checked →
 * GrowthSignalRecordedEvent published.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.1 - Growth Signal Processing Pipeline Integration")
class GrowthSignalPipelineIntegrationTest {

    @Mock
    private GrowthSignalRepository growthSignalRepository;

    @Mock
    private com.dadcoach.workspace.growth.belt.FatherBeltRepository fatherBeltRepository;

    @Mock
    private FatherStreakRepository fatherStreakRepository;

    @Mock
    private AchievementEvaluator achievementEvaluator;

    @Mock
    private MilestoneEvaluator milestoneEvaluator;

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GrowthSignalService growthSignalService;
    private GrowthScoreService growthScoreService;
    private BeltProgressionService beltProgressionService;
    private StreakService streakService;
    private GrowthSignalProcessorImpl processor;

    @BeforeEach
    void setUp() {
        growthSignalService = new GrowthSignalService(growthSignalRepository);
        growthScoreService = new GrowthScoreService(fatherBeltRepository, growthSignalRepository);
        beltProgressionService = new com.dadcoach.workspace.growth.belt.BeltProgressionServiceImpl(
                fatherBeltRepository, eventPublisher);
        streakService = new StreakService(fatherStreakRepository);

        processor = new GrowthSignalProcessorImpl(
                growthSignalService,
                growthScoreService,
                beltProgressionService,
                streakService,
                achievementEvaluator,
                milestoneEvaluator,
                achievementRepository,
                milestoneRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Full pipeline: MissionCompleted → signal recorded → score updated → belt evaluated → achievements checked → event published")
    void fullPipeline_missionCompleted_processesEntireChain() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        MissionCompletedEvent event = new MissionCompletedEvent(fatherId, missionId, childId, Instant.now());

        // No duplicate exists
        when(growthSignalRepository.existsByFatherIdAndSignalTypeAndSourceEntityId(
                fatherId, GrowthSignalType.MISSION_COMPLETED, missionId))
                .thenReturn(false);

        // Signal save returns the signal
        when(growthSignalRepository.save(any(GrowthSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Score increment: belt exists
        FatherBelt belt = new FatherBelt(fatherId);
        belt.setCurrentScore(0);
        when(fatherBeltRepository.incrementScore(fatherId, 10)).thenReturn(1);

        // getTotalScore: return updated score
        when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));

        // Streak: new streak
        FatherStreak streak = new FatherStreak(fatherId);
        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
        when(fatherStreakRepository.save(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Achievements and milestones: none earned
        when(achievementEvaluator.evaluateAll(fatherId)).thenReturn(Collections.emptyList());
        when(milestoneEvaluator.evaluateAll(fatherId)).thenReturn(Collections.emptyList());

        // When
        processor.onMissionCompleted(event);

        // Then
        // 1. Signal was recorded
        verify(growthSignalRepository).save(argThat(signal ->
                signal.getSignalType() == GrowthSignalType.MISSION_COMPLETED &&
                signal.getFatherId().equals(fatherId) &&
                signal.getPointsAwarded() == 10
        ));

        // 2. Score was incremented
        verify(fatherBeltRepository).incrementScore(fatherId, 10);

        // 3. Belt was evaluated (via findByFatherId called in evaluatePromotion)
        verify(fatherBeltRepository, atLeastOnce()).findByFatherId(fatherId);

        // 4. Achievements were checked
        verify(achievementEvaluator).evaluateAll(fatherId);
        verify(milestoneEvaluator).evaluateAll(fatherId);

        // 5. GrowthSignalRecordedEvent was published
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        boolean signalEventPublished = eventCaptor.getAllValues().stream()
                .anyMatch(e -> e instanceof GrowthSignalRecordedEvent gse
                        && gse.getFatherId().equals(fatherId)
                        && gse.getSignalType() == GrowthSignalType.MISSION_COMPLETED
                        && gse.getPointsAwarded() == 10);
        assertThat(signalEventPublished).isTrue();
    }

    @Test
    @DisplayName("Duplicate signal is skipped - no recording, no score update")
    void duplicateSignal_skipsEntireChain() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        MissionCompletedEvent event = new MissionCompletedEvent(fatherId, missionId, null, Instant.now());

        // Duplicate exists
        when(growthSignalRepository.existsByFatherIdAndSignalTypeAndSourceEntityId(
                fatherId, GrowthSignalType.MISSION_COMPLETED, missionId))
                .thenReturn(true);

        // When
        processor.onMissionCompleted(event);

        // Then - nothing else should happen
        verify(growthSignalRepository, never()).save(any());
        verify(fatherBeltRepository, never()).incrementScore(any(), anyInt());
        verify(achievementEvaluator, never()).evaluateAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Signal processing triggers belt promotion when score crosses threshold")
    void signalProcessing_triggersBeltPromotion_whenScoreCrossesThreshold() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        MissionCompletedEvent event = new MissionCompletedEvent(fatherId, missionId, null, Instant.now());

        when(growthSignalRepository.existsByFatherIdAndSignalTypeAndSourceEntityId(
                fatherId, GrowthSignalType.MISSION_COMPLETED, missionId))
                .thenReturn(false);

        when(growthSignalRepository.save(any(GrowthSignal.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Belt has score 95, signal adds 10 → new score 105 → crosses YELLOW threshold at 100
        FatherBelt belt = new FatherBelt(fatherId);
        belt.setCurrentScore(105);
        when(fatherBeltRepository.incrementScore(fatherId, 10)).thenReturn(1);
        when(fatherBeltRepository.findByFatherId(fatherId)).thenReturn(Optional.of(belt));
        when(fatherBeltRepository.save(any(FatherBelt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FatherStreak streak = new FatherStreak(fatherId);
        when(fatherStreakRepository.findByFatherId(fatherId)).thenReturn(Optional.of(streak));
        when(fatherStreakRepository.save(any(FatherStreak.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(achievementEvaluator.evaluateAll(fatherId)).thenReturn(Collections.emptyList());
        when(milestoneEvaluator.evaluateAll(fatherId)).thenReturn(Collections.emptyList());

        // When
        processor.onMissionCompleted(event);

        // Then - belt promotion should have triggered (belt saved with YELLOW)
        verify(fatherBeltRepository, atLeastOnce()).save(argThat(b ->
                b.getBeltLevel() == BeltLevel.YELLOW
        ));
    }
}
