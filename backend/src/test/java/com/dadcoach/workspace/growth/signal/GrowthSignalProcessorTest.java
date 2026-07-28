package com.dadcoach.workspace.growth.signal;

import com.dadcoach.workspace.event.ConversationCompletedEvent;
import com.dadcoach.workspace.event.GoalCompletedEvent;
import com.dadcoach.workspace.event.GoalProgressEvent;
import com.dadcoach.workspace.event.GrowthSignalRecordedEvent;
import com.dadcoach.workspace.event.MissionCompletedEvent;
import com.dadcoach.workspace.event.MissionReflectedEvent;
import com.dadcoach.workspace.event.PositiveActivityReportedEvent;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;
import com.dadcoach.workspace.growth.achievement.AchievementEvaluator;
import com.dadcoach.workspace.growth.achievement.AchievementRepository;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.milestone.MilestoneEvaluator;
import com.dadcoach.workspace.growth.milestone.MilestoneRepository;
import com.dadcoach.workspace.growth.score.GrowthScoreService;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GrowthSignalProcessorImpl}.
 */
@ExtendWith(MockitoExtension.class)
class GrowthSignalProcessorTest {

    @Mock
    private GrowthSignalService growthSignalService;

    @Mock
    private GrowthScoreService growthScoreService;

    @Mock
    private BeltProgressionService beltProgressionService;

    @Mock
    private StreakService streakService;

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

    private GrowthSignalProcessorImpl processor;

    @BeforeEach
    void setUp() {
        processor = new GrowthSignalProcessorImpl(growthSignalService, growthScoreService, beltProgressionService, streakService, achievementEvaluator, milestoneEvaluator, achievementRepository, milestoneRepository, eventPublisher);
        // By default, evaluatePromotion returns empty (no promotion)
        lenient().when(beltProgressionService.evaluatePromotion(any(), anyInt())).thenReturn(Optional.empty());
        // By default, recordQualifyingInteraction returns a non-milestone streak
        lenient().when(streakService.recordQualifyingInteraction(any(), any())).thenReturn(1);
        // By default, achievement and milestone evaluators return empty lists
        lenient().when(achievementEvaluator.evaluateAll(any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(milestoneEvaluator.evaluateAll(any())).thenReturn(java.util.Collections.emptyList());
    }

    private GrowthSignal buildSignal(GrowthSignalType type, UUID fatherId, UUID sourceEntityId, int points) {
        return GrowthSignal.builder()
                .fatherId(fatherId)
                .signalType(type)
                .pointsAwarded(points)
                .sourceEntityId(sourceEntityId)
                .sourceEntityType("test")
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("onMissionCompleted")
    class OnMissionCompletedTests {

        @Test
        void shouldRecordSignalAndPublishEvent() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionCompletedEvent event = new MissionCompletedEvent(fatherId, missionId, null, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MISSION_COMPLETED, fatherId, missionId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.MISSION_COMPLETED, fatherId, missionId, 10);
            when(growthSignalService.recordSignal(GrowthSignalType.MISSION_COMPLETED, fatherId, missionId, "mission"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(110);

            processor.onMissionCompleted(event);

            verify(growthScoreService).incrementScore(fatherId, 10);
            ArgumentCaptor<GrowthSignalRecordedEvent> captor = ArgumentCaptor.forClass(GrowthSignalRecordedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            GrowthSignalRecordedEvent published = captor.getValue();
            assertThat(published.getFatherId()).isEqualTo(fatherId);
            assertThat(published.getSignalType()).isEqualTo(GrowthSignalType.MISSION_COMPLETED);
            assertThat(published.getPointsAwarded()).isEqualTo(10);
            assertThat(published.getNewTotalScore()).isEqualTo(110);
        }

        @Test
        void shouldSkipDuplicateSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionCompletedEvent event = new MissionCompletedEvent(fatherId, missionId, null, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MISSION_COMPLETED, fatherId, missionId))
                    .thenReturn(true);

            processor.onMissionCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(growthScoreService, never()).incrementScore(any(), anyInt());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onMissionReflected")
    class OnMissionReflectedTests {

        @Test
        void shouldRecordSignalAndPublishEvent() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionReflectedEvent event = new MissionReflectedEvent(fatherId, missionId, null, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MISSION_REFLECTED, fatherId, missionId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.MISSION_REFLECTED, fatherId, missionId, 5);
            when(growthSignalService.recordSignal(GrowthSignalType.MISSION_REFLECTED, fatherId, missionId, "mission"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(115);

            processor.onMissionReflected(event);

            verify(growthScoreService).incrementScore(fatherId, 5);
            ArgumentCaptor<GrowthSignalRecordedEvent> captor = ArgumentCaptor.forClass(GrowthSignalRecordedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getSignalType()).isEqualTo(GrowthSignalType.MISSION_REFLECTED);
            assertThat(captor.getValue().getPointsAwarded()).isEqualTo(5);
        }

        @Test
        void shouldSkipDuplicateSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID missionId = UUID.randomUUID();
            MissionReflectedEvent event = new MissionReflectedEvent(fatherId, missionId, null, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MISSION_REFLECTED, fatherId, missionId))
                    .thenReturn(true);

            processor.onMissionReflected(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onGoalProgress")
    class OnGoalProgressTests {

        @Test
        void shouldRecordSignalWhenProgressIncreaseIsAtLeast10Percent() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(fatherId, goalId, 40, 50, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId, 15);
            when(growthSignalService.recordSignal(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId, "goal"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(65);

            processor.onGoalProgress(event);

            verify(growthScoreService).incrementScore(fatherId, 15);
            verify(eventPublisher).publishEvent(any(GrowthSignalRecordedEvent.class));
        }

        @Test
        void shouldRecordSignalWhenProgressIncreaseIsExactly10Percent() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(fatherId, goalId, 20, 30, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId, 15);
            when(growthSignalService.recordSignal(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId, "goal"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(15);

            processor.onGoalProgress(event);

            verify(growthScoreService).incrementScore(fatherId, 15);
        }

        @Test
        void shouldSkipWhenProgressIncreaseIsLessThan10Percent() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(fatherId, goalId, 20, 29, Instant.now());

            processor.onGoalProgress(event);

            verify(growthSignalService, never()).isDuplicate(any(), any(), any());
            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldSkipDuplicateGoalProgressSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalProgressEvent event = new GoalProgressEvent(fatherId, goalId, 40, 60, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.GOAL_PROGRESS, fatherId, goalId))
                    .thenReturn(true);

            processor.onGoalProgress(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onGoalCompleted")
    class OnGoalCompletedTests {

        @Test
        void shouldRecordSignalAndPublishEvent() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalCompletedEvent event = new GoalCompletedEvent(fatherId, goalId, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.GOAL_COMPLETED, fatherId, goalId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.GOAL_COMPLETED, fatherId, goalId, 50);
            when(growthSignalService.recordSignal(GrowthSignalType.GOAL_COMPLETED, fatherId, goalId, "goal"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(200);

            processor.onGoalCompleted(event);

            verify(growthScoreService).incrementScore(fatherId, 50);
            ArgumentCaptor<GrowthSignalRecordedEvent> captor = ArgumentCaptor.forClass(GrowthSignalRecordedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getPointsAwarded()).isEqualTo(50);
        }

        @Test
        void shouldSkipDuplicateSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID goalId = UUID.randomUUID();
            GoalCompletedEvent event = new GoalCompletedEvent(fatherId, goalId, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.GOAL_COMPLETED, fatherId, goalId))
                    .thenReturn(true);

            processor.onGoalCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onConversationCompleted")
    class OnConversationCompletedTests {

        @Test
        void shouldRecordSignalWhenQualityAndExchangesMeetThreshold() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 10, 0.8, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, conversationId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, conversationId, 8);
            when(growthSignalService.recordSignal(GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, conversationId, "conversation"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(58);

            processor.onConversationCompleted(event);

            verify(growthScoreService).incrementScore(fatherId, 8);
            verify(eventPublisher).publishEvent(any(GrowthSignalRecordedEvent.class));
        }

        @Test
        void shouldSkipWhenQualityRatingIsTooLow() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 10, 0.5, Instant.now());

            processor.onConversationCompleted(event);

            verify(growthSignalService, never()).isDuplicate(any(), any(), any());
            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldSkipWhenQualityRatingIsExactly0Point6() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 10, 0.6, Instant.now());

            processor.onConversationCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldSkipWhenExchangeCountIsTooLow() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 4, 0.9, Instant.now());

            processor.onConversationCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldSkipWhenExchangeCountIsExactly5() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 5, 0.9, Instant.now());

            processor.onConversationCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldSkipDuplicateMeaningfulConversationSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID conversationId = UUID.randomUUID();
            ConversationCompletedEvent event = new ConversationCompletedEvent(
                    fatherId, conversationId, "coaching", 10, 0.8, Instant.now());

            when(growthSignalService.isDuplicate(GrowthSignalType.MEANINGFUL_CONVERSATION, fatherId, conversationId))
                    .thenReturn(true);

            processor.onConversationCompleted(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onQualityTimeReported")
    class OnQualityTimeReportedTests {

        @Test
        void shouldRecordSignalAndPublishEvent() {
            UUID fatherId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            QualityTimeReportedEvent event = new QualityTimeReportedEvent(
                    fatherId, childId, 30, LocalDate.now(), reportId);

            when(growthSignalService.isDuplicate(GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, reportId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, reportId, 12);
            when(growthSignalService.recordSignal(GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, reportId, "activity_report"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(62);

            processor.onQualityTimeReported(event);

            verify(growthScoreService).incrementScore(fatherId, 12);
            ArgumentCaptor<GrowthSignalRecordedEvent> captor = ArgumentCaptor.forClass(GrowthSignalRecordedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getSignalType()).isEqualTo(GrowthSignalType.QUALITY_TIME_REPORTED);
            assertThat(captor.getValue().getPointsAwarded()).isEqualTo(12);
        }

        @Test
        void shouldSkipDuplicateSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            QualityTimeReportedEvent event = new QualityTimeReportedEvent(
                    fatherId, UUID.randomUUID(), 30, LocalDate.now(), reportId);

            when(growthSignalService.isDuplicate(GrowthSignalType.QUALITY_TIME_REPORTED, fatherId, reportId))
                    .thenReturn(true);

            processor.onQualityTimeReported(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("onPositiveActivityReported")
    class OnPositiveActivityReportedTests {

        @Test
        void shouldRecordSignalAndPublishEvent() {
            UUID fatherId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            PositiveActivityReportedEvent event = new PositiveActivityReportedEvent(
                    fatherId, "PRAISE", childId, LocalDate.now(), reportId);

            when(growthSignalService.isDuplicate(GrowthSignalType.POSITIVE_ACTIVITY, fatherId, reportId))
                    .thenReturn(false);
            GrowthSignal signal = buildSignal(GrowthSignalType.POSITIVE_ACTIVITY, fatherId, reportId, 5);
            when(growthSignalService.recordSignal(GrowthSignalType.POSITIVE_ACTIVITY, fatherId, reportId, "activity_report"))
                    .thenReturn(signal);
            when(growthScoreService.getTotalScore(fatherId)).thenReturn(55);

            processor.onPositiveActivityReported(event);

            verify(growthScoreService).incrementScore(fatherId, 5);
            ArgumentCaptor<GrowthSignalRecordedEvent> captor = ArgumentCaptor.forClass(GrowthSignalRecordedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getSignalType()).isEqualTo(GrowthSignalType.POSITIVE_ACTIVITY);
            assertThat(captor.getValue().getPointsAwarded()).isEqualTo(5);
        }

        @Test
        void shouldSkipDuplicateSignal() {
            UUID fatherId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            PositiveActivityReportedEvent event = new PositiveActivityReportedEvent(
                    fatherId, "SHARED_ACTIVITY", UUID.randomUUID(), LocalDate.now(), reportId);

            when(growthSignalService.isDuplicate(GrowthSignalType.POSITIVE_ACTIVITY, fatherId, reportId))
                    .thenReturn(true);

            processor.onPositiveActivityReported(event);

            verify(growthSignalService, never()).recordSignal(any(), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
