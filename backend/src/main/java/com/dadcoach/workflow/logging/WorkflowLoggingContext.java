package com.dadcoach.workflow.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing MDC (Mapped Diagnostic Context) in workflow operations.
 * 
 * <p>This class provides a consistent way to include father_id and other workflow-related
 * context in all log messages. Using MDC ensures that log aggregation systems can
 * filter and search logs by father, making debugging and monitoring easier.</p>
 * 
 * <p><b>MDC Keys:</b></p>
 * <ul>
 *   <li>{@code father_id} - The UUID of the father being processed</li>
 *   <li>{@code workflow_state} - The current workflow state</li>
 *   <li>{@code trigger_type} - The type of trigger (USER_MESSAGE, SCHEDULER, etc.)</li>
 *   <li>{@code message_id} - The ID of the message being processed</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try (var ctx = WorkflowLoggingContext.forFather(fatherId)) {
 *     ctx.setState(WorkflowState.WELCOME);
 *     // All log statements in this block will include father_id and workflow_state
 *     log.info("Processing message");
 * }
 * // MDC is automatically cleared when leaving the try block
 * }</pre>
 * 
 * <p>Implements Requirement 16.6: "ALL logs SHALL include father_id as a field to enable
 * filtering logs for a specific user's journey."</p>
 * 
 * @see org.slf4j.MDC
 */
public class WorkflowLoggingContext implements AutoCloseable {

    /** MDC key for father ID */
    public static final String KEY_FATHER_ID = "father_id";
    
    /** MDC key for workflow state */
    public static final String KEY_WORKFLOW_STATE = "workflow_state";
    
    /** MDC key for trigger type */
    public static final String KEY_TRIGGER_TYPE = "trigger_type";
    
    /** MDC key for message ID */
    public static final String KEY_MESSAGE_ID = "message_id";
    
    /** MDC key for transition from state */
    public static final String KEY_FROM_STATE = "from_state";
    
    /** MDC key for transition to state */
    public static final String KEY_TO_STATE = "to_state";
    
    /** MDC key for trigger reason */
    public static final String KEY_TRIGGER_REASON = "trigger_reason";
    
    /** MDC key for job name (for scheduler jobs) */
    public static final String KEY_JOB_NAME = "job_name";

    private final boolean ownsContext;

    /**
     * Private constructor - use static factory methods.
     * 
     * @param ownsContext whether this context owns the MDC and should clear it on close
     */
    private WorkflowLoggingContext(boolean ownsContext) {
        this.ownsContext = ownsContext;
    }

    /**
     * Creates a new logging context for a father.
     * 
     * <p>Sets the father_id in MDC. The context should be used with try-with-resources
     * to ensure MDC is cleared after use.</p>
     * 
     * @param fatherId the father's UUID
     * @return a new WorkflowLoggingContext
     */
    public static WorkflowLoggingContext forFather(UUID fatherId) {
        MDC.put(KEY_FATHER_ID, fatherId != null ? fatherId.toString() : "unknown");
        return new WorkflowLoggingContext(true);
    }

    /**
     * Creates a new logging context for a father using their Long domain ID.
     * 
     * @param fatherDomainId the father's domain ID (Long)
     * @return a new WorkflowLoggingContext
     */
    public static WorkflowLoggingContext forFather(Long fatherDomainId) {
        if (fatherDomainId != null) {
            // Derive UUID from domain ID (same logic as WorkflowEngineImpl)
            UUID fatherUuid = new UUID(0L, fatherDomainId);
            MDC.put(KEY_FATHER_ID, fatherUuid.toString());
        } else {
            MDC.put(KEY_FATHER_ID, "unknown");
        }
        return new WorkflowLoggingContext(true);
    }

    /**
     * Creates a new logging context for a scheduler job.
     * 
     * @param jobName the name of the scheduler job
     * @return a new WorkflowLoggingContext
     */
    public static WorkflowLoggingContext forJob(String jobName) {
        MDC.put(KEY_JOB_NAME, jobName != null ? jobName : "unknown");
        return new WorkflowLoggingContext(true);
    }

    /**
     * Sets the current workflow state in MDC.
     * 
     * @param state the workflow state
     * @return this context for chaining
     */
    public WorkflowLoggingContext setState(String state) {
        if (state != null) {
            MDC.put(KEY_WORKFLOW_STATE, state);
        }
        return this;
    }

    /**
     * Sets the current workflow state in MDC using the enum.
     * 
     * @param state the workflow state enum
     * @return this context for chaining
     */
    public WorkflowLoggingContext setState(Enum<?> state) {
        if (state != null) {
            MDC.put(KEY_WORKFLOW_STATE, state.name());
        }
        return this;
    }

    /**
     * Sets the trigger type in MDC.
     * 
     * @param triggerType the trigger type
     * @return this context for chaining
     */
    public WorkflowLoggingContext setTriggerType(String triggerType) {
        if (triggerType != null) {
            MDC.put(KEY_TRIGGER_TYPE, triggerType);
        }
        return this;
    }

    /**
     * Sets the message ID in MDC.
     * 
     * @param messageId the message ID
     * @return this context for chaining
     */
    public WorkflowLoggingContext setMessageId(UUID messageId) {
        if (messageId != null) {
            MDC.put(KEY_MESSAGE_ID, messageId.toString());
        }
        return this;
    }

    /**
     * Sets state transition details in MDC for structured logging.
     * 
     * <p>This method is used to log state transitions with all relevant context.
     * Implements Requirement 16.1: Log transitions with from_state, to_state, trigger_reason.</p>
     * 
     * @param fromState the state being transitioned from
     * @param toState the state being transitioned to
     * @param triggerReason the reason for the transition
     * @return this context for chaining
     */
    public WorkflowLoggingContext setTransition(Enum<?> fromState, Enum<?> toState, String triggerReason) {
        if (fromState != null) {
            MDC.put(KEY_FROM_STATE, fromState.name());
        }
        if (toState != null) {
            MDC.put(KEY_TO_STATE, toState.name());
        }
        if (triggerReason != null) {
            MDC.put(KEY_TRIGGER_REASON, triggerReason);
        }
        return this;
    }

    /**
     * Clears transition-specific MDC keys after logging the transition.
     * Call this after logging a state transition to avoid polluting subsequent logs.
     * 
     * @return this context for chaining
     */
    public WorkflowLoggingContext clearTransition() {
        MDC.remove(KEY_FROM_STATE);
        MDC.remove(KEY_TO_STATE);
        MDC.remove(KEY_TRIGGER_REASON);
        return this;
    }

    /**
     * Gets the current father_id from MDC.
     * 
     * @return the father_id or null if not set
     */
    public static String getCurrentFatherId() {
        return MDC.get(KEY_FATHER_ID);
    }

    /**
     * Gets the current workflow_state from MDC.
     * 
     * @return the workflow_state or null if not set
     */
    public static String getCurrentState() {
        return MDC.get(KEY_WORKFLOW_STATE);
    }

    /**
     * Checks if a father context is currently set in MDC.
     * 
     * @return true if father_id is present in MDC
     */
    public static boolean hasFatherContext() {
        String fatherId = MDC.get(KEY_FATHER_ID);
        return fatherId != null && !fatherId.equals("unknown");
    }

    /**
     * Clears all workflow-related MDC keys.
     * Called automatically when using try-with-resources.
     */
    @Override
    public void close() {
        if (ownsContext) {
            MDC.remove(KEY_FATHER_ID);
            MDC.remove(KEY_WORKFLOW_STATE);
            MDC.remove(KEY_TRIGGER_TYPE);
            MDC.remove(KEY_MESSAGE_ID);
            MDC.remove(KEY_FROM_STATE);
            MDC.remove(KEY_TO_STATE);
            MDC.remove(KEY_TRIGGER_REASON);
            MDC.remove(KEY_JOB_NAME);
        }
    }
}
