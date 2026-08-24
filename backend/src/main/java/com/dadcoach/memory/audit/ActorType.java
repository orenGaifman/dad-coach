package com.dadcoach.memory.audit;

/**
 * Represents the type of actor that triggered a memory lifecycle event.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * Every memory lifecycle event SHALL produce a durable audit record containing
 * the actor_type indicating who or what triggered the action.
 *
 * @see MemoryAuditLog
 * @see MemoryAuditService
 */
public enum ActorType {

    /**
     * Action triggered by the AI system (e.g., extraction, consolidation).
     * Used for automatic memory creation from conversation extraction.
     */
    AI,

    /**
     * Action triggered by the father (user) explicitly.
     * Used for corrections, confirmations, and deletion requests.
     */
    USER,

    /**
     * Action triggered by the system automatically.
     * Used for scheduled jobs (decay, expiration, cleanup) and GDPR erasure.
     */
    SYSTEM
}
