package com.dadcoach.conversation.sideeffect;

/**
 * Enum of side-effect types that can be scheduled via the transactional outbox.
 * <p>
 * Mandatory effects (MEMORY_EXTRACTION, EVENT_PUBLISH) retry indefinitely.
 * Best-effort effects (METRIC_UPDATE, etc.) retry up to 3 times.
 */
public enum SideEffect {

    /** Extract memories from a completed/expired conversation transcript. Mandatory. */
    MEMORY_EXTRACTION(true),

    /** Publish a business event (CONVERSATION_COMPLETED, etc.). Mandatory. */
    EVENT_PUBLISH(true),

    /** Update engagement metrics, coaching streaks, etc. Best-effort. */
    METRIC_UPDATE(false),

    /** Retry AI generation after a fallback was delivered. Best-effort. */
    DEFERRED_AI_REGENERATION(false),

    /** Track which memories were injected into a prompt. Best-effort. */
    MEMORY_INJECTION_TRACKING(false),

    /** Confirm or supersede a memory based on father response. Best-effort. */
    MEMORY_CONFIRMATION(false);

    private final boolean mandatory;

    SideEffect(boolean mandatory) {
        this.mandatory = mandatory;
    }

    /**
     * Mandatory effects have unlimited retries (Integer.MAX_VALUE).
     * Best-effort effects have max 3 retries.
     */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Returns the max retries for this effect type.
     * Mandatory: Integer.MAX_VALUE (effectively unlimited).
     * Best-effort: 3.
     */
    public int getMaxRetries() {
        return mandatory ? Integer.MAX_VALUE : 3;
    }
}
