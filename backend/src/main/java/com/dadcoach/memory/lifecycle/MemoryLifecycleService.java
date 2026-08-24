package com.dadcoach.memory.lifecycle;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.audit.ActorType;
import com.dadcoach.memory.audit.MemoryAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing memory lifecycle state transitions.
 *
 * <p><b>Version History and Audit Architecture (SPEC-004 Requirements 10, 18, 24):</b>
 * <ul>
 *   <li>Every lifecycle state transition creates an audit entry with before/after state snapshots</li>
 *   <li>Audit entries are written synchronously within the same transaction as memory operations</li>
 *   <li>State snapshots capture: content, category, state, importance_score, confidence_score, subject_type, child_id, superseded_by</li>
 *   <li>Audit log is append-only and retained for 2 years per product policy</li>
 *   <li>Version history enables auditability and potential rollback scenarios</li>
 * </ul>
 *
 * <p>From SPEC-004 Requirement 2 (REQ-7):
 * Each memory follows a defined lifecycle: ACTIVE → CONFIRMED → SUPERSEDED/ARCHIVED/EXPIRED/DELETED.
 * This service handles state transitions with proper validation, audit logging, and persistence.
 *
 * <p>State transitions handled:
 * <ul>
 *   <li>{@link #confirmMemory(UUID)} - ACTIVE → CONFIRMED (user verification)</li>
 *   <li>{@link #supersedeMemory(UUID, String, BigDecimal)} - ACTIVE/CONFIRMED → SUPERSEDED (correction)</li>
 *   <li>{@link #archiveMemory(UUID, ActorType)} - ACTIVE/CONFIRMED → ARCHIVED (capacity or manual)</li>
 *   <li>{@link #expireMemory(UUID)} - ACTIVE → EXPIRED (decay/timeout)</li>
 *   <li>{@link #deleteMemory(UUID, ActorType)} - Any state → DELETED (user request or GDPR)</li>
 *   <li>{@link #reactivateMemory(UUID, ActorType)} - ARCHIVED/EXPIRED → ACTIVE (re-reference)</li>
 * </ul>
 *
 * <p>Audit requirements (REQ-24):
 * Every lifecycle event produces a durable audit record containing event_type, memory_id,
 * father_id, timestamp, actor_type, and before/after state snapshots.
 *
 * @see Memory
 * @see MemoryState
 * @see MemoryAuditService
 */
@Service
public class MemoryLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(MemoryLifecycleService.class);

    private final MemoryRepository memoryRepository;
    private final MemoryAuditService auditService;

    /**
     * Creates a MemoryLifecycleService with required dependencies.
     *
     * @param memoryRepository the repository for memory persistence
     * @param auditService     the service for audit logging
     */
    public MemoryLifecycleService(MemoryRepository memoryRepository, MemoryAuditService auditService) {
        this.memoryRepository = memoryRepository;
        this.auditService = auditService;
    }

    /**
     * Confirms a memory, transitioning it from ACTIVE to CONFIRMED state.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 3:
     * WHEN a memory transitions to CONFIRMED state, THE Memory_System SHALL:
     * <ul>
     *   <li>Set confidence_score to max(current_confidence, 0.9)</li>
     *   <li>Reset the decay timer (by extending expiration)</li>
     *   <li>Increment confirmation_count</li>
     *   <li>Set last_confirmed_at timestamp</li>
     * </ul>
     *
     * <p>State transition: ACTIVE → CONFIRMED
     *
     * @param memoryId the ID of the memory to confirm
     * @return the updated memory in CONFIRMED state
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to CONFIRMED state
     */
    @Transactional
    public Memory confirmMemory(UUID memoryId) {
        log.debug("Confirming memory. memoryId={}", memoryId);

        // Load the memory
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for confirmation. memoryId={}", memoryId);
                    return new EntityNotFoundException("Memory not found: " + memoryId);
                });

        // Capture state before for audit
        String stateBefore = auditService.serializeMemoryState(memory);
        MemoryState previousState = memory.getState();

        // Perform the state transition (validates ACTIVE state)
        memory.confirm();

        // Create audit entry (per REQ-24)
        auditService.createAuditEntryForConfirm(memory, stateBefore);

        // Persist the updated memory
        Memory savedMemory = memoryRepository.save(memory);

        log.info("Memory confirmed. memoryId={}, fatherId={}, previousState={}, newState={}, " +
                        "confirmationCount={}, newConfidence={}",
                memoryId, memory.getFatherId(), previousState, savedMemory.getState(),
                savedMemory.getConfirmationCount(), savedMemory.getConfidenceScore());

        return savedMemory;
    }

    /**
     * Supersedes an old memory with a new one containing updated content.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 4:
     * WHEN a memory transitions to SUPERSEDED state, THE Memory_System SHALL:
     * <ul>
     *   <li>Record superseded_by (reference to new memory)</li>
     *   <li>Record superseded_at timestamp (via lastUpdatedAt)</li>
     *   <li>Preserve the old content in version history</li>
     * </ul>
     *
     * <p>From SPEC-004 Requirement 7 (Conflict Resolution):
     * WHEN an explicit correction is detected, create the new memory with confidence 1.0,
     * transition the old memory to SUPERSEDED state, and record the supersession link.
     *
     * <p>Valid state transitions to SUPERSEDED:
     * <ul>
     *   <li>ACTIVE → SUPERSEDED</li>
     *   <li>CONFIRMED → SUPERSEDED</li>
     * </ul>
     *
     * @param oldMemoryId   the ID of the memory to supersede
     * @param newContent    the updated content for the new memory
     * @param newConfidence the confidence score for the new memory (typically 1.0 for corrections)
     * @return the newly created memory that supersedes the old one
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to SUPERSEDED state
     * @throws IllegalArgumentException if newContent is null or empty, or confidence is invalid
     */
    @Transactional
    public Memory supersedeMemory(UUID oldMemoryId, String newContent, BigDecimal newConfidence) {
        log.debug("Superseding memory. oldMemoryId={}, newConfidence={}", oldMemoryId, newConfidence);

        // Validate inputs
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("New content cannot be null or empty");
        }
        if (newConfidence == null || newConfidence.compareTo(BigDecimal.ZERO) < 0 
                || newConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }

        // Load the old memory
        Memory oldMemory = memoryRepository.findById(oldMemoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for supersession. oldMemoryId={}", oldMemoryId);
                    return new EntityNotFoundException("Memory not found: " + oldMemoryId);
                });

        // Validate state transition is allowed
        if (!oldMemory.getState().canTransitionTo(MemoryState.SUPERSEDED)) {
            throw new IllegalStateException(
                    "Cannot transition from " + oldMemory.getState() + " to SUPERSEDED");
        }

        // Capture state before for audit
        String oldMemoryStateBefore = auditService.serializeMemoryState(oldMemory);
        MemoryState previousState = oldMemory.getState();

        // Create the new memory with updated content
        // Inherits category, subject type, father, child from old memory
        Memory newMemory = new Memory(
                oldMemory.getFatherId(),
                oldMemory.getCategory(),
                oldMemory.getSubjectType(),
                newContent,
                oldMemory.getImportanceScore(),
                newConfidence,
                MemorySourceType.FATHER_CORRECTION
        );
        newMemory.setChildId(oldMemory.getChildId());
        newMemory.setSourceConversationId(oldMemory.getSourceConversationId());
        newMemory.setGoalId(oldMemory.getGoalId());
        // Copy event-related fields if applicable
        newMemory.setEventDate(oldMemory.getEventDate());
        newMemory.setEventEndDate(oldMemory.getEventEndDate());
        newMemory.setIsRecurring(oldMemory.getIsRecurring());

        // Save the new memory first to get its ID
        Memory savedNewMemory = memoryRepository.save(newMemory);

        // Create audit entry for the new memory creation
        auditService.createAuditEntryForCreate(savedNewMemory, ActorType.USER);

        // Mark the old memory as superseded
        oldMemory.markSuperseded(savedNewMemory.getId());

        // Create audit entry for the old memory supersession
        auditService.createAuditEntryForSupersede(oldMemory, ActorType.USER, oldMemoryStateBefore);

        // Persist the updated old memory
        Memory savedOldMemory = memoryRepository.save(oldMemory);

        log.info("Memory superseded. oldMemoryId={}, newMemoryId={}, fatherId={}, " +
                        "previousState={}, newState={}, newConfidence={}",
                oldMemoryId, savedNewMemory.getId(), oldMemory.getFatherId(),
                previousState, savedOldMemory.getState(), newConfidence);

        return savedNewMemory;
    }

    /**
     * Archives a memory, transitioning it from ACTIVE or CONFIRMED to ARCHIVED state.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 5:
     * WHEN a memory transitions to ARCHIVED state, THE Memory_System SHALL:
     * <ul>
     *   <li>Preserve all data but exclude it from retrieval queries</li>
     *   <li>Exclude it from active memory count</li>
     *   <li>Create an audit entry with before/after state snapshots</li>
     * </ul>
     *
     * <p>Archiving typically occurs due to:
     * <ul>
     *   <li>Memory count exceeding the 500 limit (capacity enforcement)</li>
     *   <li>Manual archive request</li>
     * </ul>
     *
     * <p>Valid state transitions to ARCHIVED:
     * <ul>
     *   <li>ACTIVE → ARCHIVED</li>
     *   <li>CONFIRMED → ARCHIVED</li>
     * </ul>
     *
     * @param memoryId  the ID of the memory to archive
     * @param actorType who triggered the archive (SYSTEM for capacity, USER for manual)
     * @return the updated memory in ARCHIVED state
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to ARCHIVED state
     */
    @Transactional
    public Memory archiveMemory(UUID memoryId, ActorType actorType) {
        log.debug("Archiving memory. memoryId={}, actorType={}", memoryId, actorType);

        // Load the memory
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for archiving. memoryId={}", memoryId);
                    return new EntityNotFoundException("Memory not found: " + memoryId);
                });

        // Capture state before for audit
        String stateBefore = auditService.serializeMemoryState(memory);
        MemoryState previousState = memory.getState();

        // Perform the state transition (validates allowed states)
        memory.archive();

        // Create audit entry (per REQ-24)
        auditService.createAuditEntryForArchive(memory, actorType, stateBefore);

        // Persist the updated memory
        Memory savedMemory = memoryRepository.save(memory);

        log.info("Memory archived. memoryId={}, fatherId={}, previousState={}, newState={}, actorType={}",
                memoryId, memory.getFatherId(), previousState, savedMemory.getState(), actorType);

        return savedMemory;
    }

    /**
     * Expires a memory, transitioning it from ACTIVE to EXPIRED state.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 6:
     * WHEN a memory transitions to EXPIRED state, THE Memory_System SHALL:
     * <ul>
     *   <li>Preserve it for 30 days before automatic deletion</li>
     *   <li>Allow reactivation if the father re-references the information</li>
     *   <li>Create an audit entry with before/after state snapshots</li>
     * </ul>
     *
     * <p>Expiration occurs when:
     * <ul>
     *   <li>Confidence drops below 0.5 AND memory not accessed in 60 days</li>
     *   <li>Confidence reaches 0.0 regardless of access (Req 5 Criteria 4)</li>
     * </ul>
     *
     * <p>Valid state transition: ACTIVE → EXPIRED
     *
     * @param memoryId the ID of the memory to expire
     * @return the updated memory in EXPIRED state
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to EXPIRED state
     */
    @Transactional
    public Memory expireMemory(UUID memoryId) {
        log.debug("Expiring memory. memoryId={}", memoryId);

        // Load the memory
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for expiration. memoryId={}", memoryId);
                    return new EntityNotFoundException("Memory not found: " + memoryId);
                });

        // Capture state before for audit
        String stateBefore = auditService.serializeMemoryState(memory);
        MemoryState previousState = memory.getState();

        // Perform the state transition (validates ACTIVE state)
        memory.expire();

        // Create audit entry (per REQ-24) - expiration is always a SYSTEM operation
        auditService.createAuditEntryForExpire(memory, stateBefore);

        // Persist the updated memory
        Memory savedMemory = memoryRepository.save(memory);

        log.info("Memory expired. memoryId={}, fatherId={}, previousState={}, newState={}, confidence={}",
                memoryId, memory.getFatherId(), previousState, savedMemory.getState(), 
                savedMemory.getConfidenceScore());

        return savedMemory;
    }

    /**
     * Deletes a memory, transitioning it to DELETED state.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 7:
     * WHEN a memory transitions to DELETED state, THE Memory_System SHALL:
     * <ul>
     *   <li>Perform complete content erasure within 72 hours</li>
     *   <li>Erase: memory content field, all version history content_snapshots, embedding vector</li>
     *   <li>Retain only audit metadata (memory_id, father_id, category, operation timestamps)</li>
     *   <li>Create an audit entry with before/after state snapshots</li>
     * </ul>
     *
     * <p>Note: This method marks the memory for deletion. Actual content erasure is performed
     * by a background job (MemoryDeletionService) within 72 hours.
     *
     * <p>Deletion can be triggered by:
     * <ul>
     *   <li>Father requests deletion (USER)</li>
     *   <li>GDPR erasure request (USER)</li>
     *   <li>Cleanup job for SUPERSEDED (90 days) or EXPIRED (30 days) memories (SYSTEM)</li>
     * </ul>
     *
     * <p>Valid state transitions to DELETED:
     * <ul>
     *   <li>ACTIVE → DELETED</li>
     *   <li>CONFIRMED → DELETED</li>
     *   <li>SUPERSEDED → DELETED</li>
     *   <li>ARCHIVED → DELETED</li>
     *   <li>EXPIRED → DELETED</li>
     * </ul>
     *
     * @param memoryId  the ID of the memory to delete
     * @param actorType who triggered the deletion (USER for request, SYSTEM for cleanup)
     * @return the updated memory in DELETED state
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to DELETED state
     */
    @Transactional
    public Memory deleteMemory(UUID memoryId, ActorType actorType) {
        log.debug("Deleting memory. memoryId={}, actorType={}", memoryId, actorType);

        // Load the memory
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for deletion. memoryId={}", memoryId);
                    return new EntityNotFoundException("Memory not found: " + memoryId);
                });

        // Capture state before for audit
        String stateBefore = auditService.serializeMemoryState(memory);
        MemoryState previousState = memory.getState();

        // Perform the state transition (validates allowed states)
        memory.delete();

        // Create audit entry (per REQ-24)
        auditService.createAuditEntryForDelete(memory, actorType, stateBefore);

        // Persist the updated memory
        Memory savedMemory = memoryRepository.save(memory);

        log.info("Memory marked for deletion. memoryId={}, fatherId={}, previousState={}, " +
                        "newState={}, actorType={}",
                memoryId, memory.getFatherId(), previousState, savedMemory.getState(), actorType);

        return savedMemory;
    }

    /**
     * Reactivates an ARCHIVED or EXPIRED memory, returning it to ACTIVE state.
     *
     * <p>From SPEC-004 Requirement 2 State Machine:
     * <ul>
     *   <li>ARCHIVED → ACTIVE: Father re-references the information</li>
     *   <li>EXPIRED → ACTIVE: Father re-references the information</li>
     * </ul>
     *
     * <p>Reactivation resets the expiration timer based on the memory's tier
     * and current importance score.
     *
     * <p>Reactivation can occur when:
     * <ul>
     *   <li>Father explicitly references archived/expired information in conversation</li>
     *   <li>System detects semantic match with archived/expired memory</li>
     * </ul>
     *
     * <p>Valid state transitions to ACTIVE:
     * <ul>
     *   <li>ARCHIVED → ACTIVE</li>
     *   <li>EXPIRED → ACTIVE</li>
     * </ul>
     *
     * @param memoryId  the ID of the memory to reactivate
     * @param actorType who triggered the reactivation (USER or SYSTEM)
     * @return the updated memory in ACTIVE state
     * @throws EntityNotFoundException if no memory exists with the given ID
     * @throws IllegalStateException   if the memory cannot transition to ACTIVE state
     */
    @Transactional
    public Memory reactivateMemory(UUID memoryId, ActorType actorType) {
        log.debug("Reactivating memory. memoryId={}, actorType={}", memoryId, actorType);

        // Load the memory
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> {
                    log.warn("Memory not found for reactivation. memoryId={}", memoryId);
                    return new EntityNotFoundException("Memory not found: " + memoryId);
                });

        // Capture state before for audit
        String stateBefore = auditService.serializeMemoryState(memory);
        MemoryState previousState = memory.getState();

        // Perform the state transition (validates ARCHIVED or EXPIRED state)
        memory.reactivate();

        // Create audit entry (per REQ-24)
        auditService.createAuditEntryForReactivate(memory, actorType, stateBefore);

        // Persist the updated memory
        Memory savedMemory = memoryRepository.save(memory);

        log.info("Memory reactivated. memoryId={}, fatherId={}, previousState={}, newState={}, " +
                        "actorType={}, newExpiresAt={}",
                memoryId, memory.getFatherId(), previousState, savedMemory.getState(), 
                actorType, savedMemory.getExpiresAt());

        return savedMemory;
    }

    /**
     * Deletes all memories for a father as part of GDPR erasure request.
     *
     * <p>From SPEC-004 Requirement 2 Criteria 7 and Requirement 17:
     * WHEN a GDPR erasure request is received, THE Memory_System SHALL:
     * <ul>
     *   <li>Find all memories belonging to the father (regardless of current state)</li>
     *   <li>Transition each memory to DELETED state (except already DELETED memories)</li>
     *   <li>Create audit log entries for each deletion with GDPR erasure reason</li>
     *   <li>Content erasure is performed by a background job within 72 hours</li>
     * </ul>
     *
     * <p><strong>Atomicity:</strong> This operation is transactional. If any memory
     * deletion fails, the entire operation rolls back to maintain data consistency.
     * This is critical for GDPR compliance - partial erasure is not acceptable.
     *
     * <p><strong>Audit Trail:</strong> Each deleted memory generates an audit entry
     * with ActorType.USER and triggered_by="USER:gdpr_erasure". The audit entries
     * are preserved for 2 years per SPEC-004 Requirement 2 Criteria 7.
     *
     * @param fatherId the ID of the father whose memories should be deleted
     * @return the count of memories transitioned to DELETED state
     * @throws IllegalArgumentException if fatherId is null
     */
    @Transactional
    public int deleteAllForFather(UUID fatherId) {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }

        log.info("Starting GDPR erasure for father. fatherId={}", fatherId);

        // Find all memories for this father
        List<Memory> allMemories = memoryRepository.findByFatherId(fatherId);

        if (allMemories.isEmpty()) {
            log.info("No memories found for father. fatherId={}", fatherId);
            return 0;
        }

        int deletedCount = 0;
        int alreadyDeletedCount = 0;

        for (Memory memory : allMemories) {
            // Skip already DELETED memories
            if (memory.getState() == MemoryState.DELETED) {
                alreadyDeletedCount++;
                log.debug("Skipping already deleted memory. memoryId={}", memory.getId());
                continue;
            }

            // Capture state before for audit
            String stateBefore = auditService.serializeMemoryState(memory);
            MemoryState previousState = memory.getState();

            // Perform the state transition to DELETED
            memory.delete();

            // Create audit entry with GDPR erasure context (per REQ-24)
            auditService.createAuditEntryForDeleteWithFullTracking(
                    memory,
                    previousState,
                    ActorType.USER,
                    "USER:gdpr_erasure",
                    stateBefore
            );

            // Persist the updated memory
            memoryRepository.save(memory);

            deletedCount++;

            log.debug("Memory marked for GDPR deletion. memoryId={}, previousState={}",
                    memory.getId(), previousState);
        }

        log.info("GDPR erasure completed. fatherId={}, memoriesDeleted={}, alreadyDeleted={}, totalProcessed={}",
                fatherId, deletedCount, alreadyDeletedCount, allMemories.size());

        return deletedCount;
    }
}
