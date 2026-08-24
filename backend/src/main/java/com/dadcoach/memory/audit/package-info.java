/**
 * Memory audit components for SPEC-004 (Memory & Knowledge System).
 *
 * <p>This package provides audit logging functionality for memory lifecycle events
 * as defined in SPEC-004 Requirement 24 (REQ-24).
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link com.dadcoach.memory.audit.MemoryAuditLog} - JPA entity for audit entries</li>
 *   <li>{@link com.dadcoach.memory.audit.MemoryAuditRepository} - Repository for audit persistence</li>
 *   <li>{@link com.dadcoach.memory.audit.MemoryAuditService} - Service for creating audit entries</li>
 *   <li>{@link com.dadcoach.memory.audit.EventType} - Enum for lifecycle event types</li>
 *   <li>{@link com.dadcoach.memory.audit.ActorType} - Enum for actor types (AI, USER, SYSTEM)</li>
 * </ul>
 *
 * <p>From REQ-24: Every memory lifecycle event (create, update, archive, confirm, expire)
 * SHALL produce a durable audit record containing: event_type, memory_id, father_id,
 * timestamp, actor_type (AI/USER/SYSTEM), before/after state snapshots. The audit trail
 * is separate from operational memory storage and retained independently.
 *
 * @see com.dadcoach.memory.audit.MemoryAuditService
 */
package com.dadcoach.memory.audit;
