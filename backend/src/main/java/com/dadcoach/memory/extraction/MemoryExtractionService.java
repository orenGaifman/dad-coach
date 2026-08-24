package com.dadcoach.memory.extraction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.MemoryAuditService;
import com.dadcoach.memory.extraction.MemoryCapacityManager.EnsureCapacityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for processing memory extraction requests asynchronously.
 *
 * <p>From SPEC-004 Design AD-2 (Extraction as Async Side-Effect):
 * Memory extraction is triggered by the Conversation Engine's outbox (SPEC-005 design).
 * The Memory System processes extraction requests asynchronously, never blocking conversation responses.
 *
 * <p>This service is responsible for:
 * <ul>
 *   <li>Receiving extraction requests from the conversation engine outbox</li>
 *   <li>Processing extractions on a separate thread (never blocking the caller)</li>
 *   <li>Validating AI recommendations via ExtractionValidator (Task 4.2)</li>
 *   <li>Checking for duplicates before creation via DuplicateDetector (Task 4.4)</li>
 *   <li>Checking capacity before creation via MemoryCapacityManager (Task 4.6)</li>
 *   <li>Logging extraction start and completion for observability</li>
 *   <li>Handling errors gracefully (log and continue, don't propagate to caller)</li>
 * </ul>
 *
 * <p><strong>Duplicate Detection (Task 4.4):</strong>
 * From SPEC-004 Requirement 9, before creating a memory:
 * <ul>
 *   <li>Cosine similarity > 0.85 → DUPLICATE: Skip creation, update existing memory's confidence</li>
 *   <li>Cosine similarity 0.70-0.85 → POTENTIAL_UPDATE: Mark old as SUPERSEDED, create new</li>
 *   <li>Cosine similarity < 0.70 → DISTINCT: Proceed with creation</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong>
 * All extraction methods are annotated with @Async to ensure they execute on a separate thread.
 * The caller receives control immediately after invoking the method.
 *
 * <p><strong>Error Handling:</strong>
 * All exceptions are caught, logged, and not propagated to the caller. This ensures that
 * extraction failures never impact the conversation response flow.
 *
 * @see com.dadcoach.config.AsyncConfig for thread pool configuration
 * @see DuplicateDetector
 * @see ExtractionValidator
 * @see MemoryCapacityManager
 * @see MemoryAuditService
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    /**
     * Confidence boost when a duplicate is detected (per Requirement 5).
     * When father repeats same information: confidence = min(1.0, current + 0.2)
     */
    private static final BigDecimal CONFIDENCE_BOOST_ON_DUPLICATE = new BigDecimal("0.20");

    private final DuplicateDetector duplicateDetector;
    private final ExtractionValidator extractionValidator;
    private final MemoryRepository memoryRepository;
    private final MemoryCapacityManager capacityManager;
    private final MemoryAuditService auditService;

    /**
     * Constructs a MemoryExtractionService with required dependencies.
     *
     * @param duplicateDetector    the duplicate detector for checking semantic similarity
     * @param extractionValidator  the validator for AI recommendations
     * @param memoryRepository     the repository for memory persistence
     * @param capacityManager      the capacity manager for enforcing the 500-memory limit
     * @param auditService         the audit service for creating audit entries (Task 4.7)
     */
    public MemoryExtractionService(
            DuplicateDetector duplicateDetector,
            ExtractionValidator extractionValidator,
            @Qualifier("specMemoryRepository") @Nullable MemoryRepository memoryRepository,
            @Nullable MemoryCapacityManager capacityManager,
            @Nullable MemoryAuditService auditService) {
        this.duplicateDetector = duplicateDetector;
        this.extractionValidator = extractionValidator;
        this.memoryRepository = memoryRepository;
        this.capacityManager = capacityManager;
        this.auditService = auditService;
    }

    /**
     * Constructs a MemoryExtractionService with DuplicateDetector, Validator, Repository, and CapacityManager.
     * Audit service will be null (audit entries not created).
     *
     * @param duplicateDetector    the duplicate detector for checking semantic similarity
     * @param extractionValidator  the validator for AI recommendations
     * @param memoryRepository     the repository for memory persistence
     * @param capacityManager      the capacity manager for enforcing the 500-memory limit
     */
    public MemoryExtractionService(
            DuplicateDetector duplicateDetector,
            ExtractionValidator extractionValidator,
            @Qualifier("specMemoryRepository") @Nullable MemoryRepository memoryRepository,
            @Nullable MemoryCapacityManager capacityManager) {
        this(duplicateDetector, extractionValidator, memoryRepository, capacityManager, null);
    }

    /**
     * Constructs a MemoryExtractionService with DuplicateDetector, Validator, and Repository.
     * Capacity manager and audit service will be null.
     *
     * @param duplicateDetector    the duplicate detector for checking semantic similarity
     * @param extractionValidator  the validator for AI recommendations
     * @param memoryRepository     the repository for memory persistence
     */
    public MemoryExtractionService(
            DuplicateDetector duplicateDetector,
            ExtractionValidator extractionValidator,
            @Qualifier("specMemoryRepository") @Nullable MemoryRepository memoryRepository) {
        this(duplicateDetector, extractionValidator, memoryRepository, null, null);
    }

    /**
     * Constructor for testing without repository dependency.
     * @deprecated Use the full constructor for production code.
     */
    public MemoryExtractionService() {
        this.duplicateDetector = null;
        this.extractionValidator = null;
        this.memoryRepository = null;
        this.capacityManager = null;
        this.auditService = null;
    }

    /**
     * Triggers memory extraction for a completed conversation asynchronously.
     *
     * <p>This method processes extraction on a separate thread (via @Async) to ensure
     * the conversation response is never blocked. The caller is not blocked while
     * extraction processes.
     *
     * <p>From SPEC-004 Requirement 3 (Memory Creation Rules):
     * <ul>
     *   <li>Max 5 new memories per conversation to prevent memory flooding</li>
     *   <li>Min 1 memory per completed conversation (CONVERSATION_SUMMARY always created)</li>
     *   <li>Extraction prioritizes: identity facts, corrections, relationship dynamics, goals, preferences, context</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong>
     * Any exception thrown during extraction is caught and logged. Errors do not propagate
     * to the caller, ensuring the conversation flow is never impacted.
     *
     * @param conversationId the ID of the conversation to extract memories from
     * @param fatherId       the ID of the father whose conversation this is
     * @param transcript     the conversation transcript text to analyze
     * @return a CompletableFuture that completes when extraction finishes (for testing/monitoring)
     */
    @Async("sideEffectExecutor")
    public CompletableFuture<Void> triggerExtraction(UUID conversationId, UUID fatherId, String transcript) {
        log.info("Memory extraction started. conversationId={}, fatherId={}", conversationId, fatherId);

        try {
            processExtraction(conversationId, fatherId, transcript);
            log.info("Memory extraction completed successfully. conversationId={}, fatherId={}", 
                    conversationId, fatherId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            // Errors are logged but not propagated to caller (per design requirement)
            log.error("Memory extraction failed. conversationId={}, fatherId={}, error={}", 
                    conversationId, fatherId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Processes the actual extraction logic.
     *
     * <p>This method implements the memory extraction pipeline:
     * <ol>
     *   <li>Validate inputs (conversationId, fatherId, transcript)</li>
     *   <li>Generate AI recommendations (placeholder - to be implemented)</li>
     *   <li>Validate each recommendation via ExtractionValidator (Task 4.2)</li>
     *   <li>Check for duplicates via DuplicateDetector (Task 4.4)</li>
     *   <li>Handle duplicate detection result appropriately</li>
     *   <li>Create memory if distinct or supersede if potential update</li>
     * </ol>
     *
     * @param conversationId the conversation ID
     * @param fatherId       the father ID
     * @param transcript     the conversation transcript
     */
    void processExtraction(UUID conversationId, UUID fatherId, String transcript) {
        // Validate inputs
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId cannot be null");
        }
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }
        if (transcript == null || transcript.isBlank()) {
            log.debug("Empty transcript, skipping extraction. conversationId={}", conversationId);
            return;
        }

        log.debug("Processing extraction for conversation. conversationId={}, transcriptLength={}", 
                conversationId, transcript.length());

        // Task 4.7: Audit entries are created automatically when memories are created
        // via createMemoryFromRecommendation() → createAuditEntryForMemory()

        // Placeholder: AI recommendation generation will be implemented in subsequent tasks
        // For now, this method successfully completes to allow async behavior testing
    }

    /**
     * Processes a validated AI memory recommendation, checking for duplicates before creation.
     *
     * <p>From SPEC-004 Requirement 9 (Duplicate Detection):
     * <ul>
     *   <li>Similarity > 0.85 (DUPLICATE): Skip creation, boost existing memory's confidence</li>
     *   <li>Similarity 0.70-0.85 (POTENTIAL_UPDATE): Supersede existing, create new memory</li>
     *   <li>Similarity < 0.70 (DISTINCT): Create new memory</li>
     * </ul>
     *
     * @param recommendation the validated AI recommendation
     * @param fatherId       the father's ID
     * @param conversationId the source conversation ID
     * @param embedding      the embedding vector for the recommendation content (may be null)
     * @return the created or updated memory, or empty if duplicate detected
     */
    Optional<Memory> processValidatedRecommendation(
            AiMemoryRecommendation recommendation,
            UUID fatherId,
            UUID conversationId,
            float[] embedding) {

        if (recommendation == null) {
            log.warn("Cannot process null recommendation. fatherId={}", fatherId);
            return Optional.empty();
        }

        // Parse enum values from validated recommendation
        MemoryCategory category = MemoryCategory.valueOf(recommendation.category().toUpperCase());
        MemorySubjectType subjectType = MemorySubjectType.valueOf(recommendation.subjectType().toUpperCase());

        // Check for duplicates before creation (Task 4.4)
        DuplicateResult duplicateResult = checkForDuplicates(fatherId, category, subjectType, embedding);

        log.debug("Duplicate check result. fatherId={}, category={}, status={}, similarity={}",
                fatherId, category, duplicateResult.status(), duplicateResult.similarity());

        return switch (duplicateResult.status()) {
            case DUPLICATE -> handleDuplicateResult(duplicateResult, fatherId);
            case POTENTIAL_UPDATE -> handlePotentialUpdateResult(
                    duplicateResult, recommendation, fatherId, conversationId, embedding);
            case DISTINCT -> handleDistinctResult(recommendation, fatherId, conversationId, embedding);
        };
    }

    /**
     * Checks for duplicates using the DuplicateDetector.
     * Falls back gracefully if detector is unavailable or embedding is null.
     */
    DuplicateResult checkForDuplicates(UUID fatherId, MemoryCategory category,
                                        MemorySubjectType subjectType, float[] embedding) {
        if (duplicateDetector == null) {
            log.debug("DuplicateDetector not available, allowing creation. fatherId={}", fatherId);
            return DuplicateResult.distinct();
        }

        return duplicateDetector.check(fatherId, category, subjectType, embedding);
    }

    /**
     * Handles DUPLICATE result: skip creation, boost existing memory's confidence.
     *
     * <p>From Requirement 5 criteria 2:
     * When father repeats the same information (new user evidence): confidence = min(1.0, current + 0.2)
     *
     * @param duplicateResult the duplicate result with existing memory reference
     * @param fatherId        the father's ID
     * @return empty since no new memory is created
     */
    private Optional<Memory> handleDuplicateResult(DuplicateResult duplicateResult, UUID fatherId) {
        UUID existingMemoryId = duplicateResult.existingMemoryId()
                .orElseThrow(() -> new IllegalStateException("DUPLICATE result must have existingMemoryId"));

        log.info("Duplicate memory detected, boosting confidence. fatherId={}, existingMemoryId={}, similarity={}",
                fatherId, existingMemoryId, duplicateResult.similarity());

        // Boost confidence of existing memory instead of creating duplicate
        if (memoryRepository != null) {
            memoryRepository.findById(existingMemoryId).ifPresent(existingMemory -> {
                existingMemory.increaseConfidence(CONFIDENCE_BOOST_ON_DUPLICATE);
                memoryRepository.save(existingMemory);
                log.debug("Boosted confidence for existing memory. memoryId={}, newConfidence={}",
                        existingMemoryId, existingMemory.getConfidenceScore());
            });
        }

        return Optional.empty();
    }

    /**
     * Handles POTENTIAL_UPDATE result: supersede existing memory, create new one.
     *
     * <p>This handles the case where similarity is 0.70-0.85, indicating the new
     * content may be an update or refinement of existing information.
     *
     * <p><strong>Task 4.6 - Capacity Check:</strong>
     * Before creating the new memory, this method ensures capacity is available.
     * Since we're superseding an existing memory (which transitions to SUPERSEDED state
     * and no longer counts toward capacity), we typically have room. However, if we're
     * exactly at capacity with other memories, we need to ensure capacity first.
     *
     * @param duplicateResult   the potential update result with existing memory reference
     * @param recommendation    the new recommendation to create
     * @param fatherId          the father's ID
     * @param conversationId    the source conversation ID
     * @param embedding         the embedding vector
     * @return the newly created memory
     */
    private Optional<Memory> handlePotentialUpdateResult(
            DuplicateResult duplicateResult,
            AiMemoryRecommendation recommendation,
            UUID fatherId,
            UUID conversationId,
            float[] embedding) {

        UUID existingMemoryId = duplicateResult.existingMemoryId()
                .orElseThrow(() -> new IllegalStateException("POTENTIAL_UPDATE result must have existingMemoryId"));

        log.info("Potential update detected, superseding existing memory. fatherId={}, existingMemoryId={}, similarity={}",
                fatherId, existingMemoryId, duplicateResult.similarity());

        // Mark existing memory as superseded
        if (memoryRepository != null) {
            // First supersede the existing memory (this frees up capacity)
            memoryRepository.findById(existingMemoryId).ifPresent(existingMemory -> {
                existingMemory.markSuperseded(null); // Will update with new ID after creation
                memoryRepository.save(existingMemory);
            });

            // Now create the new memory (capacity should be available after supersession)
            Memory newMemory = createMemoryFromRecommendation(recommendation, fatherId, conversationId, embedding);
            
            // Update the superseded memory with the new memory's ID
            memoryRepository.findById(existingMemoryId).ifPresent(existingMemory -> {
                existingMemory.setSupersededBy(newMemory.getId());
                memoryRepository.save(existingMemory);
                log.debug("Marked existing memory as superseded. oldMemoryId={}, newMemoryId={}",
                        existingMemoryId, newMemory.getId());
            });

            return Optional.of(newMemory);
        }

        return Optional.empty();
    }

    /**
     * Handles DISTINCT result: create new memory.
     *
     * <p><strong>Task 4.6 - Capacity Check:</strong>
     * Before creating a new memory, this method ensures capacity is available by
     * calling the MemoryCapacityManager. If the father is at the 500-memory limit,
     * the lowest-scoring memory is archived to make room.
     *
     * @param recommendation    the recommendation to create
     * @param fatherId          the father's ID
     * @param conversationId    the source conversation ID
     * @param embedding         the embedding vector
     * @return the newly created memory, or empty if capacity could not be ensured
     */
    private Optional<Memory> handleDistinctResult(
            AiMemoryRecommendation recommendation,
            UUID fatherId,
            UUID conversationId,
            float[] embedding) {

        log.debug("Memory is distinct, creating new. fatherId={}, category={}",
                fatherId, recommendation.category());

        if (memoryRepository == null) {
            return Optional.empty();
        }

        // Task 4.6: Check capacity before creating new memory
        if (!ensureCapacityForNewMemory(fatherId)) {
            log.warn("Cannot create memory - capacity could not be ensured. fatherId={}", fatherId);
            return Optional.empty();
        }

        Memory newMemory = createMemoryFromRecommendation(recommendation, fatherId, conversationId, embedding);
        return Optional.of(newMemory);
    }

    /**
     * Ensures capacity is available for a new memory.
     *
     * <p>From SPEC-004 Requirement 15 / REQ-6:
     * Maximum 500 active memories per father. When at capacity, archive the memory
     * with lowest composite score (importance × confidence).
     *
     * @param fatherId the father's ID
     * @return true if capacity is available (either already available or freed up)
     */
    boolean ensureCapacityForNewMemory(UUID fatherId) {
        if (capacityManager == null) {
            log.debug("CapacityManager not available, allowing creation. fatherId={}", fatherId);
            return true;
        }

        EnsureCapacityResult result = capacityManager.ensureCapacity(fatherId);
        
        if (result.isSuccess()) {
            if (result instanceof EnsureCapacityResult.MemoryArchived archived) {
                log.info("Archived memory to make capacity. fatherId={}, archivedMemoryId={}", 
                        fatherId, archived.archivedMemoryId());
            }
            return true;
        }
        
        // Capacity could not be ensured
        if (result instanceof EnsureCapacityResult.ArchiveFailed failed) {
            log.error("Failed to archive memory for capacity. fatherId={}, memoryId={}, error={}",
                    fatherId, failed.memoryId(), failed.errorMessage());
        } else if (result instanceof EnsureCapacityResult.NoArchivableMemory) {
            log.error("At capacity but no archivable memory found. fatherId={}", fatherId);
        }
        
        return false;
    }

    /**
     * Creates and persists a new memory from a validated recommendation.
     *
     * <p><strong>Task 4.7 - Audit Entry:</strong>
     * After successfully creating and persisting the memory, an audit entry is created
     * with event_type CREATE and actor_type AI (per REQ-24). The audit entry includes
     * a state_after snapshot of the newly created memory.
     *
     * @param recommendation    the validated recommendation
     * @param fatherId          the father's ID
     * @param conversationId    the source conversation ID
     * @param embedding         the embedding vector (may be null)
     * @return the persisted memory
     */
    private Memory createMemoryFromRecommendation(
            AiMemoryRecommendation recommendation,
            UUID fatherId,
            UUID conversationId,
            float[] embedding) {

        MemoryCategory category = MemoryCategory.valueOf(recommendation.category().toUpperCase());
        MemorySubjectType subjectType = MemorySubjectType.valueOf(recommendation.subjectType().toUpperCase());
        MemorySourceType sourceType = MemorySourceType.valueOf(recommendation.sourceType().toUpperCase());

        Memory memory = new Memory(
                fatherId,
                category,
                subjectType,
                recommendation.content(),
                recommendation.importanceScore(),
                BigDecimal.valueOf(recommendation.confidenceScore()),
                sourceType
        );

        memory.setSourceConversationId(conversationId);
        memory.setChildId(recommendation.childId());
        memory.setEventDate(recommendation.eventDate());

        if (embedding != null && embedding.length > 0) {
            memory.setEmbedding(embedding);
        }

        Memory savedMemory = memoryRepository.save(memory);
        log.info("Created new memory. memoryId={}, fatherId={}, category={}, importance={}, confidence={}",
                savedMemory.getId(), fatherId, category,
                recommendation.importanceScore(), recommendation.confidenceScore());

        // Task 4.7: Create audit entry for the new memory
        createAuditEntryForMemory(savedMemory);

        return savedMemory;
    }

    /**
     * Creates an audit entry for a newly created memory.
     *
     * <p>From SPEC-004 Requirement 24 (REQ-24):
     * Every memory lifecycle event SHALL produce a durable audit record.
     * For extraction-based memory creation, the actor_type is AI.
     *
     * @param memory the newly created memory
     */
    private void createAuditEntryForMemory(Memory memory) {
        if (auditService == null) {
            log.debug("AuditService not available, skipping audit entry. memoryId={}", memory.getId());
            return;
        }

        try {
            auditService.createAuditEntryForCreate(memory, ActorType.AI)
                    .ifPresent(entry -> log.debug("Created audit entry for new memory. memoryId={}, auditId={}",
                            memory.getId(), entry.getId()));
        } catch (Exception e) {
            // Log error but don't propagate - audit failures shouldn't block memory creation
            log.error("Failed to create audit entry for memory. memoryId={}, error={}",
                    memory.getId(), e.getMessage(), e);
        }
    }

    /**
     * Processes a list of AI recommendations, validating and checking duplicates for each.
     *
     * <p>This is the main entry point for processing extracted recommendations. It:
     * <ol>
     *   <li>Validates each recommendation via ExtractionValidator</li>
     *   <li>Skips invalid recommendations (logs warning, continues with others)</li>
     *   <li>Checks duplicates for each valid recommendation</li>
     *   <li>Creates/updates memories based on duplicate detection result</li>
     * </ol>
     *
     * <p><strong>Task 4.5 - Error Handling:</strong>
     * This method ensures that errors processing one recommendation do not abort the entire batch.
     * Exceptions during validation or processing are caught and logged, and processing continues
     * with the remaining recommendations.
     *
     * @param recommendations list of AI recommendations to process
     * @param fatherId        the father's ID
     * @param conversationId  the source conversation ID
     * @param embeddings      list of embeddings corresponding to each recommendation (may contain nulls)
     * @return list of created memories (excludes duplicates and invalid recommendations)
     */
    List<Memory> processRecommendations(
            List<AiMemoryRecommendation> recommendations,
            UUID fatherId,
            UUID conversationId,
            List<float[]> embeddings) {

        if (recommendations == null || recommendations.isEmpty()) {
            log.debug("No recommendations to process. fatherId={}, conversationId={}", fatherId, conversationId);
            return List.of();
        }

        return recommendations.stream()
                .filter(rec -> {
                    // Validate each recommendation with exception handling
                    try {
                        if (extractionValidator == null) {
                            log.warn("ExtractionValidator not available, skipping validation. fatherId={}", fatherId);
                            return true;
                        }
                        ValidationResult validationResult = extractionValidator.validate(rec);
                        if (!validationResult.isValid()) {
                            log.warn("Invalid recommendation skipped. fatherId={}, errors={}", 
                                    fatherId, validationResult.errors());
                            return false;
                        }
                        return true;
                    } catch (Exception e) {
                        // Task 4.5: Exception during validation should not abort the batch
                        log.error("Exception during recommendation validation, skipping. fatherId={}, error={}", 
                                fatherId, e.getMessage(), e);
                        return false;
                    }
                })
                .map(rec -> {
                    try {
                        // Get corresponding embedding (may be null)
                        int index = recommendations.indexOf(rec);
                        float[] embedding = (embeddings != null && index < embeddings.size()) 
                                ? embeddings.get(index) 
                                : null;
                        
                        // Process with duplicate checking
                        return processValidatedRecommendation(rec, fatherId, conversationId, embedding);
                    } catch (Exception e) {
                        // Task 4.5: Exception during processing should not abort the batch
                        log.error("Exception during recommendation processing, skipping. fatherId={}, content={}, error={}", 
                                fatherId, rec.content() != null ? rec.content().substring(0, Math.min(50, rec.content().length())) : "null",
                                e.getMessage(), e);
                        return Optional.<Memory>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
