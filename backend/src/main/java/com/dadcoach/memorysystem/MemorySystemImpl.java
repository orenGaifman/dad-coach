package com.dadcoach.memorysystem;

import com.dadcoach.domain.memory.Memory;
import com.dadcoach.domain.memory.MemoryRepository;
import com.dadcoach.domain.memory.MemoryService;
import com.dadcoach.domain.memory.MemoryTier;
import com.dadcoach.memory.MemoryCategory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link MemorySystem} interface.
 *
 * <p>Provides ranked memory retrieval, consolidation, and lifecycle management
 * by delegating to {@link MemoryService} for CRUD and adding ranking/consolidation logic.</p>
 *
 * <p>Key algorithms:
 * <ul>
 *   <li>Composite ranking: (importance × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)</li>
 *   <li>Recency factor: max(0, 1.0 - (days_since_creation × 0.05)) — decays to 0 after 20 days</li>
 *   <li>Relevance: simple topic-matching heuristic (content contains topic → 1.0, else 0.0)</li>
 *   <li>Consolidation: merge short-term memories (importance 1-3) older than 7 days</li>
 * </ul>
 */
@Service
@Transactional
public class MemorySystemImpl implements MemorySystem {

    /** Default retrieval limit per Requirement 7.6. */
    public static final int DEFAULT_TOP_MEMORIES_LIMIT = 15;

    /** Number of days after which short-term memories are eligible for consolidation. */
    public static final int CONSOLIDATION_AGE_DAYS = 7;

    /** Weight for importance in the composite ranking formula. */
    static final double IMPORTANCE_WEIGHT = 0.5;

    /** Weight for recency in the composite ranking formula. */
    static final double RECENCY_WEIGHT = 0.3;

    /** Weight for relevance in the composite ranking formula. */
    static final double RELEVANCE_WEIGHT = 0.2;

    /** Daily decay rate for recency_factor. Full decay occurs at 20 days. */
    static final double RECENCY_DECAY_RATE = 0.05;

    private final MemoryService memoryService;
    private final MemoryRepository memoryRepository;

    public MemorySystemImpl(MemoryService memoryService, MemoryRepository memoryRepository) {
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
    }

    // ─── Create ──────────────────────────────────────────────────────────

    @Override
    public Memory createMemory(Long fatherId, MemoryCategory category,
                               String content, int importanceScore, double confidenceScore) {
        BigDecimal confidence = BigDecimal.valueOf(confidenceScore)
                .setScale(2, RoundingMode.HALF_UP);
        return memoryService.createMemory(fatherId, null, category, content,
                importanceScore, confidence);
    }

    // ─── Retrieval with Ranking ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Memory> retrieveTopMemories(Long fatherId, String topic, int limit) {
        List<Memory> activeMemories = memoryRepository.findActiveByFatherId(fatherId);

        Instant now = Instant.now();

        // Compute composite scores, sort descending, take top N
        List<Memory> ranked = activeMemories.stream()
                .sorted(Comparator.comparingDouble(
                        (Memory m) -> computeCompositeScore(m, topic, now)).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        // Record access for returned memories
        List<Long> memoryIds = ranked.stream()
                .map(Memory::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (!memoryIds.isEmpty()) {
            memoryService.recordAccessBatch(memoryIds);
        }

        return ranked;
    }

    // ─── Consolidation ───────────────────────────────────────────────────

    @Override
    public void consolidateMemories(Long fatherId) {
        Instant threshold = Instant.now().minus(Duration.ofDays(CONSOLIDATION_AGE_DAYS));

        List<Memory> activeMemories = memoryRepository.findActiveByFatherId(fatherId);

        // Filter to short-term memories (importance 1-3) older than 7 days
        List<Memory> toConsolidate = activeMemories.stream()
                .filter(m -> m.getImportanceScore() <= 3)
                .filter(m -> m.getCreatedAt().isBefore(threshold))
                .collect(Collectors.toList());

        if (toConsolidate.isEmpty()) {
            return;
        }

        // Find highest importance among candidates
        int maxImportance = toConsolidate.stream()
                .mapToInt(Memory::getImportanceScore)
                .max()
                .orElse(3);

        // Average confidence scores
        BigDecimal avgConfidence = toConsolidate.stream()
                .map(Memory::getConfidenceScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(toConsolidate.size()), 2, RoundingMode.HALF_UP);

        // Merge content into a summary
        String mergedContent = toConsolidate.stream()
                .map(Memory::getContent)
                .collect(Collectors.joining("; ", "Consolidated: ", ""));

        // Archive old memories
        for (Memory memory : toConsolidate) {
            memory.archive();
            memoryRepository.save(memory);
        }

        // Create summary memory
        memoryService.createMemory(fatherId, null, MemoryCategory.CONVERSATION_SUMMARY,
                mergedContent, maxImportance, avgConfidence);
    }

    // ─── Supersede ───────────────────────────────────────────────────────

    @Override
    public Memory supersedeMemory(Long existingMemoryId, String newContent) {
        return memoryService.supersedeMemory(existingMemoryId, newContent);
    }

    // ─── Expire Low Confidence ───────────────────────────────────────────

    @Override
    public void expireLowConfidenceMemories() {
        // Find all active memories with low confidence not accessed in 60 days
        // We process all fathers by fetching all qualifying memories directly
        BigDecimal confidenceThreshold = new BigDecimal("0.50");
        Instant accessThreshold = Instant.now().minus(Duration.ofDays(60));

        List<Memory> toExpire = memoryRepository.findAllLowConfidenceUnaccessed(
                confidenceThreshold, accessThreshold);

        for (Memory memory : toExpire) {
            memory.expire();
            memoryRepository.save(memory);
        }
    }

    // ─── Scoring Logic (package-private for testing) ─────────────────────

    /**
     * Computes the composite ranking score for a memory.
     *
     * <p>Formula: (importance × 0.5) + (recency_factor × 0.3) + (relevance × 0.2)</p>
     *
     * @param memory the memory to score
     * @param topic  the topic for relevance scoring
     * @param now    the current time for recency calculation
     * @return the composite score
     */
    static double computeCompositeScore(Memory memory, String topic, Instant now) {
        double normalizedImportance = memory.getImportanceScore() / 10.0;
        double recencyFactor = computeRecencyFactor(memory.getCreatedAt(), now);
        double relevance = computeRelevance(memory.getContent(), topic);

        return (normalizedImportance * IMPORTANCE_WEIGHT)
                + (recencyFactor * RECENCY_WEIGHT)
                + (relevance * RELEVANCE_WEIGHT);
    }

    /**
     * Computes the recency factor for a memory.
     *
     * <p>Formula: max(0, 1.0 - (days_since_creation × 0.05))</p>
     * <p>Decays linearly to 0 after 20 days.</p>
     *
     * @param createdAt the memory creation time
     * @param now       the current time
     * @return recency factor between 0.0 and 1.0
     */
    static double computeRecencyFactor(Instant createdAt, Instant now) {
        long daysSinceCreation = Duration.between(createdAt, now).toDays();
        return Math.max(0.0, 1.0 - (daysSinceCreation * RECENCY_DECAY_RATE));
    }

    /**
     * Computes relevance score using simple topic-matching heuristic.
     *
     * <p>If the memory content contains the topic keyword (case-insensitive), relevance = 1.0.
     * Otherwise, relevance = 0.0.</p>
     *
     * @param content the memory content
     * @param topic   the topic keyword to match
     * @return 1.0 if content contains topic, 0.0 otherwise
     */
    static double computeRelevance(String content, String topic) {
        if (topic == null || topic.isBlank()) {
            return 0.0;
        }
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        return content.toLowerCase().contains(topic.toLowerCase()) ? 1.0 : 0.0;
    }
}
