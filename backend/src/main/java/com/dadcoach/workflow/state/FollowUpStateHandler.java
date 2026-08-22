package com.dadcoach.workflow.state;

import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionServiceFactory;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemState.QualityTimeEvent;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Handler for the QUALITY_TIME_FOLLOW_UP workflow state.
 * 
 * <p>This handler manages follow-up interactions after a scheduled Quality Time event
 * has ended. It processes the father's response to determine if the Quality Time
 * was completed or missed, updates the relevant metrics, and transitions back to
 * the SCHEDULE_QUALITY_TIME state.</p>
 * 
 * <p><b>Mission Abstraction:</b> Uses MissionService through MissionServiceFactory
 * to support future extensibility to other mission types while keeping MVP
 * focused on Quality Time (Requirement 1.1).</p>
 * 
 * <p><b>Behavior:</b></p>
 * <ul>
 *   <li><b>MARK_COMPLETED:</b> Gets current Mission, extracts notes if provided,
 *       calls complete(), checks for belt milestone, and transitions to
 *       SCHEDULE_QUALITY_TIME with a completion/celebration message.</li>
 *   <li><b>MARK_MISSED:</b> Gets current Mission, marks it as missed, and
 *       transitions to SCHEDULE_QUALITY_TIME with an encouraging message.</li>
 *   <li><b>Unmatched:</b> Returns clarification with yes/no options.</li>
 * </ul>
 * 
 * <p>Implements Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 1.1 (Mission abstraction) from the 
 * deterministic-workflow-engine spec.</p>
 * 
 * @see StateHandler
 * @see StatePatterns#FOLLOW_UP_PATTERNS
 * @see MissionService#complete(UUID, String)
 */
@Component
public class FollowUpStateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(FollowUpStateHandler.class);

    /**
     * Short affirmative responses that indicate completion without additional notes.
     * Messages matching these exactly (case-insensitive) won't have notes extracted.
     */
    private static final Set<String> SHORT_AFFIRMATIVE_RESPONSES = Set.of(
            "yes", "done", "completed", "finished", "did it", "we did",
            "כן", "סיימתי", "עשיתי", "הושלם", "עשינו"
    );

    /**
     * Minimum message length to consider for note extraction.
     * Messages shorter than this are considered simple confirmations.
     */
    private static final int MIN_NOTE_LENGTH = 15;

    /**
     * Pattern to strip common completion prefixes when extracting notes.
     */
    private static final Pattern COMPLETION_PREFIX_PATTERN = Pattern.compile(
            "(?i)^(yes[,!.\\s]*|done[,!.\\s]*|completed[,!.\\s]*|finished[,!.\\s]*|" +
            "did it[,!.\\s]*|we did[,!.\\s]*|כן[,!.\\s]*|סיימתי[,!.\\s]*|עשיתי[,!.\\s]*|" +
            "הושלם[,!.\\s]*|עשינו[,!.\\s]*)+"
    );

    private final MissionServiceFactory missionServiceFactory;
    private final SystemStateLoader systemStateLoader;
    private final MessageGenerator messageGenerator;
    private final WorkflowMetrics workflowMetrics;

    /**
     * Creates a new FollowUpStateHandler with required dependencies.
     *
     * @param missionServiceFactory factory for obtaining MissionService (uses default Quality Time service for MVP)
     * @param systemStateLoader  loader for system state (Read Before Write principle)
     * @param messageGenerator   generator for response messages
     * @param workflowMetrics    metrics collector for workflow monitoring (Requirement 16.2)
     */
    public FollowUpStateHandler(
            MissionServiceFactory missionServiceFactory,
            SystemStateLoader systemStateLoader,
            MessageGenerator messageGenerator,
            WorkflowMetrics workflowMetrics
    ) {
        this.missionServiceFactory = missionServiceFactory;
        this.systemStateLoader = systemStateLoader;
        this.messageGenerator = messageGenerator;
        this.workflowMetrics = workflowMetrics;
    }

    @Override
    public WorkflowState getState() {
        return WorkflowState.QUALITY_TIME_FOLLOW_UP;
    }

    @Override
    public List<StatePattern> getExpectedPatterns() {
        return StatePatterns.FOLLOW_UP_PATTERNS;
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
        log.debug("Handling follow-up action {} for father {}", action, context.getFatherId());

        return switch (action) {
            case MARK_COMPLETED -> handleMarkCompleted(context);
            case MARK_MISSED -> handleMarkMissed(context);
            default -> {
                log.warn("Unexpected action {} in QUALITY_TIME_FOLLOW_UP state", action);
                yield handleUnmatched(context);
            }
        };
    }

    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        log.debug("Unmatched message in QUALITY_TIME_FOLLOW_UP for father {}", context.getFatherId());

        // Load system state to get father's locale
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        String locale = state.fatherProfile().locale();

        // Build state-specific clarification message with explicit options
        // Per Requirement 11.4: Do NOT use AI to interpret unmatched messages
        String clarificationMessage = buildFollowUpClarificationMessage(locale);

        return StateAction.clarify(clarificationMessage);
    }

    /**
     * Builds a state-specific clarification message for the QUALITY_TIME_FOLLOW_UP state.
     * 
     * <p>Per Requirement 11.4: The message is specific to the follow-up state context
     * and explicitly lists valid response options. No AI interpretation is used.</p>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message with explicit options
     */
    private String buildFollowUpClarificationMessage(String locale) {
        if ("he".equals(locale)) {
            return "אנא ספר לי - האם השלמת את זמן האיכות? השב 'כן' או 'לא'.";
        }
        return "Please tell me - did you complete your Quality Time? Reply 'yes' or 'no'.";
    }

    /**
     * Handles the MARK_COMPLETED action when father confirms Quality Time completion.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Loads the system state for the father</li>
     *   <li>Gets the Mission that needs follow-up from the state (COMPLETED/ENDED QT, not upcoming)</li>
     *   <li>Extracts any notes from the message beyond simple yes/done</li>
     *   <li>Calls complete() on MissionService which updates streak, belt, etc.</li>
     *   <li>Checks if a new belt was earned</li>
     *   <li>Generates completion message (with celebration if new belt)</li>
     *   <li>Returns transition to SCHEDULE_QUALITY_TIME</li>
     * </ol>
     * 
     * <p>Uses MissionService abstraction (Requirement 1.1) for future extensibility.</p>
     *
     * @param context the workflow context
     * @return StateAction with transition to SCHEDULE_QUALITY_TIME
     */
    private StateAction handleMarkCompleted(WorkflowContext context) {
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        
        // Get the Mission that needs follow-up - must be a COMPLETED/ENDED QT (end_time < now)
        // DO NOT use getNextScheduledQualityTime() as that returns UPCOMING events
        QualityTimeEvent qualityTimeEvent = findQualityTimeForFollowUp(state);

        if (qualityTimeEvent == null) {
            log.warn("No completed Quality Time found for follow-up, father: {}", context.getFatherId());
            // Transition to schedule even if no Mission found
            return transitionToScheduleWithGenericMessage(state);
        }

        // Extract notes from the message if longer than a simple yes/done
        String notes = extractCompletionNotes(context.getInboundMessage());
        
        log.info("Completing Mission {} for father {}, notes: {}",
                qualityTimeEvent.qualityTimeId(), context.getFatherId(), 
                notes != null ? "provided" : "none");

        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Complete the Mission - this updates streak, belt, etc.
        Mission completedMission = missionService.complete(qualityTimeEvent.qualityTimeId(), notes);

        // Reload state to get updated metrics
        SystemState updatedState = systemStateLoader.loadState(context.getFatherId());
        
        // Get the current belt and streak from the updated state
        Belt currentBelt = updatedState.dashboardMetrics().currentBelt();
        int newStreak = updatedState.dashboardMetrics().currentStreak();
        
        // Check if a new belt was earned (compare with previous)
        Belt previousBelt = Belt.fromCompletionCount(updatedState.dashboardMetrics().totalCompleted() - 1);
        Belt beltEarned = currentBelt != previousBelt ? currentBelt : null;

        // Build message context for completion response
        MessageContext.Builder messageContextBuilder = MessageContext.builder()
                .messageType(MessageType.FOLLOW_UP_COMPLETED)
                .fatherName(state.fatherProfile().displayName())
                .locale(state.fatherProfile().locale())
                .timezone(state.fatherProfile().timezone())
                .childName(qualityTimeEvent.childName())
                .streakCount(newStreak)
                .currentBelt(currentBelt);

        // Add belt earned if new belt was achieved
        if (beltEarned != null) {
            messageContextBuilder.beltEarned(beltEarned);
            log.info("Father {} earned new belt: {}", context.getFatherId(), beltEarned);
        }

        // Add completion notes to context if provided
        if (notes != null && !notes.isBlank()) {
            messageContextBuilder.completionNotes(notes);
        }

        MessageContext messageContext = messageContextBuilder.build();

        String completionMessage = messageGenerator.generateWithFallback(
                MessageType.FOLLOW_UP_COMPLETED,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );

        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, completionMessage);
    }

    /**
     * Handles the MARK_MISSED action when father indicates Quality Time was not completed.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Loads the system state for the father</li>
     *   <li>Gets the Quality Time that needs follow-up (ENDED, not upcoming)</li>
     *   <li>Marks the Quality Time as missed (no streak/belt update)</li>
     *   <li>Generates encouraging message</li>
     *   <li>Returns transition to SCHEDULE_QUALITY_TIME</li>
     * </ol>
     *
     * @param context the workflow context
     * @return StateAction with transition to SCHEDULE_QUALITY_TIME
     */
    private StateAction handleMarkMissed(WorkflowContext context) {
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        
        // Get the Quality Time that needs follow-up - must be ENDED (end_time < now)
        // DO NOT use getNextScheduledQualityTime() as that returns UPCOMING events
        QualityTimeEvent qualityTimeEvent = findQualityTimeForFollowUp(state);

        if (qualityTimeEvent == null) {
            log.warn("No completed Quality Time found for marking missed, father: {}", context.getFatherId());
            return transitionToScheduleWithGenericMessage(state);
        }

        log.info("Marking Mission {} as missed for father {}",
                qualityTimeEvent.qualityTimeId(), context.getFatherId());

        // Cancel/mark missed the Mission using MissionService abstraction
        // Note: MARK_MISSED doesn't affect streak - we just cancel and transition with encouragement
        MissionService missionService = missionServiceFactory.getDefaultService();
        missionService.cancel(qualityTimeEvent.qualityTimeId());
        
        // Record Quality Time missed metric (Requirement 16.2)
        workflowMetrics.recordQualityTimeMissed();

        // Build message context for missed response
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.FOLLOW_UP_MISSED)
                .fatherName(state.fatherProfile().displayName())
                .locale(state.fatherProfile().locale())
                .timezone(state.fatherProfile().timezone())
                .childName(qualityTimeEvent.childName())
                .build();

        String encouragingMessage = messageGenerator.generateWithFallback(
                MessageType.FOLLOW_UP_MISSED,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );

        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, encouragingMessage);
    }

    /**
     * Extracts completion notes from the inbound message if it contains more than
     * a simple yes/done confirmation.
     *
     * @param message the inbound message
     * @return extracted notes, or null if message is a simple confirmation
     */
    private String extractCompletionNotes(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String trimmed = message.trim();
        String lowerTrimmed = trimmed.toLowerCase();

        // Check if it's a short affirmative response with no additional content
        if (SHORT_AFFIRMATIVE_RESPONSES.contains(lowerTrimmed)) {
            return null;
        }

        // If message is too short, don't extract notes
        if (trimmed.length() < MIN_NOTE_LENGTH) {
            return null;
        }

        // Strip completion prefix to get just the notes
        String notes = COMPLETION_PREFIX_PATTERN.matcher(trimmed).replaceFirst("").trim();

        // If after stripping we have meaningful content, return it
        if (notes.length() >= 5) { // At least a few characters of actual notes
            return notes;
        }

        return null;
    }

    /**
     * Finds the Quality Time event that should be followed up on.
     * This looks for Quality Time events that have ENDED (scheduledEnd < now).
     * 
     * <p>For follow-up, we need to find QT events that have completed their time slot,
     * not upcoming scheduled ones. The QT may still have status "SCHEDULED" since 
     * it's pending follow-up confirmation from the father.</p>
     *
     * @param state the system state
     * @return the quality time event to follow up on, or null if none found
     */
    private QualityTimeEvent findQualityTimeForFollowUp(SystemState state) {
        if (state.qualityTimeEvents() == null || state.qualityTimeEvents().isEmpty()) {
            log.debug("No quality time events found for follow-up");
            return null;
        }

        Instant now = Instant.now();

        // Find the most recent Quality Time that has ENDED (scheduledEnd < now)
        // This is the one we're following up on
        // Still filter by SCHEDULED status - means not yet processed/confirmed
        QualityTimeEvent result = state.qualityTimeEvents().stream()
                .filter(qt -> qt.scheduledEnd() != null && qt.scheduledEnd().isBefore(now))
                .filter(qt -> "SCHEDULED".equals(qt.status())) // Still SCHEDULED means not yet processed
                .max(Comparator.comparing(QualityTimeEvent::scheduledEnd))
                .orElse(null);
        
        if (result == null) {
            log.warn("No completed Quality Time found for follow-up. Events: {}, now: {}", 
                    state.qualityTimeEvents().size(), now);
        } else {
            log.debug("Found Quality Time for follow-up: id={}, scheduledEnd={}", 
                    result.qualityTimeId(), result.scheduledEnd());
        }
        
        return result;
    }

    /**
     * Creates a transition to SCHEDULE_QUALITY_TIME with a generic message
     * when no specific Quality Time was found.
     *
     * @param state the system state
     * @return StateAction transitioning to SCHEDULE_QUALITY_TIME
     */
    private StateAction transitionToScheduleWithGenericMessage(SystemState state) {
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .fatherName(state.fatherProfile().displayName())
                .locale(state.fatherProfile().locale())
                .timezone(state.fatherProfile().timezone())
                .build();

        String message = messageGenerator.generateWithFallback(
                MessageType.SCHEDULE_SLOTS,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );

        return StateAction.transition(WorkflowState.SCHEDULE_QUALITY_TIME, message);
    }

    /**
     * Builds clarification options based on the father's locale.
     *
     * @param locale the father's locale ("en" or "he")
     * @return list of valid response options
     */
    private List<String> buildClarificationOptions(String locale) {
        if ("he".equals(locale)) {
            return List.of(
                    "כן / סיימתי / עשינו - אם הזמן האיכותי הושלם",
                    "לא / עוד לא - אם לא הספקתם"
            );
        }
        return List.of(
                "Yes / Done / We did it - if the Quality Time was completed",
                "No / Not yet - if you didn't get to it"
        );
    }
}
