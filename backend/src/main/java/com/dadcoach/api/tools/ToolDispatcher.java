package com.dadcoach.api.tools;

import com.dadcoach.common.AppConstants;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.qualitytime.dto.UpcomingQualityTimeDto;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.dto.ActivityIdeaDto;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageContext.ActivityIdea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes tool execution requests to the appropriate service methods.
 * 
 * <p>This component maintains a registry of tool handlers and dispatches
 * incoming tool execution requests to the correct handler based on the tool key.</p>
 * 
 * <p>Supported tools (16 total):</p>
 * <h3>Scheduling Tools</h3>
 * <ul>
 *   <li>schedule_quality_time - Schedule a new Quality Time event</li>
 *   <li>reschedule_quality_time - Reschedule an existing Quality Time</li>
 *   <li>cancel_quality_time - Cancel a scheduled Quality Time</li>
 *   <li>complete_quality_time - Mark a Quality Time as completed</li>
 *   <li>show_available_slots - Get available calendar slots</li>
 * </ul>
 * 
 * <h3>Weekly Goal Tools</h3>
 * <ul>
 *   <li>set_weekly_goal - Set a new weekly goal</li>
 *   <li>get_weekly_goal_status - Get current goal progress</li>
 *   <li>show_weekly_summary - Get weekly summary data</li>
 * </ul>
 * 
 * <h3>Progress & Dashboard Tools</h3>
 * <ul>
 *   <li>show_progress - Get father's progress metrics</li>
 *   <li>get_dashboard_link - Get URL to the dashboard</li>
 * </ul>
 * 
 * <h3>Activity & Communication Tools</h3>
 * <ul>
 *   <li>get_activity_ideas - Get activity suggestions by age/type</li>
 *   <li>greet - Get greeting message data</li>
 *   <li>show_help - Get help menu data</li>
 *   <li>clarify - Get clarification prompt</li>
 *   <li>connect_calendar - Get calendar OAuth URL</li>
 * </ul>
 * 
 * @see ToolApiController
 * @see ToolHandler
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();

    private final QualityTimeService qualityTimeService;
    private final QualityTimeRepository qualityTimeRepository;
    private final WeeklyGoalService weeklyGoalService;
    private final GoogleCalendarService googleCalendarService;
    private final SystemStateLoader systemStateLoader;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;

    @Value("${app.dashboard.base-url:https://dadcoach.app}")
    private String dashboardBaseUrl;

    public ToolDispatcher(
            QualityTimeService qualityTimeService,
            QualityTimeRepository qualityTimeRepository,
            WeeklyGoalService weeklyGoalService,
            GoogleCalendarService googleCalendarService,
            SystemStateLoader systemStateLoader,
            FatherRepository fatherRepository,
            ChildRepository childRepository) {
        this.qualityTimeService = qualityTimeService;
        this.qualityTimeRepository = qualityTimeRepository;
        this.weeklyGoalService = weeklyGoalService;
        this.googleCalendarService = googleCalendarService;
        this.systemStateLoader = systemStateLoader;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
    }

    @PostConstruct
    public void initializeHandlers() {
        // Scheduling Tools
        handlers.put("schedule_quality_time", this::handleScheduleQualityTime);
        handlers.put("reschedule_quality_time", this::handleRescheduleQualityTime);
        handlers.put("cancel_quality_time", this::handleCancelQualityTime);
        handlers.put("complete_quality_time", this::handleCompleteQualityTime);
        handlers.put("show_available_slots", this::handleShowAvailableSlots);

        // Weekly Goal Tools
        handlers.put("set_weekly_goal", this::handleSetWeeklyGoal);
        handlers.put("get_weekly_goal_status", this::handleGetWeeklyGoalStatus);
        handlers.put("show_weekly_summary", this::handleShowWeeklySummary);

        // Progress & Dashboard Tools
        handlers.put("show_progress", this::handleShowProgress);
        handlers.put("get_dashboard_link", this::handleGetDashboardLink);

        // Activity & Communication Tools
        handlers.put("get_activity_ideas", this::handleGetActivityIdeas);
        handlers.put("greet", this::handleGreet);
        handlers.put("show_help", this::handleShowHelp);
        handlers.put("clarify", this::handleClarify);
        handlers.put("connect_calendar", this::handleConnectCalendar);
        handlers.put("get_upcoming_quality_time", this::handleGetUpcomingQualityTime);

        log.info("ToolDispatcher initialized with {} handlers", handlers.size());
    }

    /**
     * Dispatches a tool execution request to the appropriate handler.
     *
     * @param toolKey the tool identifier
     * @param request the execution request
     * @return the execution response
     */
    public ToolExecutionResponse dispatch(String toolKey, ToolApiController.ResolvedToolRequest request) {
        log.info("Dispatching tool: toolKey={}, executionId={}, userId={}",
                toolKey, request.executionId(), request.userId());

        ToolHandler handler = handlers.get(toolKey);
        if (handler == null) {
            log.warn("Unknown tool requested: {}", toolKey);
            return ToolExecutionResponse.toolNotFound(toolKey);
        }

        try {
            return handler.execute(request);
        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found during tool execution: {}", e.getMessage());
            return ToolExecutionResponse.notFound(e.getEntityType(), String.valueOf(e.getIdentifier()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for tool {}: {}", toolKey, e.getMessage());
            return ToolExecutionResponse.invalidParameters(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Invalid state for tool {}: {}", toolKey, e.getMessage());
            return ToolExecutionResponse.failure(e.getMessage(), "INVALID_STATE");
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolKey, e.getMessage(), e);
            return ToolExecutionResponse.failure("Internal error: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    /**
     * Returns the set of available tool keys.
     */
    public Set<String> getAvailableTools() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    // ─── Scheduling Tool Handlers ────────────────────────────────────────────

    private ToolExecutionResponse handleScheduleQualityTime(ToolApiController.ResolvedToolRequest request) {
        Long childId = request.getLongParam("child_id");
        String startTimeStr = request.getStringParam("start_time");
        Integer durationMinutes = request.getIntParam("duration_minutes");

        if (childId == null) {
            return ToolExecutionResponse.invalidParameters("child_id is required");
        }
        if (startTimeStr == null) {
            return ToolExecutionResponse.invalidParameters("start_time is required");
        }
        if (durationMinutes == null) {
            durationMinutes = 30; // Default 30 minutes
        }

        Instant startTime = Instant.parse(startTimeStr);
        Duration duration = Duration.ofMinutes(durationMinutes);

        ScheduleQualityTimeResult result = qualityTimeService.scheduleQualityTime(
                request.userId(), childId, startTime, duration);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quality_time_id", result.qualityTimeId().toString());
        data.put("calendar_event_id", result.calendarEventId());
        data.put("child_name", result.childName());
        data.put("start_time", result.startTime().toString());
        data.put("end_time", result.endTime().toString());
        data.put("status", result.status().name());

        log.info("Quality Time scheduled: qualityTimeId={}", result.qualityTimeId());
        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleRescheduleQualityTime(ToolApiController.ResolvedToolRequest request) {
        String qualityTimeIdStr = request.getStringParam("quality_time_id");
        String newStartTimeStr = request.getStringParam("new_start_time");
        Integer newDurationMinutes = request.getIntParam("new_duration_minutes");

        if (qualityTimeIdStr == null) {
            return ToolExecutionResponse.invalidParameters("quality_time_id is required");
        }
        if (newStartTimeStr == null) {
            return ToolExecutionResponse.invalidParameters("new_start_time is required");
        }

        UUID qualityTimeId = UUID.fromString(qualityTimeIdStr);
        Instant newStartTime = Instant.parse(newStartTimeStr);

        // Get the existing quality time to find child
        QualityTime existing = qualityTimeRepository.findById(qualityTimeId)
                .orElseThrow(() -> new ResourceNotFoundException("QualityTime", qualityTimeId));

        // Verify ownership
        if (!existing.getFatherId().equals(request.userId())) {
            throw new ResourceNotFoundException("QualityTime", qualityTimeId);
        }

        // Cancel the old one
        qualityTimeService.cancelQualityTime(qualityTimeId);

        // Schedule a new one with the new time
        int duration = newDurationMinutes != null ? newDurationMinutes : 30;
        ScheduleQualityTimeResult result = qualityTimeService.scheduleQualityTime(
                request.userId(),
                existing.getChildId(),
                newStartTime,
                Duration.ofMinutes(duration)
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("old_quality_time_id", qualityTimeIdStr);
        data.put("new_quality_time_id", result.qualityTimeId().toString());
        data.put("calendar_event_id", result.calendarEventId());
        data.put("child_name", result.childName());
        data.put("new_start_time", result.startTime().toString());
        data.put("new_end_time", result.endTime().toString());
        data.put("status", result.status().name());

        log.info("Quality Time rescheduled: old={}, new={}", qualityTimeIdStr, result.qualityTimeId());
        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleCancelQualityTime(ToolApiController.ResolvedToolRequest request) {
        String qualityTimeIdStr = request.getStringParam("quality_time_id");

        if (qualityTimeIdStr == null) {
            return ToolExecutionResponse.invalidParameters("quality_time_id is required");
        }

        UUID qualityTimeId = UUID.fromString(qualityTimeIdStr);

        // Verify ownership before cancelling
        QualityTime existing = qualityTimeRepository.findById(qualityTimeId)
                .orElseThrow(() -> new ResourceNotFoundException("QualityTime", qualityTimeId));

        if (!existing.getFatherId().equals(request.userId())) {
            throw new ResourceNotFoundException("QualityTime", qualityTimeId);
        }

        qualityTimeService.cancelQualityTime(qualityTimeId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quality_time_id", qualityTimeIdStr);
        data.put("status", "CANCELLED");

        log.info("Quality Time cancelled: qualityTimeId={}", qualityTimeId);
        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleCompleteQualityTime(ToolApiController.ResolvedToolRequest request) {
        String qualityTimeIdStr = request.getStringParam("quality_time_id");
        String notes = request.getStringParam("notes");

        if (qualityTimeIdStr == null) {
            return ToolExecutionResponse.invalidParameters("quality_time_id is required");
        }

        UUID qualityTimeId = UUID.fromString(qualityTimeIdStr);

        // Verify ownership before completing
        QualityTime existing = qualityTimeRepository.findById(qualityTimeId)
                .orElseThrow(() -> new ResourceNotFoundException("QualityTime", qualityTimeId));

        if (!existing.getFatherId().equals(request.userId())) {
            throw new ResourceNotFoundException("QualityTime", qualityTimeId);
        }

        CompleteQualityTimeResult result = qualityTimeService.completeQualityTime(qualityTimeId, notes);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quality_time_id", result.qualityTimeId().toString());
        data.put("status", result.status().name());
        data.put("new_streak", result.newStreak());
        data.put("current_belt", result.currentBelt().name());
        data.put("points_awarded", result.pointsAwarded());
        if (result.beltEarned() != null) {
            data.put("belt_earned", result.beltEarned().name());
        }

        log.info("Quality Time completed: qualityTimeId={}, newStreak={}", qualityTimeId, result.newStreak());
        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleShowAvailableSlots(ToolApiController.ResolvedToolRequest request) {
        Integer daysAhead = request.getIntParam("days_ahead");
        if (daysAhead == null || daysAhead < 1) {
            daysAhead = 7;
        }
        if (daysAhead > 14) {
            daysAhead = 14;
        }

        Father father = fatherRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Father", request.userId()));

        UUID fatherUuid = new UUID(0, request.userId());

        List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(fatherUuid, daysAhead);

        List<Map<String, Object>> slotsList = new ArrayList<>();
        for (AvailableSlot slot : slots) {
            Map<String, Object> slotMap = new LinkedHashMap<>();
            slotMap.put("start_time", slot.startTime().toString());
            slotMap.put("end_time", slot.endTime().toString());
            slotMap.put("duration_minutes", slot.durationMinutes());
            slotsList.add(slotMap);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slots", slotsList);
        data.put("calendar_connected", father.hasGoogleCalendarConfigured());
        data.put("timezone", father.getTimezone() != null ? father.getTimezone() : AppConstants.DEFAULT_TIMEZONE);

        log.info("Available slots returned: count={}, userId={}", slots.size(), request.userId());
        return ToolExecutionResponse.success(data);
    }

    // ─── Weekly Goal Tool Handlers ───────────────────────────────────────────

    private ToolExecutionResponse handleSetWeeklyGoal(ToolApiController.ResolvedToolRequest request) {
        Integer targetHours = request.getIntParam("target_hours");

        if (targetHours == null || targetHours < 1) {
            return ToolExecutionResponse.invalidParameters("target_hours must be at least 1");
        }

        WeeklyGoal goal = weeklyGoalService.createWeeklyGoal(request.userId(), targetHours);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goal_id", goal.getId());
        data.put("week_start_date", goal.getWeekStartDate().toString());
        data.put("target_hours", goal.getTargetHours());
        data.put("status", goal.getStatus().name());

        log.info("Weekly goal set: goalId={}, targetHours={}", goal.getId(), targetHours);
        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleGetWeeklyGoalStatus(ToolApiController.ResolvedToolRequest request) {
        Optional<WeeklyGoal> activeGoal = weeklyGoalService.getActiveGoal(request.userId());

        Map<String, Object> data = new LinkedHashMap<>();
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            data.put("has_goal", true);
            data.put("goal_id", goal.getId());
            data.put("week_start_date", goal.getWeekStartDate().toString());
            data.put("target_hours", goal.getTargetHours());
            data.put("actual_minutes", goal.getActualMinutes());
            data.put("actual_hours", goal.getActualHours());
            data.put("progress_percent", calculateProgressPercent(goal));
            data.put("status", goal.getStatus().name());
            data.put("is_goal_met", goal.isGoalMet());
        } else {
            data.put("has_goal", false);
        }

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleShowWeeklySummary(ToolApiController.ResolvedToolRequest request) {
        WeeklyGoalService.WeeklySummary summary = weeklyGoalService.generateWeeklySummary(request.userId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("has_previous_goal", summary.hasPreviousGoal());
        if (summary.hasPreviousGoal()) {
            data.put("target_hours", summary.targetHours());
            data.put("actual_hours", summary.actualHours());
            data.put("completed_count", summary.completedCount());
            data.put("scheduled_count", summary.scheduledCount());
            data.put("goal_met", summary.goalMet());
            data.put("consecutive_weeks", summary.consecutiveWeeks());
            if (summary.startingBelt() != null) {
                data.put("starting_belt", summary.startingBelt().name());
            }
            if (summary.endingBelt() != null) {
                data.put("ending_belt", summary.endingBelt().name());
            }
            data.put("was_promoted", summary.wasPromoted());
        }

        return ToolExecutionResponse.success(data);
    }

    // ─── Progress & Dashboard Tool Handlers ──────────────────────────────────

    private ToolExecutionResponse handleShowProgress(ToolApiController.ResolvedToolRequest request) {
        Father father = fatherRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Father", request.userId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current_belt", father.getCurrentBelt().name());
        data.put("quality_time_streak", father.getQualityTimeStreak());
        data.put("quality_time_longest_streak", father.getQualityTimeLongestStreak());
        data.put("total_quality_times_completed", father.getTotalQualityTimesCompleted());
        data.put("total_quality_minutes", father.getTotalQualityMinutes());
        data.put("current_streak_weeks", father.getCurrentStreakWeeks());
        data.put("longest_streak_weeks", father.getLongestStreakWeeks());

        // Add weekly goal progress if exists
        Optional<WeeklyGoal> activeGoal = weeklyGoalService.getActiveGoal(request.userId());
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            data.put("weekly_goal_target_hours", goal.getTargetHours());
            data.put("weekly_goal_actual_hours", goal.getActualHours());
            data.put("weekly_goal_progress_percent", calculateProgressPercent(goal));
        }

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleGetDashboardLink(ToolApiController.ResolvedToolRequest request) {
        Father father = fatherRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Father", request.userId()));

        // Generate dashboard URL with father ID
        String dashboardUrl = dashboardBaseUrl + "/dashboard?fatherId=" + request.userId();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dashboard_url", dashboardUrl);
        data.put("father_name", father.getDisplayName());

        return ToolExecutionResponse.success(data);
    }

    // ─── Activity & Communication Tool Handlers ──────────────────────────────

    private ToolExecutionResponse handleGetActivityIdeas(ToolApiController.ResolvedToolRequest request) {
        Long childId = request.getLongParam("child_id");
        String activityType = request.getStringParam("activity_type"); // indoor, outdoor, or null for both

        Father father = fatherRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Father", request.userId()));

        String locale = father.getLocale() != null ? father.getLocale() : "he";
        int childAge = 5; // Default

        if (childId != null) {
            Child child = childRepository.findById(childId)
                    .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

            if (!child.getFatherId().equals(request.userId())) {
                throw new ResourceNotFoundException("Child", childId);
            }

            childAge = calculateChildAge(child.getBirthDate());
        }

        List<ActivityIdea> ideas = generateActivityIdeas(childAge, locale, activityType);

        List<Map<String, Object>> ideasList = new ArrayList<>();
        for (ActivityIdea idea : ideas) {
            Map<String, Object> ideaMap = new LinkedHashMap<>();
            ideaMap.put("title", idea.title());
            ideaMap.put("description", idea.description());
            ideaMap.put("duration_minutes", idea.durationMinutes());
            ideaMap.put("indoor", idea.indoor());
            ideasList.add(ideaMap);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ideas", ideasList);
        data.put("child_age", childAge);

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleGreet(ToolApiController.ResolvedToolRequest request) {
        // Handle null userId for new users (no Father record exists yet)
        Optional<Father> fatherOpt = request.userId() != null 
            ? fatherRepository.findById(request.userId())
            : Optional.empty();
        
        String locale;
        String displayName;
        boolean isNewUser;
        
        if (fatherOpt.isPresent()) {
            Father father = fatherOpt.get();
            locale = father.getLocale() != null ? father.getLocale() : "he";
            displayName = father.getDisplayName();
            isNewUser = false;
        } else {
            // New user - use default Hebrew locale and no name
            locale = "he";
            displayName = null;
            isNewUser = true;
        }
        
        String greeting;
        String welcomeMessage;

        if ("he".equals(locale)) {
            greeting = "שלום " + (displayName != null ? displayName : "אבא") + "! 👋";
            welcomeMessage = isNewUser 
                ? "ברוכים הבאים ל-Dad Coach! אני כאן לעזור לך ליצור זמן איכות משמעותי עם הילדים שלך."
                : "אני Dad Coach, המאמן האישי שלך לזמן איכות עם הילדים. איך אפשר לעזור לך היום?";
        } else {
            greeting = "Hello " + (displayName != null ? displayName : "Dad") + "! 👋";
            welcomeMessage = isNewUser
                ? "Welcome to Dad Coach! I'm here to help you create meaningful quality time with your children."
                : "I'm Dad Coach, your personal coach for quality time with your children. How can I help you today?";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("greeting", greeting);
        data.put("welcome_message", welcomeMessage);
        data.put("father_name", displayName);
        data.put("locale", locale);
        data.put("is_new_user", isNewUser);

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleShowHelp(ToolApiController.ResolvedToolRequest request) {
        // Handle null userId for new users (no Father record exists yet)
        Optional<Father> fatherOpt = request.userId() != null
            ? fatherRepository.findById(request.userId())
            : Optional.empty();
        String locale = fatherOpt.map(f -> f.getLocale() != null ? f.getLocale() : "he").orElse("he");

        List<Map<String, String>> commands = new ArrayList<>();
        if ("he".equals(locale)) {
            commands.add(Map.of("command", "תכנן זמן איכות", "description", "לתזמן זמן איכות חדש עם הילדים"));
            commands.add(Map.of("command", "הצג לוח זמנים", "description", "לראות את זמני האיכות הקרובים"));
            commands.add(Map.of("command", "רעיונות לפעילויות", "description", "לקבל רעיונות לפעילויות מותאמות"));
            commands.add(Map.of("command", "הצג התקדמות", "description", "לראות את ההתקדמות שלך"));
            commands.add(Map.of("command", "קבע יעד שבועי", "description", "להגדיר יעד שעות שבועי"));
            commands.add(Map.of("command", "חבר יומן", "description", "לחבר את Google Calendar"));
        } else {
            commands.add(Map.of("command", "Schedule quality time", "description", "Schedule a new quality time with your children"));
            commands.add(Map.of("command", "Show schedule", "description", "See your upcoming quality times"));
            commands.add(Map.of("command", "Activity ideas", "description", "Get personalized activity ideas"));
            commands.add(Map.of("command", "Show progress", "description", "See your progress and achievements"));
            commands.add(Map.of("command", "Set weekly goal", "description", "Set your weekly hours goal"));
            commands.add(Map.of("command", "Connect calendar", "description", "Connect your Google Calendar"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commands", commands);
        data.put("locale", locale);

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleClarify(ToolApiController.ResolvedToolRequest request) {
        String topic = request.getStringParam("topic");

        // Handle null userId for new users (no Father record exists yet)
        Optional<Father> fatherOpt = request.userId() != null
            ? fatherRepository.findById(request.userId())
            : Optional.empty();
        String locale = fatherOpt.map(f -> f.getLocale() != null ? f.getLocale() : "he").orElse("he");
        String clarificationPrompt;

        if ("he".equals(locale)) {
            clarificationPrompt = "לא הצלחתי להבין. " +
                    (topic != null ? "לגבי " + topic + ", " : "") +
                    "אפשר להסביר יותר? או להקליד 'עזרה' לראות את הפקודות הזמינות.";
        } else {
            clarificationPrompt = "I didn't quite understand. " +
                    (topic != null ? "Regarding " + topic + ", " : "") +
                    "Could you explain more? Or type 'help' to see available commands.";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clarification_prompt", clarificationPrompt);
        data.put("topic", topic);
        data.put("locale", locale);

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleConnectCalendar(ToolApiController.ResolvedToolRequest request) {
        String redirectUrl = request.getStringParam("redirect_url");

        String authUrl = redirectUrl != null
                ? googleCalendarService.getAuthorizationUrl(request.userId(), redirectUrl)
                : googleCalendarService.getAuthorizationUrl(request.userId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auth_url", authUrl);
        data.put("instructions_he", "לחץ על הקישור לחיבור Google Calendar שלך");
        data.put("instructions_en", "Click the link to connect your Google Calendar");

        return ToolExecutionResponse.success(data);
    }

    private ToolExecutionResponse handleGetUpcomingQualityTime(ToolApiController.ResolvedToolRequest request) {
        Optional<UpcomingQualityTimeDto> upcoming = qualityTimeService.getUpcomingQualityTime(request.userId());

        Map<String, Object> data = new LinkedHashMap<>();
        if (upcoming.isPresent()) {
            UpcomingQualityTimeDto qt = upcoming.get();
            data.put("has_upcoming", true);
            data.put("quality_time_id", qt.id().toString());
            data.put("child_name", qt.childName());
            data.put("child_id", qt.childId());
            data.put("scheduled_start", qt.scheduledStart().toString());
            data.put("scheduled_end", qt.scheduledEnd().toString());
            data.put("status", qt.status().name());
        } else {
            data.put("has_upcoming", false);
        }

        return ToolExecutionResponse.success(data);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private int calculateChildAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 5;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private int calculateProgressPercent(WeeklyGoal goal) {
        if (goal.getTargetHours() <= 0) {
            return 0;
        }
        int progressPercent = (int) ((goal.getActualMinutes() / (goal.getTargetHours() * 60.0)) * 100);
        return Math.min(progressPercent, 100);
    }

    private List<ActivityIdea> generateActivityIdeas(int childAge, String locale, String activityType) {
        List<ActivityIdea> allIdeas = new ArrayList<>();
        boolean hebrew = "he".equals(locale);
        boolean indoorOnly = "indoor".equalsIgnoreCase(activityType);
        boolean outdoorOnly = "outdoor".equalsIgnoreCase(activityType);

        if (childAge <= 5) {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("בניית מגדל קוביות", 
                            "בנו יחד מגדל מקוביות או לגו. תנו לילד להוביל את הבנייה ולבחור את הצבעים.",
                            20, true));
                    allIdeas.add(new ActivityIdea("סיפור עם קולות",
                            "קראו יחד ספר אהוב ועשו קולות שונים לכל דמות. תנו לילד לבחור את הקולות.",
                            15, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("ציד אוצרות בחצר",
                            "צאו לחצר או לפארק הקרוב וחפשו יחד אוצרות טבע: עלים, אבנים מיוחדות, או פרחים.",
                            30, false));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("Building Block Tower",
                            "Build a tower together using blocks or Lego. Let your child lead the construction.",
                            20, true));
                    allIdeas.add(new ActivityIdea("Story Time with Voices",
                            "Read a favorite book together and use different voices for each character.",
                            15, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Backyard Treasure Hunt",
                            "Go to the backyard or nearby park and search for nature treasures together.",
                            30, false));
                }
            }
        } else if (childAge <= 10) {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("בישול יחד",
                            "הכינו יחד מתכון פשוט כמו פנקייקים או עוגיות. תנו לילד למדוד חומרים ולערבב.",
                            30, true));
                    allIdeas.add(new ActivityIdea("משחק לוח",
                            "שחקו יחד במשחק לוח מתאים לגיל. זה מפתח חשיבה אסטרטגית.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("טיול אופניים",
                            "צאו לרכיבת אופניים יחד בפארק או בשכונה. זמן איכות נהדר לשיחה.",
                            45, false));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("Cooking Together",
                            "Make a simple recipe together like pancakes or cookies. Let your child help measure.",
                            30, true));
                    allIdeas.add(new ActivityIdea("Board Game",
                            "Play an age-appropriate board game together. Great for strategic thinking.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Bike Ride",
                            "Go for a bike ride together in the park or neighborhood.",
                            45, false));
                }
            }
        } else {
            if (hebrew) {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("פרויקט DIY",
                            "בנו יחד משהו - בית ציפורים, מדף, או כל פרויקט יצירתי שהילד בוחר.",
                            45, true));
                    allIdeas.add(new ActivityIdea("לימוד מיומנות חדשה",
                            "למדו יחד משהו חדש - נגינה, שפה, או תכנות בסיסי.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("משחק כדורסל",
                            "צאו לשחק כדורסל או כדורגל יחד. זמן פעילות גופנית משותפת מחזק את הקשר.",
                            40, false));
                }
            } else {
                if (!outdoorOnly) {
                    allIdeas.add(new ActivityIdea("DIY Project",
                            "Build something together - a birdhouse, shelf, or any creative project.",
                            45, true));
                    allIdeas.add(new ActivityIdea("Learn a New Skill",
                            "Learn something new together - music, a language, or basic coding.",
                            30, true));
                }
                if (!indoorOnly) {
                    allIdeas.add(new ActivityIdea("Basketball Game",
                            "Go play basketball or soccer together. Physical activity strengthens your bond.",
                            40, false));
                }
            }
        }

        // Return first 3 ideas
        return allIdeas.subList(0, Math.min(3, allIdeas.size()));
    }
}
