package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionServiceFactory;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Handler for the QUALITY_TIME_REMINDER workflow state.
 * 
 * <p>The QUALITY_TIME_REMINDER state is entered approximately 1 hour before
 * a scheduled Quality Time begins. This state is designed to:</p>
 * <ul>
 *   <li>Send activity ideas to prepare the father for quality time</li>
 *   <li>Confirm the upcoming quality time details</li>
 *   <li>Allow cancellation if the father can no longer attend</li>
 * </ul>
 * 
 * <p>This is primarily a passive state - the father doesn't need to respond,
 * but can cancel if needed. The state automatically transitions to
 * QUALITY_TIME_FOLLOW_UP when the scheduled end time passes.</p>
 * 
 * <p><b>Mission Abstraction:</b> Uses MissionService through MissionServiceFactory
 * to support future extensibility to other mission types.</p>
 * 
 * <p>Implements the workflow architecture analysis recommendations for
 * distinct pre-QT reminder state handling.</p>
 */
@Component
public class QualityTimeReminderStateHandler implements StateHandler {
    
    private static final Logger log = LoggerFactory.getLogger(QualityTimeReminderStateHandler.class);
    
    /** Default timeout for message generation in milliseconds. */
    private static final long MESSAGE_TIMEOUT_MS = 5000L;
    
    private final MissionServiceFactory missionServiceFactory;
    private final SystemStateLoader systemStateLoader;
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    
    /**
     * Creates a new QualityTimeReminderStateHandler with required dependencies.
     * 
     * @param missionServiceFactory factory for obtaining MissionService
     * @param systemStateLoader loader for system state (Read Before Write)
     * @param messageGenerator generator for response messages
     * @param fatherRepository repository for Father entity operations
     */
    public QualityTimeReminderStateHandler(
            MissionServiceFactory missionServiceFactory,
            SystemStateLoader systemStateLoader,
            MessageGenerator messageGenerator,
            FatherRepository fatherRepository) {
        this.missionServiceFactory = missionServiceFactory;
        this.systemStateLoader = systemStateLoader;
        this.messageGenerator = messageGenerator;
        this.fatherRepository = fatherRepository;
    }
    
    @Override
    public WorkflowState getState() {
        return WorkflowState.QUALITY_TIME_REMINDER;
    }
    
    @Override
    public List<StatePattern> getExpectedPatterns() {
        // In reminder state, father can: cancel, ask for ideas, or acknowledge
        return StatePatterns.REMINDER_PATTERNS;
    }
    
    @Override
    public StateAction handle(WorkflowContext context, PatternResult match) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (match == null) {
            throw new IllegalArgumentException("match must not be null");
        }
        
        WorkflowAction action = match.matchedAction();
        log.debug("Handling QUALITY_TIME_REMINDER action: {} for father: {}", action, context.getFatherId());
        
        return switch (action) {
            case CANCEL_QUALITY_TIME -> handleCancelQualityTime(context);
            case TRANSITION_TO_ACTIVITY_IDEAS -> handleTransitionToActivityIdeas(context);
            case ACKNOWLEDGE_REMINDER -> handleAcknowledgeReminder(context);
            default -> {
                log.warn("Unexpected action {} in QUALITY_TIME_REMINDER state for father: {}", 
                        action, context.getFatherId());
                yield handleUnmatched(context);
            }
        };
    }
    
    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        
        log.debug("Handling unmatched message in QUALITY_TIME_REMINDER state for father: {}", 
                context.getFatherId());
        
        // Load system state for locale
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        
        // In this state, acknowledge any message and remind about the upcoming QT
        String clarificationMessage = buildReminderClarificationMessage(locale);
        
        return StateAction.respond(clarificationMessage);
    }
    
    /**
     * Builds a state-specific clarification message for the QUALITY_TIME_REMINDER state.
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message
     */
    private String buildReminderClarificationMessage(String locale) {
        if ("he".equals(locale)) {
            return "זמן האיכות שלך מתחיל בקרוב! 🎉\n\n" +
                   "אם אתה צריך לבטל, כתוב 'בטל'. אחרת, תהנה מהזמן עם הילד! 💪";
        }
        return "Your Quality Time is starting soon! 🎉\n\n" +
               "If you need to cancel, type 'cancel'. Otherwise, enjoy your time together! 💪";
    }
    
    /**
     * Handles cancellation of the upcoming Quality Time.
     * Cancels the scheduled event and transitions back to WAITING.
     */
    private StateAction handleCancelQualityTime(WorkflowContext context) {
        log.info("Cancelling Quality Time for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get the default MissionService
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Get and cancel the upcoming Mission
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isPresent()) {
            Mission upcoming = upcomingOpt.get();
            missionService.cancel(upcoming.getId());
            log.info("Cancelled Mission {} for father: {}", upcoming.getId(), fatherId);
        }
        
        // Build cancellation message
        String cancelMessage = buildCancellationMessage(locale, fatherName);
        
        // Transition back to WAITING (or could go to SCHEDULE_QUALITY_TIME to reschedule)
        return StateAction.transition(WorkflowState.WAITING, cancelMessage);
    }
    
    /**
     * Handles request for activity ideas while in reminder state.
     * Stores previous state and transitions to ACTIVITY_IDEAS.
     */
    private StateAction handleTransitionToActivityIdeas(WorkflowContext context) {
        log.info("Transitioning to ACTIVITY_IDEAS from QUALITY_TIME_REMINDER for father: {}", 
                context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        
        // Store previous workflow state = QUALITY_TIME_REMINDER on Father entity
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));
        father.setPreviousWorkflowState(WorkflowState.QUALITY_TIME_REMINDER);
        fatherRepository.save(father);
        
        log.debug("Stored previous_workflow_state=QUALITY_TIME_REMINDER for father: {}", fatherId);
        
        // Build intro message for activity ideas
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get first child for context
        SystemState.ChildInfo defaultChild = state.getDefaultChild();
        String childName = defaultChild != null ? defaultChild.name() : null;
        Integer childAge = defaultChild != null ? defaultChild.age() : null;
        
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.ACTIVITY_IDEAS)
                .fatherName(fatherName)
                .childName(childName)
                .childAge(childAge)
                .locale(locale)
                .timezone(state.fatherProfile().timezone())
                .build();
        
        String introMessage = messageGenerator.generateWithFallback(
                MessageType.ACTIVITY_IDEAS, 
                messageContext, 
                MESSAGE_TIMEOUT_MS);
        
        return StateAction.transition(WorkflowState.ACTIVITY_IDEAS, introMessage);
    }
    
    /**
     * Handles acknowledgment of the reminder (ok, thanks, etc.).
     * Sends an encouraging message and stays in the same state.
     */
    private StateAction handleAcknowledgeReminder(WorkflowContext context) {
        log.debug("Handling acknowledgment in QUALITY_TIME_REMINDER state for father: {}", 
                context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        
        // Build encouraging response
        String acknowledgment = buildAcknowledgmentMessage(locale);
        
        // Stay in the same state - no transition needed
        return StateAction.respond(acknowledgment);
    }
    
    // ─── Private Helper Methods ──────────────────────────────────────────────
    
    private String buildCancellationMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "בסדר %s, ביטלתי את זמן האיכות. 📅\n\n" +
                "שלח הודעה כשתרצה לקבוע זמן חדש!",
                fatherName != null ? fatherName : ""
            ).trim();
        } else {
            return String.format(
                "Okay %s, I've cancelled your Quality Time. 📅\n\n" +
                "Send a message when you'd like to schedule a new one!",
                fatherName != null ? fatherName : ""
            ).trim();
        }
    }
    
    private String buildAcknowledgmentMessage(String locale) {
        if ("he".equals(locale)) {
            return "מעולה! 💪 תהנה מהזמן האיכות!\n\n" +
                   "אשאל אותך אחר כך איך היה. 🌟";
        } else {
            return "Awesome! 💪 Enjoy your Quality Time!\n\n" +
                   "I'll check in with you afterwards. 🌟";
        }
    }
}
