package com.dadcoach.memory;

/**
 * Subject type indicating who or what a memory is about.
 *
 * <p>From SPEC-004 Requirement 1:
 * Each memory has a subject type indicating whether it pertains to
 * the father himself, a specific child, or the family as a whole.
 */
public enum MemorySubjectType {

    /**
     * Memory is about the father himself.
     * Examples: Father's profession, preferences, goals, challenges.
     */
    FATHER,

    /**
     * Memory is about a specific child.
     * Examples: Child's interests, personality traits, milestones.
     */
    CHILD,

    /**
     * Memory is about the family as a whole.
     * Examples: Family structure, custody arrangements, family dynamics.
     */
    FAMILY
}
