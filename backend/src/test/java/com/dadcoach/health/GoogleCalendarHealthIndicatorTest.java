package com.dadcoach.health;

import com.dadcoach.calendar.CalendarSyncLog;
import com.dadcoach.calendar.CalendarSyncLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoogleCalendarHealthIndicator}.
 * 
 * Tests Requirement 16.5: Health endpoint reports Google Calendar API status.
 */
class GoogleCalendarHealthIndicatorTest {

    @Test
    @DisplayName("Should report UP when recent successful API call exists")
    void shouldReportUpWhenRecentSuccessfulCallExists() {
        // Given
        CalendarSyncLogRepository mockRepo = mock(CalendarSyncLogRepository.class);
        CalendarSyncLog recentSuccessLog = createSuccessfulSyncLog(Instant.now().minus(Duration.ofHours(1)));
        when(mockRepo.findTopBySuccessTrueOrderBySyncedAtDesc()).thenReturn(Optional.of(recentSuccessLog));

        GoogleCalendarHealthIndicator indicator = new GoogleCalendarHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("component");
        assertThat(health.getDetails().get("component")).isEqualTo("GoogleCalendarAPI");
        assertThat(health.getDetails()).containsKey("lastSuccessfulCall");
    }

    @Test
    @DisplayName("Should report DOWN when last successful call is too old")
    void shouldReportDownWhenLastCallIsTooOld() {
        // Given
        CalendarSyncLogRepository mockRepo = mock(CalendarSyncLogRepository.class);
        // Last success was 30 hours ago (beyond 24-hour threshold)
        CalendarSyncLog oldSuccessLog = createSuccessfulSyncLog(Instant.now().minus(Duration.ofHours(30)));
        when(mockRepo.findTopBySuccessTrueOrderBySyncedAtDesc()).thenReturn(Optional.of(oldSuccessLog));

        GoogleCalendarHealthIndicator indicator = new GoogleCalendarHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("No successful API call within threshold");
    }

    @Test
    @DisplayName("Should report UNKNOWN when no successful API calls recorded")
    void shouldReportUnknownWhenNoSuccessfulCallsRecorded() {
        // Given
        CalendarSyncLogRepository mockRepo = mock(CalendarSyncLogRepository.class);
        when(mockRepo.findTopBySuccessTrueOrderBySyncedAtDesc()).thenReturn(Optional.empty());

        GoogleCalendarHealthIndicator indicator = new GoogleCalendarHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsKey("status");
        assertThat(health.getDetails().get("status")).isEqualTo("No successful API calls recorded");
    }

    @Test
    @DisplayName("Should report UNKNOWN when repository is null")
    void shouldReportUnknownWhenRepositoryIsNull() {
        // Given
        GoogleCalendarHealthIndicator indicator = new GoogleCalendarHealthIndicator(null);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("Repository not available");
    }

    private CalendarSyncLog createSuccessfulSyncLog(Instant syncedAt) {
        CalendarSyncLog log = CalendarSyncLog.success(1L, 1L, "CREATE", "test-event-id");
        log.setSyncedAt(syncedAt);
        return log;
    }
}
