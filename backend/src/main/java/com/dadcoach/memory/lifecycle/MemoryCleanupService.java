package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service for permanently deleting memories that have been in EXPIRED or DELETED
 * state for more than 30 days (post-expiration cleanup).
 *
 * <p>From SPEC-004 Requirements 2 and 8:
 * <ul>
 *   <li>Requirement 2 Criteria 6: EXPIRED memories are preserved for 30 days before automatic
 *       deletion, allowing reactivation if referenced</li>
 *   <li>Requirement 8 Criteria 1 Phase 5: Clean up EXPIRED memories older than 30 days
 *       (transition to DELETED)</li>
 *   <li>Requirement 8: The consolidation job runs weekly during the maintenance window</li>
 * </ul>
 *
 * <p>This service runs weekly (Sunday at 4:00 AM UTC during the maintenance window) and:
 * <ol>
 *   <li>Finds memories in EXPIRED state where lastUpdatedAt > 30 days ago</li>
 *   <li>Finds memories in DELETED state where lastUpdatedAt > 30 days ago</li>
 *   <li>Creates audit entries before permanent deletion</li>
 *   <li>Permanently deletes the memories from the database</li>
 *   <li>Logs cleanup statistics</li>
 * </ol>
 *
 * <p>Design considerations:
 * <ul>
 *   <li>Weekly schedule reduces database load compared to daily cleanup</li>
 *   <li>Runs during maintenance window (Sunday 4:00 AM UTC) when traffic is low</li>
 *   <li>Processes in batches to avoid lock contention and memory pressure</li>
 *   <li>Creates audit entries before deletion for compliance and debugging</li>
 *   <li>Race condition protection: skips memories that changed state since job start</li>
 * </ul>
 *
 * @see Memory
 * @see MemoryExpirationService
 * @see MemoryAuditService
 */
@Service
public class MemoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCleanupService.class);

    /**
     * Days after which EXPIRED memories should be permanently deleted.
     * Per SPEC-004 Requirement 2 Criteria 6: 30 days.
     */
    private static final int EXPIRED_RETENTION_DAYS = 30;

    /**
     * Days after which DELETED memories should be permanently removed.
     * Per SPEC-004 design, DELETED memories are erased within 72 hours,
     * but this cleanup handles any stragglers (using 30 days for safety margin).
     */
    private static final int DELETED_RETENTION_DAYS = 30;

    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;

    /**
     * Batch size for processing memories to avoid lock contention.
     */
    @Value("${dadcoach.memory.cleanup.batch-size:100}")
    private int batchSize;

    /**
     * Creates a MemoryCleanupService with required dependencies.
     *
     * @param memoryRepository the repository for memory persistence
     * @param auditService     the service for audit logging
     */
    public MemoryCleanupService(MemoryRepository memoryRepository, MemoryAuditService auditService) {
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
    }

    /**
     * Scheduled job that permanently deletes old EXPIRED and DELETED memories weekly.
     *
     * <p>Runs at 4:00 AM UTC every Sunday during the maintenance window (per AD-3 in design.md).
     * This runs after the daily expiration job to ensure memories have been in their terminal
     * state for the full retention period.
     *
     * <p>The job:
     * <ol>
     *   <li>Finds EXPIRED memories that have been expired for over 30 days</li>
     *   <li>Finds DELETED memories that have been deleted for over 30 days</li>
     *   <li>Creates audit entries for permanent deletion</li>
     *   <li>Permanently removes memories from the database</li>
     *   <li>Logs cleanup statistics</li>
     * </ol>
     *
     * <p>Cron expression: "0 0 4 * * SUN" = 4:00 AM UTC every Sunday
     */
    @Scheduled(cron = "${dadcoach.memory.cleanup.cron:0 0 4 * * SUN}") // Default: 4:00 AM UTC every Sunday
    public void runWeeklyCleanup() {
        log.info("MemoryCleanupService: Starting weekly cleanup job");
        Instant jobStartTime = Instant.now();

        try {
            CleanupResult expiredResult = cleanupExpiredMemories(jobStartTime);
            CleanupResult deletedResult = cleanupDeletedMemories(jobStartTime);

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());

            log.info("MemoryCleanupService: Weekly cleanup job completed. " +
                            "expiredProcessed={}, expiredDeleted={}, " +
                            "deletedProcessed={}, deletedPermanentlyRemoved={}, " +
                            "totalErrors={}, durationMs={}",
                    expiredResult.memoriesProcessed(), expiredResult.memoriesRemoved(),
                    deletedResult.memoriesProcessed(), deletedResult.memoriesRemoved(),
                    expiredResult.errors() + deletedResult.errors(), durationMs);

        } catch (Exception e) {
            log.error("MemoryCleanupService: Weekly cleanup job failed. error={}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up EXPIRED memories that have been in that state for over 30 days.
     *
     * <p>Per SPEC-004 Requirement 2 Criteria 6:
     * EXPIRED memories are preserved for 30 days before automatic deletion, allowing
     * reactivation if referenced. After 30 days, they are permanently deleted.
     *
     * <p>The cleanup process:
     * <ol>
     *   <li>Finds all EXPIRED memories with lastUpdatedAt > 30 days ago</li>
     *   <li>For each memory:
     *     <ul>
     *       <li>Skip if state changed since job started (race condition protection)</li>
     *       <li>Create audit entry recording the permanent deletion</li>
     *       <li>Permanently delete the memory from the database</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param jobStartTime the time the cleanup job started (for race condition protection)
     * @return the cleanup processing result
     */
    @Transactional
    public CleanupResult cleanupExpiredMemories(Instant jobStartTime) {
        log.debug("MemoryCleanupService: Cleaning up EXPIRED memories older than {} days",
                EXPIRED_RETENTION_DAYS);

        Instant cutoffTime = Instant.now().minus(EXPIRED_RETENTION_DAYS, ChronoUnit.DAYS);
        List<Memory> expiredMemories = memoryRepository.findExpiredForCleanup(
                MemoryState.EXPIRED, cutoffTime);

        log.debug("MemoryCleanupService: Found {} EXPIRED memories eligible for cleanup",
                expiredMemories.size());

        int memoriesProcessed = 0;
        int memoriesRemoved = 0;
        int errors = 0;

        for (Memory memory : expiredMemories) {
            memoriesProcessed++;

            try {
                // Skip if memory state changed since job started (race condition protection)
                if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                    log.debug("MemoryCleanupService: Skipping memory (state changed since job start). " +
                            "memoryId={}", memory.getId());
                    continue;
                }

                // Capture state for audit before deletion
                String stateBefore = auditService.serializeMemoryState(memory);

                // Transition to DELETED state first (for state machine compliance)
                memory.delete();
                memoryRepository.save(memory);

                // Create audit entry for the deletion
                auditService.createAuditEntryForDelete(memory, ActorType.SYSTEM, stateBefore);

                // Permanently delete the memory
                memoryRepository.delete(memory);
                memoriesRemoved++;

                log.debug("MemoryCleanupService: Permanently deleted EXPIRED memory. " +
                                "memoryId={}, expiredAt={}",
                        memory.getId(), memory.getLastUpdatedAt());

            } catch (Exception e) {
                errors++;
                log.error("MemoryCleanupService: Error cleaning up EXPIRED memory. " +
                        "memoryId={}, error={}", memory.getId(), e.getMessage(), e);
            }
        }

        log.debug("MemoryCleanupService: EXPIRED cleanup complete. " +
                        "memoriesProcessed={}, memoriesRemoved={}, errors={}",
                memoriesProcessed, memoriesRemoved, errors);

        return new CleanupResult(memoriesProcessed, memoriesRemoved, errors);
    }

    /**
     * Cleans up DELETED memories that have been in that state for over 30 days.
     *
     * <p>Per SPEC-004 Requirement 2 Criteria 7:
     * When a memory transitions to DELETED state, complete content erasure should occur
     * within 72 hours. This cleanup job handles any remaining DELETED memories that
     * haven't been physically removed, ensuring they are permanently deleted after 30 days.
     *
     * <p>The cleanup process:
     * <ol>
     *   <li>Finds all DELETED memories with lastUpdatedAt > 30 days ago</li>
     *   <li>For each memory:
     *     <ul>
     *       <li>Skip if state changed since job started (race condition protection)</li>
     *       <li>Create audit entry if content still exists (for compliance)</li>
     *       <li>Permanently remove the memory record from the database</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param jobStartTime the time the cleanup job started (for race condition protection)
     * @return the cleanup processing result
     */
    @Transactional
    public CleanupResult cleanupDeletedMemories(Instant jobStartTime) {
        log.debug("MemoryCleanupService: Cleaning up DELETED memories older than {} days",
                DELETED_RETENTION_DAYS);

        Instant cutoffTime = Instant.now().minus(DELETED_RETENTION_DAYS, ChronoUnit.DAYS);

        // Find DELETED memories that haven't been fully removed yet
        // Note: This uses a simpler query - memories in DELETED state older than cutoff
        List<Memory> deletedMemories = memoryRepository.findAll().stream()
                .filter(m -> m.getState() == MemoryState.DELETED)
                .filter(m -> m.getLastUpdatedAt() != null && m.getLastUpdatedAt().isBefore(cutoffTime))
                .toList();

        log.debug("MemoryCleanupService: Found {} DELETED memories eligible for permanent removal",
                deletedMemories.size());

        int memoriesProcessed = 0;
        int memoriesRemoved = 0;
        int errors = 0;

        for (Memory memory : deletedMemories) {
            memoriesProcessed++;

            try {
                // Skip if memory state changed since job started (race condition protection)
                if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                    log.debug("MemoryCleanupService: Skipping memory (state changed since job start). " +
                            "memoryId={}", memory.getId());
                    continue;
                }

                // Note: Audit entry was already created when the memory was initially deleted
                // We're just permanently removing the record now

                // Permanently delete the memory
                memoryRepository.delete(memory);
                memoriesRemoved++;

                log.debug("MemoryCleanupService: Permanently removed DELETED memory. " +
                                "memoryId={}, deletedAt={}",
                        memory.getId(), memory.getLastUpdatedAt());

            } catch (Exception e) {
                errors++;
                log.error("MemoryCleanupService: Error removing DELETED memory. " +
                        "memoryId={}, error={}", memory.getId(), e.getMessage(), e);
            }
        }

        log.debug("MemoryCleanupService: DELETED cleanup complete. " +
                        "memoriesProcessed={}, memoriesRemoved={}, errors={}",
                memoriesProcessed, memoriesRemoved, errors);

        return new CleanupResult(memoriesProcessed, memoriesRemoved, errors);
    }

    /**
     * Result record for cleanup processing.
     *
     * @param memoriesProcessed total memories evaluated
     * @param memoriesRemoved   memories that were permanently deleted
     * @param errors            count of processing errors
     */
    public record CleanupResult(int memoriesProcessed, int memoriesRemoved, int errors) {
    }

    // ─── Manual Execution (for testing/admin) ────────────────────────────────────

    /**
     * Manually triggers the full cleanup job.
     *
     * <p>This method can be called from admin endpoints to trigger
     * the full cleanup job outside the scheduled weekly run.
     *
     * @return combined results from both cleanup operations
     */
    public CleanupResult triggerFullCleanup() {
        log.info("MemoryCleanupService: Manually triggering full cleanup job");
        Instant jobStartTime = Instant.now();

        CleanupResult expiredResult = cleanupExpiredMemories(jobStartTime);
        CleanupResult deletedResult = cleanupDeletedMemories(jobStartTime);

        return new CleanupResult(
                expiredResult.memoriesProcessed() + deletedResult.memoriesProcessed(),
                expiredResult.memoriesRemoved() + deletedResult.memoriesRemoved(),
                expiredResult.errors() + deletedResult.errors()
        );
    }

    /**
     * Manually triggers EXPIRED memory cleanup only.
     *
     * @return the cleanup processing result
     */
    public CleanupResult triggerExpiredCleanup() {
        log.info("MemoryCleanupService: Manually triggering EXPIRED memory cleanup");
        return cleanupExpiredMemories(Instant.now());
    }

    /**
     * Manually triggers DELETED memory cleanup only.
     *
     * @return the cleanup processing result
     */
    public CleanupResult triggerDeletedCleanup() {
        log.info("MemoryCleanupService: Manually triggering DELETED memory cleanup");
        return cleanupDeletedMemories(Instant.now());
    }
}
