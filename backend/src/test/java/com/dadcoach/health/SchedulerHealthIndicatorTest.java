package com.dadcoach.health;

import com.dadcoach.scheduler.SchedulerJobLog;
import com.dadcoach.scheduler.SchedulerJobLogRepository;
import com.dadcoach.scheduler.SchedulerJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SchedulerHealthIndicator}.
 * 
 * Tests Requirement 16.5: Health endpoint reports scheduler last-run timestamps.
 */
class SchedulerHealthIndicatorTest {

    @Test
    @DisplayName("Should report UP when all scheduler jobs have run recently")
    void shouldReportUpWhenAllJobsRunRecently() {
        // Given
        SchedulerJobLogRepository mockRepo = mock(SchedulerJobLogRepository.class);
        Instant now = Instant.now();

        // Morning reminder - ran 10 minutes ago (threshold: 25 hours)
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("morning_reminder"))
                .thenReturn(Optional.of(createJobLog("morning_reminder", now.minus(Duration.ofMinutes(10)))));
        
        // Follow-up transition - ran 5 minutes ago (threshold: 30 minutes)
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("follow_up_transition"))
                .thenReturn(Optional.of(createJobLog("follow_up_transition", now.minus(Duration.ofMinutes(5)))));
        
        // Stale state detection - ran 30 minutes ago (threshold: 2 hours)
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("stale_state_detection"))
                .thenReturn(Optional.of(createJobLog("stale_state_detection", now.minus(Duration.ofMinutes(30)))));

        SchedulerHealthIndicator indicator = new SchedulerHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("component");
        assertThat(health.getDetails().get("component")).isEqualTo("Scheduler");
        assertThat(health.getDetails()).containsKey("jobs");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> jobs = (Map<String, Object>) health.getDetails().get("jobs");
        assertThat(jobs).containsKeys("morning_reminder", "follow_up_transition", "stale_state_detection");
    }

    @Test
    @DisplayName("Should report DOWN when follow-up job hasn't run recently")
    void shouldReportDownWhenFollowUpJobNotRecent() {
        // Given
        SchedulerJobLogRepository mockRepo = mock(SchedulerJobLogRepository.class);
        Instant now = Instant.now();

        // Morning reminder - ran recently
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("morning_reminder"))
                .thenReturn(Optional.of(createJobLog("morning_reminder", now.minus(Duration.ofMinutes(10)))));
        
        // Follow-up transition - ran 45 minutes ago (beyond 30-minute threshold)
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("follow_up_transition"))
                .thenReturn(Optional.of(createJobLog("follow_up_transition", now.minus(Duration.ofMinutes(45)))));
        
        // Stale state detection - ran recently
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("stale_state_detection"))
                .thenReturn(Optional.of(createJobLog("stale_state_detection", now.minus(Duration.ofMinutes(30)))));

        SchedulerHealthIndicator indicator = new SchedulerHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("One or more scheduler jobs haven't run within expected interval");
    }

    @Test
    @DisplayName("Should report UNKNOWN when no jobs have run yet")
    void shouldReportUnknownWhenNoJobsHaveRun() {
        // Given
        SchedulerJobLogRepository mockRepo = mock(SchedulerJobLogRepository.class);
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("morning_reminder")).thenReturn(Optional.empty());
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("follow_up_transition")).thenReturn(Optional.empty());
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("stale_state_detection")).thenReturn(Optional.empty());

        SchedulerHealthIndicator indicator = new SchedulerHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsKey("note");
        assertThat(health.getDetails().get("note")).isEqualTo("No scheduler jobs have run yet - this may be normal for a new system");
    }

    @Test
    @DisplayName("Should report UNKNOWN when repository is null")
    void shouldReportUnknownWhenRepositoryIsNull() {
        // Given
        SchedulerHealthIndicator indicator = new SchedulerHealthIndicator(null);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails().get("error")).isEqualTo("SchedulerJobLogRepository not available");
    }

    @Test
    @DisplayName("Should include last run timestamps and status for each job")
    void shouldIncludeLastRunTimestampsAndStatus() {
        // Given
        SchedulerJobLogRepository mockRepo = mock(SchedulerJobLogRepository.class);
        Instant now = Instant.now();

        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("morning_reminder"))
                .thenReturn(Optional.of(createJobLog("morning_reminder", now.minus(Duration.ofMinutes(10)))));
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("follow_up_transition"))
                .thenReturn(Optional.of(createJobLog("follow_up_transition", now.minus(Duration.ofMinutes(5)))));
        when(mockRepo.findTopByJobNameOrderByStartedAtDesc("stale_state_detection"))
                .thenReturn(Optional.of(createJobLog("stale_state_detection", now.minus(Duration.ofMinutes(30)))));

        SchedulerHealthIndicator indicator = new SchedulerHealthIndicator(mockRepo);

        // When
        Health health = indicator.health();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> jobs = (Map<String, Object>) health.getDetails().get("jobs");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> morningReminder = (Map<String, Object>) jobs.get("morning_reminder");
        assertThat(morningReminder).containsKey("lastRun");
        assertThat(morningReminder).containsKey("timeSinceLastRun");
        assertThat(morningReminder).containsKey("lastStatus");
        assertThat(morningReminder.get("lastStatus")).isEqualTo("COMPLETED");
    }

    private SchedulerJobLog createJobLog(String jobName, Instant startedAt) {
        SchedulerJobLog log = new SchedulerJobLog(jobName);
        log.setStartedAt(startedAt);
        log.markCompleted(10, 0);
        return log;
    }
}
