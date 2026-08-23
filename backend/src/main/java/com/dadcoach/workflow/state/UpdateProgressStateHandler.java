package com.dadcoach.workflow.state;

import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Handler for the UPDATE_PROGRESS workflow state.
 * 
 * <p>The UPDATE_PROGRESS state is an internal, typically silent transition state
 * that handles updating progress metrics after a Quality Time follow-up:</p>
 * <ul>
 *   <li>Updates the WeeklyGoal with completed minutes</li>
 *   <li>Checks for belt promotion</li>
 *   <li>Updates streak count if applicable</li>
 * </ul>
 * 
 * <p>This state is designed to be a brief internal processing state. In most cases,
 * no message is sent to the user. The exception is belt promotion, where a
 * celebration message is generated.</p>
 * 
 * <p>This state should not normally receive user messages - it's entered and
 * exited automatically by the system. If a user message is received while in
 * this state (edge case), it acknowledges and transitions out.</p>
 * 
 * <p>Implements the workflow architecture analysis recommendations for
 * separating progress update logic into its own state.</p>
 */
@Component
public class UpdateProgressStateHandler implements StateHandler {
    
    private static final Logger log = LoggerFactory.getLogger(UpdateProgressStateHandler.class);
    
    /** Default timeout for message generation in milliseconds. */
    private static final long MESSAGE_TIMEOUT_MS = 5000L;
    
    private final SystemStateLoader systemStateLoader;
    private final MessageGenerator messageGenerator;
    private final WeeklyGoalService weeklyGoalService;
    
    /**
     * Creates a new UpdateProgressStateHandler with required dependencies.
     * 
     * @param systemStateLoader loader for system state (Read Before Write)
     * @param messageGenerator generator for response messages
     * @param weeklyGoalService service for weekly goal operations
     */
    public UpdateProgressStateHandler(
            SystemStateLoader systemStateLoader,
            MessageGenerator messageGenerator,
            WeeklyGoalService weeklyGoalService) {
        this.systemStateLoader = systemStateLoader;
        this.messageGenerator = messageGenerator;
        this.weeklyGoalService = weeklyGoalService;
    }
    
    @Override
    public WorkflowState getState() {
        return WorkflowState.UPDATE_PROGRESS;
    }
    
    @Override
    public List<StatePattern> getExpectedPatterns() {
        // This state doesn't expect user input - it's an internal processing state
        return Collections.emptyList();
    }
    
    @Override
    public StateAction handle(WorkflowContext context, PatternResult match) {
        // This state shouldn't normally receive matched patterns
        // Delegate to handleUnmatched which processes any message
        return handleUnmatched(context);
    }
    
    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        
        log.debug("Processing UPDATE_PROGRESS state for father: {}", context.getFatherId());
        
        // Load system state to check for belt promotion
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get current metrics
        SystemState.DashboardMetrics metrics = state.dashboardMetrics();
        Belt currentBelt = metrics.currentBelt();
        int currentStreak = metrics.currentStreak();
        
        // Check if weekly goal is met (could transition to WAITING instead of SCHEDULE_QUALITY_TIME)
        boolean weeklyGoalMet = isWeeklyGoalMet(state);
        
        // Determine next state
        WorkflowState nextState = weeklyGoalMet ? 
                WorkflowState.WAITING : 
                WorkflowState.SCHEDULE_QUALITY_TIME;
        
        // For normal transitions, this is silent (no message)
        // But if a user somehow sends a message while in this state, acknowledge and move on
        if (context.getInboundMessage() != null && !context.getInboundMessage().isBlank()) {
            // User sent a message - acknowledge and transition
            String acknowledgment = buildAcknowledgmentMessage(locale);
            return StateAction.transition(nextState, acknowledgment);
        }
        
        // Normal silent transition - return action with empty message
        // The WorkflowEngine should check WorkflowState.isSilentTransition() 
        // and not send a message
        log.info("UPDATE_PROGRESS completing silently for father: {}, next state: {}", 
                context.getFatherId(), nextState);
        
        return StateAction.transition(nextState, "");
    }
    
    /**
     * Checks if the weekly goal has been met for this week.
     * 
     * @param state the current system state
     * @return true if the weekly goal is met
     */
    private boolean isWeeklyGoalMet(SystemState state) {
        SystemState.WeeklyGoalInfo weeklyGoal = state.weeklyGoalInfo();
        if (weeklyGoal == null || !weeklyGoal.hasGoal()) {
            return false;
        }
        
        int targetQualityTimes = weeklyGoal.targetQualityTimes();
        int completedQualityTimes = weeklyGoal.completedQualityTimes();
        
        return completedQualityTimes >= targetQualityTimes;
    }
    
    /**
     * Builds an acknowledgment message for the rare case when a user sends
     * a message while in UPDATE_PROGRESS state.
     * 
     * @param locale the father's locale ("en" or "he")
     * @return acknowledgment message
     */
    private String buildAcknowledgmentMessage(String locale) {
        if ("he".equals(locale)) {
            return "מעבד את ההתקדמות שלך... 🔄\n\n" +
                   "רגע אחד ונמשיך!";
        } else {
            return "Processing your progress... 🔄\n\n" +
                   "Just a moment and we'll continue!";
        }
    }
    
    /**
     * Process belt promotion and generate celebration message if applicable.
     * This method can be called by the WorkflowEngine during state transition.
     * 
     * @param fatherId the father's UUID
     * @param previousBelt the belt before the update
     * @param newBelt the belt after the update
     * @return celebration message if belt was promoted, empty string otherwise
     */
    public String generateBeltPromotionMessage(java.util.UUID fatherId, Belt previousBelt, Belt newBelt) {
        if (newBelt == previousBelt || newBelt.ordinal() <= previousBelt.ordinal()) {
            return "";
        }
        
        // Belt promotion occurred!
        log.info("Belt promotion for father {}: {} -> {}", fatherId, previousBelt, newBelt);
        
        SystemState state = systemStateLoader.loadState(fatherId);
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.BELT_PROMOTION)
                .fatherName(fatherName)
                .currentBelt(newBelt)
                .beltEarned(newBelt)
                .locale(locale)
                .timezone(state.fatherProfile().timezone())
                .build();
        
        return messageGenerator.generateWithFallback(
                MessageType.BELT_PROMOTION,
                messageContext,
                MESSAGE_TIMEOUT_MS
        );
    }
}
