package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionServiceFactory;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State handler for the SCHEDULE_QUALITY_TIME workflow state.
 * 
 * <p>Manages Quality Time scheduling by presenting available time slots,
 * processing slot selections, and handling schedule modifications.</p>
 * 
 * <p><b>Mission Abstraction:</b> Uses MissionService through MissionServiceFactory
 * to support future extensibility to other mission types while keeping MVP
 * focused on Quality Time.</p>
 * 
 * <p><b>Supported Languages:</b> English (en) and Hebrew (he) ONLY</p>
 * 
 * <p>Implements Requirements 5.1-5.6, 1.1 (Mission abstraction), 1.7 (WEB-SPEC-007 integration)
 * from the deterministic-workflow-engine spec.</p>
 */
@Component
public class ScheduleStateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleStateHandler.class);
    
    /** Default number of slots to present (Requirement 5.1) */
    private static final int DEFAULT_SLOTS_TO_SHOW = 5;
    
    /** Maximum number of slots to present */
    private static final int MAX_SLOTS_TO_SHOW = 5;
    
    /** Exchange count threshold for summary message (Requirement 5.6) */
    private static final int SUMMARY_EXCHANGE_THRESHOLD = 5;
    
    /** Default Quality Time duration in minutes */
    private static final int DEFAULT_QUALITY_TIME_DURATION_MINUTES = 30;
    
    /** Days ahead to search for available slots */
    private static final int DAYS_AHEAD = 7;
    
    private final SystemStateLoader systemStateLoader;
    private final MissionServiceFactory missionServiceFactory;
    private final MessageGenerator messageGenerator;
    private final FatherRepository fatherRepository;
    
    /** Tracks exchange count per father for summary trigger (Requirement 5.6) */
    private final Map<Long, Integer> exchangeCountByFather = new ConcurrentHashMap<>();
    
    /** Tracks current slot offset per father for MORE_SLOTS pagination */
    private final Map<Long, Integer> slotOffsetByFather = new ConcurrentHashMap<>();
    
    /** Caches currently presented slots per father for slot verification */
    private final Map<Long, List<AvailableSlot>> presentedSlotsByFather = new ConcurrentHashMap<>();

    /**
     * Creates a new ScheduleStateHandler with required dependencies.
     *
     * @param systemStateLoader for loading system state and available slots
     * @param missionServiceFactory for obtaining MissionService (uses default Quality Time service for MVP)
     * @param messageGenerator for generating response messages
     * @param fatherRepository for accessing father data
     */
    public ScheduleStateHandler(
            SystemStateLoader systemStateLoader,
            MissionServiceFactory missionServiceFactory,
            MessageGenerator messageGenerator,
            FatherRepository fatherRepository) {
        this.systemStateLoader = Objects.requireNonNull(systemStateLoader, "systemStateLoader must not be null");
        this.missionServiceFactory = Objects.requireNonNull(missionServiceFactory, "missionServiceFactory must not be null");
        this.messageGenerator = Objects.requireNonNull(messageGenerator, "messageGenerator must not be null");
        this.fatherRepository = Objects.requireNonNull(fatherRepository, "fatherRepository must not be null");
    }
    
    @Override
    public WorkflowState getState() {
        return WorkflowState.SCHEDULE_QUALITY_TIME;
    }
    
    @Override
    public List<StatePattern> getExpectedPatterns() {
        return StatePatterns.SCHEDULE_PATTERNS;
    }

    @Override
    public StateAction handle(WorkflowContext context, PatternResult match) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(match, "match must not be null");
        
        if (!match.isMatched()) {
            log.warn("ScheduleStateHandler.handle() called with unmatched PatternResult for father {}",
                    context.getFatherId());
            return handleUnmatched(context);
        }
        
        WorkflowAction action = match.matchedAction();
        log.debug("Processing SCHEDULE_QUALITY_TIME action {} for father {} (pattern: {})",
                action, context.getFatherId(), match.patternName());
        
        // Track exchange count for summary trigger (Requirement 5.6)
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        int exchangeCount = exchangeCountByFather.compute(fatherId, (k, v) -> v == null ? 1 : v + 1);
        
        return switch (action) {
            case SELECT_SLOT -> handleSelectSlot(context, match, exchangeCount);
            case POSTPONE_SCHEDULING -> handlePostponeScheduling(context, exchangeCount);
            case SHOW_MORE_SLOTS -> handleShowMoreSlots(context, exchangeCount);
            case PARSE_TIME -> handleParseTime(context, match, exchangeCount);
            case ALREADY_SCHEDULED -> handleAlreadyScheduled(context, exchangeCount);
            case ACKNOWLEDGE_SCHEDULE -> handleAcknowledgeSchedule(context, exchangeCount);
            default -> {
                log.warn("Unexpected action {} in SCHEDULE_QUALITY_TIME state for father {}",
                        action, context.getFatherId());
                yield handleUnmatched(context);
            }
        };
    }

    @Override
    public StateAction handleUnmatched(WorkflowContext context) {
        Objects.requireNonNull(context, "context must not be null");
        
        log.debug("Handling unmatched message in SCHEDULE_QUALITY_TIME state for father {}",
                context.getFatherId());
        
        Father father = loadFather(context);
        String locale = getLocale(father);
        
        // Build state-specific clarification message with explicit options
        // Per Requirement 11.4: Do NOT use AI to interpret unmatched messages
        String clarificationMessage = buildScheduleClarificationMessage(locale);
        
        return StateAction.clarify(clarificationMessage);
    }
    
    /**
     * Builds a state-specific clarification message for the SCHEDULE_QUALITY_TIME state.
     * 
     * <p>Per Requirement 11.4: The message is specific to the scheduling state context
     * and explicitly lists valid response options. No AI interpretation is used.</p>
     * 
     * @param locale the father's locale ("en" or "he")
     * @return the clarification message with explicit options
     */
    private String buildScheduleClarificationMessage(String locale) {
        if ("he".equals(locale)) {
            return "לא הבנתי. אנא הקלד מספר (1-5) כדי לבחור זמן, 'דלג' אם אתה רוצה לתאם מאוחר יותר, או 'עוד' לאפשרויות נוספות.";
        }
        return "I didn't catch that. Please reply with a slot number (1-5), 'skip' if you want to schedule later, or 'other' for more options.";
    }

    /**
     * Handles slot number selection (Requirement 5.2).
     * Re-reads calendar before creating event (Read Before Write principle).
     */
    private StateAction handleSelectSlot(WorkflowContext context, PatternResult match, int exchangeCount) {
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        String locale = getLocale(father);
        String fatherName = getFatherName(father);
        
        // Extract slot number from message (pattern captures single digit 1-9)
        int slotNumber;
        try {
            slotNumber = Integer.parseInt(context.getInboundMessage().trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse slot number from message: {}", context.getInboundMessage());
            return handleUnmatched(context);
        }
        
        log.info("Father {} selected slot number {} for Quality Time", fatherId, slotNumber);
        
        // Read Before Write: Re-read calendar to verify slot is still available
        List<AvailableSlot> freshSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
        
        // Get the previously presented slots for validation
        List<AvailableSlot> presentedSlots = presentedSlotsByFather.get(fatherId);
        
        if (presentedSlots == null || presentedSlots.isEmpty()) {
            // No slots were presented yet, load fresh and present
            log.debug("No presented slots cached, loading fresh slots for father {}", fatherId);
            return presentSlots(context, father, freshSlots, 0, exchangeCount);
        }

        // Validate slot number is within range of presented slots
        if (slotNumber < 1 || slotNumber > presentedSlots.size()) {
            log.warn("Slot number {} out of range (1-{}) for father {}", 
                    slotNumber, presentedSlots.size(), fatherId);
            return StateAction.clarify(buildInvalidSlotMessage(locale, presentedSlots.size()));
        }
        
        // Get the selected slot (1-based index)
        AvailableSlot selectedSlot = presentedSlots.get(slotNumber - 1);
        
        // Verify slot is still available in fresh calendar read
        boolean stillAvailable = freshSlots.stream()
                .anyMatch(slot -> slot.startTime().equals(selectedSlot.startTime()) 
                        && slot.endTime().equals(selectedSlot.endTime()));
        
        if (!stillAvailable) {
            log.info("Slot {} no longer available for father {}, presenting fresh slots", 
                    slotNumber, fatherId);
            // Clear cached slots and present fresh ones
            slotOffsetByFather.put(fatherId, 0);
            return presentSlots(context, father, freshSlots, 0, exchangeCount);
        }
        
        // Schedule the Quality Time
        return scheduleQualityTime(context, father, selectedSlot, exchangeCount);
    }

    /**
     * Handles SKIP/POSTPONE action (Requirement 5.3).
     * Acknowledges the skip and remains in state.
     * Note: 24h reminder is handled by the scheduler, not this handler.
     */
    private StateAction handlePostponeScheduling(WorkflowContext context, int exchangeCount) {
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        String locale = getLocale(father);
        String fatherName = getFatherName(father);
        
        log.info("Father {} postponed scheduling Quality Time", fatherId);
        
        // Clear cached data for this father
        exchangeCountByFather.remove(fatherId);
        slotOffsetByFather.remove(fatherId);
        presentedSlotsByFather.remove(fatherId);
        
        // Generate acknowledgment message
        String acknowledgment = buildPostponeAcknowledgment(locale, fatherName);
        
        // Remain in SCHEDULE_QUALITY_TIME state (reminder will re-prompt in 24h)
        return StateAction.respond(acknowledgment);
    }

    /**
     * Handles ALREADY_SCHEDULED action.
     * When father says "כבר קבענו" (already scheduled) while in SCHEDULE state,
     * check if there's already a scheduled Mission and confirm it,
     * or continue with scheduling if not.
     */
    private StateAction handleAlreadyScheduled(WorkflowContext context, int exchangeCount) {
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        String locale = getLocale(father);
        String fatherName = getFatherName(father);
        
        log.info("Father {} said 'already scheduled' while in SCHEDULE_QUALITY_TIME state", fatherId);
        
        // Get the default MissionService (Quality Time for MVP)
        MissionService missionService = missionServiceFactory.getDefaultService();
        
        // Check if there's actually a scheduled Mission
        java.util.Optional<Mission> upcomingOpt = missionService.getNextScheduled(fatherId);
        
        if (upcomingOpt.isPresent()) {
            Mission upcoming = upcomingOpt.get();
            
            // There IS a scheduled Quality Time! Confirm and transition to WAITING
            log.info("Found scheduled Mission {} for father {}, transitioning to WAITING", upcoming.getId(), fatherId);
            
            // Clear cached data for this father
            exchangeCountByFather.remove(fatherId);
            slotOffsetByFather.remove(fatherId);
            presentedSlotsByFather.remove(fatherId);
            
            // Get child name
            SystemState state = systemStateLoader.loadState(context.getFatherId());
            String childName = state.getDefaultChild() != null ? state.getDefaultChild().name() : "";
            
            // Build confirmation message
            String confirmation = buildAlreadyScheduledConfirmation(locale, fatherName, childName, 
                    upcoming.getScheduledStart(), father.getTimezone());
            
            return StateAction.transition(WorkflowState.WAITING, confirmation);
        } else {
            // No Mission scheduled - continue with scheduling
            log.info("No scheduled Mission found for father {} despite 'already scheduled' message", fatherId);
            String clarification = buildNoScheduleYetMessage(locale, fatherName);
            return StateAction.respond(clarification);
        }
    }

    /**
     * Handles ACKNOWLEDGE_SCHEDULE action.
     * When father says "אוקי" / "ok" / "thanks" while in SCHEDULE state,
     * re-present the slots since they may not have seen them or want to review.
     * 
     * <p>This provides a graceful response instead of the "לא הבנתי" error message.</p>
     */
    private StateAction handleAcknowledgeSchedule(WorkflowContext context, int exchangeCount) {
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        String locale = getLocale(father);
        
        log.info("Father {} acknowledged in SCHEDULE_QUALITY_TIME state, re-presenting slots", fatherId);
        
        // Check if Google Calendar is connected
        if (!father.hasGoogleCalendarConfigured()) {
            log.info("Father {} has no Google Calendar connected, prompting for connection", fatherId);
            return StateAction.respond(buildCalendarConnectMessage(father, locale));
        }
        
        // Reset offset to show first batch of slots again
        slotOffsetByFather.put(fatherId, 0);
        
        // Load fresh slots
        List<AvailableSlot> freshSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
        
        // Present slots with a gentle prompt
        String prompt = "he".equals(locale)
            ? "מעולה! הנה הזמנים הפנויים:"
            : "Great! Here are the available times:";
        
        if (freshSlots.isEmpty()) {
            MessageContext messageContext = MessageContext.builder()
                    .messageType(MessageType.SCHEDULE_NO_SLOTS)
                    .fatherName(getFatherName(father))
                    .locale(locale)
                    .timezone(father.getTimezone())
                    .build();
            
            String noSlotsMessage = messageGenerator.generateWithFallback(
                    MessageType.SCHEDULE_NO_SLOTS,
                    messageContext,
                    MessageGenerator.DEFAULT_TIMEOUT_MS
            );
            return StateAction.respond(noSlotsMessage);
        }
        
        // Present slots normally
        return presentSlots(context, father, freshSlots, 0, exchangeCount);
    }

    /**
     * Handles MORE_SLOTS request (Requirement 5.4).
     * Presents the next batch of available slots.
     */
    private StateAction handleShowMoreSlots(WorkflowContext context, int exchangeCount) {
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        
        log.debug("Father {} requested more slots", fatherId);
        
        // Get current offset and increment for next batch
        int currentOffset = slotOffsetByFather.getOrDefault(fatherId, 0);
        int newOffset = currentOffset + MAX_SLOTS_TO_SHOW;
        
        // Load available slots
        List<AvailableSlot> allSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
        
        // Check if there are more slots available
        if (newOffset >= allSlots.size()) {
            // Wrap around to beginning
            newOffset = 0;
            log.debug("No more slots available, wrapping around for father {}", fatherId);
        }
        
        slotOffsetByFather.put(fatherId, newOffset);
        
        return presentSlots(context, father, allSlots, newOffset, exchangeCount);
    }

    /**
     * Handles time expression parsing (Requirement 5.5).
     * Parses natural language time expressions and proceeds as slot selection.
     * If user provides only time-of-day (morning/evening), asks for specific time.
     */
    private StateAction handleParseTime(WorkflowContext context, PatternResult match, int exchangeCount) {
        Long fatherId = context.getFatherId().getLeastSignificantBits();
        Father father = loadFather(context);
        String locale = getLocale(father);
        String message = context.getInboundMessage().toLowerCase().trim();
        
        log.debug("Father {} provided time expression: {}", fatherId, message);
        
        // Get father's timezone
        ZoneId timezone = ZoneId.of(father.getTimezone() != null 
                ? father.getTimezone() : "Asia/Jerusalem");
        
        // Check if user provided a specific time (HH:MM or am/pm format)
        LocalTime explicitTime = extractTimeFromMessage(message, locale);
        
        // Check for time-of-day expressions (morning, afternoon, evening)
        LocalTime timeOfDay = parseTimeOfDay(message, locale);
        
        // If user said something like "בערב" without specific time, ask for clarification
        if (timeOfDay == null && explicitTime == null) {
            // Detect which time period they mentioned for the follow-up question
            String timeOfDayQuestion = buildTimeOfDayQuestion(message, locale);
            if (timeOfDayQuestion != null) {
                log.info("Father {} provided general time-of-day, asking for specific time", fatherId);
                return StateAction.respond(timeOfDayQuestion);
            }
        }
        
        // Parse the full time expression
        Instant parsedTime = parseTimeExpression(message, locale, timezone);
        
        if (parsedTime == null) {
            log.warn("Failed to parse time expression '{}' for father {}", message, fatherId);
            return handleUnmatched(context);
        }
        
        // Load fresh slots and find a matching slot
        List<AvailableSlot> freshSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
        
        // Find a slot that contains the parsed time
        AvailableSlot matchingSlot = findSlotContainingTime(freshSlots, parsedTime);
        
        if (matchingSlot == null) {
            // No slot available at that time
            log.info("No available slot at parsed time {} for father {}", parsedTime, fatherId);
            String noSlotMessage = buildNoSlotAtTimeMessage(locale, parsedTime, timezone);
            return StateAction.respond(noSlotMessage);
        }
        
        // Schedule the Quality Time
        return scheduleQualityTime(context, father, matchingSlot, exchangeCount);
    }
    
    /**
     * Builds a question asking for specific time when user provides only time-of-day.
     * Returns null if the message doesn't contain a recognizable time-of-day expression.
     */
    private String buildTimeOfDayQuestion(String message, String locale) {
        // Check for morning references
        if (message.contains("morning") || message.contains("בבוקר") || message.contains("בוקר")) {
            return "he".equals(locale) 
                ? "מעולה! באיזו שעה בבוקר? (לדוגמה: 8:00, 9:30)" 
                : "Great! What time in the morning? (e.g., 8:00, 9:30)";
        }
        
        // Check for afternoon references
        if (message.contains("afternoon") || message.contains("אחר הצהריים") || message.contains("צהריים")) {
            return "he".equals(locale) 
                ? "מעולה! באיזו שעה אחרי הצהריים? (לדוגמה: 14:00, 15:30)" 
                : "Great! What time in the afternoon? (e.g., 2:00 PM, 3:30 PM)";
        }
        
        // Check for evening references
        if (message.contains("evening") || message.contains("בערב") || message.contains("ערב")) {
            return "he".equals(locale) 
                ? "מעולה! באיזו שעה בערב? (לדוגמה: 18:00, 19:30, 20:00)" 
                : "Great! What time in the evening? (e.g., 6:00 PM, 7:30 PM, 8:00 PM)";
        }
        
        // Check for night references
        if (message.contains("night") || message.contains("בלילה") || message.contains("לילה")) {
            return "he".equals(locale) 
                ? "מעולה! באיזו שעה בלילה? (לדוגמה: 20:00, 21:00)" 
                : "Great! What time at night? (e.g., 8:00 PM, 9:00 PM)";
        }
        
        // Check for "today" references without specific time
        if (message.contains("today") || message.contains("היום")) {
            return "he".equals(locale) 
                ? "מעולה! באיזו שעה היום? (בוקר/צהריים/ערב או שעה ספציפית)" 
                : "Great! What time today? (morning/afternoon/evening or a specific time)";
        }
        
        return null;
    }

    /**
     * Schedules Quality Time and transitions to WAITING state.
     * Uses MissionService abstraction for future extensibility (Requirement 1.1).
     */
    private StateAction scheduleQualityTime(WorkflowContext context, Father father, 
            AvailableSlot slot, int exchangeCount) {
        Long fatherId = father.getId();
        String locale = getLocale(father);
        String fatherName = getFatherName(father);
        
        // Get the default child (for now, assuming single child)
        // TODO: Add child selection flow for multi-child families
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        SystemState.ChildInfo child = state.getDefaultChild();
        
        if (child == null) {
            log.error("No child found for father {}", fatherId);
            return StateAction.respond(buildNoChildMessage(locale));
        }
        
        try {
            // Get the default MissionService (Quality Time for MVP)
            MissionService missionService = missionServiceFactory.getDefaultService();
            
            // Schedule the Mission (Quality Time for MVP)
            Mission mission = missionService.schedule(
                    fatherId,
                    child.childId(),
                    slot.startTime(),
                    Duration.ofMinutes(DEFAULT_QUALITY_TIME_DURATION_MINUTES)
            );
            
            log.info("Scheduled Mission {} (type: {}) for father {} with child {} at {}", 
                    mission.getId(), mission.getType(), fatherId, child.name(), slot.startTime());

            // Clear cached data for this father
            exchangeCountByFather.remove(fatherId);
            slotOffsetByFather.remove(fatherId);
            presentedSlotsByFather.remove(fatherId);
            
            // Generate confirmation message
            MessageContext messageContext = MessageContext.builder()
                    .messageType(MessageType.SCHEDULE_CONFIRM)
                    .fatherName(fatherName)
                    .childName(child.name())
                    .scheduledStart(slot.startTime())
                    .scheduledEnd(slot.endTime())
                    .locale(locale)
                    .timezone(father.getTimezone())
                    .build();
            
            String confirmationMessage = messageGenerator.generateWithFallback(
                    MessageType.SCHEDULE_CONFIRM,
                    messageContext,
                    MessageGenerator.DEFAULT_TIMEOUT_MS
            );
            
            return StateAction.transition(WorkflowState.WAITING, confirmationMessage);
            
        } catch (IllegalStateException e) {
            // Calendar conflict
            log.warn("Calendar conflict when scheduling for father {}: {}", fatherId, e.getMessage());
            List<AvailableSlot> freshSlots = systemStateLoader.loadAvailableSlots(context.getFatherId());
            slotOffsetByFather.put(fatherId, 0);
            return presentSlots(context, father, freshSlots, 0, exchangeCount);
        } catch (Exception e) {
            log.error("Failed to schedule Mission for father {}: {}", fatherId, e.getMessage(), e);
            return StateAction.respond(buildScheduleErrorMessage(locale));
        }
    }

    /**
     * Presents available slots to the father (Requirement 5.1).
     */
    private StateAction presentSlots(WorkflowContext context, Father father, 
            List<AvailableSlot> allSlots, int offset, int exchangeCount) {
        Long fatherId = father.getId();
        String locale = getLocale(father);
        String fatherName = getFatherName(father);
        
        if (allSlots.isEmpty()) {
            log.info("No available slots found for father {}", fatherId);
            MessageContext messageContext = MessageContext.builder()
                    .messageType(MessageType.SCHEDULE_NO_SLOTS)
                    .fatherName(fatherName)
                    .locale(locale)
                    .timezone(father.getTimezone())
                    .build();
            
            String noSlotsMessage = messageGenerator.generateWithFallback(
                    MessageType.SCHEDULE_NO_SLOTS,
                    messageContext,
                    MessageGenerator.DEFAULT_TIMEOUT_MS
            );
            return StateAction.respond(noSlotsMessage);
        }
        
        // Get slots for this batch (3-5 slots as per Requirement 5.1)
        int endIndex = Math.min(offset + MAX_SLOTS_TO_SHOW, allSlots.size());
        List<AvailableSlot> slotsToPresent = allSlots.subList(offset, endIndex);
        
        // Cache the presented slots for validation during selection
        presentedSlotsByFather.put(fatherId, List.copyOf(slotsToPresent));

        // Get child info for message
        SystemState state = systemStateLoader.loadState(context.getFatherId());
        SystemState.ChildInfo child = state.getDefaultChild();
        // Handle null child or null/empty name - FallbackMessages will provide generic text
        String childName = (child != null && child.name() != null && !child.name().isBlank()) 
            ? child.name() 
            : null;
        
        // Generate slots message
        MessageContext messageContext = MessageContext.builder()
                .messageType(MessageType.SCHEDULE_SLOTS)
                .fatherName(fatherName)
                .childName(childName)
                .timeSlots(slotsToPresent)
                .locale(locale)
                .timezone(father.getTimezone())
                .build();
        
        String slotsMessage = messageGenerator.generateWithFallback(
                MessageType.SCHEDULE_SLOTS,
                messageContext,
                MessageGenerator.DEFAULT_TIMEOUT_MS
        );
        
        // Check if we should add a summary message (Requirement 5.6)
        if (exchangeCount >= SUMMARY_EXCHANGE_THRESHOLD) {
            slotsMessage = appendSummaryMessage(slotsMessage, locale);
            // Reset exchange count after summary
            exchangeCountByFather.put(fatherId, 0);
        }
        
        return StateAction.respond(slotsMessage);
    }

    // ========== Time Expression Parsing ==========
    
    /**
     * Parses natural language time expressions into an Instant.
     * Supports English and Hebrew expressions.
     */
    private Instant parseTimeExpression(String message, String locale, ZoneId timezone) {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        
        // Check for "tomorrow"
        if (message.contains("tomorrow") || message.contains("מחר")) {
            LocalDate tomorrow = now.toLocalDate().plusDays(1);
            LocalTime defaultTime = extractTimeFromMessage(message, locale);
            if (defaultTime == null) {
                defaultTime = LocalTime.of(10, 0); // Default to 10:00 AM
            }
            return ZonedDateTime.of(tomorrow, defaultTime, timezone).toInstant();
        }
        
        // Check for day names
        LocalDate targetDate = parseDayName(message, locale, now);
        if (targetDate != null) {
            LocalTime targetTime = extractTimeFromMessage(message, locale);
            if (targetTime == null) {
                targetTime = LocalTime.of(10, 0); // Default to 10:00 AM
            }
            return ZonedDateTime.of(targetDate, targetTime, timezone).toInstant();
        }
        
        // Check for time of day expressions
        LocalTime timeOfDay = parseTimeOfDay(message, locale);
        if (timeOfDay != null) {
            // Assume today if time is in the future, otherwise tomorrow
            ZonedDateTime target = ZonedDateTime.of(now.toLocalDate(), timeOfDay, timezone);
            if (target.isBefore(now)) {
                target = target.plusDays(1);
            }
            return target.toInstant();
        }
        
        // Check for explicit time (HH:MM or H am/pm)
        LocalTime explicitTime = extractTimeFromMessage(message, locale);
        if (explicitTime != null) {
            ZonedDateTime target = ZonedDateTime.of(now.toLocalDate(), explicitTime, timezone);
            if (target.isBefore(now)) {
                target = target.plusDays(1);
            }
            return target.toInstant();
        }
        
        return null;
    }

    /**
     * Parses day names from message and returns the target date.
     */
    private LocalDate parseDayName(String message, String locale, ZonedDateTime now) {
        Map<String, java.time.DayOfWeek> englishDays = Map.of(
            "sunday", java.time.DayOfWeek.SUNDAY,
            "monday", java.time.DayOfWeek.MONDAY,
            "tuesday", java.time.DayOfWeek.TUESDAY,
            "wednesday", java.time.DayOfWeek.WEDNESDAY,
            "thursday", java.time.DayOfWeek.THURSDAY,
            "friday", java.time.DayOfWeek.FRIDAY,
            "saturday", java.time.DayOfWeek.SATURDAY
        );
        
        Map<String, java.time.DayOfWeek> hebrewDays = Map.of(
            "יום ראשון", java.time.DayOfWeek.SUNDAY,
            "יום שני", java.time.DayOfWeek.MONDAY,
            "יום שלישי", java.time.DayOfWeek.TUESDAY,
            "יום רביעי", java.time.DayOfWeek.WEDNESDAY,
            "יום חמישי", java.time.DayOfWeek.THURSDAY,
            "יום שישי", java.time.DayOfWeek.FRIDAY,
            "שבת", java.time.DayOfWeek.SATURDAY
        );
        
        // Check English days
        for (Map.Entry<String, java.time.DayOfWeek> entry : englishDays.entrySet()) {
            if (message.contains(entry.getKey())) {
                return getNextOccurrence(now.toLocalDate(), entry.getValue());
            }
        }
        
        // Check Hebrew days
        for (Map.Entry<String, java.time.DayOfWeek> entry : hebrewDays.entrySet()) {
            if (message.contains(entry.getKey())) {
                return getNextOccurrence(now.toLocalDate(), entry.getValue());
            }
        }
        
        return null;
    }

    /**
     * Gets the next occurrence of a specific day of week.
     */
    private LocalDate getNextOccurrence(LocalDate from, java.time.DayOfWeek targetDay) {
        LocalDate next = from;
        while (next.getDayOfWeek() != targetDay) {
            next = next.plusDays(1);
        }
        // If it's today, move to next week
        if (next.equals(from)) {
            next = next.plusWeeks(1);
        }
        return next;
    }
    
    /**
     * Extracts time from message (e.g., "3pm", "15:00", "3:30").
     */
    private LocalTime extractTimeFromMessage(String message, String locale) {
        // Match HH:MM format
        Pattern timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher matcher = timePattern.matcher(message);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute);
            }
        }
        
        // Match am/pm format
        Pattern ampmPattern = Pattern.compile("(\\d{1,2})\\s*(am|pm)", Pattern.CASE_INSENSITIVE);
        matcher = ampmPattern.matcher(message);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            boolean isPm = matcher.group(2).equalsIgnoreCase("pm");
            if (isPm && hour != 12) {
                hour += 12;
            } else if (!isPm && hour == 12) {
                hour = 0;
            }
            if (hour >= 0 && hour <= 23) {
                return LocalTime.of(hour, 0);
            }
        }
        
        return null;
    }

    /**
     * Parses time of day expressions (morning, afternoon, evening).
     * Returns null to trigger a follow-up question for specific time.
     * This allows the user to provide a more precise time slot.
     */
    private LocalTime parseTimeOfDay(String message, String locale) {
        // Check for exact time patterns first (these are specific enough)
        // For general time-of-day expressions, return null to ask for specific time
        
        // Morning with specific indicator like "early morning" (6-7am) or "late morning" (10-11am)
        if (message.contains("early morning") || message.contains("בוקר מוקדם")) {
            return LocalTime.of(7, 0);
        }
        if (message.contains("late morning") || message.contains("בוקר מאוחר")) {
            return LocalTime.of(11, 0);
        }
        
        // General time-of-day without specific hour - return null to trigger follow-up
        // This fixes the bug where "בערב" sets 18:00 without asking for specific time
        if (message.contains("morning") || message.contains("בבוקר") || message.contains("בוקר")) {
            // Return null - will trigger specific time question
            return null;
        }
        if (message.contains("afternoon") || message.contains("אחר הצהריים") || message.contains("צהריים")) {
            // Return null - will trigger specific time question
            return null;
        }
        if (message.contains("evening") || message.contains("בערב") || message.contains("ערב")) {
            // Return null - will trigger specific time question
            return null;
        }
        if (message.contains("night") || message.contains("בלילה") || message.contains("לילה")) {
            // Return null - will trigger specific time question
            return null;
        }
        return null;
    }
    
    /**
     * Finds a slot that contains the given time.
     */
    private AvailableSlot findSlotContainingTime(List<AvailableSlot> slots, Instant targetTime) {
        return slots.stream()
                .filter(slot -> !targetTime.isBefore(slot.startTime()) 
                        && targetTime.isBefore(slot.endTime()))
                .findFirst()
                .orElse(null);
    }

    // ========== Helper Methods ==========
    
    private Father loadFather(WorkflowContext context) {
        // Convert UUID to Long: father UUID uses least significant bits for the Long ID
        // (UUID is created as new UUID(0L, domainId), so getLeastSignificantBits() returns the domain ID)
        return fatherRepository.findById(context.getFatherId().getLeastSignificantBits())
                .orElseThrow(() -> new IllegalStateException(
                        "Father not found: " + context.getFatherId()));
    }
    
    private String getLocale(Father father) {
        return father.getLocale() != null ? father.getLocale() : "en";
    }
    
    private String getFatherName(Father father) {
        return father.getDisplayName() != null ? father.getDisplayName() : "";
    }
    
    // ========== Message Building ==========
    
    /**
     * Builds clarification options based on locale.
     */
    private List<String> buildClarificationOptions(String locale) {
        if ("he".equals(locale)) {
            return List.of("1-5", "דלג", "עוד אפשרויות", "מחר בבוקר");
        }
        return List.of("1-5", "skip", "more options", "tomorrow morning");
    }
    
    /**
     * Builds invalid slot number message.
     */
    private String buildInvalidSlotMessage(String locale, int maxSlot) {
        if ("he".equals(locale)) {
            return String.format("אנא בחר מספר בין 1 ל-%d, או הקלד 'עוד' לאפשרויות נוספות.", maxSlot);
        }
        return String.format("Please choose a number between 1 and %d, or type 'more' for additional options.", maxSlot);
    }

    /**
     * Builds postpone acknowledgment message.
     */
    private String buildPostponeAcknowledgment(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format("בסדר %s, ניצור קשר מחר כדי לתאם זמן איכות. " +
                    "אתה תמיד יכול לכתוב לי כשתהיה מוכן! 💪", fatherName);
        }
        return String.format("No problem %s, I'll check back tomorrow to schedule Quality Time. " +
                "You can always message me when you're ready! 💪", fatherName);
    }
    
    /**
     * Builds no child found message.
     */
    private String buildNoChildMessage(String locale) {
        if ("he".equals(locale)) {
            return "לא נמצאו ילדים בפרופיל שלך. אנא צור קשר עם התמיכה.";
        }
        return "No children found in your profile. Please contact support.";
    }
    
    /**
     * Builds schedule error message.
     */
    private String buildScheduleErrorMessage(String locale) {
        if ("he".equals(locale)) {
            return "אירעה שגיאה בתיאום הפגישה. אנא נסה שוב.";
        }
        return "There was an error scheduling the event. Please try again.";
    }

    /**
     * Builds no slot at time message.
     */
    private String buildNoSlotAtTimeMessage(String locale, Instant time, ZoneId timezone) {
        ZonedDateTime zdt = time.atZone(timezone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a")
                .withLocale("he".equals(locale) ? new Locale("he") : Locale.ENGLISH);
        String formattedTime = zdt.format(formatter);
        
        if ("he".equals(locale)) {
            return String.format("לצערי, לא נמצא זמן פנוי ב%s. אנא בחר מהאפשרויות או הקלד 'עוד' לאפשרויות נוספות.", 
                    formattedTime);
        }
        return String.format("Unfortunately, there's no available time at %s. Please choose from the options or type 'more' for additional slots.", 
                formattedTime);
    }
    
    /**
     * Appends summary message after reaching exchange threshold (Requirement 5.6).
     */
    private String appendSummaryMessage(String originalMessage, String locale) {
        String summary;
        if ("he".equals(locale)) {
            summary = "\n\n📋 *סיכום:* אתה יכול לבחור זמן על ידי הקלדת מספר (1-5), " +
                    "להקליד 'דלג' לדחייה, 'עוד' לאפשרויות נוספות, " +
                    "או להקליד זמן ספציפי כמו 'מחר בבוקר'.";
        } else {
            summary = "\n\n📋 *Quick recap:* You can pick a time by typing a number (1-5), " +
                    "type 'skip' to postpone, 'more' for additional options, " +
                    "or type a specific time like 'tomorrow morning'.";
        }
        return originalMessage + summary;
    }
    
    /**
     * Builds confirmation message when father says "already scheduled" and there IS a scheduled Mission.
     */
    private String buildAlreadyScheduledConfirmation(String locale, String fatherName, String childName, 
                                                     Instant scheduledStart, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone != null ? timezone : "Asia/Jerusalem");
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Jerusalem");
        }
        
        ZonedDateTime zdt = scheduledStart.atZone(zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "he".equals(locale) ? "EEEE בשעה H:mm" : "EEEE 'at' h:mm a")
                .withLocale("he".equals(locale) ? new Locale("he") : Locale.ENGLISH);
        String formattedTime = zdt.format(formatter);
        
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
    
    /**
     * Builds message when father says "already scheduled" but there's no Mission scheduled.
     */
    private String buildNoScheduleYetMessage(String locale, String fatherName) {
        if ("he".equals(locale)) {
            return String.format(
                "היי %s, נראה שעדיין לא קבענו זמן. 📅\n" +
                "בוא נתאם עכשיו - מתי יש לך 10-15 דקות היום?",
                fatherName.isEmpty() ? "אבא" : fatherName
            );
        } else {
            return String.format(
                "Hey %s, it looks like we haven't set a time yet. 📅\n" +
                "Let's schedule now - when do you have 10-15 minutes today?",
                fatherName.isEmpty() ? "dad" : fatherName
            );
        }
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
