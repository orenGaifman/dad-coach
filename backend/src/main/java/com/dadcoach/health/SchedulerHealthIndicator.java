package com.dadcoach.health;

import com.dadcoach.workflow.scheduler.SchedulerJobLog;
import com.dadcoach.workflow.scheduler.SchedulerJobLogRepository;
import com.dadcoach.workflow.scheduler.SchedulerJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Health indicator for scheduler jobs.
 * 
 * <p>Reports the status of workflow scheduler jobs by checking last-run timestamps
 * for each job type:</p>
 * <ul>
 *   <li><b>morning_reminder</b>: Sends reminders at 8 AM local time for Quality Time events</li>
 *   <li><b>follow_up_transition</b>: Transitions fathers to follow-up state after Quality Time ends</li>
 *   <li><b>stale_state_detection</b>: Detects fathers stuck in follow-up state for over 24 hours</li>
 * </ul>
 * 
 * <p>The indicator reports:
 * <ul>
 *   <li>UP - when all jobs have run recently (within their expected intervals)</li>
 *   <li>DOWN - when any job hasn't run within its expected interval</li>
 *   <li>UNKNOWN - when no job logs exist (system may be new)</li>
 * </ul>
 * </p>
 * 
 * <p>Implements Requirement 16.5: The system SHALL expose a health endpoint that
 * reports scheduler last-run timestamps.</p>
 */
@Component
public class SchedulerHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SchedulerHealthIndicator.class);

    /**
     * Job name for morning reminder scheduler.
     */
    static final String JOB_MORNING_REMINDER = "morning_reminder";

    /**
     * Job name for follow-up transition scheduler.
     */
    static final String JOB_FOLLOW_UP_TRANSITION = "follow_up_transition";

    /**
     * Job name for stale state detection scheduler.
     */
    static final String JOB_STALE_STATE_DETECTION = "stale_state_detection";

    /**
     * Maximum expected interval for morning reminder job.
     * Job runs daily at 7:50 AM UTC, so we expect it to run at least once per 25 hours.
     */
    private static final Duration MORNING_REMINDER_THRESHOLD = Duration.ofHours(25);

    /**
     * Maximum expected interval for follow-up transition job.
     * Job runs every 15 minutes, so we expect it to run at least once per 30 minutes.
     */
    private static final Duration FOLLOW_UP_TRANSITION_THRESHOLD = Duration.ofMinutes(30);

    /**
     * Maximum expected interval for stale state detection job.
     * Job runs every hour, so we expect it to run at least once per 2 hours.
     */
    private static final Duration STALE_STATE_DETECTION_THRESHOLD = Duration.ofHours(2);

    private final SchedulerJobLogRepository jobLogRepository;

    /**
     * Constructs the health indicator with the scheduler job log repository.
     *
     * @param jobLogRepository repository for scheduler job logs
     */
    public SchedulerHealthIndicator(SchedulerJobLogRepository jobLogRepository) {
        this.jobLogRepository = jobLogRepository;
    }

    @Override
    public Health health() {
        try {
            if (jobLogRepository == null) {
                return Health.unknown()
                        .withDetail("component", "Scheduler")
                        .withDetail("error", "SchedulerJobLogRepository not available")
                        .build();
            }

            // Get last run timestamps for each job
            Map<String, JobStatus> jobStatuses = new HashMap<>();
            jobStatuses.put(JOB_MORNING_REMINDER, 
                    getJobStatus(JOB_MORNING_REMINDER, MORNING_REMINDER_THRESHOLD));
            jobStatuses.put(JOB_FOLLOW_UP_TRANSITION, 
                    getJobStatus(JOB_FOLLOW_UP_TRANSITION, FOLLOW_UP_TRANSITION_THRESHOLD));
            jobStatuses.put(JOB_STALE_STATE_DETECTION, 
                    getJobStatus(JOB_STALE_STATE_DETECTION, STALE_STATE_DETECTION_THRESHOLD));

            // Build health response with job details
            Health.Builder healthBuilder = Health.up();
            boolean allHealthy = true;
            boolean anyJobRan = false;

            Map<String, Object> jobDetails = new HashMap<>();
            for (Map.Entry<String, JobStatus> entry : jobStatuses.entrySet()) {
                String jobName = entry.getKey();
                JobStatus status = entry.getValue();
                
                Map<String, Object> jobInfo = new HashMap<>();
                if (status.lastRun != null) {
                    anyJobRan = true;
                    jobInfo.put("lastRun", status.lastRun.toString());
                    jobInfo.put("timeSinceLastRun", formatDuration(status.timeSinceLastRun));
                    jobInfo.put("lastStatus", status.lastStatus != null ? status.lastStatus.name() : "UNKNOWN");
                    jobInfo.put("healthy", status.isHealthy);
                    
                    if (!status.isHealthy) {
                        allHealthy = false;
                    }
                } else {
                    jobInfo.put("lastRun", "never");
                    jobInfo.put("healthy", "unknown");
                }
                
                jobDetails.put(jobName, jobInfo);
            }

            healthBuilder.withDetail("component", "Scheduler");
            healthBuilder.withDetail("jobs", jobDetails);

            if (!anyJobRan) {
                // No jobs have run yet - system may be new
                return Health.unknown()
                        .withDetail("component", "Scheduler")
                        .withDetail("jobs", jobDetails)
                        .withDetail("note", "No scheduler jobs have run yet - this may be normal for a new system")
                        .build();
            }

            if (allHealthy) {
                return healthBuilder.build();
            } else {
                return Health.down()
                        .withDetail("component", "Scheduler")
                        .withDetail("jobs", jobDetails)
                        .withDetail("error", "One or more scheduler jobs haven't run within expected interval")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error checking Scheduler health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("component", "Scheduler")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Gets the status of a specific scheduler job.
     *
     * @param jobName the job name
     * @param threshold the maximum expected interval since last run
     * @return the job status
     */
    private JobStatus getJobStatus(String jobName, Duration threshold) {
        Optional<SchedulerJobLog> lastJobLog = jobLogRepository.findTopByJobNameOrderByStartedAtDesc(jobName);
        
        if (lastJobLog.isEmpty()) {
            return new JobStatus(null, null, null, true); // No data yet - consider healthy
        }

        SchedulerJobLog jobLog = lastJobLog.get();
        Instant lastRun = jobLog.getStartedAt();
        Duration timeSinceLastRun = Duration.between(lastRun, Instant.now());
        boolean isHealthy = timeSinceLastRun.compareTo(threshold) <= 0;
        
        return new JobStatus(lastRun, timeSinceLastRun, jobLog.getStatus(), isHealthy);
    }

    /**
     * Formats a duration into a human-readable string.
     *
     * @param duration the duration to format
     * @return formatted string like "2h 30m" or "45m" or "1d 3h"
     */
    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "N/A";
        }
        
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }

    /**
     * Internal class to hold job status information.
     */
    private static class JobStatus {
        final Instant lastRun;
        final Duration timeSinceLastRun;
        final SchedulerJobStatus lastStatus;
        final boolean isHealthy;

        JobStatus(Instant lastRun, Duration timeSinceLastRun, SchedulerJobStatus lastStatus, boolean isHealthy) {
            this.lastRun = lastRun;
            this.timeSinceLastRun = timeSinceLastRun;
            this.lastStatus = lastStatus;
            this.isHealthy = isHealthy;
        }
    }
}
