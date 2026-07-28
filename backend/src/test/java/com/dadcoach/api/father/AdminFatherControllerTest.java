package com.dadcoach.api.father;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.ActorType;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.api.pagination.CursorPageResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminFatherControllerTest {

    private AdminFatherService adminFatherService;
    private AdminFatherController controller;
    private ActorContext adminActor;

    @BeforeEach
    void setUp() {
        adminFatherService = mock(AdminFatherService.class);
        controller = new AdminFatherController(adminFatherService);
        adminActor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());
    }

    @Test
    void listFathers_returnsPaginatedResults() {
        AdminFatherSummaryDto summary = new AdminFatherSummaryDto();
        summary.setId(UUID.randomUUID());
        summary.setDisplayName("John Doe");
        summary.setPhoneNumber("+1234567890");
        summary.setStatus("ACTIVE");
        summary.setCoachingPhase("BUILDING_HABITS");
        summary.setEngagementScore(75);
        summary.setCreatedAt(Instant.now());

        CursorPageResponse<AdminFatherSummaryDto> page =
                CursorPageResponse.of(List.of(summary), "next_abc", true);

        when(adminFatherService.listFathers(null, null, null, null, 20))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response =
                controller.listFathers(adminActor, null, null, null, null, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("has_more")).isEqualTo(true);
        assertThat(body.get("next_cursor")).isEqualTo("next_abc");

        @SuppressWarnings("unchecked")
        List<AdminFatherSummaryDto> items = (List<AdminFatherSummaryDto>) body.get("items");
        assertThat(items).hasSize(1);
    }

    @Test
    void listFathers_masksPhoneNumbers_forRegularAdmin() {
        AdminFatherSummaryDto summary = new AdminFatherSummaryDto();
        summary.setId(UUID.randomUUID());
        summary.setDisplayName("Jane Smith");
        summary.setPhoneNumber("+1234567890");
        summary.setStatus("ACTIVE");

        CursorPageResponse<AdminFatherSummaryDto> page =
                CursorPageResponse.of(List.of(summary), null, false);

        when(adminFatherService.listFathers(null, null, null, null, 20))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response =
                controller.listFathers(adminActor, null, null, null, null, 20);

        @SuppressWarnings("unchecked")
        List<AdminFatherSummaryDto> items =
                (List<AdminFatherSummaryDto>) response.getBody().get("items");

        // Phone should be masked: country code + masked digits + last 2
        assertThat(items.get(0).getPhoneNumber()).doesNotContain("234567");
        assertThat(items.get(0).getPhoneNumber()).endsWith("90");
    }

    @Test
    void listFathers_capsPageSizeAtMaximum() {
        CursorPageResponse<AdminFatherSummaryDto> emptyPage = CursorPageResponse.empty();
        when(adminFatherService.listFathers(isNull(), isNull(), isNull(), isNull(), eq(100)))
                .thenReturn(emptyPage);

        controller.listFathers(adminActor, null, null, null, null, 500);

        verify(adminFatherService).listFathers(null, null, null, null, 100);
    }

    @Test
    void listFathers_enforceMinimumPageSize() {
        CursorPageResponse<AdminFatherSummaryDto> emptyPage = CursorPageResponse.empty();
        when(adminFatherService.listFathers(isNull(), isNull(), isNull(), isNull(), eq(1)))
                .thenReturn(emptyPage);

        controller.listFathers(adminActor, null, null, null, null, -5);

        verify(adminFatherService).listFathers(null, null, null, null, 1);
    }

    @Test
    void listFathers_passesSearchQueryAndFilters() {
        CursorPageResponse<AdminFatherSummaryDto> emptyPage = CursorPageResponse.empty();
        when(adminFatherService.listFathers("john", "ACTIVE", "ONBOARDING", "cursor123", 10))
                .thenReturn(emptyPage);

        controller.listFathers(adminActor, "john", "ACTIVE", "ONBOARDING", "cursor123", 10);

        verify(adminFatherService).listFathers("john", "ACTIVE", "ONBOARDING", "cursor123", 10);
    }

    @Test
    void getFatherDetail_returnsFullContext() {
        UUID fatherId = UUID.randomUUID();
        AdminFatherDetailDto detail = new AdminFatherDetailDto();
        detail.setId(fatherId);
        detail.setDisplayName("John Doe");
        detail.setPhoneNumber("+1234567890");
        detail.setStatus("ACTIVE");
        detail.setCoachingPhase("BUILDING_HABITS");
        detail.setEngagementScore(80);
        detail.setChildrenCount(2);
        detail.setActiveGoalsCount(3);
        detail.setTotalConversations(15);
        detail.setTotalMemories(42);
        detail.setCreatedAt(Instant.now());

        when(adminFatherService.getFatherDetail(fatherId)).thenReturn(Optional.of(detail));

        ResponseEntity<AdminFatherDetailDto> response =
                controller.getFatherDetail(adminActor, fatherId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDisplayName()).isEqualTo("John Doe");
        assertThat(response.getBody().getChildrenCount()).isEqualTo(2);
    }

    @Test
    void getFatherDetail_masksPhoneNumber_forRegularAdmin() {
        UUID fatherId = UUID.randomUUID();
        AdminFatherDetailDto detail = new AdminFatherDetailDto();
        detail.setId(fatherId);
        detail.setDisplayName("John Doe");
        detail.setPhoneNumber("+1234567890");

        when(adminFatherService.getFatherDetail(fatherId)).thenReturn(Optional.of(detail));

        ResponseEntity<AdminFatherDetailDto> response =
                controller.getFatherDetail(adminActor, fatherId);

        assertThat(response.getBody().getPhoneNumber()).doesNotContain("234567");
        assertThat(response.getBody().getPhoneNumber()).endsWith("90");
    }

    @Test
    void getFatherDetail_throwsNotFound_whenFatherDoesNotExist() {
        UUID fatherId = UUID.randomUUID();
        when(adminFatherService.getFatherDetail(fatherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getFatherDetail(adminActor, fatherId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Father");
    }

    @Test
    void maskPhone_masksMiddleDigits() {
        // +1 country code (1 digit after +), 11 chars total: +1 + 7 masked + 90
        assertThat(AdminFatherController.maskPhone("+1234567890")).isEqualTo("+1*******90");
        // +972 country code (3 digits after +), 13 chars total: +972 + 7 masked + 67
        assertThat(AdminFatherController.maskPhone("+972501234567")).isEqualTo("+972*******67");
    }

    @Test
    void maskPhone_returnsNullForNull() {
        assertThat(AdminFatherController.maskPhone(null)).isNull();
    }

    @Test
    void maskPhone_handlesShortNumbers() {
        // Too short to mask meaningfully
        assertThat(AdminFatherController.maskPhone("+12")).isEqualTo("+12");
    }
}
