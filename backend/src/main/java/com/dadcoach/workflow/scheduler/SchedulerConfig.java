package com.dadcoach.workflow.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for the workflow scheduler jobs.
 * 
 * <p>This configuration provides externalized settings for the scheduler
 * jobs defined in {@link WorkflowScheduler}:</p>
 * <ul>
 *   <li><b>morning-reminder-cron</b>: Cron expression for morning reminder job (runs at 7:50 AM UTC by default)</li>
 *   <li><b>follow-up-interval-ms</b>: Interval in milliseconds for follow-up transition job (15 minutes by default)</li>
 *   <li><b>stale-detection-interval-ms</b>: Interval in milliseconds for stale state detection job (1 hour by default)</li>
 *   <li><b>batch-size</b>: Number of records to process per batch (100 by default)</li>
 * </ul>
 * 
 * <p>Configuration is loaded from {@code application.yml} under the prefix {@code dadcoach.scheduler}:</p>
 * <pre>
 * dadcoach:
 *   scheduler:
 *     morning-reminder-cron: "0 50 7 * * *"
 *     follow-up-interval-ms: 900000
 *     stale-detection-interval-ms: 3600000
 *     batch-size: 100
 * </pre>
 * 
 * <p>Implements Requirement 12.1 from the deterministic-workflow-engine spec.</p>
 * 
 * @see WorkflowScheduler
 */
@Validated
@ConfigurationProperties(prefix = "dadcoach.scheduler")
public record SchedulerConfig(
    /**
     * Cron expression for the morning reminder job.
     * <p>Default: "0 50 7 * * *" (7:50 AM UTC)</p>
     * <p>The job queries Quality Times scheduled today and sends reminders
     * when the father's local time is approximately 8 AM.</p>
     */
    @NotBlank
    String morningReminderCron,
    
    /**
     * Interval in milliseconds for the follow-up transition job.
     * <p>Default: 900000 (15 minutes)</p>
     * <p>The job checks for Quality Time events that have ended and
     * transitions fathers to QUALITY_TIME_FOLLOW_UP state.</p>
     */
    @Min(60000) // Minimum 1 minute
    long followUpIntervalMs,
    
    /**
     * Interval in milliseconds for the stale state detection job.
     * <p>Default: 3600000 (1 hour)</p>
     * <p>The job detects fathers stuck in QUALITY_TIME_FOLLOW_UP state
     * for over 24 hours and auto-transitions them.</p>
     */
    @Min(300000) // Minimum 5 minutes
    long staleDetectionIntervalMs,
    
    /**
     * Number of records to process per batch.
     * <p>Default: 100</p>
     * <p>Processing fathers in batches avoids overwhelming the system
     * or WhatsApp rate limits (Requirement 12.6).</p>
     */
    @Min(1)
    int batchSize
) {
    /**
     * Default constructor with default values for all properties.
     * <p>These defaults are used when properties are not specified in configuration.</p>
     */
    public SchedulerConfig {
        // Apply defaults if not provided
        if (morningReminderCron == null || morningReminderCron.isBlank()) {
            morningReminderCron = "0 50 7 * * *"; // 7:50 AM UTC
        }
        if (followUpIntervalMs <= 0) {
            followUpIntervalMs = 900000L; // 15 minutes
        }
        if (staleDetectionIntervalMs <= 0) {
            staleDetectionIntervalMs = 3600000L; // 1 hour
        }
        if (batchSize <= 0) {
            batchSize = 100; // Default batch size per Requirement 12.6
        }
    }
    
    /**
     * Creates a SchedulerConfig with all default values.
     * 
     * @return a SchedulerConfig with default settings
     */
    public static SchedulerConfig defaults() {
        return new SchedulerConfig(
            "0 50 7 * * *",  // Morning reminder at 7:50 AM UTC
            900000L,         // 15 minutes for follow-up checks
            3600000L,        // 1 hour for stale state detection
            100              // Batch size of 100
        );
    }
}
