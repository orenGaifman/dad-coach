package com.dadcoach.memory.audit;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating and querying memory audit log entries.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * Every memory lifecycle event (create, update, archive, confirm, expire) SHALL produce
 * a durable audit record containing: event_type, memory_id, father_id, timestamp,
 * actor_type (AI/USER/SYSTEM), before/after state snapshots. The audit trail is separate
 * from operational memory storage and retained independently.
 *
 * <p>From SPEC-004 Requirement 2 Criteria 9:
 * THE Memory_System SHALL log every state transition with: memory_id, from_state,
 * to_state, trigger_reason, triggered_by (system/father), and timestamp.
 *
 * <p><strong>Transactional Design (SPEC-004 Design - Correctness Properties):</strong>
 * <ul>
 *   <li>Append-only: Audit entries are never modified after creation</li>
 *   <li>Synchronous: Audit writes are part of the same transaction as memory operations</li>
 *   <li><strong>Rollback on failure: If audit logging fails, the entire transaction 
 *       (including the memory operation) is rolled back</strong></li>
 *   <li>State snapshots: Key memory fields are serialized to JSON for before/after tracking</li>
 *   <li>State transitions: Explicit from_state and to_state fields capture lifecycle changes</li>
 * </ul>
 *
 * <p>The strict transactional requirement ensures that memory operations and their audit
 * trail are always consistent. A memory operation without its corresponding audit entry
 * is considered a system integrity violation.
 *
 * @see MemoryAuditLog
 * @see MemoryAuditRepository
 * @see EventType
 * @see ActorType
 */
@Service
public class MemoryAuditService {

    private static final Logger log = LoggerFactory.getLogger(MemoryAuditService.class);

    private final MemoryAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a MemoryAuditService with required dependencies.
     *
     * @param auditRepository the repository for persisting audit entries
     * @param objectMapper    the JSON mapper for state serialization
     */
    @org.springframework.beans.factory.annotation.Autowired
    public MemoryAuditService(@Nullable MemoryAuditRepository auditRepository,
                              ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a MemoryAuditService with repository only.
     * Uses a default ObjectMapper for JSON serialization.
     * This constructor is for testing purposes only.
     *
     * @param auditRepository the repository for persisting audit entries
     */
    public MemoryAuditService(@Nullable MemoryAuditRepository auditRepository) {
        this(auditRepository, new ObjectMapper());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Audit Entry Creation (Full State Tracking)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an audit entry for a memory lifecycle event with full state transition tracking.
     *
     * <p>This method captures all required audit fields per SPEC-004:
     * <ul>
     *   <li>operation_type - The type of lifecycle event</li>
     *   <li>from_state - The memory state before the operation</li>
     *   <li>to_state - The memory state after the operation</li>
     *   <li>trigger_type - Who/what type triggered the event (AI, USER, SYSTEM)</li>
     *   <li>triggered_by - Specific actor reference</li>
     *   <li>state_before/state_after - JSON snapshots of full memory state</li>
     * </ul>
     *
     * @param memory      the memory being audited (with current/new state)
     * @param operationType the type of lifecycle event
     * @param fromState   the memory state before the operation (null for CREATE)
     * @param toState     the memory state after the operation
     * @param triggerType who or what type triggered the event
     * @param triggeredBy specific actor reference (e.g., "SYSTEM:decay_job", "USER:confirmation")
     * @param stateBefore JSON snapshot of state before the event (null for CREATE)
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryWithStateTransition(
            Memory memory,
            EventType operationType,
            @Nullable MemoryState fromState,
            MemoryState toState,
            ActorType triggerType,
            String triggeredBy,
            @Nullable String stateBefore) {
        
        if (auditRepository == null) {
            throw new MemoryAuditException("AuditRepository not available - audit logging is required");
        }

        if (memory == null) {
            throw new IllegalArgumentException("Cannot create audit entry for null memory");
        }

        if (memory.getId() == null) {
            throw new IllegalArgumentException("Cannot create audit entry for memory without ID");
        }

        if (triggeredBy == null || triggeredBy.isBlank()) {
            throw new IllegalArgumentException("triggeredBy is required for audit entry");
        }

        String stateAfter = serializeMemoryState(memory);

        MemoryAuditLog auditEntry = new MemoryAuditLog(
                memory.getId(),
                memory.getFatherId(),
                operationType,
                fromState,
                toState,
                triggerType,
                triggeredBy,
                stateBefore,
                stateAfter
        );

        try {
            MemoryAuditLog savedEntry = auditRepository.save(auditEntry);

            log.debug("Created audit entry with state transition. memoryId={}, operationType={}, " +
                            "fromState={}, toState={}, triggerType={}, triggeredBy={}, auditId={}",
                    memory.getId(), operationType, fromState, toState, triggerType, triggeredBy, savedEntry.getId());

            return savedEntry;

        } catch (Exception e) {
            log.error("Failed to create audit entry - triggering transaction rollback. " +
                            "memoryId={}, operationType={}, error={}",
                    memory.getId(), operationType, e.getMessage(), e);
            throw new MemoryAuditException(
                    "Failed to create audit entry for memory " + memory.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Creates an audit entry for a memory CREATE event with full tracking.
     *
     * @param memory      the newly created memory
     * @param triggerType who or what triggered the creation
     * @param triggeredBy specific actor reference (e.g., "AI:extraction", "SYSTEM:onboarding")
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForCreateWithFullTracking(
            Memory memory, ActorType triggerType, String triggeredBy) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.CREATE,
                null,  // No from_state for CREATE
                memory.getState(),
                triggerType,
                triggeredBy,
                null   // No state_before for CREATE
        );
    }

    /**
     * Creates an audit entry for a memory UPDATE event with full tracking.
     *
     * @param memory      the updated memory (with new state)
     * @param fromState   the memory state before the update
     * @param triggerType who or what triggered the update
     * @param triggeredBy specific actor reference
     * @param stateBefore JSON snapshot of state before the update
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForUpdateWithFullTracking(
            Memory memory, MemoryState fromState, ActorType triggerType,
            String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.UPDATE,
                fromState,
                memory.getState(),
                triggerType,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory CONFIRM event with full tracking.
     *
     * @param memory      the confirmed memory
     * @param fromState   the memory state before confirmation (typically ACTIVE)
     * @param triggeredBy specific actor reference (e.g., "USER:confirmation")
     * @param stateBefore JSON snapshot of state before confirmation
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForConfirmWithFullTracking(
            Memory memory, MemoryState fromState, String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.CONFIRM,
                fromState,
                MemoryState.CONFIRMED,
                ActorType.USER,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory ARCHIVE event with full tracking.
     *
     * @param memory      the archived memory
     * @param fromState   the memory state before archiving
     * @param triggerType who or what triggered the archive
     * @param triggeredBy specific actor reference (e.g., "SYSTEM:capacity_enforcement")
     * @param stateBefore JSON snapshot of state before archiving
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForArchiveWithFullTracking(
            Memory memory, MemoryState fromState, ActorType triggerType,
            String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.ARCHIVE,
                fromState,
                MemoryState.ARCHIVED,
                triggerType,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory SUPERSEDE event with full tracking.
     *
     * @param memory      the superseded memory
     * @param fromState   the memory state before supersession
     * @param triggerType who or what triggered the supersession
     * @param triggeredBy specific actor reference (e.g., "USER:correction", "AI:contradiction")
     * @param stateBefore JSON snapshot of state before supersession
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForSupersedeWithFullTracking(
            Memory memory, MemoryState fromState, ActorType triggerType,
            String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.SUPERSEDE,
                fromState,
                MemoryState.SUPERSEDED,
                triggerType,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory EXPIRE event with full tracking.
     *
     * @param memory      the expired memory
     * @param fromState   the memory state before expiration
     * @param triggeredBy specific actor reference (e.g., "SYSTEM:decay_job", "SYSTEM:expiration_job")
     * @param stateBefore JSON snapshot of state before expiration
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForExpireWithFullTracking(
            Memory memory, MemoryState fromState, String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.EXPIRE,
                fromState,
                MemoryState.EXPIRED,
                ActorType.SYSTEM,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory DELETE event with full tracking.
     *
     * @param memory      the deleted memory
     * @param fromState   the memory state before deletion
     * @param triggerType who or what triggered the deletion
     * @param triggeredBy specific actor reference (e.g., "USER:deletion_request", "SYSTEM:gdpr_erasure")
     * @param stateBefore JSON snapshot of state before deletion
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForDeleteWithFullTracking(
            Memory memory, MemoryState fromState, ActorType triggerType,
            String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.DELETE,
                fromState,
                MemoryState.DELETED,
                triggerType,
                triggeredBy,
                stateBefore
        );
    }

    /**
     * Creates an audit entry for a memory REACTIVATE event with full tracking.
     *
     * @param memory      the reactivated memory
     * @param fromState   the memory state before reactivation (ARCHIVED or EXPIRED)
     * @param triggerType who or what triggered the reactivation
     * @param triggeredBy specific actor reference (e.g., "USER:re_reference", "SYSTEM:reactivation")
     * @param stateBefore JSON snapshot of state before reactivation
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForReactivateWithFullTracking(
            Memory memory, MemoryState fromState, ActorType triggerType,
            String triggeredBy, String stateBefore) {
        return createAuditEntryWithStateTransition(
                memory,
                EventType.REACTIVATE,
                fromState,
                MemoryState.ACTIVE,
                triggerType,
                triggeredBy,
                stateBefore
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Legacy Audit Entry Creation (backward compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an audit entry for a memory lifecycle event with strict transactional semantics.
     *
     * <p>This method serializes the memory state to JSON and persists an audit entry.
     * For CREATE events, stateBefore is null. For other events, stateBefore should
     * contain the state prior to the change.
     *
     * <p><strong>Transactional Behavior:</strong>
     * This method participates in the calling transaction (Propagation.MANDATORY when 
     * strict mode is enabled). If the audit entry cannot be created, a 
     * {@link MemoryAuditException} is thrown, causing the entire transaction to roll back.
     * This ensures memory operations and audit entries are always consistent.
     *
     * @param memory      the memory being audited
     * @param eventType   the type of lifecycle event
     * @param actorType   who or what triggered the event
     * @param stateBefore JSON snapshot of state before the event (null for CREATE)
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     * @throws IllegalArgumentException if memory is null or has no ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryStrict(Memory memory, EventType eventType,
                                                  ActorType actorType, @Nullable String stateBefore) {
        if (auditRepository == null) {
            throw new MemoryAuditException("AuditRepository not available - audit logging is required");
        }

        if (memory == null) {
            throw new IllegalArgumentException("Cannot create audit entry for null memory");
        }

        if (memory.getId() == null) {
            throw new IllegalArgumentException("Cannot create audit entry for memory without ID");
        }

        String stateAfter = serializeMemoryState(memory);

        // Extract from_state and to_state from the JSON snapshots if available
        MemoryState fromState = extractStateFromJson(stateBefore);
        MemoryState toState = memory.getState();

        MemoryAuditLog auditEntry = new MemoryAuditLog(
                memory.getId(),
                memory.getFatherId(),
                eventType,
                fromState,
                toState,
                actorType,
                actorType.name() + ":legacy",
                stateBefore,
                stateAfter
        );

        try {
            MemoryAuditLog savedEntry = auditRepository.save(auditEntry);

            log.debug("Created audit entry (strict mode). memoryId={}, eventType={}, actorType={}, auditId={}",
                    memory.getId(), eventType, actorType, savedEntry.getId());

            return savedEntry;

        } catch (Exception e) {
            log.error("Failed to create audit entry (strict mode) - triggering transaction rollback. " +
                            "memoryId={}, eventType={}, error={}",
                    memory.getId(), eventType, e.getMessage(), e);
            throw new MemoryAuditException(
                    "Failed to create audit entry for memory " + memory.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the MemoryState from a JSON state snapshot.
     *
     * @param jsonState the JSON snapshot (may be null)
     * @return the extracted MemoryState, or null if not found
     */
    @Nullable
    private MemoryState extractStateFromJson(@Nullable String jsonState) {
        if (jsonState == null || jsonState.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(jsonState);
            var stateNode = node.get("state");
            if (stateNode != null && !stateNode.isNull()) {
                return MemoryState.valueOf(stateNode.asText());
            }
        } catch (Exception e) {
            log.debug("Could not extract state from JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Creates an audit entry for a memory lifecycle event with graceful degradation.
     *
     * <p>This method serializes the memory state to JSON and persists an audit entry.
     * For CREATE events, stateBefore is null. For other events, stateBefore should
     * contain the state prior to the change.
     *
     * <p><strong>Note:</strong> This method provides backward-compatible behavior where
     * audit failures are logged but do not block the memory operation. For strict
     * transactional consistency, use {@link #createAuditEntryStrict} instead.
     *
     * @param memory      the memory being audited
     * @param eventType   the type of lifecycle event
     * @param actorType   who or what triggered the event
     * @param stateBefore JSON snapshot of state before the event (null for CREATE)
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryWithStateTransition} for new code that requires
     *             full state transition tracking with explicit from_state, to_state, and triggered_by
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntry(Memory memory, EventType eventType,
                                                     ActorType actorType, @Nullable String stateBefore) {
        if (auditRepository == null) {
            log.debug("AuditRepository not available, skipping audit entry creation");
            return Optional.empty();
        }

        if (memory == null) {
            log.warn("Cannot create audit entry for null memory");
            return Optional.empty();
        }

        if (memory.getId() == null) {
            log.warn("Cannot create audit entry for memory without ID");
            return Optional.empty();
        }

        try {
            String stateAfter = serializeMemoryState(memory);
            MemoryState fromState = extractStateFromJson(stateBefore);
            MemoryState toState = memory.getState();

            MemoryAuditLog auditEntry = new MemoryAuditLog(
                    memory.getId(),
                    memory.getFatherId(),
                    eventType,
                    fromState,
                    toState,
                    actorType,
                    actorType.name() + ":legacy",
                    stateBefore,
                    stateAfter
            );

            MemoryAuditLog savedEntry = auditRepository.save(auditEntry);

            log.debug("Created audit entry. memoryId={}, eventType={}, actorType={}, auditId={}",
                    memory.getId(), eventType, actorType, savedEntry.getId());

            return Optional.of(savedEntry);

        } catch (Exception e) {
            // Log error but don't propagate - audit failures shouldn't block memory operations
            log.error("Failed to create audit entry. memoryId={}, eventType={}, error={}",
                    memory.getId(), eventType, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Strict Audit Entry Methods (with transaction rollback on failure)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an audit entry for a memory CREATE event with strict transactional semantics.
     *
     * <p>This is a convenience method for memory creation events where there is no
     * before state. The actor type is typically AI for extraction-based creation.
     *
     * @param memory    the newly created memory
     * @param actorType who or what triggered the creation (typically AI)
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForCreateStrict(Memory memory, ActorType actorType) {
        return createAuditEntryStrict(memory, EventType.CREATE, actorType, null);
    }

    /**
     * Creates an audit entry for a memory UPDATE event with strict transactional semantics.
     *
     * @param memory      the updated memory (with new state)
     * @param actorType   who or what triggered the update
     * @param stateBefore JSON snapshot of state before the update
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForUpdateStrict(Memory memory, ActorType actorType,
                                                           String stateBefore) {
        return createAuditEntryStrict(memory, EventType.UPDATE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory CONFIRM event with strict transactional semantics.
     *
     * @param memory      the confirmed memory
     * @param stateBefore JSON snapshot of state before confirmation
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForConfirmStrict(Memory memory, String stateBefore) {
        return createAuditEntryStrict(memory, EventType.CONFIRM, ActorType.USER, stateBefore);
    }

    /**
     * Creates an audit entry for a memory ARCHIVE event with strict transactional semantics.
     *
     * @param memory      the archived memory
     * @param actorType   who or what triggered the archive (typically SYSTEM)
     * @param stateBefore JSON snapshot of state before archiving
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForArchiveStrict(Memory memory, ActorType actorType,
                                                            String stateBefore) {
        return createAuditEntryStrict(memory, EventType.ARCHIVE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory SUPERSEDE event with strict transactional semantics.
     *
     * @param memory      the superseded memory
     * @param actorType   who or what triggered the supersession
     * @param stateBefore JSON snapshot of state before supersession
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForSupersedeStrict(Memory memory, ActorType actorType,
                                                              String stateBefore) {
        return createAuditEntryStrict(memory, EventType.SUPERSEDE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory EXPIRE event with strict transactional semantics.
     *
     * @param memory      the expired memory
     * @param stateBefore JSON snapshot of state before expiration
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForExpireStrict(Memory memory, String stateBefore) {
        return createAuditEntryStrict(memory, EventType.EXPIRE, ActorType.SYSTEM, stateBefore);
    }

    /**
     * Creates an audit entry for a memory DELETE event with strict transactional semantics.
     *
     * @param memory      the deleted memory
     * @param actorType   who or what triggered the deletion (USER for request, SYSTEM for cleanup)
     * @param stateBefore JSON snapshot of state before deletion
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForDeleteStrict(Memory memory, ActorType actorType,
                                                           String stateBefore) {
        return createAuditEntryStrict(memory, EventType.DELETE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory REACTIVATE event with strict transactional semantics.
     *
     * <p>Reactivation occurs when an ARCHIVED or EXPIRED memory is re-referenced
     * by the father and returned to ACTIVE state.
     *
     * @param memory      the reactivated memory
     * @param actorType   who or what triggered the reactivation (USER or SYSTEM)
     * @param stateBefore JSON snapshot of state before reactivation
     * @return the created audit entry
     * @throws MemoryAuditException if audit entry creation fails (triggers transaction rollback)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemoryAuditLog createAuditEntryForReactivateStrict(Memory memory, ActorType actorType,
                                                               String stateBefore) {
        return createAuditEntryStrict(memory, EventType.REACTIVATE, actorType, stateBefore);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Graceful Audit Entry Methods (backward-compatible, deprecated)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an audit entry for a memory CREATE event.
     *
     * <p>This is a convenience method for memory creation events where there is no
     * before state. The actor type is typically AI for extraction-based creation.
     *
     * @param memory    the newly created memory
     * @param actorType who or what triggered the creation (typically AI)
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForCreateWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForCreate(Memory memory, ActorType actorType) {
        return createAuditEntry(memory, EventType.CREATE, actorType, null);
    }

    /**
     * Creates an audit entry for a memory UPDATE event.
     *
     * @param memory      the updated memory (with new state)
     * @param actorType   who or what triggered the update
     * @param stateBefore JSON snapshot of state before the update
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForUpdateWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForUpdate(Memory memory, ActorType actorType,
                                                              String stateBefore) {
        return createAuditEntry(memory, EventType.UPDATE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory CONFIRM event.
     *
     * @param memory      the confirmed memory
     * @param stateBefore JSON snapshot of state before confirmation
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForConfirmWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForConfirm(Memory memory, String stateBefore) {
        return createAuditEntry(memory, EventType.CONFIRM, ActorType.USER, stateBefore);
    }

    /**
     * Creates an audit entry for a memory ARCHIVE event.
     *
     * @param memory      the archived memory
     * @param actorType   who or what triggered the archive (typically SYSTEM)
     * @param stateBefore JSON snapshot of state before archiving
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForArchiveWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForArchive(Memory memory, ActorType actorType,
                                                               String stateBefore) {
        return createAuditEntry(memory, EventType.ARCHIVE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory SUPERSEDE event.
     *
     * @param memory      the superseded memory
     * @param actorType   who or what triggered the supersession
     * @param stateBefore JSON snapshot of state before supersession
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForSupersedeWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForSupersede(Memory memory, ActorType actorType,
                                                                  String stateBefore) {
        return createAuditEntry(memory, EventType.SUPERSEDE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory EXPIRE event.
     *
     * @param memory      the expired memory
     * @param stateBefore JSON snapshot of state before expiration
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForExpireWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForExpire(Memory memory, String stateBefore) {
        return createAuditEntry(memory, EventType.EXPIRE, ActorType.SYSTEM, stateBefore);
    }

    /**
     * Creates an audit entry for a memory DELETE event.
     *
     * @param memory      the deleted memory
     * @param actorType   who or what triggered the deletion (USER for request, SYSTEM for cleanup)
     * @param stateBefore JSON snapshot of state before deletion
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForDeleteWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForDelete(Memory memory, ActorType actorType,
                                                              String stateBefore) {
        return createAuditEntry(memory, EventType.DELETE, actorType, stateBefore);
    }

    /**
     * Creates an audit entry for a memory REACTIVATE event.
     *
     * <p>Reactivation occurs when an ARCHIVED or EXPIRED memory is re-referenced
     * by the father and returned to ACTIVE state.
     *
     * @param memory      the reactivated memory
     * @param actorType   who or what triggered the reactivation (USER or SYSTEM)
     * @param stateBefore JSON snapshot of state before reactivation
     * @return the created audit entry, or empty if audit could not be created
     * @deprecated Use {@link #createAuditEntryForReactivateWithFullTracking} for full state tracking
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<MemoryAuditLog> createAuditEntryForReactivate(Memory memory, ActorType actorType,
                                                                   String stateBefore) {
        return createAuditEntry(memory, EventType.REACTIVATE, actorType, stateBefore);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Serialization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Serializes key memory fields to a JSON string for audit snapshots.
     *
     * <p>The snapshot includes:
     * <ul>
     *   <li>content - the memory text</li>
     *   <li>category - the memory category</li>
     *   <li>state - the lifecycle state (for from_state/to_state extraction)</li>
     *   <li>importance_score - importance ranking (1-10)</li>
     *   <li>confidence_score - certainty level (0.0-1.0)</li>
     *   <li>subject_type - who the memory is about</li>
     *   <li>child_id - optional child reference</li>
     *   <li>superseded_by - reference to superseding memory (if any)</li>
     * </ul>
     *
     * @param memory the memory to serialize
     * @return JSON string representation of key fields
     */
    public String serializeMemoryState(Memory memory) {
        if (memory == null) {
            return null;
        }

        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("content", memory.getContent());
            node.put("category", memory.getCategory() != null ? memory.getCategory().name() : null);
            node.put("state", memory.getState() != null ? memory.getState().name() : null);
            node.put("importance_score", memory.getImportanceScore());
            node.put("confidence_score", memory.getConfidenceScore() != null 
                    ? memory.getConfidenceScore().doubleValue() : null);
            node.put("subject_type", memory.getSubjectType() != null 
                    ? memory.getSubjectType().name() : null);
            node.put("child_id", memory.getChildId() != null 
                    ? memory.getChildId().toString() : null);
            node.put("superseded_by", memory.getSupersededBy() != null 
                    ? memory.getSupersededBy().toString() : null);

            return objectMapper.writeValueAsString(node);

        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize memory state. memoryId={}, error={}", 
                    memory.getId(), e.getMessage());
            // Return a minimal fallback representation
            return String.format("{\"memory_id\":\"%s\",\"error\":\"serialization_failed\"}", 
                    memory.getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retrieves all audit entries for a specific memory.
     *
     * @param memoryId the memory's ID
     * @return list of audit entries ordered by creation time ascending (chronological)
     */
    public List<MemoryAuditLog> getAuditHistoryForMemory(UUID memoryId) {
        if (auditRepository == null) {
            return List.of();
        }
        return auditRepository.findByMemoryIdOrderByCreatedAtAsc(memoryId);
    }

    /**
     * Retrieves all audit entries for a father.
     *
     * @param fatherId the father's ID
     * @return list of audit entries ordered by creation time descending (most recent first)
     */
    public List<MemoryAuditLog> getAuditHistoryForFather(UUID fatherId) {
        if (auditRepository == null) {
            return List.of();
        }
        return auditRepository.findByFatherIdOrderByCreatedAtDesc(fatherId);
    }

    /**
     * Retrieves audit entries for a father within a specified time range.
     *
     * <p>This method supports compliance and troubleshooting by allowing queries
     * that filter audit entries by both father_id and time range. The results
     * are ordered by creation time descending (most recent first).
     *
     * <p><strong>Use cases:</strong>
     * <ul>
     *   <li>Compliance audits: "Show all memory changes for this user in the last 30 days"</li>
     *   <li>Troubleshooting: "What happened to this father's memories on a specific date?"</li>
     *   <li>GDPR requests: "Show all data processing activities for this user in the requested period"</li>
     * </ul>
     *
     * @param fatherId  the father's ID
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (exclusive)
     * @return list of audit entries for the father within the time range, ordered by creation time descending
     * @throws IllegalArgumentException if fatherId is null or startTime is after endTime
     */
    public List<MemoryAuditLog> getAuditHistoryForFatherInTimeRange(UUID fatherId, 
                                                                     java.time.Instant startTime, 
                                                                     java.time.Instant endTime) {
        if (auditRepository == null) {
            log.debug("AuditRepository not available, returning empty list");
            return List.of();
        }
        
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId cannot be null");
        }
        
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime cannot be null");
        }
        
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        
        log.debug("Querying audit history for fatherId={} from {} to {}", fatherId, startTime, endTime);
        return auditRepository.findByFatherIdAndTimeRange(fatherId, startTime, endTime);
    }

    /**
     * Retrieves all audit entries within a specified time range (system-wide).
     *
     * <p>This method supports system-wide compliance queries and troubleshooting
     * by returning all audit entries created within the specified time range,
     * regardless of father_id. Results are ordered by creation time descending.
     *
     * <p><strong>Use cases:</strong>
     * <ul>
     *   <li>System monitoring: "Show all memory operations in the last hour"</li>
     *   <li>Incident investigation: "What happened during the outage window?"</li>
     *   <li>Compliance reporting: "Generate activity report for the audit period"</li>
     * </ul>
     *
     * <p><strong>Note:</strong> This is a potentially expensive query. For large
     * time ranges, consider using pagination or narrowing the scope.
     *
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (exclusive)
     * @return list of audit entries within the time range, ordered by creation time descending
     * @throws IllegalArgumentException if startTime is after endTime
     */
    public List<MemoryAuditLog> getAuditHistoryInTimeRange(java.time.Instant startTime, 
                                                            java.time.Instant endTime) {
        if (auditRepository == null) {
            log.debug("AuditRepository not available, returning empty list");
            return List.of();
        }
        
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime cannot be null");
        }
        
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        
        log.debug("Querying system-wide audit history from {} to {}", startTime, endTime);
        return auditRepository.findByTimeRange(startTime, endTime);
    }

    /**
     * Counts the number of audit entries for a memory.
     *
     * @param memoryId the memory's ID
     * @return count of audit entries
     */
    public long countAuditEntriesForMemory(UUID memoryId) {
        if (auditRepository == null) {
            return 0;
        }
        return auditRepository.countByMemoryId(memoryId);
    }

    /**
     * Counts the number of audit entries for a father.
     *
     * @param fatherId the father's ID
     * @return count of audit entries for the father
     */
    public long countAuditEntriesForFather(UUID fatherId) {
        if (auditRepository == null) {
            return 0;
        }
        if (fatherId == null) {
            return 0;
        }
        return auditRepository.countByFatherId(fatherId);
    }
}
