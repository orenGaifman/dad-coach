package com.dadcoach.ai.agent;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildService;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.systemstate.AvailableSlot;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workspace.magiclink.MagicLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of ToolExecutor that routes tool calls to appropriate services.
 * 
 * <p>This class acts as a coordinator between the AI agent's tool selections
 * and the actual business services that perform the operations.</p>
 */
@Component
public class ToolExecutorImpl implements ToolExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(ToolExecutorImpl.class);
    
    private static final Set<String> SUPPORTED_TOOLS = Set.of(
        "schedule_quality_time",
        "reschedule_quality_time", 
        "cancel_quality_time",
        "show_available_slots",
        "get_activity_ideas",
        "complete_quality_time",
        "show_progress",
        "greet",
        "show_help",
        "clarify"
    );
    
    private static final Duration DEFAULT_QUALITY_TIME_DURATION = Duration.ofMinutes(60);
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");
    
    private final QualityTimeService qualityTimeService;
    private final ChildService childService;
    private final MagicLinkService magicLinkService;
    private final SystemStateLoader systemStateLoader;
    
    public ToolExecutorImpl(
            QualityTimeService qualityTimeService,
            ChildService childService,
            MagicLinkService magicLinkService,
            SystemStateLoader systemStateLoader
    ) {
        this.qualityTimeService = qualityTimeService;
        this.childService = childService;
        this.magicLinkService = magicLinkService;
        this.systemStateLoader = systemStateLoader;
    }
    
    @Override
    public boolean canExecute(String toolName) {
        return SUPPORTED_TOOLS.contains(toolName);
    }
    
    @Override
    public AgentToolResult execute(String toolName, Map<String, Object> parameters, AgentContext context) {
        log.info("Executing tool: {} with parameters: {} for father: {}", 
                 toolName, parameters, context.fatherId());
        
        try {
            return switch (toolName) {
                case "schedule_quality_time" -> executeScheduleQualityTime(parameters, context);
                case "reschedule_quality_time" -> executeRescheduleQualityTime(parameters, context);
                case "cancel_quality_time" -> executeCancelQualityTime(parameters, context);
                case "show_available_slots" -> executeShowAvailableSlots(context);
                case "get_activity_ideas" -> executeGetActivityIdeas(parameters, context);
                case "complete_quality_time" -> executeCompleteQualityTime(parameters, context);
                case "show_progress" -> executeShowProgress(context);
                case "greet" -> executeGreet(context);
                case "show_help" -> executeShowHelp(context);
                case "clarify" -> executeClarify(parameters, context);
                default -> AgentToolResult.failure(toolName, "כלי לא מוכר: " + toolName);
            };
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return AgentToolResult.failure(toolName, "שגיאה בביצוע הפעולה: " + e.getMessage());
        }
    }
    
    // ─── Tool Implementations ────────────────────────────────────────────────
    
    private AgentToolResult executeScheduleQualityTime(Map<String, Object> params, AgentContext context) {
        // Extract parameters
        Integer daySelection = getIntParam(params, "day_selection", 0);
        String timeStr = getStringParam(params, "time", "");
        Integer childSelection = getIntParam(params, "child_selection", 1);
        
        // Validate we have a day selection
        if (daySelection == null || daySelection == 0) {
            return AgentToolResult.success("schedule_quality_time", 
                "באיזה יום תרצה לקבוע? אפשר לומר 'היום', 'מחר', או לציין יום ספציפי.", 
                params);
        }
        
        // Validate we have a time
        if (timeStr == null || timeStr.isEmpty()) {
            return AgentToolResult.success("schedule_quality_time",
                "באיזו שעה תרצה לקבוע את זמן האיכות?",
                params);
        }
        
        // Calculate the actual date
        LocalDate targetDate = LocalDate.now(ISRAEL_ZONE).plusDays(daySelection - 1);
        LocalTime time = parseTime(timeStr);
        if (time == null) {
            return AgentToolResult.success("schedule_quality_time",
                "לא הצלחתי להבין את השעה. אפשר לנסות שוב בפורמט כמו 17:00 או '5 אחה\"צ'?",
                params);
        }
        
        Instant startTime = targetDate.atTime(time)
            .atZone(ISRAEL_ZONE)
            .toInstant();
        
        // Get the child
        Child child = getChildForScheduling(context, childSelection);
        if (child == null) {
            return AgentToolResult.failure("schedule_quality_time", 
                "לא נמצא ילד במערכת. יש להוסיף ילד קודם.");
        }
        
        // Get father ID as Long (from the internal database ID, not UUID)
        Long fatherDbId = getFatherDbId(context);
        if (fatherDbId == null) {
            return AgentToolResult.failure("schedule_quality_time", 
                "לא נמצא פרופיל אב.");
        }
        
        // Schedule the quality time
        try {
            ScheduleQualityTimeResult result = qualityTimeService.scheduleQualityTime(
                fatherDbId,
                child.getId(),
                startTime,
                DEFAULT_QUALITY_TIME_DURATION
            );
            
            // Generate dashboard link for the father
            String dashboardUrl = magicLinkService.generateMagicLink(
                fatherDbId,
                "/dashboard",
                "schedule_quality_time"
            );
            
            String dayName = formatDayName(targetDate);
            String response = String.format(
                "מעולה! 🎯 קבעתי זמן איכות עם %s ל%s ב-%s.\nתזכורת תגיע שעה לפני.\n\n📊 לצפייה בדשבורד: %s",
                child.getName(),
                dayName,
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                dashboardUrl
            );
            
            return AgentToolResult.success(
                "schedule_quality_time",
                response,
                WorkflowState.WAITING,
                params
            );
        } catch (IllegalStateException e) {
            // Calendar conflict
            return AgentToolResult.success("schedule_quality_time",
                "נראה שיש לך משהו ביומן בשעה הזו. רוצה לנסות שעה אחרת?",
                params);
        }
    }
    
    private AgentToolResult executeRescheduleQualityTime(Map<String, Object> params, AgentContext context) {
        Integer daySelection = getIntParam(params, "day_selection", 0);
        String timeStr = getStringParam(params, "time", "");
        
        // Get current quality time
        QualityTime currentQT = getCurrentQualityTime(context);
        if (currentQT == null) {
            return AgentToolResult.success("reschedule_quality_time",
                "אין כרגע זמן איכות מתוכנן לשינוי. רוצה לקבוע זמן חדש?",
                params);
        }
        
        // Validate parameters
        if (daySelection == null || daySelection == 0 || timeStr == null || timeStr.isEmpty()) {
            return AgentToolResult.success("reschedule_quality_time",
                "למתי תרצה לשנות את זמן האיכות? ציין יום ושעה.",
                params);
        }
        
        LocalDate targetDate = LocalDate.now(ISRAEL_ZONE).plusDays(daySelection - 1);
        LocalTime time = parseTime(timeStr);
        if (time == null) {
            return AgentToolResult.success("reschedule_quality_time",
                "לא הצלחתי להבין את השעה. אפשר לנסות שוב בפורמט כמו 17:00?",
                params);
        }
        
        // Cancel old and create new
        try {
            qualityTimeService.cancelQualityTime(currentQT.getId());
            
            Instant newStartTime = targetDate.atTime(time)
                .atZone(ISRAEL_ZONE)
                .toInstant();
            
            Long fatherDbId = getFatherDbId(context);
            qualityTimeService.scheduleQualityTime(
                fatherDbId,
                currentQT.getChild().getId(),
                newStartTime,
                DEFAULT_QUALITY_TIME_DURATION
            );
            
            String dayName = formatDayName(targetDate);
            String response = String.format(
                "שיניתי את זמן האיכות ל%s ב-%s 👍\nתזכורת תגיע אליך שעה לפני.",
                dayName,
                time.format(DateTimeFormatter.ofPattern("HH:mm"))
            );
            
            return AgentToolResult.success("reschedule_quality_time", response, params);
        } catch (Exception e) {
            log.error("Failed to reschedule quality time", e);
            return AgentToolResult.failure("reschedule_quality_time",
                "לא הצלחתי לשנות את זמן האיכות. אפשר לנסות שוב?");
        }
    }
    
    private AgentToolResult executeCancelQualityTime(Map<String, Object> params, AgentContext context) {
        QualityTime currentQT = getCurrentQualityTime(context);
        if (currentQT == null) {
            return AgentToolResult.success("cancel_quality_time",
                "אין כרגע זמן איכות מתוכנן לביטול.",
                params);
        }
        
        try {
            qualityTimeService.cancelQualityTime(currentQT.getId());
            return AgentToolResult.success(
                "cancel_quality_time",
                "ביטלתי את זמן האיכות. רוצה לקבוע זמן חדש?",
                WorkflowState.SCHEDULE_QUALITY_TIME,
                params
            );
        } catch (Exception e) {
            log.error("Failed to cancel quality time", e);
            return AgentToolResult.failure("cancel_quality_time",
                "לא הצלחתי לבטל את זמן האיכות. אפשר לנסות שוב?");
        }
    }
    
    private AgentToolResult executeShowAvailableSlots(AgentContext context) {
        // Load available slots from SystemStateLoader
        List<AvailableSlot> slots = systemStateLoader.loadAvailableSlots(context.fatherId(), 7);
        
        if (slots == null || slots.isEmpty()) {
            return AgentToolResult.success("show_available_slots",
                "הימים הקרובים פנויים - מתי נוח לך?",
                Map.of());
        }
        
        StringBuilder sb = new StringBuilder("הזמנים הפנויים שלך:\n\n");
        
        LocalDate lastDate = null;
        int dayNum = 0;
        
        for (int i = 0; i < Math.min(slots.size(), 10); i++) {
            AvailableSlot slot = slots.get(i);
            LocalDate slotDate = slot.startTime().atZone(ISRAEL_ZONE).toLocalDate();
            
            if (!slotDate.equals(lastDate)) {
                dayNum++;
                lastDate = slotDate;
                String dayName = formatDayName(slotDate);
                sb.append("\n📅 ").append(dayNum).append(". ").append(dayName).append("\n");
            }
            
            LocalTime startTime = slot.startTime().atZone(ISRAEL_ZONE).toLocalTime();
            LocalTime endTime = slot.endTime().atZone(ISRAEL_ZONE).toLocalTime();
            sb.append("   ⏰ ").append(startTime.format(DateTimeFormatter.ofPattern("HH:mm")))
              .append(" - ").append(endTime.format(DateTimeFormatter.ofPattern("HH:mm")))
              .append("\n");
        }
        
        sb.append("\nבאיזה יום ושעה תרצה?");
        
        return AgentToolResult.success("show_available_slots", sb.toString(), Map.of());
    }
    
    private AgentToolResult executeGetActivityIdeas(Map<String, Object> params, AgentContext context) {
        String activityType = getStringParam(params, "activity_type", "");
        
        // Get child age for context
        int childAge = 5; // default
        if (context.systemState() != null && 
            context.systemState().fatherProfile() != null &&
            context.systemState().fatherProfile().children() != null &&
            !context.systemState().fatherProfile().children().isEmpty()) {
            childAge = context.systemState().fatherProfile().children().get(0).age();
        }
        
        String ideas = generateActivityIdeas(childAge, activityType);
        return AgentToolResult.success("get_activity_ideas", ideas, params);
    }
    
    private AgentToolResult executeCompleteQualityTime(Map<String, Object> params, AgentContext context) {
        QualityTime currentQT = getCurrentQualityTime(context);
        if (currentQT == null) {
            return AgentToolResult.success("complete_quality_time",
                "אין זמן איכות פעיל לסימון כהושלם.",
                params);
        }
        
        String feedback = getStringParam(params, "feedback", null);
        
        try {
            var result = qualityTimeService.completeQualityTime(currentQT.getId(), feedback);
            
            String response = String.format(
                "כל הכבוד! 🎉 סימנתי את זמן האיכות כהושלם.\n" +
                "🔥 הרצף שלך: %d זמני איכות רצופים!\n" +
                "🥋 החגורה: %s\n\n" +
                "רוצה לקבוע את זמן האיכות הבא?",
                result.newStreak(),
                result.currentBelt() != null ? result.currentBelt().getDisplayName() : "לבנה"
            );
            
            return AgentToolResult.success(
                "complete_quality_time",
                response,
                WorkflowState.SCHEDULE_QUALITY_TIME,
                params
            );
        } catch (Exception e) {
            log.error("Failed to complete quality time", e);
            return AgentToolResult.failure("complete_quality_time",
                "לא הצלחתי לעדכן את זמן האיכות. אפשר לנסות שוב?");
        }
    }
    
    private AgentToolResult executeShowProgress(AgentContext context) {
        try {
            // Get fatherId from systemState (as Long)
            Long fatherIdLong = context.systemState() != null && 
                                context.systemState().fatherProfile() != null 
                ? context.systemState().fatherProfile().fatherId() 
                : context.fatherId().getLeastSignificantBits();
            
            String dashboardUrl = magicLinkService.generateMagicLink(
                fatherIdLong, 
                "/dashboard", 
                "show_progress"
            );
            
            StringBuilder sb = new StringBuilder("📊 הנה הסיכום שלך:\n\n");
            
            if (context.systemState() != null && context.systemState().dashboardMetrics() != null) {
                var metrics = context.systemState().dashboardMetrics();
                sb.append("🥋 חגורה: ").append(metrics.currentBelt()).append("\n");
                sb.append("🔥 רצף: ").append(metrics.currentStreak()).append(" זמני איכות\n");
                sb.append("📈 סה\"כ: ").append(metrics.totalCompleted()).append(" זמני איכות\n\n");
            }
            
            sb.append("🔗 לפרטים נוספים: ").append(dashboardUrl);
            
            return AgentToolResult.success("show_progress", sb.toString(), Map.of());
        } catch (Exception e) {
            log.error("Failed to create dashboard link", e);
            return AgentToolResult.success("show_progress",
                "לא הצלחתי ליצור קישור לדשבורד. אפשר לנסות שוב מאוחר יותר.",
                Map.of());
        }
    }
    
    private AgentToolResult executeGreet(AgentContext context) {
        String name = context.fatherName() != null ? context.fatherName() : "";
        String greeting;
        
        LocalTime now = LocalTime.now(ISRAEL_ZONE);
        if (now.isBefore(LocalTime.of(12, 0))) {
            greeting = "בוקר טוב" + (name.isEmpty() ? "" : " " + name) + "! ☀️";
        } else if (now.isBefore(LocalTime.of(17, 0))) {
            greeting = "צהריים טובים" + (name.isEmpty() ? "" : " " + name) + "! 👋";
        } else {
            greeting = "ערב טוב" + (name.isEmpty() ? "" : " " + name) + "! 🌙";
        }
        
        // Add context-aware message
        if (context.systemState() != null) {
            var nextQT = context.systemState().getNextScheduledQualityTime();
            if (nextQT != null) {
                greeting += "\n\nיש לך זמן איכות מתוכנן עם " + nextQT.childName() + ".";
            } else {
                greeting += "\n\nרוצה לקבוע זמן איכות?";
            }
        } else {
            greeting += "\n\nמה נשמע? רוצה לקבוע זמן איכות?";
        }
        
        return AgentToolResult.success("greet", greeting, Map.of());
    }
    
    private AgentToolResult executeShowHelp(AgentContext context) {
        String help = """
            🤝 הנה מה שאני יכול לעזור:
            
            📅 **קביעת זמן איכות**
            "קבע לי זמן איכות מחר ב-17:00"
            
            🔄 **שינוי זמן**
            "שנה את זמן האיכות להיום ב-19:00"
            
            ❌ **ביטול**
            "בטל את זמן האיכות"
            
            💡 **רעיונות לפעילויות**
            "תן לי רעיונות לפעילות"
            
            ✅ **סיום זמן איכות**
            "סיימנו את זמן האיכות"
            
            📊 **צפייה בהתקדמות**
            "הראה לי את ההתקדמות שלי"
            
            מה תרצה לעשות?
            """;
        
        return AgentToolResult.success("show_help", help, Map.of());
    }
    
    private AgentToolResult executeClarify(Map<String, Object> params, AgentContext context) {
        String question = getStringParam(params, "question", "לא הבנתי. אפשר להסביר שוב?");
        return AgentToolResult.success("clarify", question, params);
    }
    
    // ─── Helper Methods ────────────────────────────────────────────────
    
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        
        // Try standard HH:mm format
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {}
        
        // Try H:mm format
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ignored) {}
        
        // Try just hours
        try {
            int hour = Integer.parseInt(timeStr);
            if (hour >= 0 && hour <= 23) {
                return LocalTime.of(hour, 0);
            }
        } catch (NumberFormatException ignored) {}
        
        return null;
    }
    
    private String formatDayName(LocalDate date) {
        LocalDate today = LocalDate.now(ISRAEL_ZONE);
        
        if (date.equals(today)) {
            return "היום";
        } else if (date.equals(today.plusDays(1))) {
            return "מחר";
        } else if (date.equals(today.plusDays(2))) {
            return "מחרתיים";
        } else {
            return switch (date.getDayOfWeek()) {
                case SUNDAY -> "יום ראשון";
                case MONDAY -> "יום שני";
                case TUESDAY -> "יום שלישי";
                case WEDNESDAY -> "יום רביעי";
                case THURSDAY -> "יום חמישי";
                case FRIDAY -> "יום שישי";
                case SATURDAY -> "שבת";
            };
        }
    }
    
    private Child getChildForScheduling(AgentContext context, Integer childSelection) {
        if (context.systemState() == null || 
            context.systemState().fatherProfile() == null ||
            context.systemState().fatherProfile().children() == null ||
            context.systemState().fatherProfile().children().isEmpty()) {
            return null;
        }
        
        var children = context.systemState().fatherProfile().children();
        int index = Math.max(0, Math.min(childSelection - 1, children.size() - 1));
        var childInfo = children.get(index);
        
        // Get the actual Child entity from the service using father DB id
        Long fatherDbId = getFatherDbId(context);
        if (fatherDbId == null) {
            return null;
        }
        
        try {
            List<Child> fatherChildren = childService.getChildrenByFather(fatherDbId);
            return fatherChildren.stream()
                .filter(child -> child.getName().equals(childInfo.name()))
                .findFirst()
                .orElse(fatherChildren.isEmpty() ? null : fatherChildren.get(0));
        } catch (Exception e) {
            log.error("Failed to find child for scheduling", e);
            return null;
        }
    }
    
    private QualityTime getCurrentQualityTime(AgentContext context) {
        if (context.systemState() == null) {
            return null;
        }
        var nextQT = context.systemState().getNextScheduledQualityTime();
        if (nextQT == null) {
            return null;
        }
        // The SystemState has a summary, but we need the actual QualityTime entity
        // This would need to be fetched from the repository
        // For now, return null and let the service handle the lookup
        return null;
    }
    
    private Long getFatherDbId(AgentContext context) {
        if (context.systemState() != null && context.systemState().fatherProfile() != null) {
            return context.systemState().fatherProfile().fatherId();
        }
        return null;
    }
    
    private String generateActivityIdeas(int childAge, String activityType) {
        StringBuilder sb = new StringBuilder("💡 הנה כמה רעיונות לפעילויות");
        if (childAge > 0) {
            sb.append(" לילד בגיל ").append(childAge);
        }
        sb.append(":\n\n");
        
        if (childAge <= 3) {
            sb.append("🎨 משחק בבצק\n");
            sb.append("📚 קריאת ספר ביחד\n");
            sb.append("🏃 משחק במגרש משחקים\n");
            sb.append("🎵 שירה וריקוד\n");
            sb.append("🧱 בניה עם קוביות\n");
        } else if (childAge <= 6) {
            sb.append("🚴 רכיבה על אופניים\n");
            sb.append("🎲 משחק קופסא\n");
            sb.append("🎨 ציור ויצירה\n");
            sb.append("⚽ משחק כדור בפארק\n");
            sb.append("👨‍🍳 בישול ביחד\n");
        } else if (childAge <= 10) {
            sb.append("🏀 משחק ספורט\n");
            sb.append("🎮 משחק מחשב ביחד\n");
            sb.append("🔬 ניסוי מדעי\n");
            sb.append("🚶 טיול בטבע\n");
            sb.append("🧩 פאזל או לגו\n");
        } else {
            sb.append("🎬 צפייה בסרט ביחד\n");
            sb.append("🎯 פעילות חדשה שלא ניסיתם\n");
            sb.append("💬 שיחה על מה שמעניין אותו\n");
            sb.append("🍕 בישול ארוחה ביחד\n");
            sb.append("🏓 משחק ספורט או תחביב משותף\n");
        }
        
        sb.append("\nמה נשמע לך?");
        return sb.toString();
    }
    
    private Integer getIntParam(Map<String, Object> params, String key, Integer defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
    
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}
