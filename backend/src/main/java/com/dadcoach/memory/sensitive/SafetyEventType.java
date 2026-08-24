package com.dadcoach.memory.sensitive;

/**
 * Types of safety events that can be recorded in the system.
 *
 * <p>From SPEC-004 design, safety events are stored separately from normal memories
 * with long retention for legal/compliance reasons.
 *
 * <p>Safety events should NOT be deleted even during GDPR erasure (retained for legal compliance).
 */
public enum SafetyEventType {

    /**
     * A safety concern was detected in conversation content.
     * Examples: mentions of harm, abuse, dangerous situations.
     */
    SAFETY_CONCERN_DETECTED,

    /**
     * Escalation to human support was triggered.
     * Examples: critical safety issue requiring immediate human review.
     */
    ESCALATION_TRIGGERED,

    /**
     * Safety threshold breached based on accumulated signals.
     */
    THRESHOLD_BREACHED,

    /**
     * User expressed distress or crisis.
     */
    CRISIS_DETECTED,

    /**
     * Potential child safety concern.
     */
    CHILD_SAFETY_CONCERN,

    /**
     * Self-harm indicators detected.
     */
    SELF_HARM_INDICATOR,

    /**
     * Third-party safety concern (e.g., another child, family member).
     */
    THIRD_PARTY_CONCERN
}
