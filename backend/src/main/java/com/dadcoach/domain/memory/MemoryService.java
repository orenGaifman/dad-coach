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
import java.util.List;

/**
 * Service layer for Memory entity lifecycle management.
 *
 * <p>Handles memory creation with tier-based expiration and 500-memory capacity enforcement.</p>
 *
 * <p>Business rules implemented:
 * <ul>
 *   <li>Req 7.2: Tier classification — importance 1-3 → 90d, 4-6 → 180d, 7-10 → never</li>
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

    /**
     * Enforces the 500-memory capacity limit per father (Requirement 7.11).
     * When exceeded, archives memories with the lowest combined score (importance × confidence).
     *
     * @param fatherId the father ID to enforce capacity for
     * @return the number of memories archived due to capacity enforcement
     */
    private int enforceCapacityLimit(Long fatherId) {
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
