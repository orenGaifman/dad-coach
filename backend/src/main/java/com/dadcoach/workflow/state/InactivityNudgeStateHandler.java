package com.dadcoach.workflow.state;

import com.dadcoach.common.AppConstants;
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
 * Handler for the INACTIVITY_NUDGE workflow state.
 * 
 * <p>The INACTIVITY_NUDGE state is entered after 3 days of no interaction
 * from the father. This state is designed to:</p>
 * <ul>
 *   <li>Gently re-engage the father with a supportive message</li>
 *   <li>Remind them of any scheduled Quality Time</li>
 *   <li>Offer easy ways to get back on track</li>
 * </ul>
 * 
 * <p>If the father responds, they transition back to WAITING or SCHEDULE_QUALITY_TIME.
 * If they remain inactive for 7 total days, their status changes to PAUSED.</p>
 * 
 * <p><b>Mission Abstraction:</b> Uses MissionService through MissionServiceFactory
 * to support future extensibility to other mission types.</p>
 * 
 * <p>Implements the workflow architecture analysis recommendations for
 * inactivity detection and re-engagement (LLD Section 9.1).</p>
 */
@Component
public class InactivityNudgeStateHandler implements StateHandler {
    
    private static final Logger log = LoggerFactory.getLogger(InactivityNudgeStateHandler.class);
    
    /** Default timeout for message generation in milliseconds. */
    private static final long MESSAGE_TIMEOUT_MS = 5000L;
    
    private final MissionServiceFactory missionServiceFactory;
    private final SystemStateLoader systemStateLoader;
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    
    /**
     * Creates a new InactivityNudgeStateHandler with required dependencies.
     * 
     * @param missionServiceFactory factory for obtaining MissionService
     * @param systemStateLoader loader for system state (Read Before Write)
     * @param messageGenerator generator for response messages
     * @param fatherRepository repository for Father entity operations
     */
    public InactivityNudgeStateHandler(
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
        return WorkflowState.INACTIVITY_NUDGE;
    }
    
    @Override
    public List<StatePattern> getExpectedPatterns() {
        // In nudge state, any response is a win - we want to re-engage
        return StatePatterns.INACTIVITY_PATTERNS;
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
        log.debug("Handling INACTIVITY_NUDGE action: {} for father: {}", action, context.getFatherId());
        
        return switch (action) {
            case SCHEDULE_NOW -> handleScheduleNow(context);
            case ACKNOWLEDGE_REMINDER -> handleReEngagement(context);
            case PAUSE_COACHING -> handlePauseRequest(context);
            default -> handleReEngagement(context); // Any response is good
        };
    }
    
    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        
        // Any message from the father in nudge state is treated as re-engagement
        log.info("Father {} responded to inactivity nudge, re-engaging", context.getFatherId());
        
        return handleReEngagement(context);
    }
    
    /**
     * Handles a request to schedule Quality Time now.
     * Transitions to SCHEDULE_QUALITY_TIME with available slots.
     */
    private StateAction handleScheduleNow(WorkflowContext context) {
        log.info("Father {} wants to schedule now, transitioning to SCHEDULE_QUALITY_TIME", 
                context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Update last interaction time
        updateLastInteraction(state.fatherProfile().fatherId());
        
        // Build scheduling message
        String scheduleMessage = buildScheduleMessage(locale, fatherName);
        
        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, scheduleMessage);
    }
    
    /**
     * Handles general re-engagement (any positive response).
     * Checks for existing scheduled QT and transitions appropriately.
     */
    private StateAction handleReEngagement(WorkflowContext context) {
        log.info("Father {} re-engaged from inactivity nudge", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        String timezone = state.fatherProfile().timezone();
        
        // Update last interaction time
        updateLastInteraction(fatherId);
        
        // Check if there's already a scheduled Quality Time
        MissionService missionService = missionServiceFactory.getDefaultService();
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isPresent()) {
            // There's already a scheduled QT - transition to WAITING with reminder
            Mission upcoming = upcomingOpt.get();
            String childName = getChildName(state, upcoming.getChildId());
            String formattedTime = formatScheduledTime(upcoming.getScheduledStart(), timezone, locale);
            
            String welcomeBackMessage = buildWelcomeBackWithScheduleMessage(
                    locale, fatherName, childName, formattedTime);
            
            return StateAction.transition(WorkflowState.WAITING, welcomeBackMessage);
        } else {
            // No scheduled QT - offer to schedule
            String welcomeBackMessage = buildWelcomeBackNoScheduleMessage(locale, fatherName);
            
            return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, welcomeBackMessage);
        }
    }
    
    /**
     * Handles a request to pause coaching.
     * This is for fathers who want to take a break.
     */
    private StateAction handlePauseRequest(WorkflowContext context) {
        log.info("Father {} requested to pause coaching", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Update father status to PAUSED
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));
        
        // Note: We're not actually setting status to PAUSED here - that would be
        // a FatherStatus change which needs separate handling. For now, we
        // acknowledge and stay in a "paused" like state.
        
        String pauseMessage = buildPauseAcknowledgmentMessage(locale, fatherName);
        
        // Transition to WAITING - the father can re-engage anytime
        return StateAction.transition(WorkflowState.WAITING, pauseMessage);
    }
    
    // ─── Private Helper Methods ──────────────────────────────────────────────
    
    private void updateLastInteraction(Long fatherId) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));
        father.setLastInteractionAt(Instant.now());
        fatherRepository.save(father);
    }
    
    private String getChildName(SystemState state, Long childId) {
        return state.fatherProfile().children().stream()
                .filter(child -> child.childId().equals(childId))
                .map(SystemState.ChildInfo::name)
                .findFirst()
                .orElse("");
    }
    
    private String formatScheduledTime(Instant time, String timezone, String locale) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone != null ? timezone : AppConstants.DEFAULT_TIMEZONE);
        } catch (Exception e) {
            zoneId = AppConstants.DEFAULT_ZONE_ID;
        }
        
        Locale displayLocale = "he".equals(locale) ? Locale.forLanguageTag("he-IL") : Locale.ENGLISH;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE 'at' h:mm a", displayLocale)
                .withZone(zoneId);
        
        return formatter.format(time);
    }
    
    private String buildScheduleMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "מעולה %s! 👋 טוב לראות אותך חזרה!\n\n" +
                "בוא נקבע זמן איכות. מתי נוח לך?",
                fatherName != null ? fatherName : ""
            ).trim();
        } else {
            return String.format(
                "Awesome %s! 👋 Great to see you back!\n\n" +
                "Let's schedule some quality time. When works for you?",
                fatherName != null ? fatherName : ""
            ).trim();
        }
    }
    
    private String buildWelcomeBackWithScheduleMessage(String locale, String fatherName, 
            String childName, String formattedTime) {
        if ("he".equals(locale)) {
            return String.format(
                "היי %s! 👋 שמח לראות אותך!\n\n" +
                "יש לך זמן איכות מתוזמן עם %s ביום %s.\n\n" +
                "אני כאן אם תצטרך משהו! 💪",
                fatherName != null ? fatherName : "",
                childName.isEmpty() ? "הילד" : childName,
                formattedTime
            );
        } else {
            return String.format(
                "Hey %s! 👋 Good to see you!\n\n" +
                "You have Quality Time scheduled with %s on %s.\n\n" +
                "I'm here if you need anything! 💪",
                fatherName != null ? fatherName : "",
                childName.isEmpty() ? "your child" : childName,
                formattedTime
            );
        }
    }
    
    private String buildWelcomeBackNoScheduleMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "היי %s! 👋 טוב לראות אותך!\n\n" +
                "בוא נקבע זמן איכות. מתי נוח לך השבוע?",
                fatherName != null ? fatherName : ""
            );
        } else {
            return String.format(
                "Hey %s! 👋 Good to see you!\n\n" +
                "Let's schedule some quality time. When works for you this week?",
                fatherName != null ? fatherName : ""
            );
        }
    }
    
    private String buildPauseAcknowledgmentMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "%s, אני מבין. 🤗\n\n" +
                "קח את הזמן שאתה צריך. אני כאן כשתרצה לחזור.\n\n" +
                "פשוט שלח הודעה ונמשיך! 💪",
                fatherName != null ? fatherName : "היי"
            );
        } else {
            return String.format(
                "%s, I understand. 🤗\n\n" +
                "Take the time you need. I'm here when you're ready to continue.\n\n" +
                "Just send a message and we'll pick up where we left off! 💪",
                fatherName != null ? fatherName : "Hey"
            );
        }
    }
}
