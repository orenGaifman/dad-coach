package com.dadcoach.workflow.state;

import com.dadcoach.systemstate.SystemState;
import com.dadcoach.workflow.WorkflowState;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable context for workflow processing.
 * 
 * <p>Contains all the information needed to process a workflow action within
 * a specific state, including the complete system state (Read Before Write principle),
 * father identification, current workflow state, and the inbound message being processed.</p>
 * 
 * <p>This class is designed for use in the deterministic workflow engine where
 * state handlers need access to:</p>
 * <ul>
 *   <li>{@link SystemState} - Complete loaded state including father profile, calendar events,
 *       Quality Time records, dashboard metrics, and conversation context</li>
 *   <li>Father ID - UUID of the father being processed</li>
 *   <li>Current workflow state - The state from which processing begins</li>
 *   <li>Inbound message content - The text message being processed</li>
 * </ul>
 * 
 * <p>Use the {@link Builder} pattern to construct instances:</p>
 * <pre>{@code
 * WorkflowContext context = WorkflowContext.builder()
 *     .systemState(systemState)
 *     .fatherId(fatherId)
 *     .currentState(WorkflowState.WELCOME)
 *     .inboundMessage("yes")
 *     .build();
 * }</pre>
 * 
 * <p>Implements Requirement 11.1 from the deterministic-workflow-engine spec.</p>
 * 
 * @see StateHandler
 * @see StateAction
 * @see SystemState
 */
public final class WorkflowContext {
    
    private final SystemState systemState;
    private final UUID fatherId;
    private final WorkflowState currentState;
    private final String inboundMessage;
    
    /**
     * Private constructor for builder pattern - use {@link Builder} to create instances.
     * 
     * @param builder the builder containing the context data
     */
    private WorkflowContext(Builder builder) {
        this.systemState = builder.systemState;
        this.fatherId = Objects.requireNonNull(builder.fatherId, "fatherId must not be null");
        this.currentState = Objects.requireNonNull(builder.currentState, "currentState must not be null");
        this.inboundMessage = builder.inboundMessage;
    }
    
    /**
     * Creates a new workflow context with minimal fields.
     * 
     * <p>This constructor is provided for backward compatibility and simple use cases.
     * For full functionality with system state, use the {@link Builder} pattern.</p>
     * 
     * @param fatherId the ID of the father being processed
     * @param currentState the current workflow state
     * @param inboundMessage the inbound message text
     * @throws NullPointerException if fatherId or currentState is null
     */
    public WorkflowContext(UUID fatherId, WorkflowState currentState, String inboundMessage) {
        this.systemState = null;
        this.fatherId = Objects.requireNonNull(fatherId, "fatherId must not be null");
        this.currentState = Objects.requireNonNull(currentState, "currentState must not be null");
        this.inboundMessage = inboundMessage;
    }
    
    /**
     * Creates a new builder for constructing WorkflowContext instances.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns the complete system state loaded via Read Before Write principle.
     * 
     * <p>The system state includes:</p>
     * <ul>
     *   <li>Father profile (name, children, preferences, locale, timezone)</li>
     *   <li>Current workflow state</li>
     *   <li>Google Calendar events for the next 7 days</li>
     *   <li>Scheduled Quality Time events</li>
     *   <li>Dashboard metrics (belt, streak, achievements)</li>
     *   <li>Recent conversation context</li>
     * </ul>
     * 
     * @return the system state, or null if not loaded
     */
    public SystemState getSystemState() {
        return systemState;
    }
    
    /**
     * Returns the father ID.
     * 
     * @return the UUID of the father being processed
     */
    public UUID getFatherId() {
        return fatherId;
    }
    
    /**
     * Returns the current workflow state.
     * 
     * @return the current workflow state
     */
    public WorkflowState getCurrentState() {
        return currentState;
    }
    
    /**
     * Returns the inbound message text.
     * 
     * @return the inbound message content, or null if not a message-triggered context
     */
    public String getInboundMessage() {
        return inboundMessage;
    }
    
    /**
     * Checks if system state has been loaded.
     * 
     * @return true if system state is available
     */
    public boolean hasSystemState() {
        return systemState != null;
    }
    
    /**
     * Checks if an inbound message is present.
     * 
     * @return true if an inbound message is available
     */
    public boolean hasInboundMessage() {
        return inboundMessage != null && !inboundMessage.isEmpty();
    }
    
    @Override
    public String toString() {
        return "WorkflowContext{" +
                "fatherId=" + fatherId +
                ", currentState=" + currentState +
                ", hasSystemState=" + (systemState != null) +
                ", hasInboundMessage=" + (inboundMessage != null) +
                '}';
    }
    
    /**
     * Builder class for constructing immutable {@link WorkflowContext} instances.
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * WorkflowContext context = WorkflowContext.builder()
     *     .systemState(loadedState)
     *     .fatherId(fatherId)
     *     .currentState(WorkflowState.SCHEDULE_QUALITY_TIME)
     *     .inboundMessage("1")
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        
        private SystemState systemState;
        private UUID fatherId;
        private WorkflowState currentState;
        private String inboundMessage;
        
        /**
         * Private constructor - use {@link WorkflowContext#builder()}.
         */
        private Builder() {
        }
        
        /**
         * Sets the system state loaded via Read Before Write principle.
         * 
         * @param systemState the complete system state
         * @return this builder for method chaining
         */
        public Builder systemState(SystemState systemState) {
            this.systemState = systemState;
            return this;
        }
        
        /**
         * Sets the father ID.
         * 
         * @param fatherId the UUID of the father being processed
         * @return this builder for method chaining
         */
        public Builder fatherId(UUID fatherId) {
            this.fatherId = fatherId;
            return this;
        }
        
        /**
         * Sets the current workflow state.
         * 
         * @param currentState the current workflow state
         * @return this builder for method chaining
         */
        public Builder currentState(WorkflowState currentState) {
            this.currentState = currentState;
            return this;
        }
        
        /**
         * Sets the inbound message text.
         * 
         * @param inboundMessage the inbound message content
         * @return this builder for method chaining
         */
        public Builder inboundMessage(String inboundMessage) {
            this.inboundMessage = inboundMessage;
            return this;
        }
        
        /**
         * Builds an immutable {@link WorkflowContext} instance.
         * 
         * @return a new WorkflowContext
         * @throws NullPointerException if fatherId or currentState is null
         */
        public WorkflowContext build() {
            return new WorkflowContext(this);
        }
    }
}
