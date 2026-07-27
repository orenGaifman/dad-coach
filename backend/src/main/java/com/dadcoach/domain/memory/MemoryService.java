package com.dadcoach.domain.memory;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.memory.MemoryCategory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Service layer for Memory entity lifecycle management.
 *
 * <p>Handles memory creation (with tier-based expiration), supersede operations,
 * confidence decay on contradiction, access tracking, expiration, and 500-memory
 * capacity enforcement.</p>
 *
 * <p>Business rules implemented:
 * <ul>
 *   <li>Req 7.2: Tier classification — importance 1-3 → 90d, 4-6 → 180d, 7-10 → never</li>
 *   <li>Req 7.7: Supersede — old memory gets superseded_by=new_id, new memory gets confidence 1.0</li>
 *   <li>Req 7.9: Confidence decay on contradiction — reduce by 0.3, min 0.0</li>
 *   <li>Req 7.10: Access tracking — increment access_count and update last_accessed_at</li>
 *   <li>Req 7.11: Max 500 active memories per Father; archive lowest importance×confidence</li>
 * </ul>
 */
@Service
@Transactional
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;

    public MemoryService(MemoryRepository memoryRepository,
                         FatherRepository fatherRepository,
                         ChildRepository childRepository) {
        this.memoryRepository = memoryRepository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
    }

    // ─── Creation ────────────────────────────────────────────────────────

    /**
     * Creates a new memory with tier-based expiration and capacity enforcement.
     *
     * <p>After creating the memory, enforces the 500-memory capacity limit by archiving
     * memories with the lowest combined score (importance × confidence) if exceeded.</p>
     *
     * @param fatherId        the father ID
     * @param childId         the child ID (nullable)
     * @param category        the memory category
     * @param content         the memory content
     * @param importanceScore importance score (1-10)
     * @param confidenceScore confidence score (0.0-1.0)
     * @return the persisted Memory entity
     * @throws ResourceNotFoundException      if the father or child is not found
     * @throws BusinessRuleViolationException if importance or confidence scores are out of range
     */
    public Memory createMemory(Long fatherId, Long childId, MemoryCategory category,
                               String content, int importanceScore, BigDecimal confidenceScore) {
        validateScores(importanceScore, confidenceScore);

        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        Memory memory = new Memory(father, category, content, importanceScore, confidenceScore);

        // Link to child if provided
        if (childId != null) {
            Child child = childRepository.findById(childId)
                    .orElseThrow(() -> new ResourceNotFoundException("Child", childId));
            memory.setChild(child);
        }

        Memory saved = memoryRepository.save(memory);

        // Enforce capacity limit after creation
        enforceCapacityLimit(fatherId);

        return saved;
    }

    // ─── Supersede ───────────────────────────────────────────────────────

    /**
     * Supersedes an existing memory with corrected information (Requirement 7.7).
     *
     * <p>The old memory is marked as SUPERSEDED with a reference to the new memory.
     * The new memory is created with confidence_score 1.0.</p>
     *
     * @param existingMemoryId the ID of the memory being superseded
     * @param newContent       the corrected content
     * @return the new memory that supersedes the old one
     * @throws ResourceNotFoundException if the existing memory is not found
     */
    public Memory supersedeMemory(Long existingMemoryId, String newContent) {
        Memory existing = findMemoryOrThrow(existingMemoryId);

        if (!existing.isActive()) {
            throw new BusinessRuleViolationException("MEMORY_NOT_ACTIVE",
                    "Cannot supersede a memory that is not active");
        }

        // Create new memory with confidence 1.0 (directly stated correction)
        Memory newMemory = new Memory(
                existing.getFather(),
                existing.getCategory(),
                newContent,
                existing.getImportanceScore(),
                BigDecimal.ONE
        );
        newMemory.setChild(existing.getChild());

        Memory savedNew = memoryRepository.save(newMemory);

        // Mark old memory as superseded
        existing.markSuperseded(savedNew.getId());
        memoryRepository.save(existing);

        // Enforce capacity limit after creation
        enforceCapacityLimit(existing.getFatherId());

        return savedNew;
    }

    // ─── Confidence Decay ────────────────────────────────────────────────

    /**
     * Applies confidence decay to a memory due to contradiction detection (Requirement 7.9).
     * Reduces confidence_score by 0.3, with a minimum of 0.0.
     *
     * @param memoryId the ID of the memory to decay
     * @return the updated memory
     * @throws ResourceNotFoundException if the memory is not found
     */
    public Memory applyConfidenceDecay(Long memoryId) {
        Memory memory = findMemoryOrThrow(memoryId);

        if (!memory.isActive()) {
            throw new BusinessRuleViolationException("MEMORY_NOT_ACTIVE",
                    "Cannot apply confidence decay to a memory that is not active");
        }

        memory.applyConfidenceDecay();
        return memoryRepository.save(memory);
    }

    // ─── Access Tracking ─────────────────────────────────────────────────

    /**
     * Records an access to a memory (Requirement 7.10).
     * Increments access_count and updates last_accessed_at.
     *
     * @param memoryId the ID of the memory accessed
     * @return the updated memory
     * @throws ResourceNotFoundException if the memory is not found
     */
    public Memory recordAccess(Long memoryId) {
        Memory memory = findMemoryOrThrow(memoryId);
        memory.recordAccess();
        return memoryRepository.save(memory);
    }

    /**
     * Records access for multiple memories at once (batch access tracking).
     *
     * @param memoryIds the IDs of the memories accessed
     */
    public void recordAccessBatch(List<Long> memoryIds) {
        for (Long memoryId : memoryIds) {
            memoryRepository.findById(memoryId).ifPresent(memory -> {
                memory.recordAccess();
                memoryRepository.save(memory);
            });
        }
    }

    // ─── Expiration ──────────────────────────────────────────────────────

    /**
     * Expires a specific memory.
     *
     * @param memoryId the ID of the memory to expire
     * @return the updated memory
     * @throws ResourceNotFoundException if the memory is not found
     */
    public Memory expireMemory(Long memoryId) {
        Memory memory = findMemoryOrThrow(memoryId);
        memory.expire();
        return memoryRepository.save(memory);
    }

    /**
     * Expires all active memories that have passed their expiration time.
     * Called by the scheduled expiration job.
     *
     * @return the number of memories expired
     */
    public int expireOverdueMemories() {
        List<Memory> expired = memoryRepository.findExpiredMemories(Instant.now());
        for (Memory memory : expired) {
            memory.expire();
            memoryRepository.save(memory);
        }
        return expired.size();
    }

    /**
     * Expires memories with low confidence that haven't been accessed recently (Requirement 7.3).
     * Criteria: confidence_score < 0.5 AND not accessed in 60 days.
     *
     * @param fatherId the father ID
     * @return the number of memories expired
     */
    public int expireLowConfidenceMemories(Long fatherId) {
        BigDecimal confidenceThreshold = new BigDecimal("0.50");
        Instant accessThreshold = Instant.now().minus(60, ChronoUnit.DAYS);

        List<Memory> toExpire = memoryRepository.findLowConfidenceUnaccessed(
                fatherId, confidenceThreshold, accessThreshold);

        for (Memory memory : toExpire) {
            memory.expire();
            memoryRepository.save(memory);
        }
        return toExpire.size();
    }

    // ─── Archive ─────────────────────────────────────────────────────────

    /**
     * Archives a specific memory.
     *
     * @param memoryId the ID of the memory to archive
     * @return the updated memory
     * @throws ResourceNotFoundException if the memory is not found
     */
    public Memory archiveMemory(Long memoryId) {
        Memory memory = findMemoryOrThrow(memoryId);
        memory.archive();
        return memoryRepository.save(memory);
    }

    // ─── Capacity Enforcement ────────────────────────────────────────────

    /**
     * Enforces the 500-memory capacity limit per father (Requirement 7.11).
     * When exceeded, archives memories with the lowest combined score (importance × confidence).
     *
     * @param fatherId the father ID to enforce capacity for
     * @return the number of memories archived due to capacity enforcement
     */
    public int enforceCapacityLimit(Long fatherId) {
        long activeCount = memoryRepository.countActiveByFatherId(fatherId);
        if (activeCount <= Memory.MAX_ACTIVE_MEMORIES_PER_FATHER) {
            return 0;
        }

        long excess = activeCount - Memory.MAX_ACTIVE_MEMORIES_PER_FATHER;

        // Get all active memories sorted by combined score ascending (lowest first)
        List<Memory> candidates = memoryRepository.findActiveByFatherIdOrderByCombinedScoreAsc(fatherId);

        int archived = 0;
        for (Memory memory : candidates) {
            if (archived >= excess) {
                break;
            }
            memory.archive();
            memoryRepository.save(memory);
            archived++;
        }

        return archived;
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Gets a memory by ID.
     *
     * @param memoryId the memory ID
     * @return the Memory entity
     * @throws ResourceNotFoundException if the memory is not found
     */
    @Transactional(readOnly = true)
    public Memory getMemory(Long memoryId) {
        return findMemoryOrThrow(memoryId);
    }

    /**
     * Gets all active memories for a father.
     *
     * @param fatherId the father ID
     * @return list of active memories
     */
    @Transactional(readOnly = true)
    public List<Memory> getActiveMemories(Long fatherId) {
        return memoryRepository.findActiveByFatherId(fatherId);
    }

    /**
     * Gets the count of active memories for a father.
     *
     * @param fatherId the father ID
     * @return the active memory count
     */
    @Transactional(readOnly = true)
    public long getActiveMemoryCount(Long fatherId) {
        return memoryRepository.countActiveByFatherId(fatherId);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private Memory findMemoryOrThrow(Long memoryId) {
        return memoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", memoryId));
    }

    private void validateScores(int importanceScore, BigDecimal confidenceScore) {
        if (importanceScore < 1 || importanceScore > 10) {
            throw new BusinessRuleViolationException("INVALID_IMPORTANCE_SCORE",
                    "Importance score must be between 1 and 10, got: " + importanceScore);
        }
        if (confidenceScore.compareTo(BigDecimal.ZERO) < 0
                || confidenceScore.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessRuleViolationException("INVALID_CONFIDENCE_SCORE",
                    "Confidence score must be between 0.0 and 1.0, got: " + confidenceScore);
        }
    }
}
