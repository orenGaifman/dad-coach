package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Service for grouping conflicting memories under a shared conflict_group_id.
 *
 * <p>From SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution):
 * <ul>
 *   <li>Criteria 4: WHEN two conflicting memories are both ACTIVE with confidence >= 0.5,
 *       THE Memory_System SHALL mark them with a conflict_group_id linking them together</li>
 *   <li>Criteria 5: THE Memory_System SHALL resolve conflict_groups during the weekly
 *       consolidation job</li>
 *   <li>Criteria 8: THE Memory_System SHALL track conflict resolution history</li>
 * </ul>
 *
 * <p>This service handles:
 * <ul>
 *   <li>Creating new conflict groups when contradictions are detected</li>
 *   <li>Adding memories to existing conflict groups</li>
 *   <li>Retrieving conflict groups for a father</li>
 *   <li>Resolving conflict groups based on access patterns and confidence</li>
 * </ul>
 *
 * @see ContradictionDetectionService
 * @see Contradiction
 */
@Service
public class ConflictGroupService {

    private static final Logger log = LoggerFactory.getLogger(ConflictGroupService.class);

    /**
     * Minimum confidence score for memories to be eligible for conflict grouping (Req 7 criteria 4).
     */
    public static final BigDecimal MIN_CONFIDENCE_FOR_CONFLICT_GROUP = new BigDecimal("0.50");

    /**
     * Days threshold for "recently accessed" in conflict resolution (Req 7 criteria 5).
     */
    public static final int RECENT_ACCESS_DAYS = 14;

    /**
     * Days threshold for "not accessed" in conflict resolution (Req 7 criteria 5).
     */
    public static final int UNACCESSED_DAYS = 30;

    /**
     * Confidence difference threshold below which both memories should be kept and flagged for user confirmation.
     * When the absolute difference in confidence between two conflicting memories is less than this value,
     * both are retained and flagged for the user to decide which is correct.
     */
    public static final BigDecimal SIMILAR_CONFIDENCE_THRESHOLD = new BigDecimal("0.15");

    private final MemoryRepository memoryRepository;
    private final ContradictionDetectionService contradictionDetectionService;

    public ConflictGroupService(
            MemoryRepository memoryRepository,
            ContradictionDetectionService contradictionDetectionService) {
        this.memoryRepository = memoryRepository;
        this.contradictionDetectionService = contradictionDetectionService;
    }

    // ─── Conflict Group Creation ─────────────────────────────────────────

    /**
     * Groups conflicting memories under a shared conflict_group_id.
     *
     * <p>Per SPEC-004 Req 7 criteria 4: When two conflicting memories are both ACTIVE
     * with confidence >= 0.5, mark them with a conflict_group_id.
     *
     * <p>This method:
     * <ol>
     *   <li>Validates both memories have sufficient confidence (>= 0.5)</li>
     *   <li>Validates both memories are in ACTIVE or CONFIRMED state</li>
     *   <li>Creates a new conflict group if neither memory has one</li>
     *   <li>Adds to existing group if one memory already has a conflict_group_id</li>
     *   <li>Merges groups if both memories have different conflict_group_ids</li>
     * </ol>
     *
     * @param contradiction the detected contradiction to group
     * @return the conflict_group_id assigned, or null if grouping not applicable
     */
    @Transactional
    public UUID groupConflictingMemories(Contradiction contradiction) {
        if (contradiction == null) {
            throw new IllegalArgumentException("contradiction cannot be null");
        }

        Memory existingMemory = contradiction.existingMemory();
        Memory newMemory = contradiction.newMemory();

        // Validate memories are eligible for conflict grouping
        if (!isEligibleForConflictGroup(existingMemory)) {
            log.debug("Existing memory {} not eligible for conflict group (confidence={}, state={})",
                    existingMemory.getId(), existingMemory.getConfidenceScore(), existingMemory.getState());
            return null;
        }

        if (!isEligibleForConflictGroup(newMemory)) {
            log.debug("New memory {} not eligible for conflict group (confidence={}, state={})",
                    newMemory.getId(), newMemory.getConfidenceScore(), newMemory.getState());
            return null;
        }

        UUID conflictGroupId = determineConflictGroupId(existingMemory, newMemory);

        // Assign the conflict group ID to both memories
        assignConflictGroupId(existingMemory, conflictGroupId);
        assignConflictGroupId(newMemory, conflictGroupId);

        log.info("Grouped memories {} and {} under conflict_group_id {}",
                existingMemory.getId(), newMemory.getId(), conflictGroupId);

        return conflictGroupId;
    }

    /**
     * Processes multiple contradictions and groups them appropriately.
     *
     * @param contradictions list of detected contradictions
     * @return map of conflict_group_id to list of memory IDs in that group
     */
    @Transactional
    public Map<UUID, List<UUID>> groupConflictingMemories(List<Contradiction> contradictions) {
        if (contradictions == null || contradictions.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, List<UUID>> result = new HashMap<>();

        for (Contradiction contradiction : contradictions) {
            UUID groupId = groupConflictingMemories(contradiction);
            if (groupId != null) {
                result.computeIfAbsent(groupId, k -> new ArrayList<>());
                
                UUID existingId = contradiction.existingMemoryId();
                UUID newId = contradiction.newMemoryId();
                
                if (!result.get(groupId).contains(existingId)) {
                    result.get(groupId).add(existingId);
                }
                if (!result.get(groupId).contains(newId)) {
                    result.get(groupId).add(newId);
                }
            }
        }

        return result;
    }

    // ─── Conflict Group Retrieval ────────────────────────────────────────

    /**
     * Retrieves all memories in a specific conflict group.
     *
     * @param conflictGroupId the conflict group ID
     * @return list of memories in the group
     */
    public List<Memory> getConflictGroup(UUID conflictGroupId) {
        if (conflictGroupId == null) {
            return Collections.emptyList();
        }
        return memoryRepository.findByConflictGroupId(conflictGroupId);
    }

    /**
     * Retrieves all conflict groups for a father.
     *
     * @param fatherId the father's ID
     * @return map of conflict_group_id to list of memories in that group
     */
    public Map<UUID, List<Memory>> getConflictGroupsForFather(UUID fatherId) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }

        Collection<MemoryState> activeStates = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);
        List<Memory> conflictingMemories = memoryRepository.findConflictingMemories(fatherId, activeStates);

        Map<UUID, List<Memory>> groups = new HashMap<>();
        for (Memory memory : conflictingMemories) {
            UUID groupId = memory.getConflictGroupId();
            if (groupId != null) {
                groups.computeIfAbsent(groupId, k -> new ArrayList<>()).add(memory);
            }
        }

        return groups;
    }

    /**
     * Gets the count of unresolved conflict groups for a father.
     *
     * @param fatherId the father's ID
     * @return number of conflict groups
     */
    public int getConflictGroupCount(UUID fatherId) {
        return getConflictGroupsForFather(fatherId).size();
    }

    // ─── Conflict Resolution ─────────────────────────────────────────────

    /**
     * Determines the resolution action for a conflict group.
     *
     * <p>Per SPEC-004 Req 7 criteria 5:
     * <ul>
     *   <li>If one memory has been accessed more recently (within 14 days) and the other has not:
     *       supersede the unaccessed memory</li>
     *   <li>If both have been accessed recently: retain both until father provides clarification</li>
     *   <li>If neither has been accessed in 30+ days: expire the lower-confidence memory</li>
     * </ul>
     *
     * @param conflictGroupId the conflict group to analyze
     * @return the recommended resolution action
     */
    public ConflictResolution analyzeConflictGroup(UUID conflictGroupId) {
        List<Memory> memories = getConflictGroup(conflictGroupId);
        
        if (memories.isEmpty()) {
            return ConflictResolution.noAction(conflictGroupId, "No memories in conflict group");
        }

        if (memories.size() == 1) {
            // Only one memory left - conflict is already resolved
            return ConflictResolution.clearConflictGroup(conflictGroupId, memories.get(0),
                    "Only one memory remains in conflict group");
        }

        Instant now = Instant.now();
        Instant recentThreshold = now.minusSeconds(RECENT_ACCESS_DAYS * 24L * 60 * 60);
        Instant unaccessedThreshold = now.minusSeconds(UNACCESSED_DAYS * 24L * 60 * 60);

        // Categorize memories by access pattern
        List<Memory> recentlyAccessed = new ArrayList<>();
        List<Memory> notRecentlyAccessed = new ArrayList<>();
        List<Memory> longUnaccessed = new ArrayList<>();

        for (Memory memory : memories) {
            Instant lastAccessed = memory.getLastAccessedAt();
            if (lastAccessed != null && lastAccessed.isAfter(recentThreshold)) {
                recentlyAccessed.add(memory);
            } else if (lastAccessed == null || lastAccessed.isBefore(unaccessedThreshold)) {
                longUnaccessed.add(memory);
            } else {
                notRecentlyAccessed.add(memory);
            }
        }

        // Case 1: One recently accessed, others not
        if (recentlyAccessed.size() == 1 && !notRecentlyAccessed.isEmpty()) {
            // Supersede the unaccessed memories
            return ConflictResolution.supersedeMemories(conflictGroupId, recentlyAccessed.get(0),
                    notRecentlyAccessed,
                    "One memory recently accessed, superseding unaccessed memories");
        }

        // Case 2: Neither accessed in 30+ days - expire lower confidence
        if (recentlyAccessed.isEmpty() && notRecentlyAccessed.isEmpty() && longUnaccessed.size() >= 2) {
            Memory lowestConfidence = longUnaccessed.stream()
                    .min(Comparator.comparing(Memory::getConfidenceScore))
                    .orElse(null);
            
            if (lowestConfidence != null) {
                List<Memory> toExpire = new ArrayList<>();
                toExpire.add(lowestConfidence);
                return ConflictResolution.expireMemories(conflictGroupId, toExpire,
                        "Neither memory accessed in 30+ days, expiring lowest confidence");
            }
        }

        // Case 3: Both recently accessed or mixed - retain both, wait for clarification
        return ConflictResolution.waitForClarification(conflictGroupId, memories,
                "Both memories recently accessed, waiting for father clarification");
    }

    /**
     * Resolves a contradiction by checking if newer memory with higher confidence should win.
     *
     * <p>Per SPEC-004 Req 7 Task 8 criteria 3:
     * When a newer memory has higher confidence than an older conflicting memory,
     * the newer one wins and the older one is marked as SUPERSEDED.
     *
     * <p>Resolution rules:
     * <ul>
     *   <li>If the newer memory has strictly higher confidence: newer wins, older is superseded</li>
     *   <li>If confidence is equal or older has higher confidence: no automatic resolution</li>
     * </ul>
     *
     * @param contradiction the detected contradiction to resolve
     * @return the resolution result, or null if no automatic resolution applies
     */
    public ConflictResolution resolveByNewerHigherConfidence(Contradiction contradiction) {
        if (contradiction == null) {
            throw new IllegalArgumentException("contradiction cannot be null");
        }

        Memory existingMemory = contradiction.existingMemory();
        Memory newMemory = contradiction.newMemory();

        // Determine which is older and which is newer based on createdAt timestamp
        Memory olderMemory;
        Memory newerMemory;

        if (newMemory.getCreatedAt().isAfter(existingMemory.getCreatedAt())) {
            olderMemory = existingMemory;
            newerMemory = newMemory;
        } else if (existingMemory.getCreatedAt().isAfter(newMemory.getCreatedAt())) {
            olderMemory = newMemory;
            newerMemory = existingMemory;
        } else {
            // Same creation time - cannot determine newer/older
            log.debug("Memories {} and {} have same creation time, cannot resolve by newer-higher-confidence",
                    existingMemory.getId(), newMemory.getId());
            return null;
        }

        // Check if newer memory has strictly higher confidence
        int confidenceComparison = newerMemory.getConfidenceScore().compareTo(olderMemory.getConfidenceScore());

        if (confidenceComparison > 0) {
            // Newer memory has higher confidence - it wins
            log.info("Resolving conflict: newer memory {} (confidence={}) supersedes older memory {} (confidence={})",
                    newerMemory.getId(), newerMemory.getConfidenceScore(),
                    olderMemory.getId(), olderMemory.getConfidenceScore());

            UUID conflictGroupId = olderMemory.getConflictGroupId() != null 
                    ? olderMemory.getConflictGroupId() 
                    : newerMemory.getConflictGroupId();
            if (conflictGroupId == null) {
                conflictGroupId = UUID.randomUUID();
            }

            return ConflictResolution.supersedeMemories(
                    conflictGroupId,
                    newerMemory,
                    List.of(olderMemory),
                    String.format("Newer memory (created=%s, confidence=%.2f) supersedes older memory (created=%s, confidence=%.2f)",
                            newerMemory.getCreatedAt(), newerMemory.getConfidenceScore(),
                            olderMemory.getCreatedAt(), olderMemory.getConfidenceScore())
            );
        }

        // Newer memory does not have higher confidence - no automatic resolution
        log.debug("Newer memory {} does not have higher confidence than older memory {}, no automatic resolution",
                newerMemory.getId(), olderMemory.getId());
        return null;
    }

    /**
     * Resolves a contradiction by keeping both memories flagged for user confirmation when confidence is similar.
     *
     * <p>Per SPEC-004 Req 7 Task 8 criteria 4:
     * When two conflicting memories have similar confidence scores (difference < 0.15),
     * both should be kept and flagged for user confirmation.
     *
     * <p>Resolution rules:
     * <ul>
     *   <li>If the confidence difference is less than {@link #SIMILAR_CONFIDENCE_THRESHOLD}:
     *       both memories are kept, grouped under a conflict_group_id, and flagged for user confirmation</li>
     *   <li>If confidence differs by more than the threshold: no action (other resolution rules apply)</li>
     * </ul>
     *
     * @param contradiction the detected contradiction to analyze
     * @return the resolution with WAIT_FOR_CLARIFICATION action if confidence is similar, null otherwise
     */
    public ConflictResolution resolveBySimilarConfidence(Contradiction contradiction) {
        if (contradiction == null) {
            throw new IllegalArgumentException("contradiction cannot be null");
        }

        Memory existingMemory = contradiction.existingMemory();
        Memory newMemory = contradiction.newMemory();

        // Calculate the absolute difference in confidence scores
        BigDecimal confidenceDifference = existingMemory.getConfidenceScore()
                .subtract(newMemory.getConfidenceScore())
                .abs();

        // Check if confidence is similar (difference < threshold)
        if (confidenceDifference.compareTo(SIMILAR_CONFIDENCE_THRESHOLD) < 0) {
            log.info("Similar confidence detected: memory {} (confidence={}) and memory {} (confidence={}), " +
                    "difference={} < threshold={}. Flagging both for user confirmation.",
                    existingMemory.getId(), existingMemory.getConfidenceScore(),
                    newMemory.getId(), newMemory.getConfidenceScore(),
                    confidenceDifference, SIMILAR_CONFIDENCE_THRESHOLD);

            UUID conflictGroupId = determineConflictGroupId(existingMemory, newMemory);

            return ConflictResolution.waitForClarification(
                    conflictGroupId,
                    List.of(existingMemory, newMemory),
                    String.format("Similar confidence scores (existing=%.2f, new=%.2f, difference=%.2f < threshold=%.2f). " +
                            "Both memories kept and flagged for user confirmation.",
                            existingMemory.getConfidenceScore(), newMemory.getConfidenceScore(),
                            confidenceDifference, SIMILAR_CONFIDENCE_THRESHOLD)
            );
        }

        // Confidence differs by more than the threshold - no action from this method
        log.debug("Confidence difference {} >= threshold {}, similar confidence rule does not apply",
                confidenceDifference, SIMILAR_CONFIDENCE_THRESHOLD);
        return null;
    }

    /**
     * Flags memories for user confirmation when they are part of a conflict with similar confidence.
     *
     * <p>This method:
     * <ol>
     *   <li>Groups the memories under a shared conflict_group_id</li>
     *   <li>Sets needsUserConfirmation=true on both memories</li>
     *   <li>Saves the changes to the repository</li>
     * </ol>
     *
     * @param resolution the resolution containing the memories to flag
     * @return the conflict group ID under which the memories were grouped
     */
    @Transactional
    public UUID flagMemoriesForUserConfirmation(ConflictResolution resolution) {
        if (resolution == null) {
            throw new IllegalArgumentException("resolution cannot be null");
        }
        
        if (resolution.action() != ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION) {
            throw new IllegalArgumentException("Resolution action must be WAIT_FOR_CLARIFICATION");
        }

        UUID conflictGroupId = resolution.conflictGroupId();
        List<Memory> memories = resolution.affectedMemories();

        for (Memory memory : memories) {
            memory.setConflictGroupId(conflictGroupId);
            memory.flagForUserConfirmation();
            memoryRepository.save(memory);
            log.debug("Flagged memory {} for user confirmation (conflict_group_id={})",
                    memory.getId(), conflictGroupId);
        }

        log.info("Flagged {} memories for user confirmation under conflict_group_id {}",
                memories.size(), conflictGroupId);

        return conflictGroupId;
    }

    /**
     * Checks if two memories have similar confidence scores.
     *
     * @param memory1 the first memory
     * @param memory2 the second memory
     * @return true if confidence difference is less than {@link #SIMILAR_CONFIDENCE_THRESHOLD}
     */
    public boolean hasSimilarConfidence(Memory memory1, Memory memory2) {
        if (memory1 == null || memory2 == null) {
            return false;
        }
        
        BigDecimal difference = memory1.getConfidenceScore()
                .subtract(memory2.getConfidenceScore())
                .abs();
        
        return difference.compareTo(SIMILAR_CONFIDENCE_THRESHOLD) < 0;
    }

    /**
     * Processes a contradiction and automatically resolves it if the newer memory has higher confidence.
     *
     * <p>This method combines detection with resolution:
     * <ol>
     *   <li>Applies confidence decay to the older memory (per Req 7 criteria 3: -0.3)</li>
     *   <li>Checks if newer memory with higher confidence rule applies</li>
     *   <li>If yes, applies the resolution (supersedes older memory)</li>
     *   <li>If the older memory's confidence dropped below 0.3 after decay, supersedes it</li>
     *   <li>If no, checks if confidence is similar (difference < 0.15)</li>
     *   <li>If similar, flags both memories for user confirmation</li>
     *   <li>Otherwise, groups the memories for later resolution</li>
     * </ol>
     *
     * @param contradiction the detected contradiction to process
     * @return the resolution that was applied, or null if memories were just grouped
     */
    @Transactional
    public ConflictResolution processContradiction(Contradiction contradiction) {
        if (contradiction == null) {
            throw new IllegalArgumentException("contradiction cannot be null");
        }

        // SPEC-004 Req 7 criteria 3: Apply confidence decay to the older memory
        // When an implicit contradiction is detected, reduce the older memory's confidence by 0.3
        applyConfidenceDecayToOlderMemory(contradiction);

        // Check if older memory's confidence dropped below 0.3 after decay - if so, supersede it
        Memory olderMemory = getOlderMemory(contradiction);
        if (olderMemory != null && olderMemory.getConfidenceScore().compareTo(new BigDecimal("0.30")) < 0) {
            Memory newerMemory = getNewerMemory(contradiction);
            log.info("Older memory {} confidence dropped below 0.3 after decay (now {}), superseding it",
                    olderMemory.getId(), olderMemory.getConfidenceScore());
            
            UUID conflictGroupId = olderMemory.getConflictGroupId() != null 
                    ? olderMemory.getConflictGroupId() 
                    : (newerMemory.getConflictGroupId() != null ? newerMemory.getConflictGroupId() : UUID.randomUUID());
            
            ConflictResolution resolution = ConflictResolution.supersedeMemories(
                    conflictGroupId,
                    newerMemory,
                    List.of(olderMemory),
                    String.format("Older memory confidence dropped below 0.3 after contradiction decay (now %.2f)",
                            olderMemory.getConfidenceScore())
            );
            applyResolution(resolution);
            return resolution;
        }

        // First, try to resolve by newer-higher-confidence rule
        ConflictResolution resolution = resolveByNewerHigherConfidence(contradiction);

        if (resolution != null) {
            // Apply the resolution
            applyResolution(resolution);
            log.info("Automatically resolved contradiction: {}", resolution.reason());
            return resolution;
        }

        // Second, check if confidence is similar - both memories should be kept and flagged
        resolution = resolveBySimilarConfidence(contradiction);

        if (resolution != null) {
            // Flag both memories for user confirmation
            flagMemoriesForUserConfirmation(resolution);
            log.info("Similar confidence detected, both memories flagged for user confirmation: {}",
                    resolution.reason());
            return resolution;
        }

        // No automatic resolution - group the memories for later manual/scheduled resolution
        UUID groupId = groupConflictingMemories(contradiction);
        if (groupId != null) {
            log.info("Contradiction could not be auto-resolved, grouped under conflict_group_id {}",
                    groupId);
        }

        return null;
    }

    /**
     * Applies confidence decay to the older memory when a contradiction is detected.
     *
     * <p>Per SPEC-004 Requirement 7 criteria 3:
     * WHEN an implicit contradiction is detected (father states something different without correction language),
     * THE Memory_System SHALL reduce the older memory's confidence by 0.3 (minimum 0.0).
     *
     * <p>This decay is applied BEFORE other resolution rules (newer-higher-confidence, similar-confidence).
     *
     * @param contradiction the detected contradiction
     */
    @Transactional
    public void applyConfidenceDecayToOlderMemory(Contradiction contradiction) {
        if (contradiction == null) {
            throw new IllegalArgumentException("contradiction cannot be null");
        }

        Memory olderMemory = getOlderMemory(contradiction);
        if (olderMemory == null) {
            log.debug("Cannot determine older memory for contradiction, skipping confidence decay");
            return;
        }

        BigDecimal originalConfidence = olderMemory.getConfidenceScore();
        olderMemory.applyConfidenceDecayOnContradiction();
        memoryRepository.save(olderMemory);

        log.info("Applied confidence decay to older memory {} on contradiction detection: {} -> {}",
                olderMemory.getId(), originalConfidence, olderMemory.getConfidenceScore());
    }

    /**
     * Gets the older memory from a contradiction based on creation timestamp.
     *
     * @param contradiction the contradiction to analyze
     * @return the older memory, or null if timestamps are identical
     */
    Memory getOlderMemory(Contradiction contradiction) {
        Memory existingMemory = contradiction.existingMemory();
        Memory newMemory = contradiction.newMemory();

        if (newMemory.getCreatedAt().isAfter(existingMemory.getCreatedAt())) {
            return existingMemory;
        } else if (existingMemory.getCreatedAt().isAfter(newMemory.getCreatedAt())) {
            return newMemory;
        }
        // Same creation time - cannot determine older
        return null;
    }

    /**
     * Gets the newer memory from a contradiction based on creation timestamp.
     *
     * @param contradiction the contradiction to analyze
     * @return the newer memory, or null if timestamps are identical
     */
    Memory getNewerMemory(Contradiction contradiction) {
        Memory existingMemory = contradiction.existingMemory();
        Memory newMemory = contradiction.newMemory();

        if (newMemory.getCreatedAt().isAfter(existingMemory.getCreatedAt())) {
            return newMemory;
        } else if (existingMemory.getCreatedAt().isAfter(newMemory.getCreatedAt())) {
            return existingMemory;
        }
        // Same creation time - cannot determine newer
        return null;
    }

    /**
     * Resolves a conflict group by applying the recommended action.
     *
     * @param conflictGroupId the conflict group to resolve
     * @return the resolution that was applied
     */
    @Transactional
    public ConflictResolution resolveConflictGroup(UUID conflictGroupId) {
        ConflictResolution resolution = analyzeConflictGroup(conflictGroupId);
        applyResolution(resolution);
        return resolution;
    }

    /**
     * Applies a conflict resolution action.
     *
     * @param resolution the resolution to apply
     */
    @Transactional
    public void applyResolution(ConflictResolution resolution) {
        switch (resolution.action()) {
            case SUPERSEDE -> {
                for (Memory memory : resolution.affectedMemories()) {
                    memory.markSuperseded(resolution.winningMemory().getId());
                    memory.setConflictGroupId(null);
                    memoryRepository.save(memory);
                }
                // Clear conflict group from winning memory
                Memory winner = resolution.winningMemory();
                winner.setConflictGroupId(null);
                memoryRepository.save(winner);
            }
            case EXPIRE -> {
                for (Memory memory : resolution.affectedMemories()) {
                    memory.expire();
                    memory.setConflictGroupId(null);
                    memoryRepository.save(memory);
                }
            }
            case CLEAR_GROUP -> {
                Memory memory = resolution.winningMemory();
                if (memory != null) {
                    memory.setConflictGroupId(null);
                    memoryRepository.save(memory);
                }
            }
            case WAIT_FOR_CLARIFICATION, NO_ACTION -> {
                // No changes needed
                log.debug("No resolution action taken for conflict group {}: {}",
                        resolution.conflictGroupId(), resolution.reason());
            }
        }
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    /**
     * Checks if a memory is eligible for conflict grouping.
     * Per Req 7 criteria 4: must be ACTIVE/CONFIRMED with confidence >= 0.5.
     */
    boolean isEligibleForConflictGroup(Memory memory) {
        if (memory == null) {
            return false;
        }

        // Check state
        if (memory.getState() != MemoryState.ACTIVE && memory.getState() != MemoryState.CONFIRMED) {
            return false;
        }

        // Check confidence
        return memory.getConfidenceScore().compareTo(MIN_CONFIDENCE_FOR_CONFLICT_GROUP) >= 0;
    }

    /**
     * Determines the conflict group ID to use for two memories.
     * If either already has a group, use that one (or merge if both have different groups).
     */
    private UUID determineConflictGroupId(Memory memory1, Memory memory2) {
        UUID group1 = memory1.getConflictGroupId();
        UUID group2 = memory2.getConflictGroupId();

        if (group1 == null && group2 == null) {
            // Neither has a group - create new one
            return UUID.randomUUID();
        } else if (group1 != null && group2 == null) {
            // Use existing group from memory1
            return group1;
        } else if (group1 == null) {
            // Use existing group from memory2
            return group2;
        } else if (group1.equals(group2)) {
            // Already in same group
            return group1;
        } else {
            // Different groups - merge by moving all from group2 to group1
            mergeConflictGroups(group1, group2);
            return group1;
        }
    }

    /**
     * Merges two conflict groups into one.
     */
    private void mergeConflictGroups(UUID targetGroupId, UUID sourceGroupId) {
        List<Memory> sourceMemories = memoryRepository.findByConflictGroupId(sourceGroupId);
        for (Memory memory : sourceMemories) {
            memory.setConflictGroupId(targetGroupId);
            memoryRepository.save(memory);
        }
        log.info("Merged conflict group {} into {}, moved {} memories",
                sourceGroupId, targetGroupId, sourceMemories.size());
    }

    /**
     * Assigns a conflict group ID to a memory if not already set.
     */
    private void assignConflictGroupId(Memory memory, UUID conflictGroupId) {
        if (!conflictGroupId.equals(memory.getConflictGroupId())) {
            memory.setConflictGroupId(conflictGroupId);
            memory.setLastUpdatedAt(Instant.now());
            memoryRepository.save(memory);
        }
    }
}
