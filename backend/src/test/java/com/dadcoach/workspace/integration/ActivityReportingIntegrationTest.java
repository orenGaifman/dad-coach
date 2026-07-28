package com.dadcoach.workspace.integration;

import com.dadcoach.workspace.RateLimitExceededException;
import com.dadcoach.workspace.activity.ActivityReport;
import com.dadcoach.workspace.activity.ActivityReportRepository;
import com.dadcoach.workspace.activity.ActivityReportValidator;
import com.dadcoach.workspace.activity.ActivityReportingService;
import com.dadcoach.workspace.dto.request.QualityTimeRequest;
import com.dadcoach.workspace.dto.response.ActivityReportResponse;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.dadcoach.workspace.growth.signal.SignalWeight;
import com.dadcoach.workspace.security.ActivityReportRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for the Activity Reporting end-to-end flow.
 *
 * <p>Verifies: submit quality time → validation → duplicate check →
 * persist → event emitted → response with points_awarded.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("15.2 - Activity Reporting End-to-End Integration")
class ActivityReportingIntegrationTest {

    @Mock
    private ActivityReportRepository activityReportRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ActivityReportValidator activityReportValidator;
    private ActivityReportRateLimiter activityReportRateLimiter;
    private ActivityReportingService activityReportingService;

    @BeforeEach
    void setUp() {
        activityReportValidator = new ActivityReportValidator();
        activityReportRateLimiter = new ActivityReportRateLimiter(activityReportRepository);
        activityReportingService = new ActivityReportingService(
                activityReportRepository,
                activityReportValidator,
                activityReportRateLimiter,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Submit quality time → validation passes → duplicate check → persist → event emitted → 12 points awarded")
    void submitQualityTime_fullFlow_returns12Points() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        LocalDate activityDate = LocalDate.now();
        QualityTimeRequest request = new QualityTimeRequest(childId, 30, activityDate);

        // No duplicate
        when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                fatherId, childId, 30, activityDate))
                .thenReturn(false);

        // Rate limit not exceeded
        when(activityReportRepository.countByFatherIdAndReportTypeAndActivityDate(
                fatherId, ActivityReportRateLimiter.REPORT_TYPE_QUALITY_TIME, activityDate))
                .thenReturn(0L);

        // Persist
        when(activityReportRepository.save(any(ActivityReport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        ActivityReportResponse response = activityReportingService.reportQualityTime(fatherId, request);

        // Then
        int expectedPoints = SignalWeight.getPoints(GrowthSignalType.QUALITY_TIME_REPORTED);
        assertThat(expectedPoints).isEqualTo(12);
        assertThat(response.getPointsAwarded()).isEqualTo(12);
        assertThat(response.getChildId()).isEqualTo(childId);
        assertThat(response.getActivityDate()).isEqualTo(activityDate);

        // Event was emitted
        ArgumentCaptor<QualityTimeReportedEvent> eventCaptor = ArgumentCaptor.forClass(QualityTimeReportedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        QualityTimeReportedEvent emitted = eventCaptor.getValue();
        assertThat(emitted.getFatherId()).isEqualTo(fatherId);
        assertThat(emitted.getChildId()).isEqualTo(childId);
        assertThat(emitted.getDurationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Submit duplicate quality time → response indicates 0 points (idempotent)")
    void submitDuplicate_returns0Points() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        LocalDate activityDate = LocalDate.now();
        QualityTimeRequest request = new QualityTimeRequest(childId, 30, activityDate);

        // Duplicate exists
        when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                fatherId, childId, 30, activityDate))
                .thenReturn(true);

        // When
        ActivityReportResponse response = activityReportingService.reportQualityTime(fatherId, request);

        // Then
        assertThat(response.getPointsAwarded()).isEqualTo(0);
        assertThat(response.getReportId()).isNull(); // no new report created

        // No event emitted, no save
        verify(eventPublisher, never()).publishEvent(any());
        verify(activityReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Exceed rate limit → RateLimitExceededException thrown (429)")
    void exceedRateLimit_throwsRateLimitExceededException() {
        // Given
        UUID fatherId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        LocalDate activityDate = LocalDate.now();
        QualityTimeRequest request = new QualityTimeRequest(childId, 30, activityDate);

        // No duplicate
        when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                fatherId, childId, 30, activityDate))
                .thenReturn(false);

        // Rate limit exceeded: 10 reports already today
        when(activityReportRepository.countByFatherIdAndReportTypeAndActivityDate(
                fatherId, ActivityReportRateLimiter.REPORT_TYPE_QUALITY_TIME, activityDate))
                .thenReturn(10L);

        // When/Then
        assertThatThrownBy(() -> activityReportingService.reportQualityTime(fatherId, request))
                .isInstanceOf(RateLimitExceededException.class);

        // No event emitted, no save
        verify(eventPublisher, never()).publishEvent(any());
        verify(activityReportRepository, never()).save(any());
    }
}
