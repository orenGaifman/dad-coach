package com.dadcoach.memory.sensitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled service for enforcing retention policy on safety event records.
 *
 * <p>From SPEC-004 Task 12.3:
 * Safety event records should have expiration/retention enforcement. Safety events
 * are kept for legal compliance (7 years by default) and have a longer retention
 * policy than regular audit logs (2 years).
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Safety events are NOT deleted during GDPR erasure</li>
 *   <li>Only deleted after retention period (7 years by default) expires</li>
 *   <li>Runs weekly to permanently delete expired safety event records</li>
 *   <li>Processes in batches to avoid memory issues with large datasets</li>
 *   <li>Logs all deletion operations for compliance auditing</li>
 * </ul>
 *
 * <p>The service runs weekly during the maintenance window (default: Sunday 4:00 AM UTC)
 * to minimize impact on system operations.
 *
 * @see SafetyEventRecord
 * @see SafetyEventRepository
 */
@Service
public class SafetyEventRetentionService {

    private static final Logger log = LoggerFactory.getLogger(SafetyEventRetentionService.class);

    /**
     * Default batch size for processing expired records.
     */
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final SafetyEventRepository safetyEventRepository;

    /**
     * Batch size for processing expired records to avoid memory issues.
     */
    @Value("${dadcoach.safety-events.retention.batch-size:100}")
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * Whether retention enforcement is enabled.
     * Can be disabled for testing or maintenance.
     */
    @Value("${dadcoach.safety-events.retention.enabled:true}")
    private boolean retentionEnabled = true;

    /**
     * Creates a SafetyEventRetentionService with required dependencies.
     *
     * @param safetyEventRepository the repository for safety event persistence
     */
    public SafetyEventRetentionService(SafetyEventRepository safetyEventRepository) {
        this.safetyEventRepository = safetyEventRepository;
    }

    /**
     * Scheduled job that permanently deletes expired safety event records.
     *
     * <p>Runs weekly on Sunday at 4:00 AM UTC during the maintenance window.
     * This timing ensures minimal impact on system operations.
     *
     * <p>The job:
     * <ol>
     *   <li>Finds safety events with expiresAt before the current time</li>
     *   <li>Permanently deletes them in batches to avoid memory issues</li>
     *   <li>Logs all deletion operations for compliance auditing</li>
     *   <li>Reports total records processed and deleted</li>
     * </ol>
     *
     * <p>Note: Safety events are kept for legal compliance (7 years by default).
     * This is longer than regular audit logs (2 years) due to legal requirements.
     * Safety events are NOT deleted during GDPR erasure requests.
     */
    @Scheduled(cron = "${dadcoach.safety-events.retention.cron:0 0 4 * * SUN}") // Default: Sunday 4:00 AM UTC
    public void runWeeklyRetentionEnforcement() {
        if (!retentionEnabled) {
            log.info("SafetyEventRetentionService: Retention enforcement is disabled, skipping job");
            return;
        }

        log.info("SafetyEventRetentionService: Starting weekly retention enforcement job");
        Instant jobStartTime = Instant.now();

        try {
            RetentionResult result = processExpiredRecords(Instant.now());

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());

            log.info("SafetyEventRetentionService: Weekly retention enforcement job completed. " +
                            "recordsProcessed={}, recordsDeleted={}, batches={}, errors={}, durationMs={}",
                    result.recordsProcessed(), result.recordsDeleted(),
                    result.batchesProcessed(), result.errors(), durationMs);

        } catch (Exception e) {
            log.error("SafetyEventRetentionService: Weekly retention enforcement job failed. error={}",
                    e.getMessage(), e);
        }
    }

    /**
     * Processes expired safety event records in batches.
     *
     * <p>This method finds all safety events with expiresAt before the given time
     * and permanently deletes them. Processing is done in batches to avoid
     * memory issues with large datasets.
     *
     * @param expirationTime the time to check against (typically now)
     * @return the retention processing result
     */
    @Transactional
    public RetentionResult processExpiredRecords(Instant expirationTime) {
        log.debug("SafetyEventRetentionService: Processing expired records before {}", expirationTime);

        int totalProcessed = 0;
        int totalDeleted = 0;
        int batchesProcessed = 0;
        int errors = 0;

        // Process in batches until no more expired records
        boolean hasMoreRecords = true;
        while (hasMoreRecords) {
            try {
                List<SafetyEventRecord> expiredBatch = safetyEventRepository
                        .findExpiredBeforeWithLimit(expirationTime, batchSize);

                if (expiredBatch.isEmpty()) {
                    hasMoreRecords = false;
                    continue;
                }

                batchesProcessed++;
                totalProcessed += expiredBatch.size();

                // Extract IDs and delete
                List<UUID> idsToDelete = expiredBatch.stream()
                        .map(SafetyEventRecord::getId)
                        .toList();

                // Log each deletion for compliance auditing
                for (SafetyEventRecord record : expiredBatch) {
                    log.info("SafetyEventRetentionService: Deleting expired safety event. " +
                                    "eventId={}, fatherId={}, eventType={}, severity={}, " +
                                    "createdAt={}, expiresAt={}",
                            record.getId(), record.getFatherId(), record.getEventType(),
                            record.getSeverity(), record.getCreatedAt(), record.getExpiresAt());
                }

                // Perform batch delete
                safetyEventRepository.deleteByIdIn(idsToDelete);
                totalDeleted += idsToDelete.size();

                log.debug("SafetyEventRetentionService: Deleted batch of {} expired records, batch #{}",
                        idsToDelete.size(), batchesProcessed);

            } catch (Exception e) {
                errors++;
                log.error("SafetyEventRetentionService: Error processing batch. batch={}, error={}",
                        batchesProcessed, e.getMessage(), e);

                // Stop processing on error to avoid potential data issues
                hasMoreRecords = false;
            }
        }

        log.debug("SafetyEventRetentionService: Retention processing complete. " +
                        "totalProcessed={}, totalDeleted={}, batches={}, errors={}",
                totalProcessed, totalDeleted, batchesProcessed, errors);

        return new RetentionResult(totalProcessed, totalDeleted, batchesProcessed, errors);
    }

    /**
     * Gets the count of expired safety events awaiting deletion.
     *
     * <p>Useful for monitoring and reporting purposes.
     *
     * @return count of expired safety events
     */
    @Transactional(readOnly = true)
    public long countExpiredRecords() {
        return safetyEventRepository.countExpiredBefore(Instant.now());
    }

    /**
     * Gets safety events expiring within the specified number of days.
     *
     * <p>Useful for generating reports or warnings about upcoming expirations.
     *
     * @param days number of days to look ahead
     * @return list of safety events expiring within the specified window
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getExpiringWithinDays(int days) {
        Instant now = Instant.now();
        Instant endTime = now.plus(days, ChronoUnit.DAYS);
        return safetyEventRepository.findExpiringBetween(now, endTime);
    }

    /**
     * Manually triggers the retention enforcement job.
     *
     * <p>This method can be called from admin endpoints to trigger
     * the retention job outside the scheduled run.
     *
     * @return the retention processing result
     */
    public RetentionResult triggerRetentionEnforcement() {
        log.info("SafetyEventRetentionService: Manually triggering retention enforcement");
        return processExpiredRecords(Instant.now());
    }

    /**
     * Checks if retention enforcement is enabled.
     *
     * @return true if retention enforcement is enabled
     */
    public boolean isRetentionEnabled() {
        return retentionEnabled;
    }

    /**
     * Sets whether retention enforcement is enabled.
     *
     * <p>Can be used to temporarily disable retention for maintenance.
     *
     * @param enabled true to enable retention enforcement
     */
    public void setRetentionEnabled(boolean enabled) {
        this.retentionEnabled = enabled;
        log.info("SafetyEventRetentionService: Retention enforcement {}",
                enabled ? "enabled" : "disabled");
    }

    /**
     * Gets the current batch size for processing.
     *
     * @return the batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the batch size for processing.
     *
     * @param batchSize the new batch size (must be positive)
     */
    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
        log.info("SafetyEventRetentionService: Batch size set to {}", batchSize);
    }

    /**
     * Result record for retention processing.
     *
     * @param recordsProcessed total records evaluated
     * @param recordsDeleted   records that were permanently deleted
     * @param batchesProcessed number of batches processed
     * @param errors           count of processing errors
     */
    public record RetentionResult(int recordsProcessed, int recordsDeleted,
                                  int batchesProcessed, int errors) {
    }
}
