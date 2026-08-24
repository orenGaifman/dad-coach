package com.dadcoach.memory.contradiction;

/**
 * Types of contradictions that can be detected between memories.
 *
 * <p>From SPEC-004 Requirement 7, contradictions can be detected through:
 * <ul>
 *   <li>Negation patterns (e.g., "likes X" vs "doesn't like X")</li>
 *   <li>Opposite values (e.g., "bedtime is 7pm" vs "bedtime is 9pm")</li>
 *   <li>Mutually exclusive statements</li>
 * </ul>
 */
public enum ContradictionType {

    /**
     * Negation pattern detected.
     * Example: "Lucas likes broccoli" vs "Lucas doesn't like broccoli"
     */
    NEGATION,

    /**
     * Different values for the same attribute.
     * Example: "Bedtime is 7pm" vs "Bedtime is 9pm"
     */
    DIFFERENT_VALUE,

    /**
     * Mutually exclusive statements.
     * Example: "Lucas is an only child" vs "Lucas has a younger sister"
     */
    MUTUALLY_EXCLUSIVE,

    /**
     * High semantic similarity with conflicting sentiment/meaning.
     * Detected via embedding similarity combined with content analysis.
     */
    SEMANTIC_CONFLICT,

    /**
     * Explicit correction by the father.
     * Triggered by correction language: "actually", "no, it's", "I was wrong", "correction"
     */
    EXPLICIT_CORRECTION
}
