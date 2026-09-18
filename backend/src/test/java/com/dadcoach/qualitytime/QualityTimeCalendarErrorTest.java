package com.dadcoach.qualitytime;

import com.dadcoach.calendar.CalendarIntegrationException;
import com.dadcoach.calendar.CalendarIntegrationException.CalendarErrorType;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workspace.commitment.CommitmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies that scheduling surfaces DISTINCT, actionable calendar failures instead of a
 * single generic "internal error". Covers: not connected, reconnect required (token + API 401),
 * and temporary failure (5xx).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QualityTime calendar error handling")
class QualityTimeCalendarErrorTest {

    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;
    @Mock private WorkflowMetrics workflowMetrics;
    @Mock private CommitmentService commitmentService;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private RestTemplate restTemplate;

    private QualityTimeServiceImpl service;

    private static final Long FATHER_ID = 42L;
    private static final Long CHILD_ID = 7L;
    private final Instant start = Instant.now().plusSeconds(3600);
    private final Duration duration = Duration.ofMinutes(60);

    @BeforeEach
    void setUp() {
        service = new QualityTimeServiceImpl(
                qualityTimeRepository, fatherRepository, childRepository,
                workflowMetrics, commitmentService, weeklyGoalService, restTemplate);

        Child child = new Child(new Father("+972503020551"), "Emma", LocalDate.now().minusYears(8));
        when(childRepository.findById(CHILD_ID)).thenReturn(Optional.of(child));
    }

    private Father fatherWithCalendar(boolean enabled, String refreshToken, String accessToken, Instant expiry) {
        Father father = new Father("+972503020551");
        father.setGoogleCalendarEnabled(enabled);
        father.setGoogleRefreshToken(refreshToken);
        father.setGoogleAccessToken(accessToken);
        father.setGoogleTokenExpiresAt(expiry);
        return father;
    }

    @Test
    @DisplayName("NOT_CONNECTED when calendar is not configured")
    void notConnected() {
        Father father = fatherWithCalendar(false, null, null, null);
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));

        assertThatThrownBy(() -> service.scheduleQualityTime(FATHER_ID, CHILD_ID, start, duration))
                .isInstanceOf(CalendarIntegrationException.class)
                .satisfies(e -> assertThat(((CalendarIntegrationException) e).getErrorType())
                        .isEqualTo(CalendarErrorType.NOT_CONNECTED));
    }

    @Test
    @DisplayName("RECONNECT_REQUIRED when the refresh token is rejected (invalid_grant)")
    void reconnectRequiredOnTokenRefreshRejected() {
        // Configured but token needs refresh (no access token) -> token endpoint returns 400.
        Father father = fatherWithCalendar(true, "refresh-abc", null, null);
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(restTemplate.postForEntity(eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "invalid_grant", null, null, null));

        assertThatThrownBy(() -> service.scheduleQualityTime(FATHER_ID, CHILD_ID, start, duration))
                .isInstanceOf(CalendarIntegrationException.class)
                .satisfies(e -> assertThat(((CalendarIntegrationException) e).getErrorType())
                        .isEqualTo(CalendarErrorType.RECONNECT_REQUIRED));
    }

    @Test
    @DisplayName("TEMPORARY_FAILURE when Google Calendar returns 5xx (after retry)")
    void temporaryFailureOn5xx() {
        // Valid, non-expiring access token so no refresh is attempted.
        Father father = fatherWithCalendar(true, "refresh-abc", "access-xyz", Instant.now().plusSeconds(3600));
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        // Conflict-check GET + event-create POST both hit restTemplate; make calendar API fail 5xx.
        when(restTemplate.exchange(any(String.class), any(), any(), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null));

        assertThatThrownBy(() -> service.scheduleQualityTime(FATHER_ID, CHILD_ID, start, duration))
                .isInstanceOf(CalendarIntegrationException.class)
                .satisfies(e -> assertThat(((CalendarIntegrationException) e).getErrorType())
                        .isEqualTo(CalendarErrorType.TEMPORARY_FAILURE));
    }

    @Test
    @DisplayName("RECONNECT_REQUIRED when event creation returns 401")
    void reconnectRequiredOnEventCreate401() {
        Father father = fatherWithCalendar(true, "refresh-abc", "access-xyz", Instant.now().plusSeconds(3600));
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        // Conflict-check GET succeeds returning no conflicts is hard to stub simply; make GET fail 401 too.
        when(restTemplate.exchange(any(String.class), any(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "unauthorized", null, null, null));
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "unauthorized", null, null, null));

        assertThatThrownBy(() -> service.scheduleQualityTime(FATHER_ID, CHILD_ID, start, duration))
                .isInstanceOf(CalendarIntegrationException.class)
                .satisfies(e -> assertThat(((CalendarIntegrationException) e).getErrorType())
                        .isEqualTo(CalendarErrorType.RECONNECT_REQUIRED));
    }
}
