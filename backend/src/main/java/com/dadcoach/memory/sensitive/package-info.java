/**
 * Sensitive memory subsystem for safety event recording.
 *
 * <p>This package provides components for recording and managing safety events
 * separately from normal memories. Safety events are retained for legal/compliance
 * reasons and are NEVER deleted during GDPR erasure.
 *
 * <h2>ARCHITECTURAL DECISION: Complete Isolation from Normal Memories</h2>
 *
 * <p><b>Safety events are architecturally isolated from normal memories to ensure:</b>
 * <ol>
 *   <li><b>Data Isolation:</b> Safety events are stored in the separate {@code safety_event_records}
 *       table, not the {@code memories} table used for coaching context.</li>
 *   <li><b>Repository Independence:</b> {@link com.dadcoach.memory.sensitive.SafetyEventRepository}
 *       extends {@code JpaRepository} directly and has NO relationship with
 *       {@link com.dadcoach.memory.MemoryRepository}.</li>
 *   <li><b>Retrieval Isolation:</b> Safety events are NEVER returned by memory retrieval APIs.
 *       {@link com.dadcoach.memory.retrieval.MemoryRetriever} only queries the {@code memories}
 *       table and returns {@link com.dadcoach.memory.dto.RetrievalResultDto} containing
 *       {@link com.dadcoach.memory.dto.MemoryDto}, never {@link SafetyEventRecord}.</li>
 *   <li><b>Prompt Safety:</b> This isolation ensures safety events are never accidentally
 *       injected into AI coaching prompts.</li>
 *   <li><b>Different Lifecycle:</b> Safety events have a 7-year retention policy and are
 *       NOT subject to GDPR deletion, unlike regular memories.</li>
 * </ol>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventRecord} - JPA entity for safety events
 *       (maps to {@code safety_event_records} table)</li>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventService} - Service for recording and managing events
 *       (does NOT inject MemoryRepository)</li>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventRepository} - Repository for persistence
 *       (does NOT extend MemoryRepository)</li>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventRetentionService} - Manages retention enforcement</li>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventType} - Types of safety events</li>
 *   <li>{@link com.dadcoach.memory.sensitive.SafetyEventSeverity} - Severity levels</li>
 * </ul>
 *
 * <h2>Requirements Traceability</h2>
 * <ul>
 *   <li><b>SPEC-004 Requirement 24:</b> Safety-related events need their own separate table
 *       with long retention for legal/compliance reasons.</li>
 *   <li><b>SPEC-004 Task 12.5:</b> Safety events must be stored in a separate table and
 *       NEVER mixed into normal memory retrieval.</li>
 * </ul>
 *
 * @see com.dadcoach.memory.sensitive.SafetyEventRecord
 * @see com.dadcoach.memory.sensitive.SafetyEventService
 * @see com.dadcoach.memory.sensitive.SafetyEventRepository
 */
package com.dadcoach.memory.sensitive;
