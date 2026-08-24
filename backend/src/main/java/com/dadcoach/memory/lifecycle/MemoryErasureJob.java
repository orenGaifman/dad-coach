package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditContentErasureService;
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
import java.util.UUID;

/**
 * Scheduled service for erasing memory content within 72 hours of deletion.
 *
 * <p>From SPEC-004 Requirement 2 Criteria 7:
 * WHEN a memory transitions to DELETED state, THE Memory_System SHALL perform
 * complete content erasure within 72 hours, including:
 * <ul>
 *   <li>Memory content field (set to null or "[DELETED]")</li>
 *   <li>Memory embedding vector</li>
 *   <li>All version history content_snapshots for that memory (from audit log toState field)</li>
 * </ul>
 *
 * <p>After erasure, only the following metadata SHALL be retained in the audit log:
 * <ul>
 *   <li>memory_id</li>
 *   <li>father_id</li>
 *   <li>category</li>
 *   <li>subject_type</li>
 *   <li>operation timestamps</li>
 *   <li>state transitions</li>
 * </ul>
 *
 * <p>Design decisions (per SPEC-004 AD-4):
 * <ul>
 *   <li>Memories transition to DELETED state immediately</li>
 *   <li>Content erasure is deferred to this background job</li>
 *   <li>Job runs every 6 hours to ensure 72-hour SLA</li>
 *   <li>Processes in batches to avoid lock contention</li>
 *   <li>Handles partial failures with per-memory error handling</li>
 * </ul>
 *
 * @see Memory
 * @see MemoryState
 * @see MemoryAuditService
 */
@Service
public class MemoryErasureJob {

    private static final Logger log = LoggerFactory.getLogger(MemoryErasureJob.class);

    /**
     * Placeholder content after erasure.
     * Using "[ERASED]" makes it clear the content was intentionally removed.
     */
    public static final String ERASED_CONTENT_PLACEHOLDER = "[ERASED]";

    /**
     * Default hours after DELETED state before content is erased.
     * Set to 0 to erase immediately (72 hours is max SLA, not minimum wait).
     */
    private static final int DEFAULT_ERASURE_DELAY_HOURS = 0;

    /**
     * Maximum hours after DELETED state - content MUST be erased by this time.
     * Per SPEC-004 Requirement 2 Criteria 7: 72 hours.
     */
    public static final int MAX_ERASURE_SLA_HOURS = 72;

    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;
    private final MemoryAuditContentErasureService auditContentErasureService;

    /**
     * Batch size for processing memories to avoid lock contention.
     */
    @Value("${dadcoach.memory.erasure.batch-size:50}")
    private int batchSize;

    /**
     * Hours to wait after DELETED state before erasing content.
     * Can be set to 0 to erase immediately.
     */
    @Value("${dadcoach.memory.erasure.delay-hours:0}")
    private int erasureDelayHours;

    /**
     * Creates a MemoryErasureJob with required dependencies.
     *
     * @param memoryRepository the repository for memory persistence
     * @param auditService     the service for audit logging
     * @param auditContentErasureService the service for erasing audit log content snapshots
     */
    public MemoryErasureJob(MemoryRepository memoryRepository,
                           MemoryAuditService auditService,
                           MemoryAuditContentErasureService auditContentErasureService) {
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
        this.auditContentErasureService = auditContentErasureService;
    }

    /**
     * Scheduled job that erases content from DELETED memories.
     *
     * <p>Runs every 6 hours to ensure content is erased well within the 72-hour SLA.
     * The job processes memories in batches to avoid lock contention and memory pressure.
     *
     * <p>Cron expression: "0 0 0/6 * * *" = Every 6 hours starting at midnight
     */
    @Scheduled(cron = "${dadcoach.memory.erasure.cron:0 0 0/6 * * *}")
    public void runErasureJob() {
        log.info("MemoryErasureJob: Starting content erasure job");
        Instant jobStartTime = Instant.now();

        try {
            ErasureResult result = eraseDeletedMemoryContent(jobStartTime);

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());

            log.info("MemoryErasureJob: Content erasure job completed. " +
                            "memoriesProcessed={}, memoriesErased={}, " +
                            "auditEntriesErased={}, errors={}, durationMs={}",
                    result.memoriesProcessed(), result.memoriesErased(),
                    result.auditEntriesErased(), result.errors(), durationMs);

        } catch (Exception e) {
            log.error("MemoryErasureJob: Content erasure job failed. error={}", e.getMessage(), e);
        }
    }

    /**
     * Erases content from all eligible DELETED memories.
     *
     * <p>A memory is eligible for erasure when:
     * <ul>
     *   <li>State is DELETED</li>
     *   <li>Content is not null (not already erased)</li>
     *   <li>lastUpdatedAt is before the erasure delay threshold</li>
     * </ul>
     *
     * <p>For each eligible memory:
     * <ol>
     *   <li>Erase the content field (set to "[ERASED]")</li>
     *   <li>Erase the embedding vector (set to null)</li>
     *   <li>Erase version history from audit log entries</li>
     *   <li>Create audit entry recording the erasure</li>
     * </ol>
     *
     * @param jobStartTime the time the job started (for race condition protection)
     * @return the erasure processing result
     */
    @Transactional
    public ErasureResult eraseDeletedMemoryContent(Instant jobStartTime) {
        log.debug("MemoryErasureJob: Finding DELETED memories eligible for content erasure");

        // Calculate cutoff time - erase content that's been in DELETED state long enough
        Instant cutoffTime = Instant.now().minus(erasureDelayHours, ChronoUnit.HOURS);

        // Find DELETED memories with content that hasn't been erased yet
        List<Memory> memoriesForErasure = memoryRepository.findDeletedForErasure(
                MemoryState.DELETED, cutoffTime);

        log.debug("MemoryErasureJob: Found {} DELETED memories eligible for content erasure",
                memoriesForErasure.size());

        int memoriesProcessed = 0;
        int memoriesErased = 0;
        int auditEntriesErased = 0;
        int errors = 0;

        for (Memory memory : memoriesForErasure) {
            memoriesProcessed++;

            // Skip if memory state changed since job started (race condition protection)
            if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                log.debug("MemoryErasureJob: Skipping memory (state changed since job start). memoryId={}",
                        memory.getId());
                continue;
            }

            try {
                ErasureDetails details = eraseMemoryContent(memory);
                memoriesErased++;
                auditEntriesErased += details.auditEntriesErased();

                log.debug("MemoryErasureJob: Successfully erased memory content. " +
                                "memoryId={}, fatherId={}, auditEntriesErased={}",
                        memory.getId(), memory.getFatherId(), details.auditEntriesErased());

            } catch (Exception e) {
                errors++;
                log.error("MemoryErasureJob: Error erasing memory content. memoryId={}, error={}",
                        memory.getId(), e.getMessage(), e);
            }
        }

        return new ErasureResult(memoriesProcessed, memoriesErased, auditEntriesErased, errors);
    }

    /**
     * Erases content from a single memory and its associated audit log entries.
     *
     * <p>This method performs the following erasure steps:
     * <ol>
     *   <li>Set memory content to "[ERASED]"</li>
     *   <li>Set memory embedding to null</li>
     *   <li>Erase state_before and state_after JSON in all audit entries for this memory</li>
     *   <li>Create an ERASE audit entry recording the operation</li>
     * </ol>
     *
     * @param memory the memory to erase content from
     * @return details about what was erased
     */
    @Transactional
    public ErasureDetails eraseMemoryContent(Memory memory) {
        if (memory.getState() != MemoryState.DELETED) {
            throw new IllegalStateException(
                    "Cannot erase content from non-DELETED memory. state=" + memory.getState());
        }

        UUID memoryId = memory.getId();
        UUID fatherId = memory.getFatherId();

        log.debug("MemoryErasureJob: Erasing content for memory. memoryId={}, fatherId={}",
                memoryId, fatherId);

        // Step 1: Capture metadata before erasure for audit
        String stateBefore = auditService.serializeMemoryState(memory);

        // Step 2: Erase memory content
        memory.setContent(ERASED_CONTENT_PLACEHOLDER);

        // Step 3: Erase embedding vector
        memory.setEmbedding(null);

        // Step 4: Update timestamp
        memory.setLastUpdatedAt(Instant.now());

        // Step 5: Save the erased memory
        Memory savedMemory = memoryRepository.save(memory);

        // Step 6: Erase version history from audit log entries
        int auditEntriesErased = auditContentErasureService.eraseAuditContentForMemory(memoryId);

        // Step 7: Create audit entry for the erasure operation
        auditService.createAuditEntryWithStateTransition(
                savedMemory,
                EventType.ERASE,
                MemoryState.DELETED,  // from_state
                MemoryState.DELETED,  // to_state (stays DELETED)
                ActorType.SYSTEM,
                "SYSTEM:erasure_job",
                stateBefore
        );

        log.info("MemoryErasureJob: Content erasure completed. memoryId={}, fatherId={}, " +
                        "auditEntriesErased={}",
                memoryId, fatherId, auditEntriesErased);

        return new ErasureDetails(auditEntriesErased);
    }

    /**
     * Manually triggers the content erasure job.
     *
     * <p>This method can be called from admin endpoints to trigger
     * the erasure job outside the scheduled run.
     *
     * @return the erasure processing result
     */
    public ErasureResult triggerErasureJob() {
        log.info("MemoryErasureJob: Manually triggering content erasure job");
        return eraseDeletedMemoryContent(Instant.now());
    }

    /**
     * Erases content for a specific memory immediately.
     *
     * <p>This method can be called for urgent GDPR erasure requests
     * that cannot wait for the scheduled job.
     *
     * @param memoryId the ID of the memory to erase
     * @return details about what was erased
     * @throws IllegalArgumentException if the memory is not in DELETED state
     */
    @Transactional
    public ErasureDetails eraseMemoryById(UUID memoryId) {
        log.info("MemoryErasureJob: Immediate erasure requested. memoryId={}", memoryId);

        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found: " + memoryId));

        if (memory.getState() != MemoryState.DELETED) {
            throw new IllegalArgumentException(
                    "Memory must be in DELETED state for erasure. current state=" + memory.getState());
        }

        // Check if already erased
        if (ERASED_CONTENT_PLACEHOLDER.equals(memory.getContent())) {
            log.info("MemoryErasureJob: Memory content already erased. memoryId={}", memoryId);
            return new ErasureDetails(0);
        }

        return eraseMemoryContent(memory);
    }

    /**
     * Erases all memory content for a father (for GDPR bulk erasure).
     *
     * <p>This method finds all DELETED memories for a father and erases their content.
     * Should be called after {@link MemoryLifecycleService#deleteAllForFather(UUID)}
     * when immediate erasure is required for GDPR compliance.
     *
     * @param fatherId the father whose memories should be erased
     * @return the erasure processing result
     */
    @Transactional
    public ErasureResult eraseAllContentForFather(UUID fatherId) {
        log.info("MemoryErasureJob: Bulk content erasure for father. fatherId={}", fatherId);

        List<Memory> deletedMemories = memoryRepository.findByFatherIdAndState(
                fatherId, MemoryState.DELETED);

        int memoriesProcessed = 0;
        int memoriesErased = 0;
        int auditEntriesErased = 0;
        int errors = 0;

        for (Memory memory : deletedMemories) {
            memoriesProcessed++;

            // Skip if already erased
            if (ERASED_CONTENT_PLACEHOLDER.equals(memory.getContent())) {
                log.debug("MemoryErasureJob: Skipping already erased memory. memoryId={}",
                        memory.getId());
                continue;
            }

            try {
                ErasureDetails details = eraseMemoryContent(memory);
                memoriesErased++;
                auditEntriesErased += details.auditEntriesErased();
            } catch (Exception e) {
                errors++;
                log.error("MemoryErasureJob: Error erasing memory during bulk erasure. memoryId={}, error={}",
                        memory.getId(), e.getMessage(), e);
            }
        }

        log.info("MemoryErasureJob: Bulk content erasure completed. fatherId={}, " +
                        "memoriesProcessed={}, memoriesErased={}, auditEntriesErased={}, errors={}",
                fatherId, memoriesProcessed, memoriesErased, auditEntriesErased, errors);

        return new ErasureResult(memoriesProcessed, memoriesErased, auditEntriesErased, errors);
    }

    /**
     * Result record for erasure processing.
     *
     * @param memoriesProcessed total memories evaluated
     * @param memoriesErased    memories that had content erased
     * @param auditEntriesErased total audit entries with erased content
     * @param errors            count of processing errors
     */
    public record ErasureResult(int memoriesProcessed, int memoriesErased, 
                                int auditEntriesErased, int errors) {
    }

    /**
     * Details record for individual memory erasure.
     *
     * @param auditEntriesErased number of audit entries that had content erased
     */
    public record ErasureDetails(int auditEntriesErased) {
    }
}
