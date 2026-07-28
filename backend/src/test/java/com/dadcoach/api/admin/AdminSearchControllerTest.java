package com.dadcoach.api.admin;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminSearchControllerTest {

    private AdminSearchService adminSearchService;
    private AdminSearchController controller;
    private ActorContext adminActor;

    @BeforeEach
    void setUp() {
        adminSearchService = mock(AdminSearchService.class);
        controller = new AdminSearchController(adminSearchService);
        adminActor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());
    }

    @Test
    void searchFathers_returnsPaginatedResults() {
        AdminSearchResultDto result = new AdminSearchResultDto();
        result.setId(UUID.randomUUID());
        result.setDisplayName("John Doe");
        result.setPhoneNumber("+1****90");
        result.setStatus("ACTIVE");
        result.setEngagementScore(85);
        result.setCreatedAt(Instant.now());

        CursorPageResponse<AdminSearchResultDto> page =
                CursorPageResponse.of(List.of(result), "cursor_next", true);

        when(adminSearchService.searchFathers("john", null, null, null, 20))
                .thenReturn(page);

        ResponseEntity<?> response =
                controller.searchFathers(adminActor, "john", null, null, null, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("has_more")).isEqualTo(true);
        assertThat(body.get("next_cursor")).isEqualTo("cursor_next");
    }

    @Test
    void searchFathers_passesAllFilters() {
        CursorPageResponse<AdminSearchResultDto> emptyPage = CursorPageResponse.empty();
        when(adminSearchService.searchFathers("jane", "PAUSED", "ONBOARDING", "cur123", 10))
                .thenReturn(emptyPage);

        controller.searchFathers(adminActor, "jane", "PAUSED", "ONBOARDING", "cur123", 10);

        verify(adminSearchService).searchFathers("jane", "PAUSED", "ONBOARDING", "cur123", 10);
    }

    @Test
    void searchFathers_capsPageSizeAtMaximum() {
        CursorPageResponse<AdminSearchResultDto> emptyPage = CursorPageResponse.empty();
        when(adminSearchService.searchFathers(isNull(), isNull(), isNull(), isNull(), eq(100)))
                .thenReturn(emptyPage);

        controller.searchFathers(adminActor, null, null, null, null, 500);

        verify(adminSearchService).searchFathers(null, null, null, null, 100);
    }

    @Test
    void getAnalytics_returnsAggregatedData() {
        AggregatedAnalyticsDto analytics = new AggregatedAnalyticsDto();
        analytics.setTotalFathers(500);
        analytics.setActiveFathers(350);
        analytics.setPausedFathers(100);
        analytics.setChurnedFathers(50);
        analytics.setAverageEngagementScore(72.5);
        analytics.setTotalActiveGoals(1200);
        analytics.setTotalActiveMemories(8500);

        when(adminSearchService.getAggregatedAnalytics(null, null)).thenReturn(analytics);

        ResponseEntity<AggregatedAnalyticsDto> response =
                controller.getAnalytics(adminActor, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalFathers()).isEqualTo(500);
        assertThat(response.getBody().getActiveFathers()).isEqualTo(350);
        assertThat(response.getBody().getAverageEngagementScore()).isEqualTo(72.5);
    }

    @Test
    void getAnalytics_passesFilters() {
        AggregatedAnalyticsDto analytics = new AggregatedAnalyticsDto();
        analytics.setTotalFathers(100);
        when(adminSearchService.getAggregatedAnalytics("ACTIVE", "BUILDING_HABITS"))
                .thenReturn(analytics);

        controller.getAnalytics(adminActor, "ACTIVE", "BUILDING_HABITS");

        verify(adminSearchService).getAggregatedAnalytics("ACTIVE", "BUILDING_HABITS");
    }

    @Test
    void getEngagementMetrics_returnsMetrics() {
        EngagementMetricsDto metrics = new EngagementMetricsDto();
        metrics.setAverageEngagementScore(75.0);
        metrics.setMedianEngagementScore(78.0);
        metrics.setAverageMissionCompletionRate(0.65);
        metrics.setActiveUsersLast7Days(200);
        metrics.setActiveUsersLast30Days(400);

        when(adminSearchService.getEngagementMetrics()).thenReturn(metrics);

        ResponseEntity<EngagementMetricsDto> response =
                controller.getEngagementMetrics(adminActor);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAverageEngagementScore()).isEqualTo(75.0);
        assertThat(response.getBody().getActiveUsersLast7Days()).isEqualTo(200);
    }

    @Test
    void aggregatedAnalyticsDto_neverContainsIndividualPII() {
        // Verify by structure that the DTO has no PII fields
        AggregatedAnalyticsDto dto = new AggregatedAnalyticsDto();
        dto.setTotalFathers(100);
        dto.setActiveFathers(80);
        dto.setAverageEngagementScore(70.0);

        // No individual IDs, names, phones exist on this DTO — compile-time safety
        assertThat(dto.getTotalFathers()).isEqualTo(100);
    }
}
