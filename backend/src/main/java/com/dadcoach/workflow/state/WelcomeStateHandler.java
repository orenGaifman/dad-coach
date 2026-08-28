package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.systemstate.AvailableSlot;
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
import java.util.List;
import java.util.Objects;

/**
 * State handler for the WELCOME workflow state.
 * 
 * <p>The WELCOME state is the initial state for new fathers arriving from WEB-SPEC-007
 * (Onboarding). This handler processes the father's response to the welcome message
 * and transitions to the SCHEDULE_QUALITY_TIME state when the father is ready.</p>
 * 
 * <p><b>Behavior:</b></p>
 * <ul>
 *   <li><b>AFFIRMATIVE</b>: Transition to SCHEDULE_QUALITY_TIME, set welcomed_at timestamp</li>
 *   <li><b>MORE_INFO</b>: Send explanation message and reprompt for readiness</li>
 *   <li><b>UNMATCHED</b>: Send clarification with two explicit options</li>
 * </ul>
 * 
 * <p><b>Supported Languages:</b> English (en) and Hebrew (he) ONLY</p>
 * 
 * <p>Implements Requirements 4.1, 4.2, 4.3, 4.4, 4.5 from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>4.1: Send exactly one welcome message (handled by entry action)</li>
 *   <li>4.2: Accept AFFIRMATIVE and MORE_INFO patterns in English and Hebrew</li>
 *   <li>4.3: Send clarification with explicit options for unmatched messages</li>
 *   <li>4.4: No AI decision-making - pure pattern matching</li>
 *   <li>4.5: Set welcomed_at timestamp when transitioning out of WELCOME</li>
 * </ul>
 * 
 * @see WorkflowState#WELCOME
 * @see StatePatterns#WELCOME_PATTERNS
 */
@Component
public class WelcomeStateHandler implements StateHandler {
    
    private static final Logger log = LoggerFactory.getLogger(WelcomeStateHandler.class);
    
    /** Maximum number of slots to present in the initial message */
    private static final int MAX_SLOTS_TO_SHOW = 5;
    
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    private final SystemStateLoader systemStateLoader;
    
    /**
     * Creates a new WelcomeStateHandler with required dependencies.
     * 
     * @param messageGenerator the message generator for creating response messages
     * @param fatherRepository the repository for updating father entities
     * @param systemStateLoader for loading system state and available slots
     * @throws NullPointerException if any argument is null
     */
    public WelcomeStateHandler(MessageGenerator messageGenerator, FatherRepository fatherRepository,
            SystemStateLoader systemStateLoader) {
        this.messageGenerator = Objects.requireNonNull(messageGenerator, "messageGenerator must not be null");
        this.fatherRepository = Objects.requireNonNull(fatherRepository, "fatherRepository must not be null");
        this.systemStateLoader = Objects.requireNonNull(systemStateLoader, "systemStateLoader must not be null");
    }
    
    /**
     * {@inheritDoc}
     * 
     * @return {@link WorkflowState#WELCOME}
     */
    @Override
    public WorkflowState getState() {
        return WorkflowState.WELCOME;
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Returns the WELCOME patterns defined in {@link StatePatterns#WELCOME_PATTERNS}:</p>
     * <ul>
     *   <li>AFFIRMATIVE_EN: yes|ready|let's go|ok|sure|start → TRANSITION_TO_SCHEDULE</li>
     *   <li>AFFIRMATIVE_HE: כן|מוכן|יאללה|בסדר|התחל → TRANSITION_TO_SCHEDULE</li>
     *   <li>MORE_INFO_EN: how|what is|explain|tell me more → EXPLAIN_AND_REPROMPT</li>
     *   <li>MORE_INFO_HE: איך|מה זה|הסבר|ספר לי עוד → EXPLAIN_AND_REPROMPT</li>
     * </ul>
     * 
     * @return the list of expected patterns for the WELCOME state
     */
    @Override
    public List<StatePattern> getExpectedPatterns() {
        return StatePatterns.WELCOME_PATTERNS;
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Handles matched patterns in the WELCOME state:</p>
     * <ul>
     *   <li><b>TRANSITION_TO_SCHEDULE (AFFIRMATIVE)</b>: 
     *       Updates father.welcomedAt timestamp and transitions to SCHEDULE_QUALITY_TIME</li>
     *   <li><b>EXPLAIN_AND_REPROMPT (MORE_INFO)</b>: 
     *       Sends explanation message and remains in WELCOME state</li>
     * </ul>
     * 
     * @param context the workflow context containing father info and system state
     * @param match the pattern match result
     * @return the state action to take
     * @throws IllegalArgumentException if context or match is null
     */
    @Override
    public StateAction handle(WorkflowContext context, PatternResult match) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(match, "match must not be null");
        
        if (!match.isMatched()) {
            log.warn("WelcomeStateHandler.handle() called with unmatched PatternResult for father {}",
                    context.getFatherId());
            return handleUnmatched(context);
        }
        
        WorkflowAction action = match.matchedAction();
        log.debug("Processing WELCOME action {} for father {} (pattern: {})",
                action, context.getFatherId(), match.patternName());
        
        return switch (action) {
            case TRANSITION_TO_SCHEDULE -> handleAffirmative(context);
            case EXPLAIN_AND_REPROMPT -> handleMoreInfo(context);
            default -> {
                log.warn("Unexpected action {} in WELCOME state for father {}",
                        action, context.getFatherId());
                yield handleUnmatched(context);
            }
        };
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Sends a clarification message specific to the WELCOME state with two explicit options:</p>
     * <ul>
     *   <li>English: "I didn't understand. You can reply with 'yes' to start, or 'tell me more' if you have questions."</li>
     *   <li>Hebrew: "לא הבנתי. תוכל להגיד 'כן' כדי להתחיל, או 'ספר לי עוד' אם יש לך שאלות."</li>
     * </ul>
     * 
     * <p><b>Per Requirement 11.4:</b> The clarification message is state-specific,
     * includes explicit valid options, and does NOT use AI to interpret the unmatched message.</p>
     * 
     * @param context the workflow context
     * @return a clarification action with explicit options
     * @throws IllegalArgumentException if context is null
     */
    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        log.debug("Handling unmatched message in WELCOME state for father {}", context.getFatherId());
        
        // Load father to get locale
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Father father = fatherRepository.findById(context.getFatherId().getLeastSignificantBits())
                .orElseThrow(() -> new IllegalStateException(
                        "Father not found: " + context.getFatherId()));
        
        String locale = father.getLocale() != null ? father.getLocale() : "en";
        
        // Build state-specific clarification message with explicit options
        // Per Requirement 11.4: Do NOT use AI to interpret unmatched messages
        String clarificationMessage = buildWelcomeClarificationMessage(locale);
        
        return StateAction.clarify(clarificationMessage);
    }
    
    /**
     * Builds a state-specific clarification message for the WELCOME state.
     * 
     * <p>Per Requirement 11.4: The message is specific to the WELCOME state context
     * and explicitly lists valid response options. No AI interpretation is used.</p>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message with explicit options
     */
    private String buildWelcomeClarificationMessage(String locale) {
        if ("he".equals(locale)) {
            return "לא הבנתי. תוכל להגיד 'כן' כדי להתחיל, או 'ספר לי עוד' אם יש לך שאלות.";
        }
        return "I didn't understand. You can reply with 'yes' to start, or 'tell me more' if you have questions.";
    }
    
    /**
     * Handles AFFIRMATIVE responses (father is ready to schedule).
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Updates the father's welcomedAt timestamp to the current time</li>
     *   <li>Saves the father entity</li>
     *   <li>Loads available time slots for scheduling</li>
     *   <li>Generates a transition message with the slot options</li>
     *   <li>Returns a transition action to SCHEDULE_QUALITY_TIME</li>
     * </ol>
     * 
     * <p><b>Validates Requirement 4.5:</b> When transitioning out of WELCOME,
     * the welcomed_at timestamp is set and the father never returns to WELCOME state.</p>
     * 
     * @param context the workflow context
     * @return a transition action to SCHEDULE_QUALITY_TIME
     */
    private StateAction handleAffirmative(WorkflowContext context) {
        log.info("Father {} acknowledged welcome, transitioning to SCHEDULE_QUALITY_TIME",
                context.getFatherId());
        
        // Load and update father
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Father father = fatherRepository.findById(context.getFatherId().getLeastSignificantBits())
                .orElseThrow(() -> new IllegalStateException(
                        "Father not found: " + context.getFatherId()));
        
        // Set welcomed_at timestamp (Requirement 4.5)
        father.setWelcomedAt(Instant.now());
        fatherRepository.save(father);
        
        log.debug("Set welcomed_at timestamp for father {}", context.getFatherId());
        
        String locale = father.getLocale() != null ? father.getLocale() : "en";
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        
        // Check if Google Calendar is connected - add detailed logging
        boolean calendarEnabled = Boolean.TRUE.equals(father.getGoogleCalendarEnabled());
        boolean hasRefreshToken = father.getGoogleRefreshToken() != null && !father.getGoogleRefreshToken().isEmpty();
        boolean hasCalendarConfigured = father.hasGoogleCalendarConfigured();
        
        log.info("Calendar status for father {}: enabled={}, hasRefreshToken={}, hasGoogleCalendarConfigured={}", 
                context.getFatherId(), calendarEnabled, hasRefreshToken, hasCalendarConfigured);
        
        if (!hasCalendarConfigured) {
            log.info("Father {} has no Google Calendar connected, prompting for connection", 
                    context.getFatherId());
            
            String calendarConnectMessage = buildCalendarConnectMessage(father, locale);
            
            // Still transition to SCHEDULE state - user can connect calendar from there
            return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, calendarConnectMessage);
        }
        
        // Load available slots for scheduling
        List<AvailableSlot> availableSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
        
        // Limit to MAX_SLOTS_TO_SHOW
        List<AvailableSlot> slotsToPresent = availableSlots.size() > MAX_SLOTS_TO_SHOW
                ? availableSlots.subList(0, MAX_SLOTS_TO_SHOW)
                : availableSlots;
        
        // Get child info for the message
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        SystemState.ChildInfo child = state.getDefaultChild();
        String childName = (child != null && child.name() != null && !child.name().isBlank()) 
            ? child.name() 
            : null;
        
        // Generate schedule slots message for transition with actual slots
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .fatherName(fatherName)
                .childName(childName)
                .timeSlots(slotsToPresent)
                .locale(locale)
                .timezone(father.getTimezone())
                .build();
        
        String transitionMessage = messageGenerator.generateWithFallback(
                MessageType.SCHEDULE_SLOTS,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, transitionMessage);
    }
    
    /**
     * Handles MORE_INFO responses (father wants more information).
     * 
     * <p>Generates an explanation message about Dad Coach and reprompts
     * the father for readiness. The father remains in the WELCOME state.</p>
     * 
     * @param context the workflow context
     * @return a respond action with the explanation message
     */
    private StateAction handleMoreInfo(WorkflowContext context) {
        log.debug("Father {} requested more info in WELCOME state", context.getFatherId());
        
        // Load father to get locale and name
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Father father = fatherRepository.findById(context.getFatherId().getLeastSignificantBits())
                .orElseThrow(() -> new IllegalStateException(
                        "Father not found: " + context.getFatherId()));
        
        String locale = father.getLocale() != null ? father.getLocale() : "en";
        String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "";
        
        // Generate explanation message
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.WELCOME_EXPLAIN)
                .fatherName(fatherName)
                .locale(locale)
                .timezone(father.getTimezone())
                .build();
        
        String explanationMessage = messageGenerator.generateWithFallback(
                MessageType.WELCOME_EXPLAIN,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        return StateAction.respond(explanationMessage);
    }
    
    /**
     * Builds clarification options based on the father's locale.
     * 
     * <p><b>English options:</b></p>
     * <ul>
     *   <li>"Ready to schedule"</li>
     *   <li>"Tell me more"</li>
     * </ul>
     * 
     * <p><b>Hebrew options:</b></p>
     * <ul>
     *   <li>"מוכן לתאם"</li>
     *   <li>"ספר לי עוד"</li>
     * </ul>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return list of valid options in the appropriate language
     */
    private List<String> buildClarificationOptions(String locale) {
        if ("he".equals(locale)) {
            return List.of("מוכן לתאם", "ספר לי עוד");
        }
        return List.of("Ready to schedule", "Tell me more");
    }
    
    /**
     * Builds a message prompting the father to connect their Google Calendar.
     * 
     * <p>The message explains why calendar connection is needed and provides
     * a link to connect. The link uses the backend API which will redirect
     * to Google's OAuth flow.</p>
     * 
     * @param father the father entity
     * @param locale the father's locale ("en" or "he")
     * @return the calendar connection prompt message with link
     */
    private String buildCalendarConnectMessage(Father father, String locale) {
        // Build the calendar connect URL - this goes to our backend which redirects to Google OAuth
        String connectUrl = String.format("https://dad-coach.onrender.com/api/v1/calendar/connect/%d", 
                father.getId());
        
        if ("he".equals(locale)) {
            return String.format(
                "🗓️ כדי לתזמן זמן איכות, אני צריך גישה ללוח השנה שלך בגוגל.\n\n" +
                "זה יעזור לי למצוא זמנים פנויים ולתאם אוטומטית את הזמן עם הילדים.\n\n" +
                "👉 לחץ כאן לחיבור הלוח: %s\n\n" +
                "אחרי שתחבר, שלח לי הודעה ואמשיך מאיפה שהפסקנו! 😊",
                connectUrl
            );
        }
        
        return String.format(
            "🗓️ To schedule quality time, I need access to your Google Calendar.\n\n" +
            "This helps me find available times and automatically coordinate time with your kids.\n\n" +
            "👉 Click here to connect: %s\n\n" +
            "Once connected, send me a message and I'll continue from where we left off! 😊",
            connectUrl
        );
    }
}
