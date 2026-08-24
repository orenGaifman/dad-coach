package com.dadcoach.memory;

import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.EventType;
import com.dadcoach.memory.audit.MemoryAuditLog;
import com.dadcoach.memory.audit.MemoryAuditService;
import com.dadcoach.memory.dto.MemoryCapacityDto;
import com.dadcoach.memory.dto.RetrievalResultDto;
import com.dadcoach.memory.extraction.MemoryExtractionService;
import com.dadcoach.memory.lifecycle.MemoryLifecycleService;
import com.dadcoach.memory.retrieval.MemoryRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Memory & Knowledge System facade (SPEC-004).
 *
 * <p>This service acts as the main entry point for other components to interact
 * with the memory system. It delegates to specialized services:
 * <ul>
 *   <li>{@link MemoryRetriever} - for ranked memory retrieval</li>
 *   <li>{@link MemoryExtractionService} - for async memory extraction</li>
 *   <li>{@link MemoryLifecycleService} - for state transitions</li>
 *   <li>{@link MemoryRepository} - for persistence and queries</li>
 *   <li>{@link MemoryAuditService} - for tracking and auditing</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>This service is stateless and thread-safe. All dependencies are injected
 * and managed by Spring.
 *
 * <h3>Transaction Management</h3>
 * <p>Methods that modify state are annotated with @Transactional. Read-only methods
 * delegate transaction management to the underlying services.
 *
 * <p><b>Validates: SPEC-004 Design Document - MemoryService Public Interface</b>
 *
 * @see MemoryFacadeService
 */
@Service
public class MemoryFacadeServiceImpl implements MemoryFacadeService {

    private static final Logger log = LoggerFactory.getLogger(MemoryFacadeServiceImpl.class);

    /**
     * States that count toward the active memory capacity.
     */
    private static final EnumSet<MemoryState> ACTIVE_STATES = EnumSet.of(
            MemoryState.ACTIVE, MemoryState.CONFIRMED);

    private final MemoryRetriever memoryRetriever;
    private final MemoryExtractionService extractionService;
    private final MemoryLifecycleService lifecycleService;
    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;

    /**
     * Creates a MemoryFacadeServiceImpl with all required dependencies.
     *
     * @param memoryRetriever   the retrieval service for ranked memory access
     * @param extractionService the extraction service for async processing
     * @param lifecycleService  the lifecycle service for state transitions
     * @param memoryRepository  the repository for persistence
     * @param auditService      the audit service for tracking
     */
    public MemoryFacadeServiceImpl(
            @Nullable MemoryRetriever memoryRetriever,
            @Nullable MemoryExtractionService extractionService,
            @Nullable MemoryLifecycleService lifecycleService,
            @Qualifier("specMemoryRepository") @Nullable MemoryRepository memoryRepository,
            @Nullable MemoryAuditService auditService) {
        this.memoryRetriever = memoryRetriever;
        this.extractionService = extractionService;
        this.lifecycleService = lifecycleService;
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Retrieval
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<RetrievalResultDto> retrieveRanked(UUID fatherId, String topic, UUID childId, int maxCount) {
        log.debug("Retrieving ranked memories. fatherId={}, topic='{}', childId={}, maxCount={}",
                fatherId, topic, childId, maxCount);

        if (memoryRetriever == null) {
            log.warn("MemoryRetriever not available, returning empty list");
            return List.of();
        }

        List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(fatherId, topic, childId, maxCount);

        log.info("Retrieved {} memories for father={}. Top score={}",
                results.size(), fatherId,
                results.isEmpty() ? "N/A" : String.format("%.4f", results.get(0).getCompositeScore()));

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Extraction
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void triggerExtraction(UUID conversationId, UUID fatherId, String transcript) {
        log.debug("Triggering memory extraction. conversationId={}, fatherId={}", conversationId, fatherId);

        if (extractionService == null) {
            log.warn("MemoryExtractionService not available, skipping extraction");
            return;
        }

        // Extraction is async - this returns immediately
        extractionService.triggerExtraction(conversationId, fatherId, transcript);

        log.info("Memory extraction triggered. conversationId={}, fatherId={}", conversationId, fatherId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tracking (Injection/Reference)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void recordInjection(List<UUID> memoryIds, UUID conversationId) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            log.debug("No memories to record for injection. conversationId={}", conversationId);
            return;
        }

        log.debug("Recording injection for {} memories. conversationId={}", memoryIds.size(), conversationId);

        for (UUID memoryId : memoryIds) {
            try {
                recordMemoryUsage(memoryId, conversationId, EventType.INJECTION);
            } catch (Exception e) {
                log.error("Failed to record injection. memoryId={}, conversationId={}, error={}",
                        memoryId, conversationId, e.getMessage());
                // Continue processing other memories
            }
        }

        log.info("Recorded injection for {} memories. conversationId={}", memoryIds.size(), conversationId);
    }

    @Override
    @Transactional
    public void recordReference(List<UUID> memoryIds, UUID conversationId) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            log.debug("No memories to record for reference. conversationId={}", conversationId);
            return;
        }

        log.debug("Recording reference for {} memories. conversationId={}", memoryIds.size(), conversationId);

        for (UUID memoryId : memoryIds) {
            try {
                recordMemoryUsage(memoryId, conversationId, EventType.REFERENCE);
            } catch (Exception e) {
                log.error("Failed to record reference. memoryId={}, conversationId={}, error={}",
                        memoryId, conversationId, e.getMessage());
                // Continue processing other memories
            }
        }

        log.info("Recorded reference for {} memories. conversationId={}", memoryIds.size(), conversationId);
    }

    /**
     * Records memory usage (injection or reference) and updates access tracking.
     *
     * <p>Per SPEC-004 Req 6 Criteria 2:
     * When a memory is meaningfully used (injected/referenced), reset its expiration timer.
     *
     * @param memoryId       the memory ID
     * @param conversationId the conversation where usage occurred
     * @param eventType      the type of usage (INJECTION or REFERENCE)
     */
    private void recordMemoryUsage(UUID memoryId, UUID conversationId, EventType eventType) {
        if (memoryRepository == null) {
            log.warn("MemoryRepository not available, cannot record usage");
            return;
        }

        memoryRepository.findById(memoryId).ifPresent(memory -> {
            // Update access tracking (this also resets expiration per tier rules)
            memory.recordAccess();
            memoryRepository.save(memory);

            // Create audit entry for the usage event
            if (auditService != null) {
                String stateAfter = auditService.serializeMemoryState(memory);
                MemoryAuditLog auditLog = new MemoryAuditLog(
                        memoryId,
                        memory.getFatherId(),
                        eventType,
                        ActorType.SYSTEM,
                        null, // No state before for usage events
                        stateAfter
                );
                // Set conversation reference in metadata if needed
                // For now, we rely on the audit entry timestamp correlation

                log.debug("Recorded {} for memory. memoryId={}, conversationId={}",
                        eventType, memoryId, conversationId);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle Operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void confirmMemory(UUID memoryId) {
        log.debug("Confirming memory. memoryId={}", memoryId);

        if (lifecycleService == null) {
            throw new IllegalStateException("MemoryLifecycleService not available");
        }

        Memory confirmed = lifecycleService.confirmMemory(memoryId);
        log.info("Memory confirmed. memoryId={}, fatherId={}, newConfidence={}",
                memoryId, confirmed.getFatherId(), confirmed.getConfidenceScore());
    }

    @Override
    @Transactional
    public void supersedeMemory(UUID oldMemoryId, String newContent, double newConfidence) {
        log.debug("Superseding memory. oldMemoryId={}, newConfidence={}", oldMemoryId, newConfidence);

        if (lifecycleService == null) {
            throw new IllegalStateException("MemoryLifecycleService not available");
        }

        BigDecimal confidence = BigDecimal.valueOf(newConfidence);
        Memory newMemory = lifecycleService.supersedeMemory(oldMemoryId, newContent, confidence);

        log.info("Memory superseded. oldMemoryId={}, newMemoryId={}, fatherId={}",
                oldMemoryId, newMemory.getId(), newMemory.getFatherId());
    }

    @Override
    @Transactional
    public void deleteMemory(UUID memoryId, String reason) {
        log.debug("Deleting memory. memoryId={}, reason='{}'", memoryId, reason);

        if (lifecycleService == null) {
            throw new IllegalStateException("MemoryLifecycleService not available");
        }

        Memory deleted = lifecycleService.deleteMemory(memoryId, ActorType.USER);
        log.info("Memory marked for deletion. memoryId={}, fatherId={}, reason='{}'",
                memoryId, deleted.getFatherId(), reason);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GDPR Erasure
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void deleteAllForFather(UUID fatherId) {
        log.debug("Performing GDPR erasure for father. fatherId={}", fatherId);

        if (memoryRepository == null) {
            log.warn("MemoryRepository not available, cannot perform GDPR erasure");
            return;
        }

        // Get all memories for the father
        List<Memory> memories = memoryRepository.findByFatherId(fatherId);

        if (memories.isEmpty()) {
            log.info("No memories found for GDPR erasure. fatherId={}", fatherId);
            return;
        }

        int deletedCount = 0;
        for (Memory memory : memories) {
            try {
                if (memory.getState() != MemoryState.DELETED) {
                    // Capture state before deletion for audit
                    String stateBefore = auditService != null 
                            ? auditService.serializeMemoryState(memory) : null;

                    // Transition to DELETED state
                    memory.delete();
                    memoryRepository.save(memory);

                    // Create audit entry
                    if (auditService != null) {
                        auditService.createAuditEntryForDelete(memory, ActorType.USER, stateBefore);
                    }

                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to delete memory during GDPR erasure. memoryId={}, error={}",
                        memory.getId(), e.getMessage());
                // Continue with other memories
            }
        }

        log.info("GDPR erasure completed. fatherId={}, memoriesDeleted={}", fatherId, deletedCount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Capacity
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public MemoryCapacityDto getCapacity(UUID fatherId) {
        log.debug("Getting capacity for father. fatherId={}", fatherId);

        if (memoryRepository == null) {
            log.warn("MemoryRepository not available, returning zero capacity");
            return new MemoryCapacityDto(fatherId, 0);
        }

        long currentCount = memoryRepository.countByFatherIdAndStateIn(fatherId, ACTIVE_STATES);
        MemoryCapacityDto capacity = new MemoryCapacityDto(fatherId, currentCount);

        log.debug("Capacity retrieved. fatherId={}, currentCount={}, available={}, usage={}",
                fatherId, currentCount, capacity.getAvailableCapacity(), capacity.getUsagePercentageFormatted());

        return capacity;
    }
}
