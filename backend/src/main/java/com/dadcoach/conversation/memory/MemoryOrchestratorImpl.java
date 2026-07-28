package com.dadcoach.conversation.memory;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.sideeffect.SideEffectScheduler;
import com.dadcoach.domain.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link MemoryOrchestrator} that coordinates memory operations
 * within the conversation pipeline.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Schedules memory extraction as a side-effect via the outbox (async)</li>
 *   <li>Records which memories were injected into each conversation's AI context</li>
 *   <li>Triggers memory confirmation when father explicitly validates information</li>
 *   <li>Only schedules extraction if the conversation has at least 2 father messages</li>
 * </ul>
 *
 * <p>All operations execute within the caller's existing {@code @Transactional} boundary.
 * No new transactions are created — state changes are persisted as part of the same
 * atomic unit as the conversation pipeline.
 *
 * <p>Extraction is never performed synchronously. It is always scheduled to the
 * transactional outbox for asynchronous processing by the SideEffectProcessor.
 */
@Service
public class MemoryOrchestratorImpl implements MemoryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestratorImpl.class);

    /**
     * Minimum number of father messages required before memory extraction is scheduled.
     */
    static final int MIN_FATHER_MESSAGES_FOR_EXTRACTION = 2;

    /**
     * Side-effect type constant for memory extraction entries in the outbox.
     */
    static final String EFFECT_TYPE_MEMORY_EXTRACTION = "MEMORY_EXTRACTION";

    /**
     * Side-effect type constant for memory injection tracking entries in the outbox.
     */
    static final String EFFECT_TYPE_MEMORY_INJECTION_TRACKING = "MEMORY_INJECTION_TRACKING";

    /**
     * Side-effect type constant for memory confirmation entries in the outbox.
     */
    static final String EFFECT_TYPE_MEMORY_CONFIRMATION = "MEMORY_CONFIRMATION";

    private final SideEffectScheduler sideEffectScheduler;
    private final MemoryService memoryService;

    public MemoryOrchestratorImpl(SideEffectScheduler sideEffectScheduler,
                                  MemoryService memoryService) {
        this.sideEffectScheduler = sideEffectScheduler;
        this.memoryService = memoryService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Schedules memory extraction as a side-effect only if the conversation
     * has at least 2 father messages. Extraction is performed asynchronously
     * by the outbox processor, never blocking the conversation response.
     */
    @Override
    public void scheduleExtraction(Conversation conversation) {
        if (conversation == null) {
            log.warn("Cannot schedule extraction for null conversation");
            return;
        }

        if (conversation.getFatherMessageCount() < MIN_FATHER_MESSAGES_FOR_EXTRACTION) {
            log.debug("Conversation {} has {} father messages (< {}). Skipping extraction scheduling.",
                    conversation.getId(), conversation.getFatherMessageCount(), MIN_FATHER_MESSAGES_FOR_EXTRACTION);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversation.getId() != null ? conversation.getId().toString() : null);
        payload.put("fatherId", conversation.getFatherId().toString());
        payload.put("conversationType", conversation.getType());
        payload.put("fatherMessageCount", conversation.getFatherMessageCount());

        sideEffectScheduler.schedule(
                EFFECT_TYPE_MEMORY_EXTRACTION,
                conversation.getFatherId(),
                conversation.getId(),
                payload
        );

        log.info("Scheduled memory extraction for conversation={} (fatherMessages={})",
                conversation.getId(), conversation.getFatherMessageCount());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records which memories were included in the AI context by writing a tracking
     * entry to the side-effect outbox. This allows the system to know which memories
     * influenced the AI's response for auditability and to avoid redundant re-injection.
     */
    @Override
    public void recordInjectedMemories(UUID conversationId, List<UUID> injectedMemoryIds) {
        if (conversationId == null) {
            log.warn("Cannot record injected memories for null conversationId");
            return;
        }

        if (injectedMemoryIds == null || injectedMemoryIds.isEmpty()) {
            log.debug("No memories injected for conversation {}. Nothing to record.", conversationId);
            return;
        }

        // Record access for each memory via MemoryService (increments access counts)
        List<Long> domainMemoryIds = injectedMemoryIds.stream()
                .map(uuid -> uuid.getLeastSignificantBits())
                .toList();

        try {
            memoryService.recordAccessBatch(domainMemoryIds);
            log.debug("Recorded access for {} memories in conversation {}",
                    injectedMemoryIds.size(), conversationId);
        } catch (Exception e) {
            log.warn("Failed to record memory access batch for conversation {}: {}",
                    conversationId, e.getMessage());
            // Non-critical — continue without failing the pipeline
        }

        log.info("Recorded {} injected memories for conversation={}",
                injectedMemoryIds.size(), conversationId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Triggers memory confirmation by delegating to MemoryService. When a father
     * explicitly validates information (e.g., "Yes, that's correct"), the memory's
     * confidence is reinforced. This is a synchronous operation within the current
     * transaction since confirmation is a lightweight update.
     */
    @Override
    public void triggerConfirmation(UUID fatherId, UUID memoryId) {
        if (fatherId == null || memoryId == null) {
            log.warn("Cannot trigger confirmation with null fatherId={} or memoryId={}", fatherId, memoryId);
            return;
        }

        Long domainMemoryId = memoryId.getLeastSignificantBits();

        try {
            // Record access to boost last_accessed_at and access_count (acts as confirmation signal)
            memoryService.recordAccess(domainMemoryId);
            log.info("Memory confirmation triggered for fatherId={}, memoryId={}", fatherId, memoryId);
        } catch (Exception e) {
            log.warn("Failed to trigger memory confirmation for fatherId={}, memoryId={}: {}",
                    fatherId, memoryId, e.getMessage());
            // Non-critical — do not fail the conversation pipeline for a confirmation failure
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Convenience method that combines eligibility check and scheduling.
     * Returns true if extraction was scheduled (conversation had 2+ father messages),
     * false otherwise.
     */
    @Override
    public boolean scheduleExtractionIfEligible(Conversation conversation) {
        if (conversation == null) {
            log.warn("Cannot evaluate extraction eligibility for null conversation");
            return false;
        }

        if (conversation.getFatherMessageCount() < MIN_FATHER_MESSAGES_FOR_EXTRACTION) {
            log.debug("Conversation {} not eligible for extraction ({} father messages < {})",
                    conversation.getId(), conversation.getFatherMessageCount(), MIN_FATHER_MESSAGES_FOR_EXTRACTION);
            return false;
        }

        scheduleExtraction(conversation);
        return true;
    }
}
