package com.dadcoach.api.context;

import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.message.MessageContext.ActivityIdea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes context provider requests to the appropriate handler.
 * 
 * <p>This component maintains a registry of context provider handlers and dispatches
 * incoming context requests to the correct handler based on the provider key.</p>
 * 
 * <p>Available context providers:</p>
 * <ul>
 *   <li>family_context - Father profile and children information</li>
 *   <li>calendar_context - Calendar events and available slots</li>
 *   <li>quality_time_context - Quality time history and dashboard metrics</li>
 * </ul>
 * 
 * @see ContextProviderController
 * @see ContextProviderHandler
 */
@Component
public class ContextProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ContextProviderRouter.class);

    private final Map<String, ContextProviderHandler> handlers = new ConcurrentHashMap<>();

    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final QualityTimeRepository qualityTimeRepository;
    private final WeeklyGoalService weeklyGoalService;
    private final SystemStateLoader systemStateLoader;

    public ContextProviderRouter(
            FatherRepository fatherRepository,
            ChildRepository childRepository,
            QualityTimeRepository qualityTimeRepository,
            WeeklyGoalService weeklyGoalService,
            SystemStateLoader systemStateLoader) {
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.qualityTimeRepository = qualityTimeRepository;
        this.weeklyGoalService = weeklyGoalService;
        this.systemStateLoader = systemStateLoader;
    }

    @PostConstruct
    public void initializeHandlers() {
        handlers.put("family_context", this::handleFamilyContext);
        handlers.put("calendar_context", this::handleCalendarContext);
        handlers.put("quality_time_context", this::handleQualityTimeContext);

        log.info("ContextProviderRouter initialized with {} handlers", handlers.size());
    }

    /**
     * Dispatches a context provider request to the appropriate handler.
     *
     * @param providerKey the context provider identifier
     * @param request the context provider request
     * @return the context provider response
     */
    public ContextProviderResponse dispatch(String providerKey, ContextProviderRequest request) {
        log.info("Dispatching context provider: providerKey={}, userId={}",
                providerKey, request.userId());

        ContextProviderHandler handler = handlers.get(providerKey);
        if (handler == null) {
            log.warn("Unknown context provider requested: {}", providerKey);
            return ContextProviderResponse.providerNotFound(providerKey);
        }

        try {
            return handler.loadContext(request);
        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found during context loading: {}", e.getMessage());
            return ContextProviderResponse.userNotFound(request.userId());
        } catch (Exception e) {
            log.error("Error loading context for provider {}: {}", providerKey, e.getMessage(), e);
            return ContextProviderResponse.failure("Internal error: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    /**
     * Returns the set of available context provider keys.
     */
    public Set<String> getAvailableProviders() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    // ─── Family Context Provider ─────────────────────────────────────────────

    /**
     * Loads family context including father profile and children information.
     * 
     * <p>Returns:</p>
     * <ul>
     *   <li>Father profile (displayName, locale, timezone, welcomeStep)</li>
     *   <li>Children list (name, age, gender, interests)</li>
     *   <li>hasMultipleChildren flag</li>
     *   <li>hasGoogleCalendarConnected flag</li>
     * </ul>
     */
    private ContextProviderResponse handleFamilyContext(ContextProviderRequest request) {
        Optional<Father> fatherOpt = fatherRepository.findById(request.userId());
        
        if (fatherOpt.isEmpty()) {
            // Return empty/default data for missing user
            return ContextProviderResponse.success(buildEmptyFamilyContext());
        }

        Father father = fatherOpt.get();
        List<Child> children = childRepository.findByFatherIdAndStatus(father.getId(), "ACTIVE");

        Map<String, Object> data = new LinkedHashMap<>();
        
        // Father profile
        Map<String, Object> fatherProfile = new LinkedHashMap<>();
        fatherProfile.put("father_id", father.getId());
        fatherProfile.put("display_name", father.getDisplayName());
        fatherProfile.put("locale", father.getLocale() != null ? father.getLocale() : "he");
        fatherProfile.put("timezone", father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem");
        fatherProfile.put("welcome_step", father.getWelcomeStep() != null ? father.getWelcomeStep().name() : null);
        fatherProfile.put("onboarding_state", father.getOnboardingState() != null ? father.getOnboardingState().name() : null);
        fatherProfile.put("coaching_style", father.getCoachingStyle() != null ? father.getCoachingStyle().name() : null);
        data.put("father_profile", fatherProfile);

        // Children list
        List<Map<String, Object>> childrenList = new ArrayList<>();
        for (Child child : children) {
            Map<String, Object> childData = new LinkedHashMap<>();
            childData.put("child_id", child.getId());
            childData.put("name", child.getName());
            childData.put("age", child.getAge());
            childData.put("gender", child.getGender());
            childData.put("interests", child.getInterests() != null ? child.getInterests() : List.of());
            childData.put("developmental_bracket", child.getDevelopmentalBracket().name());
            childrenList.add(childData);
        }
        data.put("children", childrenList);

        // Flags
        data.put("has_multiple_children", children.size() > 1);
        data.put("has_google_calendar_connected", father.hasGoogleCalendarConfigured());
        data.put("children_count", children.size());

        log.info("Family context loaded for userId={}, childrenCount={}", request.userId(), children.size());
        return ContextProviderResponse.success(data);
    }

    private Map<String, Object> buildEmptyFamilyContext() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("father_profile", null);
        data.put("children", List.of());
        data.put("has_multiple_children", false);
        data.put("has_google_calendar_connected", false);
        data.put("children_count", 0);
        return data;
    }

    // ─── Calendar Context Provider ───────────────────────────────────────────

    /**
     * Loads calendar context including upcoming events and available slots.
     * 
     * <p>Returns:</p>
     * <ul>
     *   <li>Upcoming quality time events (next 7 days)</li>
     *   <li>Available time slots (next 7 days)</li>
     *   <li>Calendar connection status</li>
     * </ul>
     */
    private ContextProviderResponse handleCalendarContext(ContextProviderRequest request) {
        Optional<Father> fatherOpt = fatherRepository.findById(request.userId());
        
        if (fatherOpt.isEmpty()) {
            return ContextProviderResponse.success(buildEmptyCalendarContext());
        }

        Father father = fatherOpt.get();
        
        // Get days ahead from config, default to 7
        Integer daysAhead = request.getIntConfig("days_ahead");
        if (daysAhead == null || daysAhead < 1) {
            daysAhead = 7;
        }
        if (daysAhead > 14) {
            daysAhead = 14;
        }

        Map<String, Object> data = new LinkedHashMap<>();

        // Calendar connection status
        boolean calendarConnected = father.hasGoogleCalendarConfigured();
        data.put("calendar_connected", calendarConnected);
        data.put("timezone", father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem");

        // Upcoming quality time events (next 7 days)
        List<QualityTime> upcomingQts = qualityTimeRepository.findByFatherIdAndStatus(
                request.userId(), QualityTimeStatus.SCHEDULED);
        
        Instant now = Instant.now();
        Instant cutoff = now.plus(daysAhead, ChronoUnit.DAYS);
        
        List<Map<String, Object>> upcomingList = new ArrayList<>();
        for (QualityTime qt : upcomingQts) {
            if (qt.getScheduledStart() != null && 
                qt.getScheduledStart().isAfter(now) && 
                qt.getScheduledStart().isBefore(cutoff)) {
                
                Map<String, Object> qtData = new LinkedHashMap<>();
                qtData.put("quality_time_id", qt.getId().toString());
                qtData.put("child_id", qt.getChildId());
                qtData.put("child_name", qt.getChild() != null ? qt.getChild().getName() : null);
                qtData.put("scheduled_start", qt.getScheduledStart().toString());
                qtData.put("scheduled_end", qt.getScheduledEnd().toString());
                qtData.put("status", qt.getStatus().name());
                upcomingList.add(qtData);
            }
        }
        // Sort by start time
        upcomingList.sort((a, b) -> {
            String startA = (String) a.get("scheduled_start");
            String startB = (String) b.get("scheduled_start");
            return startA.compareTo(startB);
        });
        data.put("upcoming_quality_times", upcomingList);

        // Available slots (only if calendar is connected)
        if (calendarConnected) {
            try {
                UUID fatherUuid = new UUID(0, request.userId());
                List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(fatherUuid, daysAhead);
                
                List<Map<String, Object>> slotsList = new ArrayList<>();
                for (AvailableSlot slot : slots) {
                    Map<String, Object> slotData = new LinkedHashMap<>();
                    slotData.put("start_time", slot.startTime().toString());
                    slotData.put("end_time", slot.endTime().toString());
                    slotData.put("duration_minutes", slot.durationMinutes());
                    slotsList.add(slotData);
                }
                data.put("available_slots", slotsList);
            } catch (Exception e) {
                log.warn("Failed to load available slots for userId={}: {}", request.userId(), e.getMessage());
                data.put("available_slots", List.of());
                data.put("slots_error", "Failed to load calendar slots");
            }
        } else {
            data.put("available_slots", List.of());
        }

        log.info("Calendar context loaded for userId={}, upcomingCount={}, calendarConnected={}", 
                request.userId(), upcomingList.size(), calendarConnected);
        return ContextProviderResponse.success(data);
    }

    private Map<String, Object> buildEmptyCalendarContext() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("calendar_connected", false);
        data.put("timezone", "Asia/Jerusalem");
        data.put("upcoming_quality_times", List.of());
        data.put("available_slots", List.of());
        return data;
    }

    // ─── Quality Time Context Provider ───────────────────────────────────────

    /**
     * Loads quality time context including history and dashboard metrics.
     * 
     * <p>Returns:</p>
     * <ul>
     *   <li>Recent quality time history (last 30 days)</li>
     *   <li>Weekly goal info (target, completed, remaining)</li>
     *   <li>Dashboard metrics (streak, belt, points)</li>
     *   <li>Activity suggestions based on child age</li>
     * </ul>
     */
    private ContextProviderResponse handleQualityTimeContext(ContextProviderRequest request) {
        Optional<Father> fatherOpt = fatherRepository.findById(request.userId());
        
        if (fatherOpt.isEmpty()) {
            return ContextProviderResponse.success(buildEmptyQualityTimeContext());
        }

        Father father = fatherOpt.get();
        
        Map<String, Object> data = new LinkedHashMap<>();

        // Dashboard metrics
        Map<String, Object> dashboardMetrics = new LinkedHashMap<>();
        dashboardMetrics.put("current_belt", father.getCurrentBelt().name());
        dashboardMetrics.put("quality_time_streak", father.getQualityTimeStreak());
        dashboardMetrics.put("quality_time_longest_streak", father.getQualityTimeLongestStreak());
        dashboardMetrics.put("total_quality_times_completed", father.getTotalQualityTimesCompleted());
        dashboardMetrics.put("total_quality_minutes", father.getTotalQualityMinutes());
        dashboardMetrics.put("current_streak_weeks", father.getCurrentStreakWeeks());
        dashboardMetrics.put("longest_streak_weeks", father.getLongestStreakWeeks());
        data.put("dashboard_metrics", dashboardMetrics);

        // Weekly goal info
        Optional<WeeklyGoal> activeGoal = weeklyGoalService.getActiveGoal(request.userId());
        Map<String, Object> weeklyGoalInfo = new LinkedHashMap<>();
        if (activeGoal.isPresent()) {
            WeeklyGoal goal = activeGoal.get();
            weeklyGoalInfo.put("has_goal", true);
            weeklyGoalInfo.put("goal_id", goal.getId());
            weeklyGoalInfo.put("week_start_date", goal.getWeekStartDate().toString());
            weeklyGoalInfo.put("target_hours", goal.getTargetHours());
            weeklyGoalInfo.put("actual_minutes", goal.getActualMinutes());
            weeklyGoalInfo.put("actual_hours", goal.getActualHours());
            weeklyGoalInfo.put("progress_percent", calculateProgressPercent(goal));
            weeklyGoalInfo.put("status", goal.getStatus().name());
            weeklyGoalInfo.put("is_goal_met", goal.isGoalMet());
            weeklyGoalInfo.put("remaining_hours", Math.max(0, goal.getTargetHours() - (int) goal.getActualHours()));
            weeklyGoalInfo.put("completed_count", goal.getCompletedCount());
            weeklyGoalInfo.put("scheduled_count", goal.getScheduledCount());
        } else {
            weeklyGoalInfo.put("has_goal", false);
        }
        data.put("weekly_goal", weeklyGoalInfo);

        // Recent quality time history (last 30 days)
        Integer historyDays = request.getIntConfig("history_days");
        if (historyDays == null || historyDays < 1) {
            historyDays = 30;
        }
        if (historyDays > 90) {
            historyDays = 90;
        }

        Instant cutoff = Instant.now().minus(historyDays, ChronoUnit.DAYS);
        List<QualityTime> recentQts = qualityTimeRepository.findByFatherIdOrderByScheduledStartDesc(request.userId());
        
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (QualityTime qt : recentQts) {
            if (qt.getScheduledStart() != null && qt.getScheduledStart().isAfter(cutoff)) {
                Map<String, Object> qtData = new LinkedHashMap<>();
                qtData.put("quality_time_id", qt.getId().toString());
                qtData.put("child_id", qt.getChildId());
                qtData.put("child_name", qt.getChild() != null ? qt.getChild().getName() : null);
                qtData.put("scheduled_start", qt.getScheduledStart().toString());
                qtData.put("scheduled_end", qt.getScheduledEnd().toString());
                qtData.put("status", qt.getStatus().name());
                if (qt.getCompletedAt() != null) {
                    qtData.put("completed_at", qt.getCompletedAt().toString());
                }
                if (qt.getCompletionNotes() != null) {
                    qtData.put("notes", qt.getCompletionNotes());
                }
                historyList.add(qtData);
            }
        }
        data.put("recent_history", historyList);
        data.put("history_count", historyList.size());

        // Activity suggestions based on children's ages
        List<Child> children = childRepository.findByFatherIdAndStatus(father.getId(), "ACTIVE");
        if (!children.isEmpty()) {
            // Use youngest child's age for suggestions
            int youngestAge = children.stream()
                    .mapToInt(Child::getAge)
                    .min()
                    .orElse(5);
            
            String locale = father.getLocale() != null ? father.getLocale() : "he";
            List<ActivityIdea> ideas = generateActivityIdeas(youngestAge, locale);
            
            List<Map<String, Object>> suggestionsList = new ArrayList<>();
            for (ActivityIdea idea : ideas) {
                Map<String, Object> ideaData = new LinkedHashMap<>();
                ideaData.put("title", idea.title());
                ideaData.put("description", idea.description());
                ideaData.put("duration_minutes", idea.durationMinutes());
                ideaData.put("indoor", idea.indoor());
                suggestionsList.add(ideaData);
            }
            data.put("activity_suggestions", suggestionsList);
            data.put("suggestions_for_age", youngestAge);
        } else {
            data.put("activity_suggestions", List.of());
        }

        log.info("Quality time context loaded for userId={}, historyCount={}, hasGoal={}", 
                request.userId(), historyList.size(), activeGoal.isPresent());
        return ContextProviderResponse.success(data);
    }

    private Map<String, Object> buildEmptyQualityTimeContext() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dashboard_metrics", Map.of(
                "current_belt", "WHITE",
                "quality_time_streak", 0,
                "quality_time_longest_streak", 0,
                "total_quality_times_completed", 0,
                "total_quality_minutes", 0,
                "current_streak_weeks", 0,
                "longest_streak_weeks", 0
        ));
        data.put("weekly_goal", Map.of("has_goal", false));
        data.put("recent_history", List.of());
        data.put("history_count", 0);
        data.put("activity_suggestions", List.of());
        return data;
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private int calculateProgressPercent(WeeklyGoal goal) {
        if (goal.getTargetHours() <= 0) {
            return 0;
        }
        int progressPercent = (int) ((goal.getActualMinutes() / (goal.getTargetHours() * 60.0)) * 100);
        return Math.min(progressPercent, 100);
    }

    private List<ActivityIdea> generateActivityIdeas(int childAge, String locale) {
        List<ActivityIdea> ideas = new ArrayList<>();
        boolean hebrew = "he".equals(locale);

        if (childAge <= 5) {
            if (hebrew) {
                ideas.add(new ActivityIdea("בניית מגדל קוביות",
                        "בנו יחד מגדל מקוביות או לגו. תנו לילד להוביל את הבנייה.",
                        20, true));
                ideas.add(new ActivityIdea("סיפור עם קולות",
                        "קראו יחד ספר ועשו קולות שונים לכל דמות.",
                        15, true));
                ideas.add(new ActivityIdea("משחק בחול",
                        "בנו יחד ארמון חול או צורות שונות.",
                        30, false));
            } else {
                ideas.add(new ActivityIdea("Building blocks tower",
                        "Build a tower together with blocks or lego. Let the child lead.",
                        20, true));
                ideas.add(new ActivityIdea("Story with voices",
                        "Read a book together and make different voices for each character.",
                        15, true));
                ideas.add(new ActivityIdea("Sand play",
                        "Build a sand castle or different shapes together.",
                        30, false));
            }
        } else if (childAge <= 10) {
            if (hebrew) {
                ideas.add(new ActivityIdea("משחק לוח",
                        "שחקו יחד משחק לוח או קלפים מועדף.",
                        30, true));
                ideas.add(new ActivityIdea("כדורגל בחצר",
                        "בעטו בכדור יחד ותרגלו מסירות.",
                        25, false));
                ideas.add(new ActivityIdea("יצירה משותפת",
                        "ציירו או בנו יחד פרויקט יצירתי.",
                        30, true));
            } else {
                ideas.add(new ActivityIdea("Board game",
                        "Play a favorite board game or card game together.",
                        30, true));
                ideas.add(new ActivityIdea("Backyard soccer",
                        "Kick the ball together and practice passes.",
                        25, false));
                ideas.add(new ActivityIdea("Creative project",
                        "Draw or build a creative project together.",
                        30, true));
            }
        } else {
            if (hebrew) {
                ideas.add(new ActivityIdea("שיחה על היום",
                        "שוחחו על מה שקרה היום ושאלו שאלות פתוחות.",
                        20, true));
                ideas.add(new ActivityIdea("ספורט יחד",
                        "רכיבה על אופניים, ריצה או משחק כדורסל.",
                        40, false));
                ideas.add(new ActivityIdea("בישול יחד",
                        "הכינו יחד ארוחה או קינוח.",
                        45, true));
            } else {
                ideas.add(new ActivityIdea("Talk about the day",
                        "Chat about what happened today and ask open questions.",
                        20, true));
                ideas.add(new ActivityIdea("Sports together",
                        "Bike riding, jogging, or playing basketball.",
                        40, false));
                ideas.add(new ActivityIdea("Cooking together",
                        "Prepare a meal or dessert together.",
                        45, true));
            }
        }

        return ideas;
    }
}
