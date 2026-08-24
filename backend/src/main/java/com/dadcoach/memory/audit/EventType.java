package com.dadcoach.memory.audit;

/**
 * Represents the type of memory lifecycle event being audited.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * Every memory lifecycle event SHALL produce a durable audit record.
 * The audit log supports events including create, update, archive, confirm,
 * and expire, among others.
 *
 * @see MemoryAuditLog
 * @see MemoryAuditService
 */
public enum EventType {

    /**
     * Memory was created.
     * Triggered by extraction, onboarding, or manual input.
     */
    CREATE,

    /**
     * Memory was updated (content, importance, or confidence changed).
     */
    UPDATE,

    /**
     * Memory was confirmed by the father.
     * Confidence boosted to max(current, 0.9), decay timer reset.
     */
    CONFIRM,

    /**
     * Memory was archived (excluded from active retrieval).
     * Typically due to capacity enforcement or manual archive.
     */
    ARCHIVE,

    /**
     * Memory was superseded by a newer memory.
     * Original memory preserved in version history.
     */
    SUPERSEDE,

    /**
     * Memory expired due to low confidence and lack of access.
     * Preserved for 30 days before automatic deletion.
     */
    EXPIRE,

    /**
     * Memory was marked for deletion.
     * Content erasure happens within 72 hours.
     */
    DELETE,

    /**
     * Memory content was erased (72-hour post-deletion content erasure).
     * Content, embedding, and version history snapshots are nullified.
     * Only audit metadata is retained per SPEC-004 Requirement 2 Criteria 7.
     */
    ERASE,

    /**
     * Memory was reactivated from ARCHIVED or EXPIRED state.
     * Returned to ACTIVE state with reset expiration timer.
     */
    REACTIVATE,

    /**
     * Memory was injected into a conversation prompt.
     * This is the primary "meaningful use" event that resets decay timer.
     * Per SPEC-004 Req 6 Criteria 2.
     */
    INJECTION,

    /**
     * Memory was referenced during a conversation.
     * The AI mentioned or acknowledged the memory in its response.
     */
    REFERENCE
}
