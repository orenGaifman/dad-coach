package com.dadcoach.workspace.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.dadcoach.workspace.RateLimitExceededException;
import com.dadcoach.workspace.WorkspaceException;
import com.dadcoach.workspace.dto.request.PositiveActivityRequest;
import com.dadcoach.workspace.dto.request.QualityTimeRequest;
import com.dadcoach.workspace.dto.response.ActivityReportResponse;
import com.dadcoach.workspace.event.PositiveActivityReportedEvent;
import com.dadcoach.workspace.event.QualityTimeReportedEvent;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.dadcoach.workspace.growth.signal.SignalWeight;
import com.dadcoach.workspace.security.ActivityReportRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityReportingService")
class ActivityReportingServiceTest {

    @Mock
    private ActivityReportRepository activityReportRepository;

    @Mock
    private ActivityReportValidator activityReportValidator;

    @Mock
    private ActivityReportRateLimiter activityReportRateLimiter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ActivityReportingService service;

    @BeforeEach
    void setUp() {
        service = new ActivityReportingService(
                activityReportRepository,
                activityReportValidator,
                activityReportRateLimiter,
                eventPublisher
        );
    }

    @Nested
    @DisplayName("reportQualityTime")
    class ReportQualityTimeTests {

        private final UUID fatherId = UUID.randomUUID();
        private final UUID childId = UUID.randomUUID();
        private final LocalDate today = LocalDate.now();

        @Test
        void shouldPersistAndEmitEventOnValidReport() {
            QualityTimeRequest request = new QualityTimeRequest(childId, 30, today);
            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 30, today)).thenReturn(false);
            when(activityReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ActivityReportResponse response = service.reportQualityTime(fatherId, request);

            assertThat(response.getReportId()).isNotNull();
            assertThat(response.getReportType()).isEqualTo("QUALITY_TIME");
            assertThat(response.getChildId()).isEqualTo(childId);
            assertThat(response.getActivityDate()).isEqualTo(today);
            assertThat(response.getPointsAwarded()).isEqualTo(
                    SignalWeight.getPoints(GrowthSignalType.QUALITY_TIME_REPORTED));

            verify(activityReportRepository).save(any(ActivityReport.class));
            verify(eventPublisher).publishEvent(any(QualityTimeReportedEvent.class));
        }

        @Test
        void shouldReturnZeroPointsForDuplicateReport() {
            QualityTimeRequest request = new QualityTimeRequest(childId, 30, today);
            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 30, today)).thenReturn(true);

            ActivityReportResponse response = service.reportQualityTime(fatherId, request);

            assertThat(response.getPointsAwarded()).isEqualTo(0);
            assertThat(response.getReportId()).isNull();
            verify(activityReportRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldThrowWhenRateLimitExceeded() {
            QualityTimeRequest request = new QualityTimeRequest(childId, 30, today);
            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 30, today)).thenReturn(false);
            doThrow(new RateLimitExceededException(86400))
                    .when(activityReportRateLimiter).checkLimit(fatherId, "QUALITY_TIME");

            assertThatThrownBy(() -> service.reportQualityTime(fatherId, request))
                    .isInstanceOf(RateLimitExceededException.class);

            verify(activityReportRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenValidationFails() {
            QualityTimeRequest request = new QualityTimeRequest(childId, 5, today);
            doThrow(new WorkspaceException(
                    com.dadcoach.workspace.WorkspaceErrorCode.VALIDATION_ERROR, "too short"))
                    .when(activityReportValidator).validateQualityTimeReport(5, today);

            assertThatThrownBy(() -> service.reportQualityTime(fatherId, request))
                    .isInstanceOf(WorkspaceException.class);
        }

        @Test
        void shouldEmitEventWithCorrectData() {
            QualityTimeRequest request = new QualityTimeRequest(childId, 60, today);
            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 60, today)).thenReturn(false);
            when(activityReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reportQualityTime(fatherId, request);

            ArgumentCaptor<QualityTimeReportedEvent> captor =
                    ArgumentCaptor.forClass(QualityTimeReportedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            QualityTimeReportedEvent event = captor.getValue();
            assertThat(event.getFatherId()).isEqualTo(fatherId);
            assertThat(event.getChildId()).isEqualTo(childId);
            assertThat(event.getDurationMinutes()).isEqualTo(60);
            assertThat(event.getActivityDate()).isEqualTo(today);
        }
    }

    @Nested
    @DisplayName("reportPositiveActivity")
    class ReportPositiveActivityTests {

        private final UUID fatherId = UUID.randomUUID();
        private final UUID childId = UUID.randomUUID();
        private final LocalDate today = LocalDate.now();

        @Test
        void shouldPersistAndEmitEventOnValidReport() {
            PositiveActivityRequest request = new PositiveActivityRequest(
                    childId, ActivityType.PRAISE, "Great job sharing toys!", today);
            when(activityReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ActivityReportResponse response = service.reportPositiveActivity(fatherId, request);

            assertThat(response.getReportId()).isNotNull();
            assertThat(response.getReportType()).isEqualTo("POSITIVE_ACTIVITY");
            assertThat(response.getChildId()).isEqualTo(childId);
            assertThat(response.getActivityDate()).isEqualTo(today);
            assertThat(response.getPointsAwarded()).isEqualTo(
                    SignalWeight.getPoints(GrowthSignalType.POSITIVE_ACTIVITY));

            verify(activityReportRepository).save(any(ActivityReport.class));
            verify(eventPublisher).publishEvent(any(PositiveActivityReportedEvent.class));
        }

        @Test
        void shouldThrowWhenRateLimitExceeded() {
            PositiveActivityRequest request = new PositiveActivityRequest(
                    childId, ActivityType.SHARED_ACTIVITY, null, today);
            doThrow(new RateLimitExceededException(86400))
                    .when(activityReportRateLimiter).checkLimit(fatherId, "POSITIVE_ACTIVITY");

            assertThatThrownBy(() -> service.reportPositiveActivity(fatherId, request))
                    .isInstanceOf(RateLimitExceededException.class);

            verify(activityReportRepository, never()).save(any());
        }

        @Test
        void shouldEmitEventWithCorrectActivityType() {
            PositiveActivityRequest request = new PositiveActivityRequest(
                    childId, ActivityType.TEACHING_MOMENT, "Learned about kindness", today);
            when(activityReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reportPositiveActivity(fatherId, request);

            ArgumentCaptor<PositiveActivityReportedEvent> captor =
                    ArgumentCaptor.forClass(PositiveActivityReportedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PositiveActivityReportedEvent event = captor.getValue();
            assertThat(event.getActivityType()).isEqualTo("TEACHING_MOMENT");
            assertThat(event.getFatherId()).isEqualTo(fatherId);
            assertThat(event.getChildId()).isEqualTo(childId);
        }

        @Test
        void shouldAllowNullChildId() {
            PositiveActivityRequest request = new PositiveActivityRequest(
                    null, ActivityType.OTHER, "General positivity", today);
            when(activityReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ActivityReportResponse response = service.reportPositiveActivity(fatherId, request);

            assertThat(response.getChildId()).isNull();
        }
    }

    @Nested
    @DisplayName("isDuplicateQualityTime")
    class DuplicateDetectionTests {

        @Test
        void shouldReturnTrueWhenDuplicateExists() {
            UUID fatherId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            LocalDate date = LocalDate.now();

            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 30, date)).thenReturn(true);

            assertThat(service.isDuplicateQualityTime(fatherId, childId, 30, date)).isTrue();
        }

        @Test
        void shouldReturnFalseWhenNoDuplicate() {
            UUID fatherId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            LocalDate date = LocalDate.now();

            when(activityReportRepository.existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
                    fatherId, childId, 30, date)).thenReturn(false);

            assertThat(service.isDuplicateQualityTime(fatherId, childId, 30, date)).isFalse();
        }
    }
}
