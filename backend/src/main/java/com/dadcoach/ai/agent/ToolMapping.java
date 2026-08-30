package com.dadcoach.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structured tool mapping for AI understanding.
 * 
 * <p>This class provides a clear, structured mapping of all available tools,
 * organized by category with user intent triggers, capabilities, and examples.
 * The AI uses this to understand which tool to use for each user request.</p>
 * 
 * <p>Design principles:</p>
 * <ul>
 *   <li>Each tool has clear user intent triggers (keywords/phrases)</li>
 *   <li>Tools are grouped by functional category</li>
 *   <li>Each tool has explicit capabilities and limitations</li>
 *   <li>Examples help AI understand usage patterns</li>
 * </ul>
 */
public final class ToolMapping {
    
    private ToolMapping() {
        // Utility class
    }
    
    /**
     * Tool category for grouping related tools.
     */
    public enum ToolCategory {
        SCHEDULING("תזמון וקביעת פגישות", "כלים לקביעה, שינוי וביטול זמני איכות"),
        WEEKLY_GOALS("יעדים שבועיים", "כלים לניהול ומעקב יעדים שבועיים"),
        PROGRESS("התקדמות ודשבורד", "כלים להצגת התקדמות והישגים"),
        ACTIVITY("רעיונות ופעילויות", "כלים לקבלת רעיונות לפעילויות"),
        COMMUNICATION("תקשורת", "כלים לתקשורת בסיסית עם המשתמש");
        
        private final String hebrewName;
        private final String description;
        
        ToolCategory(String hebrewName, String description) {
            this.hebrewName = hebrewName;
            this.description = description;
        }
        
        public String getHebrewName() { return hebrewName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Complete tool definition with all metadata for AI understanding.
     */
    public record ToolDefinition(
        String toolName,
        ToolCategory category,
        String hebrewDescription,
        List<String> userIntentTriggers,
        List<String> capabilities,
        List<String> limitations,
        Map<String, ParameterDef> parameters,
        List<UsageExample> examples,
        String whenToUse,
        String whenNotToUse
    ) {}
    
    /**
     * Parameter definition for a tool.
     */
    public record ParameterDef(
        String name,
        String type,
        String hebrewDescription,
        boolean required,
        Object defaultValue,
        List<String> validValues
    ) {}
    
    /**
     * Usage example for AI learning.
     */
    public record UsageExample(
        String userMessage,
        String expectedTool,
        Map<String, Object> expectedParameters,
        String aiResponse
    ) {}
    
    /**
     * Get all tool definitions organized by category.
     */
    public static Map<ToolCategory, List<ToolDefinition>> getAllToolsByCategory() {
        return Map.of(
            ToolCategory.SCHEDULING, getSchedulingTools(),
            ToolCategory.WEEKLY_GOALS, getWeeklyGoalTools(),
            ToolCategory.PROGRESS, getProgressTools(),
            ToolCategory.ACTIVITY, getActivityTools(),
            ToolCategory.COMMUNICATION, getCommunicationTools()
        );
    }
    
    /**
     * Get all tool definitions as a flat list.
     */
    public static List<ToolDefinition> getAllTools() {
        return List.of(
            // Scheduling tools
            buildScheduleQualityTimeTool(),
            buildRescheduleQualityTimeTool(),
            buildCancelQualityTimeTool(),
            buildCompleteQualityTimeTool(),
            buildShowAvailableSlotsTool(),
            // Weekly goal tools
            buildSetWeeklyGoalTool(),
            buildGetWeeklyGoalStatusTool(),
            buildShowWeeklySummaryTool(),
            // Progress tools
            buildShowProgressTool(),
            buildGetDashboardLinkTool(),
            // Activity tools
            buildGetActivityIdeasTool(),
            // Communication tools
            buildGreetTool(),
            buildShowHelpTool(),
            buildClarifyTool(),
            buildAcknowledgeTool()
        );
    }
    
    // ─── Scheduling Tools ────────────────────────────────────────────────────
    
    private static List<ToolDefinition> getSchedulingTools() {
        return List.of(
            buildScheduleQualityTimeTool(),
            buildRescheduleQualityTimeTool(),
            buildCancelQualityTimeTool(),
            buildCompleteQualityTimeTool(),
            buildShowAvailableSlotsTool()
        );
    }
    
    private static ToolDefinition buildScheduleQualityTimeTool() {
        return new ToolDefinition(
            "schedule_quality_time",
            ToolCategory.SCHEDULING,
            "קביעת זמן איכות חדש עם הילד",
            List.of(
                "לקבוע", "קבע", "נקבע", "רוצה לקבוע",
                "זמן איכות", "פגישה", "להיפגש",
                "מחר", "היום", "שבת", "שישי",
                "בשעה", "ב-17:00", "בחמש",
                "עוד זמן", "זמן נוסף", "עוד אחד"
            ),
            List.of(
                "קביעת זמן איכות חדש ביומן",
                "סנכרון עם Google Calendar (אם מחובר)",
                "שליחת תזכורת שעה לפני הפגישה",
                "עדכון ההתקדמות השבועית"
            ),
            List.of(
                "לא יכול לקבוע בעבר",
                "צריך יעד שבועי לפני קביעה ראשונה",
                "מוגבל ל-14 יום קדימה"
            ),
            Map.of(
                "day_selection", new ParameterDef("day_selection", "integer", 
                    "מספר הימים מהיום (1=היום, 2=מחר, 3=מחרתיים...)", 
                    true, null, List.of("1-14")),
                "time", new ParameterDef("time", "string", 
                    "שעה בפורמט HH:mm", 
                    true, null, List.of("06:00-22:00")),
                "child_selection", new ParameterDef("child_selection", "integer", 
                    "מספר הילד (1=ראשון, 2=שני...)", 
                    false, 1, null)
            ),
            List.of(
                new UsageExample(
                    "רוצה לקבוע זמן איכות מחר בחמש",
                    "schedule_quality_time",
                    Map.of("day_selection", 2, "time", "17:00", "child_selection", 1),
                    "מעולה! קובע זמן איכות למחר ב-17:00 🎯"
                ),
                new UsageExample(
                    "נקבע שבת ב-15:00",
                    "schedule_quality_time",
                    Map.of("day_selection", 4, "time", "15:00", "child_selection", 1),
                    "סגור! קובע זמן איכות ליום שבת ב-15:00 📅"
                )
            ),
            "כשהמשתמש רוצה לקבוע זמן איכות חדש, ויש לו כבר יעד שבועי מוגדר",
            "כשאין יעד שבועי - קודם להגדיר יעד עם set_weekly_goal"
        );
    }
    
    private static ToolDefinition buildRescheduleQualityTimeTool() {
        return new ToolDefinition(
            "reschedule_quality_time",
            ToolCategory.SCHEDULING,
            "שינוי מועד זמן איכות קיים",
            List.of(
                "לשנות", "להזיז", "לדחות", "להקדים",
                "שינוי", "עדכון", "מועד אחר",
                "לא מתאים לי", "צריך לשנות"
            ),
            List.of(
                "שינוי מועד של זמן איכות מתוכנן",
                "עדכון Google Calendar",
                "שמירת ההיסטוריה"
            ),
            List.of(
                "רק לזמני איכות במצב SCHEDULED",
                "לא יכול לשנות זמן שהושלם או בוטל"
            ),
            Map.of(
                "new_day_selection", new ParameterDef("new_day_selection", "integer", 
                    "היום החדש (1=היום, 2=מחר...)", true, null, List.of("1-14")),
                "new_time", new ParameterDef("new_time", "string", 
                    "השעה החדשה בפורמט HH:mm", true, null, null)
            ),
            List.of(
                new UsageExample(
                    "אפשר להזיז למחר?",
                    "reschedule_quality_time",
                    Map.of("new_day_selection", 2, "new_time", ""),
                    "בטח! לאיזו שעה מתאים לך מחר?"
                )
            ),
            "כשיש זמן איכות מתוכנן והמשתמש רוצה לשנות את המועד",
            "כשאין זמן איכות מתוכנן - להשתמש ב-schedule_quality_time"
        );
    }
    
    private static ToolDefinition buildCancelQualityTimeTool() {
        return new ToolDefinition(
            "cancel_quality_time",
            ToolCategory.SCHEDULING,
            "ביטול זמן איכות מתוכנן",
            List.of(
                "לבטל", "ביטול", "למחוק",
                "לא יכול", "נפל לי משהו", "צריך לבטל"
            ),
            List.of(
                "ביטול זמן איכות מתוכנן",
                "מחיקה מ-Google Calendar",
                "עדכון הסטטיסטיקות"
            ),
            List.of(
                "רק לזמני איכות במצב SCHEDULED",
                "לא ניתן לשחזר אחרי ביטול"
            ),
            Map.of(),
            List.of(
                new UsageExample(
                    "צריך לבטל את זמן האיכות",
                    "cancel_quality_time",
                    Map.of(),
                    "בסדר, מבטל את זמן האיכות. רוצה לקבוע מועד אחר?"
                )
            ),
            "כשהמשתמש רוצה לבטל לגמרי זמן איכות מתוכנן",
            "כשהמשתמש רוצה רק לשנות מועד - להשתמש ב-reschedule_quality_time"
        );
    }
    
    private static ToolDefinition buildCompleteQualityTimeTool() {
        return new ToolDefinition(
            "complete_quality_time",
            ToolCategory.SCHEDULING,
            "סימון זמן איכות כהושלם",
            List.of(
                "סיימתי", "עשיתי", "היה מעולה", "הושלם",
                "היינו ביחד", "בילינו", "עשינו",
                "זמן איכות היה", "נהנינו"
            ),
            List.of(
                "סימון זמן איכות כהושלם",
                "עדכון ספירת ההשלמות השבועית",
                "עדכון הרצף (streak)",
                "בדיקה אם היעד השבועי הושג"
            ),
            List.of(
                "רק לזמני איכות במצב SCHEDULED",
                "לא ניתן לבטל השלמה"
            ),
            Map.of(
                "duration_minutes", new ParameterDef("duration_minutes", "integer", 
                    "משך הזמן בדקות", false, 30, null),
                "notes", new ParameterDef("notes", "string", 
                    "הערות על הפעילות", false, null, null)
            ),
            List.of(
                new UsageExample(
                    "סיימנו זמן איכות מעולה!",
                    "complete_quality_time",
                    Map.of("duration_minutes", 30),
                    "איזה יופי! 🎉 זמן האיכות נרשם. איך היה?"
                )
            ),
            "כשהמשתמש מדווח שביצע זמן איכות",
            "כשאין זמן איכות מתוכנן להיום"
        );
    }
    
    private static ToolDefinition buildShowAvailableSlotsTool() {
        return new ToolDefinition(
            "show_available_slots",
            ToolCategory.SCHEDULING,
            "הצגת זמנים פנויים ביומן",
            List.of(
                "מתי פנוי", "זמנים פנויים", "מה יש ביומן",
                "מתי אפשר", "איזה זמנים יש"
            ),
            List.of(
                "הצגת חלונות זמן פנויים מ-Google Calendar",
                "סינון לפי שעות פעילות (6:00-22:00)"
            ),
            List.of(
                "דורש חיבור Google Calendar",
                "מוגבל ל-7 ימים קדימה"
            ),
            Map.of(),
            List.of(
                new UsageExample(
                    "מתי יש לי פנוי השבוע?",
                    "show_available_slots",
                    Map.of(),
                    "הנה הזמנים הפנויים שלך השבוע: ..."
                )
            ),
            "כשהמשתמש רוצה לראות את הזמנים הפנויים לפני קביעה",
            "כשהמשתמש כבר יודע מתי הוא רוצה - לעבור ישר ל-schedule_quality_time"
        );
    }
    
    // ─── Weekly Goal Tools ───────────────────────────────────────────────────
    
    private static List<ToolDefinition> getWeeklyGoalTools() {
        return List.of(
            buildSetWeeklyGoalTool(),
            buildGetWeeklyGoalStatusTool(),
            buildShowWeeklySummaryTool()
        );
    }
    
    private static ToolDefinition buildSetWeeklyGoalTool() {
        return new ToolDefinition(
            "set_weekly_goal",
            ToolCategory.WEEKLY_GOALS,
            "הגדרת יעד שבועי חדש",
            List.of(
                "יעד", "מטרה", "להגדיר יעד",
                "כמה זמני איכות", "השבוע רוצה",
                "1", "2", "3", "אחד", "שניים", "שלושה"
            ),
            List.of(
                "הגדרת יעד שעות זמן איכות לשבוע",
                "הפעלת היעד מיד",
                "מעקב התקדמות אוטומטי"
            ),
            List.of(
                "יעד אחד בלבד לשבוע",
                "החלפת יעד קיים"
            ),
            Map.of(
                "target_hours", new ParameterDef("target_hours", "integer", 
                    "מספר שעות זמן איכות ליעד השבועי", true, null, List.of("1", "2", "3", "4", "5"))
            ),
            List.of(
                new UsageExample(
                    "רוצה לקבוע יעד של 2 זמני איכות השבוע",
                    "set_weekly_goal",
                    Map.of("target_hours", 2),
                    "מעולה! 🎯 היעד שלך לשבוע: 2 שעות זמן איכות"
                )
            ),
            "בתחילת שבוע חדש או כשאין יעד מוגדר",
            "כשכבר יש יעד פעיל ואין צורך לשנות אותו"
        );
    }
    
    private static ToolDefinition buildGetWeeklyGoalStatusTool() {
        return new ToolDefinition(
            "get_weekly_goal_status",
            ToolCategory.WEEKLY_GOALS,
            "הצגת מצב היעד השבועי",
            List.of(
                "מה היעד", "כמה נשאר", "איפה אני עומד",
                "מצב היעד", "התקדמות", "כמה עשיתי"
            ),
            List.of(
                "הצגת היעד הנוכחי",
                "מספר השלמות וזמנים מתוכננים",
                "כמה נשאר ליעד"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "איפה אני עומד ביעד?",
                    "get_weekly_goal_status",
                    Map.of(),
                    "היעד שלך: 2 שעות. עד עכשיו: 1 שעה הושלמה, 1 מתוכנן. נשאר: 0 🎯"
                )
            ),
            "כשהמשתמש שואל על מצב היעד או ההתקדמות",
            "כשצריך תמונה מלאה של כל ההתקדמות - להשתמש ב-show_progress"
        );
    }
    
    private static ToolDefinition buildShowWeeklySummaryTool() {
        return new ToolDefinition(
            "show_weekly_summary",
            ToolCategory.WEEKLY_GOALS,
            "סיכום השבוע שעבר",
            List.of(
                "סיכום שבוע", "איך היה השבוע", "השבוע שעבר",
                "סיכום", "מה עשיתי"
            ),
            List.of(
                "סיכום השבוע שעבר",
                "האם היעד הושג",
                "סטטיסטיקות"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "איך היה השבוע שלי?",
                    "show_weekly_summary",
                    Map.of(),
                    "סיכום השבוע: יעד 2 שעות ✅ הושג! עשית 2.5 שעות זמן איכות. כל הכבוד! 🎉"
                )
            ),
            "בתחילת שבוע חדש או כשהמשתמש מבקש סיכום",
            "באמצע השבוע כשהמשתמש שואל על מצב נוכחי - להשתמש ב-get_weekly_goal_status"
        );
    }
    
    // ─── Progress Tools ──────────────────────────────────────────────────────
    
    private static List<ToolDefinition> getProgressTools() {
        return List.of(
            buildShowProgressTool(),
            buildGetDashboardLinkTool()
        );
    }
    
    private static ToolDefinition buildShowProgressTool() {
        return new ToolDefinition(
            "show_progress",
            ToolCategory.PROGRESS,
            "הצגת התקדמות כללית",
            List.of(
                "מה המצב", "התקדמות", "איך הולך",
                "הישגים", "סטטיסטיקות", "חגורה"
            ),
            List.of(
                "הצגת חגורה נוכחית",
                "רצף זמני איכות",
                "סה\"כ זמני איכות",
                "התקדמות לחגורה הבאה"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "מה המצב שלי?",
                    "show_progress",
                    Map.of(),
                    "הנה ההתקדמות שלך 📊\\nחגורה: צהובה\\nרצף: 3 זמני איכות רצופים\\nסה\"כ: 15 זמני איכות"
                )
            ),
            "כשהמשתמש שואל על המצב הכללי או ההישגים",
            "כשהמשתמש שואל ספציפית על היעד השבועי - להשתמש ב-get_weekly_goal_status"
        );
    }
    
    private static ToolDefinition buildGetDashboardLinkTool() {
        return new ToolDefinition(
            "get_dashboard_link",
            ToolCategory.PROGRESS,
            "קישור לדשבורד האישי",
            List.of(
                "דשבורד", "לראות יותר", "קישור",
                "אתר", "התקדמות מפורטת"
            ),
            List.of(
                "יצירת קישור אישי לדשבורד",
                "הקישור תקף ל-24 שעות"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "רוצה לראות את הדשבורד",
                    "get_dashboard_link",
                    Map.of(),
                    "הנה הקישור לדשבורד שלך 📊 [קישור]"
                )
            ),
            "אחרי כל פעולה משמעותית (קביעה, השלמה, הישג)",
            "לא להציע יותר מדי - פעם אחת בשיחה מספיק"
        );
    }
    
    // ─── Activity Tools ──────────────────────────────────────────────────────
    
    private static List<ToolDefinition> getActivityTools() {
        return List.of(buildGetActivityIdeasTool());
    }
    
    private static ToolDefinition buildGetActivityIdeasTool() {
        return new ToolDefinition(
            "get_activity_ideas",
            ToolCategory.ACTIVITY,
            "רעיונות לפעילויות עם הילדים",
            List.of(
                "רעיונות", "מה לעשות", "פעילות",
                "משעמם", "אין לי רעיון", "מה אפשר"
            ),
            List.of(
                "רעיונות מותאמים לגיל הילד",
                "התחשבות בתחומי עניין",
                "פעילויות לזמנים שונים (קצר/ארוך)"
            ),
            List.of(
                "רעיונות כלליים, לא מותאמים אישית לחלוטין"
            ),
            Map.of(
                "child_id", new ParameterDef("child_id", "long", 
                    "מזהה הילד (אופציונלי)", false, null, null),
                "duration_preference", new ParameterDef("duration_preference", "string", 
                    "העדפת משך (קצר/בינוני/ארוך)", false, "בינוני", List.of("קצר", "בינוני", "ארוך"))
            ),
            List.of(
                new UsageExample(
                    "מה אפשר לעשות עם הילד?",
                    "get_activity_ideas",
                    Map.of(),
                    "הנה כמה רעיונות לפעילויות: ..."
                )
            ),
            "כשהמשתמש מחפש רעיונות לפעילות",
            "כשהמשתמש כבר יודע מה הוא רוצה לעשות"
        );
    }
    
    // ─── Communication Tools ─────────────────────────────────────────────────
    
    private static List<ToolDefinition> getCommunicationTools() {
        return List.of(
            buildGreetTool(),
            buildShowHelpTool(),
            buildClarifyTool(),
            buildAcknowledgeTool()
        );
    }
    
    private static ToolDefinition buildGreetTool() {
        return new ToolDefinition(
            "greet",
            ToolCategory.COMMUNICATION,
            "ברכה ופתיחת שיחה",
            List.of(
                "היי", "שלום", "הי", "בוקר טוב", "ערב טוב",
                "מה נשמע", "מה קורה"
            ),
            List.of(
                "ברכה חמה",
                "הסבר קצר על התוכנית למשתמש חדש",
                "הכוונה לצעד הבא"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "היי",
                    "greet",
                    Map.of(),
                    "היי! 👋 מה נשמע? איך אני יכול לעזור היום?"
                )
            ),
            "בתחילת שיחה או כשהמשתמש אומר שלום",
            "באמצע שיחה כשיש הקשר ברור"
        );
    }
    
    private static ToolDefinition buildShowHelpTool() {
        return new ToolDefinition(
            "show_help",
            ToolCategory.COMMUNICATION,
            "הצגת עזרה ואפשרויות",
            List.of(
                "עזרה", "מה אפשר", "איך זה עובד",
                "לא מבין", "הסבר", "אפשרויות"
            ),
            List.of(
                "הסבר על התוכנית",
                "רשימת האפשרויות הזמינות"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "מה אפשר לעשות פה?",
                    "show_help",
                    Map.of(),
                    "הנה מה שאני יכול לעזור:\\n📅 קביעת זמני איכות\\n🎯 הגדרת יעדים\\n📊 צפייה בהתקדמות\\n💡 רעיונות לפעילויות"
                )
            ),
            "כשהמשתמש מבולבל או שואל מה האפשרויות",
            "כשיש הקשר ברור למה המשתמש רוצה"
        );
    }
    
    private static ToolDefinition buildClarifyTool() {
        return new ToolDefinition(
            "clarify",
            ToolCategory.COMMUNICATION,
            "בקשת הבהרה מהמשתמש",
            List.of(),  // No specific triggers - used when AI is confused
            List.of(
                "לשאול שאלה ממוקדת",
                "להציע אפשרויות"
            ),
            List.of(
                "להשתמש רק כשבאמת לא ברור",
                "לא לשאול שאלות שכבר נענו"
            ),
            Map.of(
                "question", new ParameterDef("question", "string", 
                    "השאלה לשאול", true, null, null)
            ),
            List.of(
                new UsageExample(
                    "בלה בלה (הודעה לא ברורה)",
                    "clarify",
                    Map.of("question", "לא הבנתי לגמרי. התכוונת לקבוע זמן איכות או משהו אחר?"),
                    "לא הבנתי לגמרי. התכוונת לקבוע זמן איכות או משהו אחר?"
                )
            ),
            "רק כשבאמת לא ברור מה המשתמש רוצה",
            "כשאפשר להבין את הכוונה מההקשר - לא לשאול"
        );
    }
    
    private static ToolDefinition buildAcknowledgeTool() {
        return new ToolDefinition(
            "acknowledge",
            ToolCategory.COMMUNICATION,
            "אישור והמשך",
            List.of(
                "כן", "סבבה", "בסדר", "אוקיי", "יאללה",
                "תודה", "מעולה", "אחלה", "טוב"
            ),
            List.of(
                "אישור שההודעה התקבלה",
                "המשך לשלב הבא בשיחה"
            ),
            List.of(),
            Map.of(),
            List.of(
                new UsageExample(
                    "סבבה",
                    "acknowledge",
                    Map.of(),
                    "מעולה! 👍 רוצה לקבוע עוד זמן איכות?"
                ),
                new UsageExample(
                    "כבר עשיתי",
                    "acknowledge",
                    Map.of(),
                    "אחלה! 👍 אז בוא נמשיך הלאה..."
                )
            ),
            "כשהמשתמש נותן אישור או תשובה קצרה חיובית",
            "כשהמשתמש שואל שאלה או מבקש משהו ספציפי"
        );
    }
    
    // ─── Utility Methods ─────────────────────────────────────────────────────
    
    /**
     * Find the best matching tool for a user message based on intent triggers.
     * Returns null if no clear match found.
     */
    public static ToolDefinition findBestMatchingTool(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        
        String lowerMessage = userMessage.toLowerCase();
        ToolDefinition bestMatch = null;
        int maxScore = 0;
        
        for (ToolDefinition tool : getAllTools()) {
            int score = calculateMatchScore(lowerMessage, tool.userIntentTriggers());
            if (score > maxScore) {
                maxScore = score;
                bestMatch = tool;
            }
        }
        
        return maxScore > 0 ? bestMatch : null;
    }
    
    private static int calculateMatchScore(String message, List<String> triggers) {
        int score = 0;
        for (String trigger : triggers) {
            if (message.contains(trigger.toLowerCase())) {
                score += trigger.length(); // Longer matches score higher
            }
        }
        return score;
    }
    
    /**
     * Generate the tool mapping documentation for the AI prompt.
     */
    public static String generateToolMappingForPrompt() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("## מיפוי כלים - מדריך לבחירת הכלי הנכון\n\n");
        sb.append("**איך להשתמש במיפוי הזה:**\n");
        sb.append("1. זהה את כוונת המשתמש מההודעה\n");
        sb.append("2. מצא את הקטגוריה המתאימה\n");
        sb.append("3. בחר את הכלי לפי ה'מתי להשתמש'\n");
        sb.append("4. אם לא מצאת כלי מתאים - רשום את הצורך בשדה tool_wish\n\n");
        
        for (ToolCategory category : ToolCategory.values()) {
            sb.append("### ").append(category.getHebrewName()).append("\n");
            sb.append("_").append(category.getDescription()).append("_\n\n");
            
            List<ToolDefinition> tools = getAllToolsByCategory().get(category);
            if (tools != null) {
                for (ToolDefinition tool : tools) {
                    sb.append("#### `").append(tool.toolName()).append("`\n");
                    sb.append("**תיאור:** ").append(tool.hebrewDescription()).append("\n");
                    sb.append("**טריגרים:** ").append(String.join(", ", tool.userIntentTriggers())).append("\n");
                    sb.append("**מתי להשתמש:** ").append(tool.whenToUse()).append("\n");
                    sb.append("**מתי לא:** ").append(tool.whenNotToUse()).append("\n");
                    
                    if (!tool.parameters().isEmpty()) {
                        sb.append("**פרמטרים:**\n");
                        for (Map.Entry<String, ParameterDef> param : tool.parameters().entrySet()) {
                            ParameterDef p = param.getValue();
                            sb.append("  - `").append(p.name()).append("` (").append(p.type()).append("): ");
                            sb.append(p.hebrewDescription());
                            if (p.required()) sb.append(" [חובה]");
                            sb.append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }
        }
        
        // Add the tool wish mechanism
        sb.append("\n### 🆕 מנגנון הצעת כלים חדשים\n");
        sb.append("אם המשתמש מבקש משהו שאין לו כלי מתאים, הוסף שדה `tool_wish` בתשובה:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"tool\": \"clarify\",\n");
        sb.append("  \"parameters\": {\"question\": \"...\"},\n");
        sb.append("  \"response\": \"...\",\n");
        sb.append("  \"tool_wish\": {\n");
        sb.append("    \"suggested_name\": \"שם_הכלי_המוצע\",\n");
        sb.append("    \"user_need\": \"תיאור הצורך של המשתמש\",\n");
        sb.append("    \"suggested_capability\": \"מה הכלי צריך לעשות\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("```\n");
        
        return sb.toString();
    }
}
