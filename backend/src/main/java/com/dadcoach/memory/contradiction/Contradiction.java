package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.Memory;

import java.util.UUID;

/**
 * Record representing a detected contradiction between two memories.
 *
 * <p>From SPEC-004 Requirement 7, contradictions are detected between memories that:
 * <ul>
 *   <li>Have the same subject (same fatherId, or same childId if about a child)</li>
 *   <li>Have the same or related category</li>
 *   <li>Contain semantically contradictory content</li>
 * </ul>
 *
 * <p>Contradiction indicators include:
 * <ul>
 *   <li>Negation patterns (e.g., "likes X" vs "doesn't like X")</li>
 *   <li>Opposite values (e.g., "bedtime is 7pm" vs "bedtime is 9pm")</li>
 *   <li>Mutually exclusive statements</li>
 * </ul>
 *
 * @param existingMemory   the older memory that may be contradicted
 * @param newMemory        the newer memory that potentially contradicts the existing one
 * @param confidenceScore  confidence score (0.0-1.0) that this is a true contradiction
 * @param contradictionType the type of contradiction detected
 * @param reason           human-readable explanation of why this is flagged as a contradiction
 */
public record Contradiction(
        Memory existingMemory,
        Memory newMemory,
        double confidenceScore,
        ContradictionType contradictionType,
        String reason
) {

    /**
     * Creates a contradiction with validation.
     */
    public Contradiction {
        if (existingMemory == null) {
            throw new IllegalArgumentException("existingMemory cannot be null");
        }
        if (newMemory == null) {
            throw new IllegalArgumentException("newMemory cannot be null");
        }
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be between 0.0 and 1.0");
        }
        if (contradictionType == null) {
            throw new IllegalArgumentException("contradictionType cannot be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be null or blank");
        }
    }

    /**
     * Returns the ID of the existing memory.
     */
    public UUID existingMemoryId() {
        return existingMemory.getId();
    }

    /**
     * Returns the ID of the new memory.
     */
    public UUID newMemoryId() {
        return newMemory.getId();
    }

    /**
     * Checks if this contradiction should trigger automatic supersession.
     * Based on SPEC-004 Req 7 criteria 3: confidence drops below 0.3 → supersede.
     */
    public boolean shouldAutoSupersede() {
        return confidenceScore >= 0.7;
    }

    /**
     * Checks if this contradiction requires manual resolution.
     * IDENTITY category contradictions always require confirmation (Req 7 criteria 6).
     */
    public boolean requiresManualResolution() {
        return existingMemory.getCategory() == com.dadcoach.memory.MemoryCategory.IDENTITY;
    }
}
