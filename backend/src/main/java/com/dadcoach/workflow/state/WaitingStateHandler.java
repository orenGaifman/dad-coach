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
 * Handler for the WAITING workflow state.
 * 
 * <p>The WAITING state is a passive state where the father waits for their scheduled
 * Quality Time to begin. In this state, the father can:</p>
 * <ul>
 *   <li>Request activity ideas (transitions to ACTIVITY_IDEAS)</li>
 *   <li>Reschedule their Quality Time (cancels existing and transitions to SCHEDULE_QUALITY_TIME)</li>
 *   <li>Inquire about their schedule (shows next Quality Time details)</li>
 *   <li>View dashboard summary (shows text summary with deep link)</li>
 * </ul>
 * 
 * <p><b>Mission Abstraction:</b> Uses MissionService through MissionServiceFactory
 * to support future extensibility to other mission types while keeping MVP
 * focused on Quality Time (Requirement 1.1).</p>
 * 
 * <p>Implements Requirements 6.1, 6.4, 6.5, 1.1 (Mission abstraction) from the deterministic-workflow-engine spec:</p>
 * <ul>
 *   <li>6.1: WAITING state is mostly passive, responds to father-initiated messages</li>
 *   <li>6.4: If father asks about schedule, read next Mission and send confirmation</li>
 *   <li>6.5: If father asks to reschedule, cancel existing and transition to SCHEDULE_QUALITY_TIME</li>
 * </ul>
 * 
 * <p>Language Support: English (en) and Hebrew (he) ONLY.</p>
 * 
 * @see WorkflowState#WAITING
 * @see StatePatterns#WAITING_PATTERNS
 */
@Component
public class WaitingStateHandler implements StateHandler {
    
    private static final Logger log = LoggerFactory.getLogger(WaitingStateHandler.class);
    
    /** Default timeout for message generation in milliseconds. */
    private static final long MESSAGE_TIMEOUT_MS = 5000L;
    
    /** Deep link URL template for the web dashboard. */
    private static final String DASHBOARD_URL_TEMPLATE = "https://app.dadcoach.ai/dashboard";
    
    private final MissionServiceFactory missionServiceFactory;
    private final SystemStateLoader systemStateLoader;
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    
    /**
     * Creates a new WaitingStateHandler with required dependencies.
     * 
     * @param missionServiceFactory factory for obtaining MissionService (uses default Quality Time service for MVP)
     * @param systemStateLoader loader for system state (Read Before Write)
     * @param messageGenerator generator for response messages
     * @param fatherRepository repository for Father entity operations
     */
    public WaitingStateHandler(
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
        return WorkflowState.WAITING;
    }
    
    @Override
    public List<StatePattern> getExpectedPatterns() {
        return StatePatterns.WAITING_PATTERNS;
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
        log.debug("Handling WAITING state action: {} for father: {}", action, context.getFatherId());
        
        return switch (action) {
            case TRANSITION_TO_ACTIVITY_IDEAS -> handleTransitionToActivityIdeas(context);
            case RESCHEDULE -> handleReschedule(context);
            case SHOW_SCHEDULE -> handleShowSchedule(context);
            case SHOW_DASHBOARD_SUMMARY -> handleShowDashboardSummary(context);
            case ACKNOWLEDGE_SCHEDULE -> handleAcknowledgeSchedule(context);
            case ALREADY_SCHEDULED -> handleAlreadyScheduled(context);
            default -> {
                log.warn("Unexpected action {} in WAITING state for father: {}", action, context.getFatherId());
                yield handleUnmatched(context);
            }
        };
    }
    
    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        
        log.debug("Handling unmatched message in WAITING state for father: {}", context.getFatherId());
        
        // Load system state for locale
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        
        // Build state-specific clarification message with explicit options
        // Per Requirement 11.4: Do NOT use AI to interpret unmatched messages
        String clarificationMessage = buildWaitingClarificationMessage(locale);
        
        return StateAction.clarify(clarificationMessage);
    }
    
    /**
     * Builds a state-specific clarification message for the WAITING state.
     * 
     * <p>Per Requirement 11.4: The message is specific to the WAITING state context
     * and explicitly lists valid response options. No AI interpretation is used.</p>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message with explicit options
     */
    private String buildWaitingClarificationMessage(String locale) {
        if ("he".equals(locale)) {
            return "לא הבנתי. תוכל לבקש 'רעיונות' לפעילויות, 'שנה זמן' לשנות את הזמן, או 'מתי' כדי לראות מתי הזמן איכות הבא שלך.";
        }
        return "I'm not sure what you mean. You can ask for 'ideas', 'reschedule', or check 'when' is your next Quality Time.";
    }
    
    // ─── Private Handler Methods ─────────────────────────────────────────────
    
    /**
     * Handles REQUEST_IDEAS action: stores previous state and transitions to ACTIVITY_IDEAS.
     * 
     * <p>Per Requirement 9.6: After exiting ACTIVITY_IDEAS, the workflow returns to the
     * state the father was in before entering (stored in previous_workflow_state field).</p>
     */
    private StateAction handleTransitionToActivityIdeas(WorkflowContext context) {
        log.info("Transitioning to ACTIVITY_IDEAS from WAITING for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        
        // Store previous workflow state = WAITING on Father entity
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));
        father.setPreviousWorkflowState(WorkflowState.WAITING);
        fatherRepository.save(father);
        
        log.debug("Stored previous_workflow_state=WAITING for father: {}", fatherId);
        
        // Build intro message for activity ideas
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get first child for context (activity ideas are child-specific)
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
     * Handles RESCHEDULE action: cancels existing Mission and transitions to SCHEDULE_QUALITY_TIME.
     * 
     * <p>Per Requirement 6.5: If the father asks to reschedule while in WAITING state,
     * cancel the existing Mission event in both database and Google Calendar,
     * then present new available time slots.</p>
     * 
     * <p>Uses MissionService abstraction (Requirement 1.1) for future extensibility.</p>
     */
    private StateAction handleReschedule(WorkflowContext context) {
        log.info("Handling reschedule request in WAITING state for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Get the current scheduled Mission
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isPresent()) {
            Mission upcoming = upcomingOpt.get();
            
            // Cancel the existing Mission (this also deletes the Google Calendar event for Quality Time)
            missionService.cancel(upcoming.getId());
            log.info("Cancelled Mission {} (type: {}) for father: {}", upcoming.getId(), upcoming.getType(), fatherId);
        } else {
            log.debug("No upcoming Mission to cancel for father: {}", fatherId);
        }
        
        // Build reschedule message
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .fatherName(fatherName)
                .locale(locale)
                .timezone(state.fatherProfile().timezone())
                .build();
        
        String rescheduleMessage = buildRescheduleMessage(locale, fatherName);
        
        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, rescheduleMessage);
    }
    
    /**
     * Handles SHOW_SCHEDULE action: reads next Mission and sends confirmation message.
     * 
     * <p>Per Requirement 6.4: If the father sends a message asking about their schedule,
     * read the next scheduled Mission and send a message confirming the date, time, and child.</p>
     * 
     * <p>Uses MissionService abstraction (Requirement 1.1) for future extensibility.</p>
     */
    private StateAction handleShowSchedule(WorkflowContext context) {
        log.debug("Showing schedule info in WAITING state for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        Long fatherId = state.fatherProfile().fatherId();
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        String timezone = state.fatherProfile().timezone();
        
        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Get next scheduled Mission
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isEmpty()) {
            // No Mission scheduled - this is unexpected in WAITING state
            log.warn("No scheduled Mission found for father {} in WAITING state", fatherId);
            String noScheduleMessage = buildNoScheduleMessage(locale, fatherName);
            return StateAction.respond(noScheduleMessage);
        }
        
        Mission upcoming = upcomingOpt.get();
        
        // Get child name from system state - access children via fatherProfile
        String childName = state.fatherProfile().children().stream()
                .filter(child -> child.childId().equals(upcoming.getChildId()))
                .map(SystemState.ChildInfo::name)
                .findFirst()
                .orElse("");
        
        // Format the scheduled time in the father's timezone
        String formattedTime = formatScheduledTime(upcoming.getScheduledStart(), upcoming.getScheduledEnd(), timezone, locale);
        
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.WAITING_SCHEDULE_INFO)
                .fatherName(fatherName)
                .childName(childName)
                .scheduledStart(upcoming.getScheduledStart())
                .scheduledEnd(upcoming.getScheduledEnd())
                .scheduledTimeFormatted(formattedTime)
                .locale(locale)
                .timezone(timezone)
                .build();
        
        String scheduleMessage = messageGenerator.generateWithFallback(
                MessageType.WAITING_SCHEDULE_INFO, 
                messageContext, 
                MESSAGE_TIMEOUT_MS);
        
        return StateAction.respond(scheduleMessage);
    }
    
    /**
     * Handles SHOW_DASHBOARD_SUMMARY action: sends text summary with deep link to dashboard.
     * 
     * <p>Per Requirement 8.3: When the father sends "dashboard" or "progress" via WhatsApp,
     * generate and send a text summary of their current stats and include a deep link
     * to the web dashboard for the full visual experience.</p>
     */
    private StateAction handleShowDashboardSummary(WorkflowContext context) {
        log.debug("Showing dashboard summary in WAITING state for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        String timezone = state.fatherProfile().timezone();
        
        // Get dashboard metrics from system state
        SystemState.DashboardMetrics metrics = state.dashboardMetrics();
        
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.DASHBOARD_SUMMARY)
                .fatherName(fatherName)
                .currentBelt(metrics.currentBelt())
                .streakCount(metrics.currentStreak())
                .longestStreak(metrics.longestStreak())
                .qualityTimeCount(metrics.totalCompleted())
                .beltProgressPercentage(metrics.progressToNextBelt())
                .qualityTimesUntilNextBelt(metrics.qualityTimesToNextBelt())
                .dashboardUrl(DASHBOARD_URL_TEMPLATE)
                .locale(locale)
                .timezone(timezone)
                .build();
        
        String summaryMessage = messageGenerator.generateWithFallback(
                MessageType.DASHBOARD_SUMMARY, 
                messageContext, 
                MESSAGE_TIMEOUT_MS);
        
        return StateAction.respond(summaryMessage);
    }
    
    /**
     * Handles ACKNOWLEDGE_SCHEDULE action: brief acknowledgment when father says "ok", "thanks", etc.
     * 
     * <p>When the father sends a simple acknowledgment like "אוקי" or "ok" after scheduling,
     * this handler responds with a brief, encouraging message reminding them of the scheduled time.</p>
     */
    private StateAction handleAcknowledgeSchedule(WorkflowContext context) {
        log.debug("Handling acknowledgment in WAITING state for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        
        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        Long fatherId = state.fatherProfile().fatherId();
        
        // Get next scheduled Mission for context
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        String acknowledgment;
        if (upcomingOpt.isPresent()) {
            Mission upcoming = upcomingOpt.get();
            String formattedTime = formatScheduledTime(upcoming.getScheduledStart(), upcoming.getScheduledEnd(), 
                    state.fatherProfile().timezone(), locale);
            acknowledgment = buildAcknowledgmentWithReminder(locale, fatherName, formattedTime);
        } else {
            // No schedule found - just acknowledge
            acknowledgment = buildSimpleAcknowledgment(locale);
        }
        
        return StateAction.respond(acknowledgment);
    }
    
    /**
     * Handles ALREADY_SCHEDULED action: confirms the existing schedule when father says "we already scheduled".
     * 
     * <p>When the father says "כבר קבענו" (already scheduled) or similar, this handler
     * confirms by showing the current scheduled Quality Time.</p>
     */
    private StateAction handleAlreadyScheduled(WorkflowContext context) {
        log.debug("Handling 'already scheduled' message in WAITING state for father: {}", context.getFatherId());
        
        // Load system state
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();
        String fatherName = state.fatherProfile().displayName();
        String timezone = state.fatherProfile().timezone();
        Long fatherId = state.fatherProfile().fatherId();
        
        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Get next scheduled Mission
        Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isPresent()) {
            Mission upcoming = upcomingOpt.get();
            
            // Get child name
            String childName = state.fatherProfile().children().stream()
                    .filter(child -> child.childId().equals(upcoming.getChildId()))
                    .map(SystemState.ChildInfo::name)
                    .findFirst()
                    .orElse("");
            
            String formattedTime = formatScheduledTime(upcoming.getScheduledStart(), upcoming.getScheduledEnd(), timezone, locale);
            String confirmation = buildAlreadyScheduledConfirmation(locale, fatherName, childName, formattedTime);
            return StateAction.respond(confirmation);
        } else {
            // No schedule found - weird state, offer to schedule
            log.warn("Father {} said 'already scheduled' but no Mission found in WAITING state", fatherId);
            String noSchedule = buildNoScheduleMessage(locale, fatherName);
            return StateAction.respond(noSchedule);
        }
    }
    
    // ─── Private Helper Methods ──────────────────────────────────────────────
    
    /**
     * Builds the list of valid options for clarification messages based on locale.
     */
    private List<String> buildValidOptions(String locale) {
        if ("he".equals(locale)) {
            return List.of(
                "רעיונות לפעילויות",    // Activity ideas
                "שינוי זמן",             // Reschedule
                "מתי הזמן איכות שלי?",   // When is my Quality Time?
                "דשבורד / התקדמות"       // Dashboard / Progress
            );
        } else {
            return List.of(
                "Activity ideas",
                "Reschedule",
                "When is my Quality Time?",
                "Dashboard / Progress"
            );
        }
    }
    
    /**
     * Builds a reschedule message based on locale.
     */
    private String buildRescheduleMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "בסדר %s, ביטלתי את זמן האיכות הקודם. 📅\n\n" +
                "בוא נמצא זמן חדש! מתי נוח לך?",
                fatherName != null ? fatherName : ""
            ).trim();
        } else {
            return String.format(
                "Okay %s, I've cancelled your previous Quality Time. 📅\n\n" +
                "Let's find a new time! When works for you?",
                fatherName != null ? fatherName : ""
            ).trim();
        }
    }
    
    /**
     * Builds a message for when no schedule is found (edge case).
     */
    private String buildNoScheduleMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "%s, נראה שאין לך זמן איכות מתוזמן כרגע. 📅\n\n" +
                "רוצה לתזמן אחד?",
                fatherName != null ? fatherName : "היי"
            );
        } else {
            return String.format(
                "%s, it looks like you don't have a Quality Time scheduled right now. 📅\n\n" +
                "Would you like to schedule one?",
                fatherName != null ? fatherName : "Hey"
            );
        }
    }
    
    /**
     * Formats the scheduled time in the father's timezone and locale.
     */
    private String formatScheduledTime(Instant start, Instant end, String timezone, String locale) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Jerusalem");
        }
        
        Locale displayLocale = "he".equals(locale) ? Locale.forLanguageTag("he-IL") : Locale.ENGLISH;
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", displayLocale)
                .withZone(zoneId);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", displayLocale)
                .withZone(zoneId);
        
        String date = dateFormatter.format(start);
        String startTime = timeFormatter.format(start);
        String endTime = timeFormatter.format(end);
        
        return String.format("%s, %s - %s", date, startTime, endTime);
    }
    
    /**
     * Builds a brief acknowledgment with a schedule reminder.
     */
    private String buildAcknowledgmentWithReminder(String locale, String fatherName, String formattedTime) {
        if ("he".equals(locale)) {
            return String.format(
                "מעולה! 👍 זמן האיכות שלך מתוכנן ל%s.\n" +
                "אזכיר לך חצי שעה לפני! 🔔",
                formattedTime
            );
        } else {
            return String.format(
                "Sounds good! 👍 Your Quality Time is set for %s.\n" +
                "I'll remind you 30 minutes before! 🔔",
                formattedTime
            );
        }
    }
    
    /**
     * Builds a simple acknowledgment when no schedule info is available.
     */
    private String buildSimpleAcknowledgment(String locale) {
        if ("he".equals(locale)) {
            return "מעולה! 👍 אני כאן אם תצטרך משהו.";
        } else {
            return "Great! 👍 I'm here if you need anything.";
        }
    }
    
    /**
     * Builds a confirmation message for "already scheduled" responses.
     */
    private String buildAlreadyScheduledConfirmation(String locale, String fatherName, String childName, String formattedTime) {
        if ("he".equals(locale)) {
            return String.format(
                "נכון! 👍 קבענו זמן איכות עם %s ב%s.\n" +
                "אזכיר לך חצי שעה לפני! 🔔",
                childName.isEmpty() ? "הילד" : childName,
                formattedTime
            );
        } else {
            return String.format(
                "Right! 👍 You have Quality Time with %s scheduled for %s.\n" +
                "I'll remind you 30 minutes before! 🔔",
                childName.isEmpty() ? "your child" : childName,
                formattedTime
            );
        }
    }
}
