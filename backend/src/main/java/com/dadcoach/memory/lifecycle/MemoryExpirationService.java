package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled service for transitioning expired memories to the EXPIRED state.
 *
 * <p>From SPEC-004 Requirements 2 and 6:
 * <ul>
 *   <li>Memories transition to EXPIRED when their expires_at timestamp is past the current time</li>
 *   <li>Memories also expire when confidence drops below 0.5 AND not accessed in 60 days
 *       (for Short-term and Medium-term tiers only)</li>
 *   <li>Long-term tier memories (importance 7-10) are exempt from time-based expiration</li>
 *   <li>Expired memories are preserved for 30 days before automatic deletion (allowing reactivation)</li>
 * </ul>
 *
 * <p>The service runs daily during the maintenance window (default: 3:15 AM UTC) and processes
 * memories in batches to avoid lock contention.
 *
 * <p>Expiration criteria checked:
 * <ol>
 *   <li><b>Time-based expiration:</b> ACTIVE memories where expires_at is in the past</li>
 *   <li><b>Confidence-based expiration:</b> ACTIVE memories where confidence &lt; 0.5 AND
 *       not accessed in 60 days (Short-term and Medium-term tiers only)</li>
 * </ol>
 *
 * <p>Race condition protection: Skips memories whose state changed after the job started.
 *
 * @see Memory
 * @see MemoryDecayService
 * @see MemoryAuditService
 */
@Service
public class MemoryExpirationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExpirationService.class);

    /**
     * States eligible for expiration processing.
     */
    private static final Set<MemoryState> EXPIRABLE_STATES = Set.of(MemoryState.ACTIVE);

    /**
     * Confidence threshold below which memories may expire (Requirement 5 criteria 5).
     */
    private static final BigDecimal CONFIDENCE_EXPIRATION_THRESHOLD = new BigDecimal("0.50");

    /**
     * Days without access before confidence-based expiration applies (Requirement 5 criteria 5).
     */
    private static final int ACCESS_WINDOW_DAYS = 60;

    /**
     * Minimum importance score for Long-term tier (exempt from time-based expiration).
     */
    private static final int LONG_TERM_MIN_IMPORTANCE = 7;

    /**
     * Confidence threshold for exempt FAMILY memories.
     */
    private static final BigDecimal FAMILY_EXEMPT_CONFIDENCE = new BigDecimal("0.90");

    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;

    /**
     * Batch size for processing memories to avoid lock contention.
     */
    @Value("${dadcoach.memory.expiration.batch-size:100}")
    private int batchSize;

    /**
     * Creates a MemoryExpirationService with required dependencies.
     *
     * @param memoryRepository the repository for memory persistence
     * @param auditService     the service for audit logging
     */
    public MemoryExpirationService(MemoryRepository memoryRepository, MemoryAuditService auditService) {
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
    }

    /**
     * Scheduled job that transitions expired memories daily.
     *
     * <p>Runs at 3:15 AM UTC daily during the maintenance window (per AD-3 in design.md).
     * This runs after the decay job (3:00 AM) to ensure decay-induced low confidence
     * is reflected before expiration checks.
     *
     * <p>The job:
     * <ol>
     *   <li>Finds ACTIVE memories past their expires_at timestamp</li>
     *   <li>Finds ACTIVE memories with low confidence and no recent access</li>
     *   <li>Transitions eligible memories to EXPIRED state</li>
     *   <li>Creates audit entries for all state transitions</li>
     *   <li>Logs processing counts and any errors</li>
     * </ol>
     */
    @Scheduled(cron = "${dadcoach.memory.expiration.cron:0 15 3 * * *}") // Default: 3:15 AM UTC daily
    public void runDailyExpiration() {
        log.info("MemoryExpirationService: Starting daily expiration job");
        Instant jobStartTime = Instant.now();

        try {
            ExpirationResult timeBasedResult = processTimeBasedExpiration(jobStartTime);
            ExpirationResult confidenceBasedResult = processConfidenceBasedExpiration(jobStartTime);

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());

            log.info("MemoryExpirationService: Daily expiration job completed. " +
                            "timeBasedProcessed={}, timeBasedExpired={}, " +
                            "confidenceBasedProcessed={}, confidenceBasedExpired={}, " +
                            "totalErrors={}, durationMs={}",
                    timeBasedResult.memoriesProcessed(), timeBasedResult.memoriesExpired(),
                    confidenceBasedResult.memoriesProcessed(), confidenceBasedResult.memoriesExpired(),
                    timeBasedResult.errors() + confidenceBasedResult.errors(), durationMs);

        } catch (Exception e) {
            log.error("MemoryExpirationService: Daily expiration job failed. error={}", e.getMessage(), e);
        }
    }

    /**
     * Processes time-based expiration for memories past their expires_at timestamp.
     *
     * <p>From SPEC-004 Requirement 6 Criteria 1:
     * <ul>
     *   <li>Short-term (importance 1-3): 90 days from creation unless promoted or accessed</li>
     *   <li>Medium-term (importance 4-6): 180 days from creation unless promoted or accessed</li>
     *   <li>Long-term (importance 7-10): Never expires (expires_at is null)</li>
     * </ul>
     *
     * @param jobStartTime the time the expiration job started (for race condition protection)
     * @return the expiration processing result
     */
    @Transactional
    public ExpirationResult processTimeBasedExpiration(Instant jobStartTime) {
        log.debug("MemoryExpirationService: Processing time-based expiration");

        Instant now = Instant.now();
        List<Memory> expiredMemories = memoryRepository.findExpiredMemories(now, MemoryState.ACTIVE);

        log.debug("MemoryExpirationService: Found {} memories past their expires_at", expiredMemories.size());

        int memoriesProcessed = 0;
        int memoriesExpired = 0;
        int errors = 0;

        for (Memory memory : expiredMemories) {
            memoriesProcessed++;

            try {
                // Skip if memory state changed since job started (race condition protection)
                if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                    log.debug("MemoryExpirationService: Skipping memory (state changed since job start). memoryId={}",
                            memory.getId());
                    continue;
                }

                // Skip Long-term tier memories (they should have null expires_at, but double-check)
                if (memory.getImportanceScore() >= LONG_TERM_MIN_IMPORTANCE) {
                    log.warn("MemoryExpirationService: Long-term memory has non-null expires_at (unexpected). " +
                                    "memoryId={}, importance={}, expiresAt={}",
                            memory.getId(), memory.getImportanceScore(), memory.getExpiresAt());
                    continue;
                }

                // Capture state before for audit
                String stateBefore = auditService.serializeMemoryState(memory);

                // Transition to EXPIRED
                memory.expire();
                memoryRepository.save(memory);

                // Create audit entry
                auditService.createAuditEntry(memory, EventType.UPDATE, ActorType.SYSTEM, stateBefore);

                memoriesExpired++;

                log.debug("MemoryExpirationService: Expired memory (time-based). memoryId={}, expiresAt={}",
                        memory.getId(), memory.getExpiresAt());

            } catch (Exception e) {
                errors++;
                log.error("MemoryExpirationService: Error expiring memory. memoryId={}, error={}",
                        memory.getId(), e.getMessage(), e);
            }
        }

        log.debug("MemoryExpirationService: Time-based expiration complete. " +
                        "memoriesProcessed={}, memoriesExpired={}, errors={}",
                memoriesProcessed, memoriesExpired, errors);

        return new ExpirationResult(memoriesProcessed, memoriesExpired, errors);
    }

    /**
     * Processes confidence-based expiration for memories with low confidence and no recent access.
     *
     * <p>From SPEC-004 Requirement 5 Criteria 5:
     * When confidence_score drops below 0.5 AND the memory has not been meaningfully used
     * in 60 days, the Memory_System SHALL transition it to EXPIRED — for Short-term and
     * Medium-term tiers only.
     *
     * <p>From Requirement 6 Criteria 7:
     * Long-term memories (importance 7-10) with IDENTITY category at confidence 1.0,
     * FAMILY memories with confidence >= 0.9, and active GOAL memories linked to a
     * non-completed goal are exempt.
     *
     * @param jobStartTime the time the expiration job started (for race condition protection)
     * @return the expiration processing result
     */
    @Transactional
    public ExpirationResult processConfidenceBasedExpiration(Instant jobStartTime) {
        log.debug("MemoryExpirationService: Processing confidence-based expiration");

        Instant accessThreshold = Instant.now().minus(ACCESS_WINDOW_DAYS, ChronoUnit.DAYS);
        List<Memory> lowConfidenceMemories = memoryRepository.findLowConfidenceUnaccessed(
                EXPIRABLE_STATES,
                CONFIDENCE_EXPIRATION_THRESHOLD,
                accessThreshold);

        log.debug("MemoryExpirationService: Found {} memories with low confidence and no recent access",
                lowConfidenceMemories.size());

        int memoriesProcessed = 0;
        int memoriesExpired = 0;
        int errors = 0;

        for (Memory memory : lowConfidenceMemories) {
            memoriesProcessed++;

            try {
                // Skip if memory state changed since job started (race condition protection)
                if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                    log.debug("MemoryExpirationService: Skipping memory (state changed since job start). memoryId={}",
                            memory.getId());
                    continue;
                }

                // Skip Long-term tier memories (they require explicit correction to expire)
                if (memory.getImportanceScore() >= LONG_TERM_MIN_IMPORTANCE) {
                    log.trace("MemoryExpirationService: Skipping Long-term memory (exempt from confidence expiration). " +
                                    "memoryId={}, importance={}",
                            memory.getId(), memory.getImportanceScore());
                    continue;
                }

                // Skip exempt memory categories (per Requirement 6 Criteria 7)
                if (isExemptFromExpiration(memory)) {
                    log.trace("MemoryExpirationService: Skipping exempt memory. memoryId={}, category={}, confidence={}",
                            memory.getId(), memory.getCategory(), memory.getConfidenceScore());
                    continue;
                }

                // Capture state before for audit
                String stateBefore = auditService.serializeMemoryState(memory);

                // Transition to EXPIRED
                memory.expire();
                memoryRepository.save(memory);

                // Create audit entry
                auditService.createAuditEntry(memory, EventType.UPDATE, ActorType.SYSTEM, stateBefore);

                memoriesExpired++;

                log.debug("MemoryExpirationService: Expired memory (confidence-based). " +
                                "memoryId={}, confidence={}, lastAccessedAt={}",
                        memory.getId(), memory.getConfidenceScore(), memory.getLastAccessedAt());

            } catch (Exception e) {
                errors++;
                log.error("MemoryExpirationService: Error expiring memory. memoryId={}, error={}",
                        memory.getId(), e.getMessage(), e);
            }
        }

        log.debug("MemoryExpirationService: Confidence-based expiration complete. " +
                        "memoriesProcessed={}, memoriesExpired={}, errors={}",
                memoriesProcessed, memoriesExpired, errors);

        return new ExpirationResult(memoriesProcessed, memoriesExpired, errors);
    }

    /**
     * Checks if a memory is exempt from confidence-based expiration.
     *
     * <p>From SPEC-004 Requirement 6 Criteria 7:
     * <ul>
     *   <li>IDENTITY memories with confidence 1.0 (hard facts like names and schools)</li>
     *   <li>FAMILY structure memories with confidence >= 0.9</li>
     *   <li>Active GOAL memories linked to a non-completed goal entity</li>
     * </ul>
     *
     * @param memory the memory to check
     * @return true if the memory is exempt from expiration
     */
    private boolean isExemptFromExpiration(Memory memory) {
        // IDENTITY memories with confidence 1.0 are exempt
        if (memory.getCategory() == MemoryCategory.IDENTITY
                && memory.getConfidenceScore().compareTo(BigDecimal.ONE) == 0) {
            return true;
        }

        // FAMILY memories with confidence >= 0.9 are exempt
        if (memory.getCategory() == MemoryCategory.FAMILY
                && memory.getConfidenceScore().compareTo(FAMILY_EXEMPT_CONFIDENCE) >= 0) {
            return true;
        }

        // GOAL memories linked to an active goal are exempt
        // Note: Checking goal_id is not null as a proxy for active goal.
        // A more complete implementation would verify the goal status.
        if (memory.getCategory() == MemoryCategory.GOAL && memory.getGoalId() != null) {
            return true;
        }

        return false;
    }

    /**
     * Result record for expiration processing.
     *
     * @param memoriesProcessed total memories evaluated
     * @param memoriesExpired   memories that were transitioned to EXPIRED
     * @param errors            count of processing errors
     */
    public record ExpirationResult(int memoriesProcessed, int memoriesExpired, int errors) {
    }

    // ─── Manual Execution (for testing/admin) ────────────────────────────────────

    /**
     * Manually triggers the full expiration job.
     *
     * <p>This method can be called from admin endpoints to trigger
     * the full expiration job outside the scheduled run.
     *
     * @return combined results from both expiration types
     */
    public ExpirationResult triggerFullExpiration() {
        log.info("MemoryExpirationService: Manually triggering full expiration job");
        Instant jobStartTime = Instant.now();

        ExpirationResult timeBasedResult = processTimeBasedExpiration(jobStartTime);
        ExpirationResult confidenceBasedResult = processConfidenceBasedExpiration(jobStartTime);

        return new ExpirationResult(
                timeBasedResult.memoriesProcessed() + confidenceBasedResult.memoriesProcessed(),
                timeBasedResult.memoriesExpired() + confidenceBasedResult.memoriesExpired(),
                timeBasedResult.errors() + confidenceBasedResult.errors()
        );
    }

    /**
     * Manually triggers time-based expiration only.
     *
     * @return the expiration processing result
     */
    public ExpirationResult triggerTimeBasedExpiration() {
        log.info("MemoryExpirationService: Manually triggering time-based expiration");
        return processTimeBasedExpiration(Instant.now());
    }

    /**
     * Manually triggers confidence-based expiration only.
     *
     * @return the expiration processing result
     */
    public ExpirationResult triggerConfidenceBasedExpiration() {
        log.info("MemoryExpirationService: Manually triggering confidence-based expiration");
        return processConfidenceBasedExpiration(Instant.now());
    }
}
