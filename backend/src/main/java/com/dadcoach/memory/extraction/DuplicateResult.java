package com.dadcoach.memory.extraction;

import com.dadcoach.memory.Memory;

import java.util.Optional;
import java.util.UUID;

/**
 * Result of duplicate detection for a potential new memory.
 *
 * <p>From SPEC-004 Requirement 9 (Duplicate Detection):
 * <ul>
 *   <li>Cosine similarity > 0.85 → DUPLICATE (reject creation, update existing confidence)</li>
 *   <li>Cosine similarity 0.70-0.85 → POTENTIAL_UPDATE (consider supersession)</li>
 *   <li>Cosine similarity < 0.70 → DISTINCT (allow creation)</li>
 * </ul>
 *
 * @see DuplicateDetector
 */
public sealed interface DuplicateResult {

    /**
     * Similarity threshold for considering a memory as a duplicate (> 0.85).
     */
    double DUPLICATE_THRESHOLD = 0.85;

    /**
     * Minimum similarity threshold for considering a memory as a potential update (>= 0.70).
     */
    double POTENTIAL_UPDATE_THRESHOLD = 0.70;

    /**
     * Returns the duplicate status classification.
     *
     * @return the status indicating whether this is a duplicate, potential update, or distinct
     */
    DuplicateStatus status();

    /**
     * Returns the ID of the most similar existing memory, if any.
     *
     * @return optional containing the similar memory ID, or empty if distinct
     */
    Optional<UUID> existingMemoryId();

    /**
     * Returns the similarity score with the most similar existing memory.
     *
     * @return the cosine similarity score, or 0.0 if distinct with no similar memories found
     */
    double similarity();

    // ─── Status Enum ─────────────────────────────────────────────────────

    /**
     * Enumeration of duplicate detection statuses.
     */
    enum DuplicateStatus {
        /**
         * Cosine similarity > 0.85: Memory is semantically duplicate.
         * Action: Reject creation, update existing memory's confidence instead.
         */
        DUPLICATE,

        /**
         * Cosine similarity 0.70-0.85: Memory may be an update to existing information.
         * Action: Consider superseding the existing memory.
         */
        POTENTIAL_UPDATE,

        /**
         * Cosine similarity < 0.70: Memory is semantically distinct.
         * Action: Allow creation as a new memory.
         */
        DISTINCT
    }

    // ─── Record Implementations ──────────────────────────────────────────

    /**
     * Result indicating the content is a duplicate of an existing memory.
     * The caller should update the existing memory's confidence instead of creating a new one.
     *
     * @param matchedMemoryId the ID of the existing duplicate memory
     * @param similarity      the cosine similarity score (> 0.85)
     */
    record Duplicate(UUID matchedMemoryId, double similarity) implements DuplicateResult {
        
        public Duplicate {
            if (matchedMemoryId == null) {
                throw new IllegalArgumentException("existingMemoryId cannot be null for DUPLICATE result");
            }
            if (similarity <= DUPLICATE_THRESHOLD) {
                throw new IllegalArgumentException(
                        "Similarity must be > " + DUPLICATE_THRESHOLD + " for DUPLICATE, was: " + similarity);
            }
        }

        @Override
        public DuplicateStatus status() {
            return DuplicateStatus.DUPLICATE;
        }

        @Override
        public Optional<UUID> existingMemoryId() {
            return Optional.of(matchedMemoryId);
        }
    }

    /**
     * Result indicating the content may be an update to an existing memory.
     * The caller should consider superseding the existing memory.
     *
     * @param matchedMemoryId the ID of the potentially related existing memory
     * @param similarity      the cosine similarity score (0.70-0.85)
     */
    record PotentialUpdate(UUID matchedMemoryId, double similarity) implements DuplicateResult {
        
        public PotentialUpdate {
            if (matchedMemoryId == null) {
                throw new IllegalArgumentException("existingMemoryId cannot be null for POTENTIAL_UPDATE result");
            }
            if (similarity > DUPLICATE_THRESHOLD || similarity < POTENTIAL_UPDATE_THRESHOLD) {
                throw new IllegalArgumentException(
                        "Similarity must be between " + POTENTIAL_UPDATE_THRESHOLD + " and " + DUPLICATE_THRESHOLD + 
                        " for POTENTIAL_UPDATE, was: " + similarity);
            }
        }

        @Override
        public DuplicateStatus status() {
            return DuplicateStatus.POTENTIAL_UPDATE;
        }

        @Override
        public Optional<UUID> existingMemoryId() {
            return Optional.of(matchedMemoryId);
        }
    }

    /**
     * Result indicating the content is distinct from existing memories.
     * The caller should proceed with creating the new memory.
     */
    record Distinct() implements DuplicateResult {
        
        @Override
        public DuplicateStatus status() {
            return DuplicateStatus.DISTINCT;
        }

        @Override
        public Optional<UUID> existingMemoryId() {
            return Optional.empty();
        }

        @Override
        public double similarity() {
            return 0.0;
        }
    }

    // ─── Factory Methods ─────────────────────────────────────────────────

    /**
     * Creates a result based on the similarity score and existing memory.
     *
     * @param existingMemoryId the ID of the most similar existing memory, or null if none found
     * @param similarity       the cosine similarity score
     * @return the appropriate DuplicateResult based on the similarity threshold
     */
    static DuplicateResult of(UUID existingMemoryId, double similarity) {
        if (existingMemoryId == null || similarity < POTENTIAL_UPDATE_THRESHOLD) {
            return new Distinct();
        }
        if (similarity > DUPLICATE_THRESHOLD) {
            return new Duplicate(existingMemoryId, similarity);
        }
        return new PotentialUpdate(existingMemoryId, similarity);
    }

    /**
     * Creates a DISTINCT result indicating no similar memories were found.
     *
     * @return a Distinct result
     */
    static DuplicateResult distinct() {
        return new Distinct();
    }
}
