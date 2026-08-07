package com.dadcoach.workflow.scheduler;

/**
 * Status of a scheduler job execution.
 * 
 * Requirements: 16.3 - Scheduler jobs must persist execution logs with status
 */
public enum SchedulerJobStatus {
    /**
     * Job is currently running.
     */
    RUNNING,

    /**
     * Job completed successfully.
     */
    COMPLETED,

    /**
     * Job failed during execution.
     */
    FAILED
}
