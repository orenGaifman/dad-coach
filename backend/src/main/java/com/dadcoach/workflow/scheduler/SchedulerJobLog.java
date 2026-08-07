package com.dadcoach.workflow.scheduler;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a scheduler job execution log entry.
 * Maps to the "scheduler_job_log" table.
 * 
 * Tracks execution of scheduled jobs for idempotency and debugging.
 * Each job run creates a new log entry that tracks start time, completion,
 * records processed, errors, and final status.
 * 
 * Requirements: 12.3, 16.3 - Scheduler jobs must be idempotent and persist execution logs
 */
@Entity
@Table(name = "scheduler_job_log")
public class SchedulerJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Identifier for the scheduler job (e.g., morning_reminder, follow_up_transition, stale_state_detection).
     */
    @Column(name = "job_name", length = 100, nullable = false)
    private String jobName;

    /**
     * Timestamp when the job started execution.
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * Timestamp when the job completed (null if still running or failed without completion).
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Count of records successfully processed during this job run.
     */
    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed = 0;

    /**
     * Count of errors encountered during this job run.
     */
    @Column(name = "errors_count", nullable = false)
    private int errorsCount = 0;

    /**
     * Current status of the job: RUNNING, COMPLETED, or FAILED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SchedulerJobStatus status = SchedulerJobStatus.RUNNING;

    /**
     * Timestamp when this log entry was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JPA-required no-arg constructor. Not for application use.
     */
    protected SchedulerJobLog() {
    }

    /**
     * Creates a new scheduler job log entry for a starting job.
     * 
     * @param jobName the identifier for the scheduler job
     */
    public SchedulerJobLog(String jobName) {
        this.jobName = jobName;
        this.startedAt = Instant.now();
        this.status = SchedulerJobStatus.RUNNING;
    }

    // ─── JPA Lifecycle Callbacks ─────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.startedAt == null) {
            this.startedAt = this.createdAt;
        }
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Marks this job as completed successfully.
     * 
     * @param recordsProcessed the number of records processed
     * @param errorsCount the number of errors encountered
     */
    public void markCompleted(int recordsProcessed, int errorsCount) {
        this.recordsProcessed = recordsProcessed;
        this.errorsCount = errorsCount;
        this.completedAt = Instant.now();
        this.status = SchedulerJobStatus.COMPLETED;
    }

    /**
     * Marks this job as failed.
     * 
     * @param recordsProcessed the number of records processed before failure
     * @param errorsCount the number of errors encountered
     */
    public void markFailed(int recordsProcessed, int errorsCount) {
        this.recordsProcessed = recordsProcessed;
        this.errorsCount = errorsCount;
        this.completedAt = Instant.now();
        this.status = SchedulerJobStatus.FAILED;
    }

    /**
     * Increments the processed records count.
     */
    public void incrementProcessed() {
        this.recordsProcessed++;
    }

    /**
     * Increments the errors count.
     */
    public void incrementErrors() {
        this.errorsCount++;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(int recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(int errorsCount) {
        this.errorsCount = errorsCount;
    }

    public SchedulerJobStatus getStatus() {
        return status;
    }

    public void setStatus(SchedulerJobStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "SchedulerJobLog{" +
                "id=" + id +
                ", jobName='" + jobName + '\'' +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", recordsProcessed=" + recordsProcessed +
                ", errorsCount=" + errorsCount +
                ", status=" + status +
                '}';
    }
}
