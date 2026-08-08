package com.dadcoach.ai.agent;

/**
 * Represents a tool that the AI agent can invoke during conversation handling.
 * 
 * <p>Each tool has a name, description (for the AI prompt), and parameter schema.
 * The AI agent will decide which tool to call based on user intent and context.</p>
 * 
 * @param name unique tool name (e.g., "schedule_quality_time")
 * @param description human-readable description for the AI prompt
 * @param parametersSchema JSON schema describing the tool's parameters
 */
public record AgentTool(
    String name,
    String description,
    String parametersSchema
) {
    
    // ─── Predefined Tools ────────────────────────────────────────────────
    
    /**
     * Schedule a new quality time for a specific day and time.
     */
    public static final AgentTool SCHEDULE_QUALITY_TIME = new AgentTool(
        "schedule_quality_time",
        "קבע זמן איכות חדש עם הילד. השתמש כאשר האב רוצה לקבוע זמן איכות.",
        """
        {
          "type": "object",
          "properties": {
            "day_selection": {
              "type": "integer",
              "description": "מספר היום מהרשימה (1=היום, 2=מחר, וכו'). אם לא ניתן לזהות, השתמש ב-0."
            },
            "time": {
              "type": "string",
              "description": "השעה בפורמט HH:MM (לדוגמה: 18:00). אם לא ניתן לזהות, השתמש במחרוזת ריקה."
            },
            "child_selection": {
              "type": "integer",
              "description": "מספר הילד מהרשימה (אם יש יותר מילד אחד). ברירת מחדל: 1."
            }
          },
          "required": ["day_selection", "time"]
        }
        """
    );
    
    /**
     * Reschedule an existing quality time to a new day/time.
     */
    public static final AgentTool RESCHEDULE_QUALITY_TIME = new AgentTool(
        "reschedule_quality_time",
        "שנה זמן איכות קיים. השתמש כאשר האב רוצה לשנות זמן שכבר נקבע.",
        """
        {
          "type": "object",
          "properties": {
            "day_selection": {
              "type": "integer",
              "description": "מספר היום החדש מהרשימה (1=היום, 2=מחר, וכו')"
            },
            "time": {
              "type": "string",
              "description": "השעה החדשה בפורמט HH:MM"
            }
          },
          "required": ["day_selection", "time"]
        }
        """
    );
    
    /**
     * Cancel the currently scheduled quality time.
     */
    public static final AgentTool CANCEL_QUALITY_TIME = new AgentTool(
        "cancel_quality_time",
        "בטל זמן איכות מתוכנן. השתמש כאשר האב רוצה לבטל.",
        """
        {
          "type": "object",
          "properties": {
            "reason": {
              "type": "string",
              "description": "סיבת הביטול (אופציונלי)"
            }
          }
        }
        """
    );
    
    /**
     * Show available time slots for scheduling.
     */
    public static final AgentTool SHOW_AVAILABLE_SLOTS = new AgentTool(
        "show_available_slots",
        "הצג את הזמנים הפנויים לקביעת זמן איכות. השתמש כאשר האב שואל מתי אפשר.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Get activity ideas for the quality time.
     */
    public static final AgentTool GET_ACTIVITY_IDEAS = new AgentTool(
        "get_activity_ideas",
        "הצע רעיונות לפעילויות לזמן איכות. השתמש כאשר האב מבקש רעיונות או לא יודע מה לעשות.",
        """
        {
          "type": "object",
          "properties": {
            "activity_type": {
              "type": "string",
              "description": "סוג פעילות מועדף (אופציונלי): indoor, outdoor, creative, active, quiet"
            }
          }
        }
        """
    );
    
    /**
     * Mark quality time as completed.
     */
    public static final AgentTool COMPLETE_QUALITY_TIME = new AgentTool(
        "complete_quality_time",
        "סמן זמן איכות כהושלם. השתמש כאשר האב מדווח שסיים את זמן האיכות.",
        """
        {
          "type": "object",
          "properties": {
            "feedback": {
              "type": "string",
              "description": "משוב מהאב על זמן האיכות (אופציונלי)"
            },
            "rating": {
              "type": "integer",
              "description": "דירוג 1-5 (אופציונלי)"
            }
          }
        }
        """
    );
    
    /**
     * Show progress summary (dashboard link).
     */
    public static final AgentTool SHOW_PROGRESS = new AgentTool(
        "show_progress",
        "הצג סיכום התקדמות, הישגים וקישור לדשבורד. השתמש כאשר האב שואל על ההתקדמות, החגורה, הסטטוס, או מבקש לראות את הדשבורד.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Get dashboard link - dedicated tool for when user asks for dashboard/link.
     */
    public static final AgentTool GET_DASHBOARD_LINK = new AgentTool(
        "get_dashboard_link",
        "שלח קישור לדשבורד. השתמש כאשר האב מבקש לינק, קישור, או גישה לדשבורד.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Send a greeting/welcome message - use when user says hi, hello, etc.
     */
    public static final AgentTool GREET = new AgentTool(
        "greet",
        "שלח הודעת ברכה ידידותית. השתמש כאשר האב אומר שלום, היי, מה נשמע וכו'.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Show help menu with available commands.
     */
    public static final AgentTool SHOW_HELP = new AgentTool(
        "show_help",
        "הצג תפריט עזרה עם האפשרויות הזמינות. השתמש כאשר האב לא יודע מה לעשות או מבקש עזרה.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Respond to unclear input - ask for clarification.
     */
    public static final AgentTool CLARIFY = new AgentTool(
        "clarify",
        "בקש הבהרה מהאב. השתמש כאשר לא ברור מה האב רוצה.",
        """
        {
          "type": "object",
          "properties": {
            "question": {
              "type": "string",
              "description": "השאלה להבהרה"
            }
          }
        }
        """
    );
    
    // ─── Weekly Goal Tools ────────────────────────────────────────────────
    
    /**
     * Show weekly summary and results from last week.
     */
    public static final AgentTool SHOW_WEEKLY_SUMMARY = new AgentTool(
        "show_weekly_summary",
        "הצג סיכום שבועי - תוצאות השבוע הקודם וקידום חגורה. השתמש בתחילת שבוע חדש או כשהאב מבקש לראות את הסיכום.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    /**
     * Set weekly quality time goal (1-5+ hours).
     */
    public static final AgentTool SET_WEEKLY_GOAL = new AgentTool(
        "set_weekly_goal",
        "קבע יעד שבועי לזמני איכות. השתמש כאשר האב רוצה לקבוע יעד לשבוע או אחרי סיכום שבועי.",
        """
        {
          "type": "object",
          "properties": {
            "target_hours": {
              "type": "integer",
              "description": "מספר שעות היעד לשבוע (1-5+). 1=שעה אחת, 2=שעתיים, וכו'.",
              "minimum": 1
            }
          },
          "required": ["target_hours"]
        }
        """
    );
    
    /**
     * Get current weekly goal status and progress.
     */
    public static final AgentTool GET_WEEKLY_GOAL_STATUS = new AgentTool(
        "get_weekly_goal_status",
        "הצג את הסטטוס של היעד השבועי הנוכחי - כמה ביצעת וכמה נותר. השתמש כאשר האב שואל על ההתקדמות או היעד.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
    
    // ─── Calendar Tools ────────────────────────────────────────────────
    
    /**
     * Get link to connect Google Calendar.
     */
    public static final AgentTool CONNECT_CALENDAR = new AgentTool(
        "connect_calendar",
        "שלח קישור לחיבור יומן גוגל. השתמש כאשר האב מבקש לחבר את היומן או כאשר היומן לא מחובר והאב רוצה תזכורות.",
        """
        {
          "type": "object",
          "properties": {}
        }
        """
    );
}
