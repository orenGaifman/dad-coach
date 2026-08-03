package com.dadcoach.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SchedulerJobLog} entities.
 * 
 * Provides methods for querying and persisting scheduler job execution logs.
 * The save method is inherited from JpaRepository for job execution logging.
 * 
 * Requirements: 16.3 - Scheduler jobs must persist execution logs
 */
@Repository
public interface SchedulerJobLogRepository extends JpaRepository<SchedulerJobLog, UUID> {

    /**
     * Find all job logs for a specific job, ordered by started time descending.
     * Used for debugging and monitoring scheduler job executions.
     * 
     * Requirements: 16.3
     * 
     * @param jobName the scheduler job name
     * @return list of job logs ordered by most recent first
     */
    List<SchedulerJobLog> findByJobNameOrderByStartedAtDesc(String jobName);

    /**
     * Find the most recent job log for a specific job.
     * Used to check if a job has run recently for idempotency.
     * 
     * @param jobName the scheduler job name
     * @return the most recent job log, if any
     */
    Optional<SchedulerJobLog> findTopByJobNameOrderByStartedAtDesc(String jobName);

    /**
     * Find all job logs by status.
     * Used for monitoring job health.
     * 
     * @param status the job status
     * @return list of job logs with the given status
     */
    List<SchedulerJobLog> findByStatus(SchedulerJobStatus status);

    /**
     * Find all job logs by job name and status.
     * 
     * @param jobName the scheduler job name
     * @param status the job status
     * @return list of matching job logs
     */
    List<SchedulerJobLog> findByJobNameAndStatus(String jobName, SchedulerJobStatus status);

    /**
     * Find job logs started within a time range.
     * Used for generating reports on job executions.
     * 
     * @param start the start of the time range
     * @param end the end of the time range
     * @return list of job logs started within the range
     */
    List<SchedulerJobLog> findByStartedAtBetween(Instant start, Instant end);

    /**
     * Count job logs by job name and status.
     * Used for monitoring and alerting on failed jobs.
     * 
     * @param jobName the scheduler job name
     * @param status the job status
     * @return count of matching job logs
     */
    long countByJobNameAndStatus(String jobName, SchedulerJobStatus status);
}
