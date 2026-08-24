package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemoryTier;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled service for applying confidence decay to memories.
 *
 * <p>From SPEC-004 Requirement 6 (Memory Decay and Aging):
 * The Memory_System SHALL apply tier-based confidence decay to memories not accessed
 * within their decay window. This service runs daily during the configured maintenance
 * window (default: 3:00 AM UTC) and processes fathers in batches to avoid lock contention.
 *
 * <p>Decay rules by tier (from Requirement 6 Criteria 3):
 * <ul>
 *   <li>Tier 1 (Short-term, importance 1-3): Decay starts 30 days after last access, rate -0.15/30 days</li>
 *   <li>Tier 2 (Medium-term, importance 4-6): Decay starts 60 days after last access, rate -0.10/30 days</li>
 *   <li>Tier 3 (Long-term, importance 7-10): Decay starts 90 days after last access, rate -0.05/30 days</li>
 * </ul>
 *
 * <p>Exemptions from decay (from Requirement 6 Criteria 7):
 * <ul>
 *   <li>IDENTITY memories with confidence 1.0 (hard facts like names and schools)</li>
 *   <li>FAMILY structure memories with confidence >= 0.9</li>
 *   <li>Active GOAL memories linked to a non-completed goal entity</li>
 * </ul>
 *
 * <p>Additional behaviors:
 * <ul>
 *   <li>Memories confirmed 3+ times have halved decay rate (Req 6 Criteria 5)</li>
 *   <li>Skips memories that changed state since job start (race condition protection)</li>
 *   <li>Creates audit entries for all decay operations</li>
 * </ul>
 *
 * @see MemoryTier
 * @see Memory
 * @see MemoryAuditService
 */
@Service
public class MemoryDecayService {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayService.class);

    /**
     * States that are eligible for decay processing.
     */
    private static final Set<MemoryState> DECAYABLE_STATES = Set.of(
            MemoryState.ACTIVE,
            MemoryState.CONFIRMED
    );

    /**
     * Confidence threshold below which memories are not included in decay processing
     * (they may be eligible for expiration instead).
     */
    private static final BigDecimal MIN_CONFIDENCE_FOR_DECAY = new BigDecimal("0.10");

    /**
     * Number of days in a decay period (decay rates are per 30 days).
     */
    private static final int DECAY_PERIOD_DAYS = 30;

    /**
     * Minimum number of confirmations to halve decay rate (Req 6 Criteria 5).
     */
    private static final int HIGH_RELIABILITY_CONFIRMATION_COUNT = 3;

    /**
     * Confidence threshold for exempt FAMILY memories.
     */
    private static final BigDecimal FAMILY_EXEMPT_CONFIDENCE = new BigDecimal("0.90");

    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;

    /**
     * Batch size for processing fathers to avoid lock contention.
     * Configurable via dadcoach.memory.decay.batch-size property.
     */
    @Value("${dadcoach.memory.decay.batch-size:50}")
    private int batchSize;

    /**
     * Delay in milliseconds between processing batches to reduce database pressure.
     * Configurable via dadcoach.memory.decay.batch-delay-ms property.
     */
    @Value("${dadcoach.memory.decay.batch-delay-ms:100}")
    private long batchDelayMs;

    /**
     * Creates a MemoryDecayService with required dependencies.
     *
     * @param memoryRepository the repository for memory persistence
     * @param auditService     the service for audit logging
     */
    public MemoryDecayService(MemoryRepository memoryRepository, MemoryAuditService auditService) {
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
    }

    /**
     * Returns the current batch size configuration.
     * Useful for testing and monitoring.
     *
     * @return the batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the batch size for processing (mainly for testing).
     *
     * @param batchSize the new batch size
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Returns the current batch delay configuration.
     * Useful for testing and monitoring.
     *
     * @return the batch delay in milliseconds
     */
    public long getBatchDelayMs() {
        return batchDelayMs;
    }

    /**
     * Sets the batch delay for processing (mainly for testing).
     *
     * @param batchDelayMs the new batch delay in milliseconds
     */
    public void setBatchDelayMs(long batchDelayMs) {
        this.batchDelayMs = batchDelayMs;
    }

    /**
     * Scheduled job that applies confidence decay to memories daily.
     *
     * <p>Runs at 3:00 AM UTC daily during the maintenance window (per AD-3 in design.md).
     * Processes fathers in batches to avoid lock contention with active operations.
     *
     * <p>Batch processing strategy (to avoid lock contention):
     * <ol>
     *   <li>Fetches all distinct father IDs with ACTIVE or CONFIRMED memories</li>
     *   <li>Splits father IDs into configurable batches (default: 50)</li>
     *   <li>Processes each father's memories in a separate READ_COMMITTED transaction</li>
     *   <li>Adds a configurable delay between batches (default: 100ms) to reduce pressure</li>
     *   <li>Logs batch progress for monitoring</li>
     * </ol>
     *
     * <p>Lock contention mitigation:
     * <ul>
     *   <li>Per-father transactions limit lock scope to one father's memories</li>
     *   <li>READ_COMMITTED isolation prevents holding locks during full batch</li>
     *   <li>Inter-batch delay reduces concurrent lock acquisition pressure</li>
     *   <li>Race condition protection skips recently modified memories</li>
     * </ul>
     */
    @Scheduled(cron = "${dadcoach.memory.decay.cron:0 0 3 * * *}") // Default: 3:00 AM UTC daily
    public void runDailyDecay() {
        log.info("MemoryDecayService: Starting daily decay job with batchSize={}, batchDelayMs={}",
                batchSize, batchDelayMs);
        Instant jobStartTime = Instant.now();

        try {
            // Get all distinct father IDs with decayable memories
            List<UUID> fatherIds = memoryRepository.findDistinctFatherIdsByStateIn(DECAYABLE_STATES);
            log.info("MemoryDecayService: Found {} fathers with decayable memories", fatherIds.size());

            int totalMemoriesProcessed = 0;
            int totalMemoriesDecayed = 0;
            int totalFathersProcessed = 0;
            int totalBatchesProcessed = 0;
            int errors = 0;

            // Calculate total number of batches for progress logging
            int totalBatches = (fatherIds.size() + batchSize - 1) / batchSize;

            // Process fathers in batches
            for (int i = 0; i < fatherIds.size(); i += batchSize) {
                int batchEnd = Math.min(i + batchSize, fatherIds.size());
                List<UUID> batch = fatherIds.subList(i, batchEnd);
                int batchNumber = (i / batchSize) + 1;

                log.debug("MemoryDecayService: Processing batch {}/{} ({} fathers)",
                        batchNumber, totalBatches, batch.size());

                for (UUID fatherId : batch) {
                    try {
                        DecayResult result = processFatherMemoriesDecay(fatherId, jobStartTime);
                        totalMemoriesProcessed += result.memoriesProcessed();
                        totalMemoriesDecayed += result.memoriesDecayed();
                        totalFathersProcessed++;
                    } catch (Exception e) {
                        log.error("MemoryDecayService: Error processing decay for father. fatherId={}, error={}",
                                fatherId, e.getMessage(), e);
                        errors++;
                    }
                }

                totalBatchesProcessed++;

                log.info("MemoryDecayService: Completed batch {}/{} - fathersInBatch={}, " +
                                "totalFathersProcessed={}, totalMemoriesDecayed={}",
                        batchNumber, totalBatches, batch.size(), totalFathersProcessed, totalMemoriesDecayed);

                // Add delay between batches to reduce database pressure (except for last batch)
                if (batchEnd < fatherIds.size() && batchDelayMs > 0) {
                    try {
                        Thread.sleep(batchDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("MemoryDecayService: Decay job interrupted during batch delay");
                        break;
                    }
                }
            }

            long durationMs = ChronoUnit.MILLIS.between(jobStartTime, Instant.now());
            log.info("MemoryDecayService: Daily decay job completed. " +
                            "batchesProcessed={}, fathersProcessed={}, memoriesProcessed={}, " +
                            "memoriesDecayed={}, errors={}, durationMs={}",
                    totalBatchesProcessed, totalFathersProcessed, totalMemoriesProcessed,
                    totalMemoriesDecayed, errors, durationMs);

        } catch (Exception e) {
            log.error("MemoryDecayService: Daily decay job failed. error={}", e.getMessage(), e);
        }
    }

    /**
     * Processes decay for all eligible memories belonging to a specific father.
     *
     * <p>This method is transactional with READ_COMMITTED isolation to minimize lock contention.
     * Lock contention is avoided by:
     * <ul>
     *   <li>Processing one father at a time (limited lock scope)</li>
     *   <li>Using READ_COMMITTED isolation (releases read locks immediately)</li>
     *   <li>Skipping memories modified since job start (avoids conflicts)</li>
     * </ul>
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Loads all ACTIVE/CONFIRMED memories for the father</li>
     *   <li>Filters out exempt memories (IDENTITY with confidence 1.0, FAMILY with high confidence)</li>
     *   <li>Checks each memory against its tier's decay threshold</li>
     *   <li>Applies the appropriate decay rate if threshold is exceeded</li>
     *   <li>Creates audit entries for decayed memories</li>
     * </ol>
     *
     * @param fatherId     the father's ID
     * @param jobStartTime the time the decay job started (for race condition protection)
     * @return the decay processing result
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DecayResult processFatherMemoriesDecay(UUID fatherId, Instant jobStartTime) {
        log.debug("MemoryDecayService: Processing decay for father. fatherId={}", fatherId);

        // Load all decayable memories for this father
        List<Memory> memories = memoryRepository.findByFatherIdAndStateIn(fatherId, DECAYABLE_STATES);

        int memoriesProcessed = 0;
        int memoriesDecayed = 0;
        int memoriesSkippedRaceCondition = 0;
        int memoriesSkippedExempt = 0;
        int memoriesSkippedBelowThreshold = 0;

        Instant now = Instant.now();

        for (Memory memory : memories) {
            memoriesProcessed++;

            // Skip if memory state changed since job started (race condition protection)
            if (memory.getLastUpdatedAt() != null && memory.getLastUpdatedAt().isAfter(jobStartTime)) {
                log.debug("MemoryDecayService: Skipping memory (state changed since job start). memoryId={}",
                        memory.getId());
                memoriesSkippedRaceCondition++;
                continue;
            }

            // Skip if exempt from decay
            if (isExemptFromDecay(memory)) {
                log.trace("MemoryDecayService: Skipping exempt memory. memoryId={}, category={}, confidence={}",
                        memory.getId(), memory.getCategory(), memory.getConfidenceScore());
                memoriesSkippedExempt++;
                continue;
            }

            // Skip if confidence already too low
            if (memory.getConfidenceScore().compareTo(MIN_CONFIDENCE_FOR_DECAY) < 0) {
                log.trace("MemoryDecayService: Skipping memory (confidence too low). memoryId={}, confidence={}",
                        memory.getId(), memory.getConfidenceScore());
                memoriesSkippedBelowThreshold++;
                continue;
            }

            // Calculate days since last access
            Instant lastAccessedAt = memory.getLastAccessedAt();
            if (lastAccessedAt == null) {
                // If never accessed, use creation time
                lastAccessedAt = memory.getCreatedAt();
            }
            long daysSinceLastAccess = ChronoUnit.DAYS.between(lastAccessedAt, now);

            // Get tier-based decay parameters
            MemoryTier tier = memory.getTier();
            int decayStartDays = tier.getDecayStartDays();

            // Check if memory is past decay threshold
            if (daysSinceLastAccess < decayStartDays) {
                log.trace("MemoryDecayService: Skipping memory (not past decay threshold). memoryId={}, " +
                                "daysSinceLastAccess={}, decayStartDays={}",
                        memory.getId(), daysSinceLastAccess, decayStartDays);
                continue;
            }

            // Calculate decay amount
            BigDecimal decayAmount = calculateDecayAmount(memory, daysSinceLastAccess, tier);

            if (decayAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Capture state before for audit
                String stateBefore = auditService.serializeMemoryState(memory);

                // Apply decay
                BigDecimal oldConfidence = memory.getConfidenceScore();
                BigDecimal newConfidence = oldConfidence.subtract(decayAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

                memory.setConfidenceScore(newConfidence);
                memory.setLastUpdatedAt(Instant.now());

                // Save the updated memory
                memoryRepository.save(memory);

                // Create audit entry for decay
                auditService.createAuditEntry(memory, EventType.UPDATE, ActorType.SYSTEM, stateBefore);

                memoriesDecayed++;

                log.debug("MemoryDecayService: Applied decay. memoryId={}, tier={}, " +
                                "daysSinceLastAccess={}, oldConfidence={}, newConfidence={}, decayAmount={}",
                        memory.getId(), tier, daysSinceLastAccess, oldConfidence, newConfidence, decayAmount);
            }
        }

        log.debug("MemoryDecayService: Completed decay processing for father. fatherId={}, " +
                        "memoriesProcessed={}, memoriesDecayed={}, skippedRaceCondition={}, " +
                        "skippedExempt={}, skippedBelowThreshold={}",
                fatherId, memoriesProcessed, memoriesDecayed, memoriesSkippedRaceCondition,
                memoriesSkippedExempt, memoriesSkippedBelowThreshold);

        return new DecayResult(memoriesProcessed, memoriesDecayed);
    }

    /**
     * Checks if a memory is exempt from confidence decay.
     *
     * <p>From SPEC-004 Requirement 6 Criteria 7:
     * <ul>
     *   <li>IDENTITY memories with confidence 1.0 (hard facts like names and schools)</li>
     *   <li>FAMILY structure memories with confidence >= 0.9</li>
     *   <li>Active GOAL memories linked to a non-completed goal entity</li>
     * </ul>
     *
     * @param memory the memory to check
     * @return true if the memory is exempt from decay
     */
    private boolean isExemptFromDecay(Memory memory) {
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
     * Calculates the confidence decay amount for a memory.
     *
     * <p>The decay is calculated based on:
     * <ul>
     *   <li>The memory's tier (determines base decay rate)</li>
     *   <li>Days since last access beyond the decay threshold</li>
     *   <li>Whether the memory has been confirmed 3+ times (halves decay rate)</li>
     * </ul>
     *
     * @param memory              the memory to calculate decay for
     * @param daysSinceLastAccess total days since the memory was last accessed
     * @param tier                the memory's tier
     * @return the confidence decay amount (non-negative)
     */
    private BigDecimal calculateDecayAmount(Memory memory, long daysSinceLastAccess, MemoryTier tier) {
        // Calculate days beyond the decay threshold
        int decayStartDays = tier.getDecayStartDays();
        long daysBeyondThreshold = daysSinceLastAccess - decayStartDays;

        if (daysBeyondThreshold <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate number of 30-day periods beyond threshold
        // We apply decay once per 30-day period
        long decayPeriods = daysBeyondThreshold / DECAY_PERIOD_DAYS;

        if (decayPeriods <= 0) {
            return BigDecimal.ZERO;
        }

        // For daily runs, we only apply decay for the most recent period
        // This prevents retroactive over-decay on first run
        decayPeriods = Math.min(decayPeriods, 1);

        // Get base decay rate for this tier
        double baseDecayRate = tier.getDecayRatePer30Days();

        // Check if decay rate should be halved (high reliability memories)
        if (memory.getConfirmationCount() >= HIGH_RELIABILITY_CONFIRMATION_COUNT) {
            baseDecayRate = baseDecayRate / 2.0;
        }

        // Calculate total decay
        return BigDecimal.valueOf(baseDecayRate * decayPeriods)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Result record for decay processing.
     *
     * @param memoriesProcessed total memories evaluated
     * @param memoriesDecayed   memories that had decay applied
     */
    public record DecayResult(int memoriesProcessed, int memoriesDecayed) {
    }

    // ─── Manual Execution (for testing/admin) ────────────────────────────────────

    /**
     * Manually triggers decay processing for a specific father.
     *
     * <p>This method can be called from admin endpoints or tests to trigger
     * decay processing outside the scheduled job.
     *
     * @param fatherId the father's ID
     * @return the decay processing result
     */
    @Transactional
    public DecayResult triggerDecayForFather(UUID fatherId) {
        log.info("MemoryDecayService: Manually triggering decay for father. fatherId={}", fatherId);
        return processFatherMemoriesDecay(fatherId, Instant.now());
    }

    /**
     * Manually triggers the full decay job.
     *
     * <p>This method can be called from admin endpoints to trigger
     * the full decay job outside the scheduled run.
     */
    public void triggerFullDecay() {
        log.info("MemoryDecayService: Manually triggering full decay job");
        runDailyDecay();
    }
}
