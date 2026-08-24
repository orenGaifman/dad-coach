package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.Memory;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Record representing a conflict resolution decision for a conflict group.
 *
 * <p>From SPEC-004 Requirement 7 criteria 5, conflict groups are resolved during
 * the weekly consolidation job based on:
 * <ul>
 *   <li>If one memory has been accessed more recently (within 14 days) and the other has not:
 *       supersede the unaccessed memory</li>
 *   <li>If both have been accessed recently: retain both until father provides clarification</li>
 *   <li>If neither has been accessed in 30+ days: expire the lower-confidence memory</li>
 * </ul>
 *
 * @param conflictGroupId   the ID of the conflict group being resolved
 * @param action            the resolution action to take
 * @param winningMemory     the memory that "wins" the conflict (for SUPERSEDE, CLEAR_GROUP)
 * @param affectedMemories  memories that will be modified (superseded, expired)
 * @param reason            human-readable explanation of the resolution decision
 */
public record ConflictResolution(
        UUID conflictGroupId,
        ResolutionAction action,
        Memory winningMemory,
        List<Memory> affectedMemories,
        String reason
) {

    /**
     * Actions that can be taken to resolve a conflict group.
     */
    public enum ResolutionAction {
        /**
         * Supersede one or more memories with the winning memory.
         */
        SUPERSEDE,

        /**
         * Expire one or more memories (typically lowest confidence).
         */
        EXPIRE,

        /**
         * Clear the conflict group (single memory remaining or already resolved).
         */
        CLEAR_GROUP,

        /**
         * Wait for father clarification (both memories recently accessed).
         */
        WAIT_FOR_CLARIFICATION,

        /**
         * No action needed (empty group or special case).
         */
        NO_ACTION
    }

    /**
     * Creates a ConflictResolution with validation.
     */
    public ConflictResolution {
        if (conflictGroupId == null) {
            throw new IllegalArgumentException("conflictGroupId cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be null or blank");
        }
        if (affectedMemories == null) {
            affectedMemories = Collections.emptyList();
        }
    }

    // ─── Factory Methods ─────────────────────────────────────────────────

    /**
     * Creates a resolution to supersede memories with a winning memory.
     *
     * @param conflictGroupId   the conflict group ID
     * @param winningMemory     the memory that wins the conflict
     * @param toSupersede       memories to be superseded
     * @param reason            explanation for the decision
     * @return the resolution
     */
    public static ConflictResolution supersedeMemories(
            UUID conflictGroupId,
            Memory winningMemory,
            List<Memory> toSupersede,
            String reason) {
        return new ConflictResolution(
                conflictGroupId,
                ResolutionAction.SUPERSEDE,
                winningMemory,
                toSupersede,
                reason
        );
    }

    /**
     * Creates a resolution to expire memories.
     *
     * @param conflictGroupId  the conflict group ID
     * @param toExpire         memories to be expired
     * @param reason           explanation for the decision
     * @return the resolution
     */
    public static ConflictResolution expireMemories(
            UUID conflictGroupId,
            List<Memory> toExpire,
            String reason) {
        return new ConflictResolution(
                conflictGroupId,
                ResolutionAction.EXPIRE,
                null,
                toExpire,
                reason
        );
    }

    /**
     * Creates a resolution to clear a conflict group (single memory or already resolved).
     *
     * @param conflictGroupId  the conflict group ID
     * @param remainingMemory  the remaining memory to clear the group from
     * @param reason           explanation for the decision
     * @return the resolution
     */
    public static ConflictResolution clearConflictGroup(
            UUID conflictGroupId,
            Memory remainingMemory,
            String reason) {
        return new ConflictResolution(
                conflictGroupId,
                ResolutionAction.CLEAR_GROUP,
                remainingMemory,
                Collections.emptyList(),
                reason
        );
    }

    /**
     * Creates a resolution to wait for father clarification.
     *
     * @param conflictGroupId  the conflict group ID
     * @param memories         the conflicting memories to retain
     * @param reason           explanation for the decision
     * @return the resolution
     */
    public static ConflictResolution waitForClarification(
            UUID conflictGroupId,
            List<Memory> memories,
            String reason) {
        return new ConflictResolution(
                conflictGroupId,
                ResolutionAction.WAIT_FOR_CLARIFICATION,
                null,
                memories,
                reason
        );
    }

    /**
     * Creates a resolution indicating no action is needed.
     *
     * @param conflictGroupId  the conflict group ID
     * @param reason           explanation for why no action is needed
     * @return the resolution
     */
    public static ConflictResolution noAction(UUID conflictGroupId, String reason) {
        return new ConflictResolution(
                conflictGroupId,
                ResolutionAction.NO_ACTION,
                null,
                Collections.emptyList(),
                reason
        );
    }

    // ─── Query Methods ───────────────────────────────────────────────────

    /**
     * Checks if this resolution requires changes to be applied.
     */
    public boolean requiresAction() {
        return action == ResolutionAction.SUPERSEDE ||
               action == ResolutionAction.EXPIRE ||
               action == ResolutionAction.CLEAR_GROUP;
    }

    /**
     * Checks if this resolution is waiting for user input.
     */
    public boolean isWaitingForClarification() {
        return action == ResolutionAction.WAIT_FOR_CLARIFICATION;
    }

    /**
     * Gets the count of memories that will be affected by this resolution.
     */
    public int affectedCount() {
        return affectedMemories.size();
    }
}
