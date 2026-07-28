package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.aggregation.*;
import com.dadcoach.workspace.dto.response.PartialResponse;
import com.dadcoach.workspace.dto.response.WorkspaceSummaryResponse;
import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.belt.FatherBelt;
import com.dadcoach.workspace.growth.score.GrowthScoreService;
import com.dadcoach.workspace.growth.streak.FatherStreak;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for Workspace Summary with partial degradation.
 *
 * <p>Verifies that when services are available → status "complete",
 * and when services throw → partial response with degraded_sections.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.3 - Workspace Summary Partial Degradation Integration")
class WorkspaceSummaryDegradationIntegrationTest {

    @Mock
    private FatherDataService fatherDataService;

    @Mock
    private GrowthScoreService growthScoreService;

    @Mock
    private BeltProgressionService beltProgressionService;

    @Mock
    private StreakService streakService;

    @Mock
    private NotificationDataService notificationDataService;

    @Mock
    private ChildDataService childDataService;

    @Mock
    private GoalDataService goalDataService;

    @Mock
    private MissionDataService missionDataService;

    private WorkspaceSummaryService workspaceSummaryService;

    @BeforeEach
    void setUp() {
        workspaceSummaryService = new WorkspaceSummaryService(
                fatherDataService,
                growthScoreService,
                beltProgressionService,
                streakService,
                notificationDataService,
                childDataService,
                goalDataService,
                missionDataService
        );
    }

    @Test
    @DisplayName("All services available → PartialResponse with status 'complete'")
    void allServicesAvailable_returnsCompleteResponse() {
        // Given
        UUID fatherId = UUID.randomUUID();
        FatherReadModel father = new FatherReadModel(fatherId, "John Doe", "+1234567890",
                "UTC", null, null, "en", null, Instant.now(), null);
        FatherBelt belt = new FatherBelt(fatherId);
        belt.setCurrentScore(150);
        FatherStreak streak = new FatherStreak(fatherId);
        streak.setCurrentStreakDays(5);

        when(fatherDataService.getFather(fatherId)).thenReturn(Optional.of(father));
        when(growthScoreService.getTotalScore(fatherId)).thenReturn(150);
        when(beltProgressionService.getCurrentBelt(fatherId)).thenReturn(belt);
        when(streakService.getStreak(fatherId)).thenReturn(streak);
        when(notificationDataService.getUnreadCount(fatherId)).thenReturn(3);
        when(childDataService.getChildrenByFatherId(fatherId)).thenReturn(Collections.emptyList());
        when(goalDataService.countActiveGoalsByFatherId(fatherId)).thenReturn(2);
        when(missionDataService.getActiveMission(fatherId)).thenReturn(Optional.empty());

        // When
        PartialResponse<WorkspaceSummaryResponse> response = workspaceSummaryService.getSummary(fatherId);

        // Then
        assertThat(response.getResponseStatus()).isEqualTo("complete");
        assertThat(response.getDegradedSections()).isNull();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getGrowthScore()).isEqualTo(150);
        assertThat(response.getData().getCurrentStreakDays()).isEqualTo(5);
        assertThat(response.getData().getUnreadNotificationsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("GrowthScoreService throws → partial response with 'growth_score' in degraded_sections")
    void growthScoreServiceFails_returnsPartialWithDegradedGrowthScore() {
        // Given
        UUID fatherId = UUID.randomUUID();
        FatherReadModel father = new FatherReadModel(fatherId, "John Doe", "+1234567890",
                "UTC", null, null, "en", null, Instant.now(), null);
        FatherBelt belt = new FatherBelt(fatherId);
        FatherStreak streak = new FatherStreak(fatherId);

        when(fatherDataService.getFather(fatherId)).thenReturn(Optional.of(father));
        when(growthScoreService.getTotalScore(fatherId)).thenThrow(new RuntimeException("DB unavailable"));
        when(beltProgressionService.getCurrentBelt(fatherId)).thenReturn(belt);
        when(streakService.getStreak(fatherId)).thenReturn(streak);
        when(notificationDataService.getUnreadCount(fatherId)).thenReturn(0);
        when(childDataService.getChildrenByFatherId(fatherId)).thenReturn(Collections.emptyList());
        when(goalDataService.countActiveGoalsByFatherId(fatherId)).thenReturn(0);
        when(missionDataService.getActiveMission(fatherId)).thenReturn(Optional.empty());

        // When
        PartialResponse<WorkspaceSummaryResponse> response = workspaceSummaryService.getSummary(fatherId);

        // Then
        assertThat(response.getResponseStatus()).isEqualTo("partial");
        assertThat(response.getDegradedSections()).contains("growth_score");
        assertThat(response.getData().getGrowthScore()).isNull();
        // Other fields should still be populated
        assertThat(response.getData().getDisplayName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("NotificationDataService throws → partial response with 'notifications' in degraded_sections")
    void notificationServiceFails_returnsPartialWithDegradedNotifications() {
        // Given
        UUID fatherId = UUID.randomUUID();
        FatherReadModel father = new FatherReadModel(fatherId, "John Doe", "+1234567890",
                "UTC", null, null, "en", null, Instant.now(), null);
        FatherBelt belt = new FatherBelt(fatherId);
        FatherStreak streak = new FatherStreak(fatherId);

        when(fatherDataService.getFather(fatherId)).thenReturn(Optional.of(father));
        when(growthScoreService.getTotalScore(fatherId)).thenReturn(100);
        when(beltProgressionService.getCurrentBelt(fatherId)).thenReturn(belt);
        when(streakService.getStreak(fatherId)).thenReturn(streak);
        when(notificationDataService.getUnreadCount(fatherId)).thenThrow(new RuntimeException("Service down"));
        when(childDataService.getChildrenByFatherId(fatherId)).thenReturn(Collections.emptyList());
        when(goalDataService.countActiveGoalsByFatherId(fatherId)).thenReturn(0);
        when(missionDataService.getActiveMission(fatherId)).thenReturn(Optional.empty());

        // When
        PartialResponse<WorkspaceSummaryResponse> response = workspaceSummaryService.getSummary(fatherId);

        // Then
        assertThat(response.getResponseStatus()).isEqualTo("partial");
        assertThat(response.getDegradedSections()).contains("notifications");
        assertThat(response.getData().getUnreadNotificationsCount()).isNull();
        // Other fields should still be populated
        assertThat(response.getData().getGrowthScore()).isEqualTo(100);
    }
}
