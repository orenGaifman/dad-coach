package com.dadcoach.memory;

/**
 * Categories for memories stored about a father and his family.
 * Used to classify information for retrieval, ranking, and lifecycle behavior.
 *
 * <p>From SPEC-004 Requirement 1:
 * Every memory is classified into exactly one category, determining its
 * default importance scoring, decay behavior, and retrieval priority.
 *
 * <p>Priority order for multi-category content (highest to lowest specificity):
 * IDENTITY > RELATIONSHIP > GOAL > CHALLENGE > MILESTONE > HABIT > EVENT > PREFERENCE > FAMILY > CONTEXT > CONVERSATION_SUMMARY
 */
public enum MemoryCategory {

    /**
     * Factual biographical information about a person not captured by domain entity fields.
     * Typical subjects: Father, Child.
     * Default importance: 9-10.
     * Examples: Father's profession, school name, child's nickname, personality traits.
     */
    IDENTITY,

    /**
     * Dynamics, quality, and patterns between father and child.
     * Typical subjects: Father-Child pair.
     * Default importance: 7-8.
     * Examples: "Lucas responds well to humor", "Sofía needs more one-on-one time".
     */
    RELATIONSHIP,

    /**
     * Likes, dislikes, interests, and aversions.
     * Typical subjects: Father, Child.
     * Default importance: 5-6.
     * Examples: "Lucas loves dinosaurs", "Father prefers morning missions".
     */
    PREFERENCE,

    /**
     * Parenting objectives, aspirations, and progress markers.
     * Typical subjects: Father.
     * Default importance: 7-8.
     * Examples: "Wants to reduce screen time for Lucas", "Working on patience".
     */
    GOAL,

    /**
     * Difficulties, obstacles, and pain points.
     * Typical subjects: Father, Child, Family.
     * Default importance: 6-7.
     * Examples: "Bedtime routine is a struggle", "Lucas has trouble sharing".
     */
    CHALLENGE,

    /**
     * Achievements, breakthroughs, and significant moments.
     * Typical subjects: Father, Child.
     * Default importance: 8-9.
     * Examples: "First time Lucas said 'I love you' unprompted", "30-day streak".
     */
    MILESTONE,

    /**
     * Situational information about current circumstances.
     * Typical subjects: Father, Family.
     * Default importance: 3-4.
     * Examples: "Father is traveling for work this week", "School exams period".
     */
    CONTEXT,

    /**
     * Condensed record of a completed conversation.
     * Typical subjects: Father.
     * Default importance: 3 (fixed).
     * Created only by the system's conversation completion process.
     */
    CONVERSATION_SUMMARY,

    /**
     * Scheduled or recurring significant dates and contextual observations.
     * Typical subjects: Child, Family.
     * Default importance: 6-8.
     * Examples: "Lucas is excited about turning 7 next week", "Family vacation August 1-15".
     */
    EVENT,

    /**
     * Recurring behaviors the father is building or tracking.
     * Typical subjects: Father.
     * Default importance: 5-7.
     * Examples: "Reading together before bed 4 nights/week", "Daily breakfast conversation".
     */
    HABIT,

    /**
     * Family-wide facts, dynamics, and structural information.
     * Typical subjects: Family.
     * Default importance: 6-8.
     * Examples: "Parents share custody 50/50", "Grandmother lives nearby and helps".
     */
    FAMILY
}
