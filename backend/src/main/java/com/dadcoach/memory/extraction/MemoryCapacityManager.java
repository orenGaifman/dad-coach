package com.dadcoach.memory.extraction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages memory capacity limits per father.
 *
 * <p>From SPEC-004 Requirement 15 (Memory Capacity) and REQ-6 (Storage Efficiency):
 * <ul>
 *   <li>Maximum 500 active memories per father</li>
 *   <li>Only ACTIVE and CONFIRMED states count toward the limit</li>
 *   <li>When at capacity, archive the memory with lowest composite score (importance × confidence)</li>
 *   <li>ARCHIVED memories are retained but excluded from retrieval and capacity count</li>
 * </ul>
 *
 * <p>From Design (Error Handling section):
 * "500-memory capacity reached → Archive lowest-scoring memory; if all protected → reject + alert operations"
 *
 * <p><strong>Validates: Requirements REQ-6, Req 15, Task 4.6</strong>
 *
 * @see Memory#MAX_ACTIVE_MEMORIES_PER_FATHER
 * @see Memory#getCombinedScore()
 */
@Service
public class MemoryCapacityManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryCapacityManager.class);

    /**
     * States that count toward the 500-memory capacity limit.
     * Per SPEC-004: Only ACTIVE and CONFIRMED memories count.
     */
    public static final Set<MemoryState> CAPACITY_COUNTING_STATES = EnumSet.of(
            MemoryState.ACTIVE,
            MemoryState.CONFIRMED
    );

    private final MemoryRepository memoryRepository;

    /**
     * Constructs a MemoryCapacityManager.
     *
     * @param memoryRepository the repository for memory persistence operations
     */
    public MemoryCapacityManager(
            @Qualifier("specMemoryRepository") @Nullable MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * Counts active memories for a father (ACTIVE and CONFIRMED states only).
     *
     * @param fatherId the father's ID
     * @return count of active memories
     */
    public long countActiveMemories(UUID fatherId) {
        if (memoryRepository == null) {
            log.debug("MemoryRepository not available, returning 0 count. fatherId={}", fatherId);
            return 0;
        }
        
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }

        return memoryRepository.countByFatherIdAndStateIn(fatherId, CAPACITY_COUNTING_STATES);
    }

    /**
     * Checks if a father has reached the 500-memory capacity limit.
     *
     * @param fatherId the father's ID
     * @return true if at or above capacity
     */
    public boolean isAtCapacity(UUID fatherId) {
        long count = countActiveMemories(fatherId);
        return count >= Memory.MAX_ACTIVE_MEMORIES_PER_FATHER;
    }

    /**
     * Returns the current capacity status for a father.
     *
     * @param fatherId the father's ID
     * @return capacity status with current count and limit
     */
    public CapacityStatus getCapacityStatus(UUID fatherId) {
        long count = countActiveMemories(fatherId);
        int limit = Memory.MAX_ACTIVE_MEMORIES_PER_FATHER;
        return new CapacityStatus(count, limit, count >= limit);
    }

    /**
     * Finds the memory with the lowest composite score for a father.
     * The composite score is importance_score × confidence_score.
     *
     * @param fatherId the father's ID
     * @return the lowest-scoring memory, or empty if none found
     */
    public Optional<Memory> findLowestScoringMemory(UUID fatherId) {
        if (memoryRepository == null) {
            log.debug("MemoryRepository not available, cannot find lowest scoring memory. fatherId={}", fatherId);
            return Optional.empty();
        }

        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }

        List<Memory> memories = memoryRepository.findByFatherIdAndStateInOrderByCombinedScoreAsc(
                fatherId, CAPACITY_COUNTING_STATES);

        if (memories.isEmpty()) {
            log.debug("No active memories found for father. fatherId={}", fatherId);
            return Optional.empty();
        }

        // First memory in the list has the lowest combined score
        return Optional.of(memories.get(0));
    }

    /**
     * Ensures capacity is available for a new memory by archiving the lowest-scoring 
     * memory if at capacity.
     *
     * <p>This method should be called before creating a new memory. If the father is at
     * the 500-memory capacity limit, it will archive the memory with the lowest composite
     * score (importance × confidence) to make room for the new one.
     *
     * <p>From SPEC-004 Design (Error Handling):
     * "500-memory capacity reached → Archive lowest-scoring memory"
     *
     * @param fatherId the father's ID
     * @return result indicating whether capacity was ensured and what action was taken
     */
    @Transactional
    public EnsureCapacityResult ensureCapacity(UUID fatherId) {
        if (memoryRepository == null) {
            log.debug("MemoryRepository not available, allowing memory creation. fatherId={}", fatherId);
            return EnsureCapacityResult.capacityAvailable();
        }

        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }

        long currentCount = countActiveMemories(fatherId);
        int limit = Memory.MAX_ACTIVE_MEMORIES_PER_FATHER;

        if (currentCount < limit) {
            log.debug("Capacity available. fatherId={}, count={}, limit={}", 
                    fatherId, currentCount, limit);
            return EnsureCapacityResult.capacityAvailable();
        }

        // At capacity - need to archive the lowest-scoring memory
        log.info("At capacity, archiving lowest-scoring memory. fatherId={}, count={}", 
                fatherId, currentCount);

        Optional<Memory> lowestScoring = findLowestScoringMemory(fatherId);
        
        if (lowestScoring.isEmpty()) {
            // This shouldn't happen if count > 0, but handle gracefully
            log.error("At capacity but no archivable memory found. fatherId={}, count={}", 
                    fatherId, currentCount);
            return EnsureCapacityResult.noArchivableMemory();
        }

        Memory memoryToArchive = lowestScoring.get();
        
        try {
            memoryToArchive.archive();
            memoryRepository.save(memoryToArchive);
            
            log.info("Archived lowest-scoring memory to make capacity. fatherId={}, archivedMemoryId={}, " +
                    "combinedScore={}", fatherId, memoryToArchive.getId(), memoryToArchive.getCombinedScore());
            
            return EnsureCapacityResult.memoryArchived(memoryToArchive.getId());
        } catch (IllegalStateException e) {
            // Memory cannot be archived (e.g., already in a state that can't transition to ARCHIVED)
            log.error("Failed to archive memory - invalid state transition. fatherId={}, memoryId={}, " +
                    "currentState={}, error={}", fatherId, memoryToArchive.getId(), 
                    memoryToArchive.getState(), e.getMessage());
            return EnsureCapacityResult.archiveFailed(memoryToArchive.getId(), e.getMessage());
        }
    }

    /**
     * Record representing the current capacity status for a father.
     *
     * @param currentCount current number of active memories
     * @param limit        maximum allowed (500)
     * @param atCapacity   true if currentCount >= limit
     */
    public record CapacityStatus(long currentCount, int limit, boolean atCapacity) {
        
        /**
         * Returns the number of memories that can still be created before reaching capacity.
         */
        public long remainingCapacity() {
            return Math.max(0, limit - currentCount);
        }
    }

    /**
     * Result of the {@link #ensureCapacity(UUID)} operation.
     */
    public sealed interface EnsureCapacityResult {
        
        /**
         * Capacity was already available, no action needed.
         */
        record CapacityAvailable() implements EnsureCapacityResult {}
        
        /**
         * A memory was archived to make room.
         * 
         * @param archivedMemoryId ID of the archived memory
         */
        record MemoryArchived(UUID archivedMemoryId) implements EnsureCapacityResult {}
        
        /**
         * At capacity but no memory could be found to archive (shouldn't happen normally).
         */
        record NoArchivableMemory() implements EnsureCapacityResult {}
        
        /**
         * Failed to archive a memory (e.g., state transition error).
         *
         * @param memoryId     ID of the memory that couldn't be archived
         * @param errorMessage description of the failure
         */
        record ArchiveFailed(UUID memoryId, String errorMessage) implements EnsureCapacityResult {}

        static CapacityAvailable capacityAvailable() {
            return new CapacityAvailable();
        }

        static MemoryArchived memoryArchived(UUID archivedMemoryId) {
            return new MemoryArchived(archivedMemoryId);
        }

        static NoArchivableMemory noArchivableMemory() {
            return new NoArchivableMemory();
        }

        static ArchiveFailed archiveFailed(UUID memoryId, String errorMessage) {
            return new ArchiveFailed(memoryId, errorMessage);
        }

        /**
         * Returns true if capacity was successfully ensured (either already available or memory was archived).
         */
        default boolean isSuccess() {
            return this instanceof CapacityAvailable || this instanceof MemoryArchived;
        }
    }
}
