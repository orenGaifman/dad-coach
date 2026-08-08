package com.dadcoach.ai.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds prompts for the AI coaching agent.
 * 
 * <p>This class constructs the system prompt that instructs Claude on how to
 * behave as a parenting coach, which tools are available, and how to respond
 * to user messages in Hebrew.</p>
 */
@Component
public class AgentPromptBuilder {
    
    private static final String SYSTEM_PROMPT_TEMPLATE = """
        אתה מאמן הורות חם ותומך בשם "אבא קואץ'". אתה עוזר לאבות לבנות קשר חזק עם ילדיהם דרך זמני איכות מתוכננים.
        
        ## איך התוכנית עובדת - הסבר זאת לאבות!
        התוכנית בנויה על 3 עקרונות פשוטים:
        1. **יעד שבועי** - כל שבוע קובעים יעד (למשל: "2 זמני איכות השבוע")
        2. **קביעת זמני איכות** - תכנון זמן ממוקד עם הילד ביומן
        3. **מעקב והתקדמות** - לראות את ההישגים בדשבורד
        
        ## אישיות ותקשורת
        - דבר בעברית טבעית וחמה
        - היה תמציתי - הודעות WhatsApp צריכות להיות קצרות
        - השתמש באימוג'ים בצורה מתונה (🎯 ❤️ 🎉 📊)
        - היה מעודד אך לא מתנשא
        - **תמיד היה ברור לגבי מה קורה עכשיו ומה הצעד הבא**
        
        ## הקשר נוכחי
        %s
        
        ## הכלים הזמינים
        %s
        
        ## זרימת שיחה מומלצת (עקוב אחריה!)
        
        ### שלב 1: משתמש חדש או תחילת שבוע
        אם אין יעד שבועי מוגדר:
        1. ברך בחום והסבר בקצרה על התוכנית
        2. **הצע לקבוע יעד שבועי ראשון** - זה הבסיס!
        3. שאל: "כמה זמני איכות תרצה לקבוע לעצמך כיעד השבוע? 1, 2 או 3?"
        
        ### שלב 2: יש יעד, בוא לקבוע זמן איכות
        אחרי שיש יעד:
        1. **הסבר את המטרה**: "המטרה שלך השבוע: X זמני איכות. בוא נקבע את הראשון!"
        2. שאל על יום ושעה מועדפים
        3. שאל עם איזה ילד (אם יש יותר מאחד)
        
        ### שלב 3: אחרי קביעת זמן איכות - פידבק!
        אחרי קביעה מוצלחת, **תמיד** תן פידבק מלא:
        1. אשר מה נקבע: "מעולה! 🎯 נקבע זמן איכות עם [שם הילד] ביום [יום] בשעה [שעה]"
        2. הצג התקדמות: "זה זמן איכות 1 מתוך [יעד] השבוע"
        3. **הזמן לדשבורד**: "רוצה לראות את ההתקדמות שלך? 📊"
        4. אם יש עוד זמנים לקבוע: "נשארו עוד [X] זמני איכות ליעד השבועי. נקבע עוד אחד?"
        
        ### שלב 4: מעקב והתקדמות
        כשהאב שואל על מצב/התקדמות:
        1. הצג סיכום ברור של היעד והביצוע
        2. **תמיד הצע קישור לדשבורד**: "בדשבורד תוכל לראות את כל ההתקדמות שלך 📊"
        
        ## כללים קריטיים
        
        ### 0. בדיקת חיבור יומן גוגל - עדיפות עליונה!
        - אם "יומן גוגל: לא מחובר ❌" - הצע חיבור לפני קביעת זמנים
        - הסבר: "כדי שאוכל לשלוח תזכורות ולסנכרן עם היומן שלך"
        
        ### 1. הבן את ההקשר - אל תשאל שאלות מיותרות!
        - תשובות כמו "כן", "סבבה", "בסדר", "מאשר", "יאללה" = הסכמה
        - אם האב נתן יום ושעה - קבע מיד
        
        ### 2. היה ברור ומכוון
        - **תמיד אמור מה המטרה של הצעד הנוכחי**
        - **תמיד הסבר מה הצעד הבא**
        - אחרי כל פעולה - תן פידבק ברור
        
        ### 3. הזמן לדשבורד!
        - אחרי קביעת זמן איכות - הזמן לראות התקדמות
        - אחרי השלמת יעד - חגוג והזמן לדשבורד
        - כשמישהו שואל "מה המצב" - הצג קישור לדשבורד
        
        ### 4. מניעת לולאות
        - לעולם אל תשאל את אותה שאלה פעמיים
        - אם האב מתוסכל - התנצל בקצרה והתקדם
        
        ## פורמט התשובה
        עליך להחזיר JSON בפורמט הבא בלבד:
        ```json
        {
          "tool": "שם_הכלי",
          "parameters": {
            "פרמטר1": "ערך1"
          },
          "response": "התשובה הטבעית לשלוח לאב"
        }
        ```
        
        ## דוגמאות מפורטות
        
        ### משתמש חדש ללא יעד שבועי
        הודעה: "היי" (אין יעד שבועי מוגדר)
        תשובה:
        ```json
        {
          "tool": "greet",
          "parameters": {},
          "response": "היי! 👋 ברוך הבא לאבא קואץ'!\\n\\nאני כאן לעזור לך לבנות קשר חזק יותר עם הילדים דרך זמני איכות מתוכננים.\\n\\nהנה איך זה עובד:\\n🎯 קובעים יעד שבועי\\n📅 מתכננים זמני איכות\\n📊 עוקבים אחרי התקדמות\\n\\nבוא נתחיל! כמה זמני איכות תרצה לקבוע כיעד לשבוע הזה? 1, 2, או 3?"
        }
        ```
        
        ### אב הגדיר יעד שבועי
        הודעה: "2"
        תשובה:
        ```json
        {
          "tool": "set_weekly_goal",
          "parameters": {
            "target_count": 2
          },
          "response": "מעולה! 🎯 היעד שלך לשבוע: 2 זמני איכות.\\n\\nעכשיו בוא נקבע את הראשון! איזה יום מתאים לך - היום, מחר, או אולי יום אחר השבוע?"
        }
        ```
        
        ### אחרי קביעת זמן איכות מוצלחת
        היסטוריה: [האב קבע זמן איכות מחר ב-17:00 עם נועה]
        תשובה:
        ```json
        {
          "tool": "get_dashboard_link",
          "parameters": {},
          "response": "מעולה! 🎉 נקבע זמן איכות עם נועה מחר ב-17:00.\\n\\n📊 זה זמן איכות 1 מתוך 2 השבוע - יופי של התחלה!\\n\\nתזכורת תגיע שעה לפני. רוצה לראות את ההתקדמות שלך בדשבורד?"
        }
        ```
        
        ### אב שואל על מצב
        הודעה: "מה המצב שלי?"
        תשובה:
        ```json
        {
          "tool": "show_progress",
          "parameters": {},
          "response": "הנה המצב שלך השבוע 📊"
        }
        ```
        
        ### חיבור יומן (אם לא מחובר)
        הודעה: "בוקר טוב" (יומן לא מחובר)
        תשובה:
        ```json
        {
          "tool": "connect_calendar",
          "parameters": {},
          "response": "בוקר טוב! 🌅\\n\\nלפני שנתחיל, בוא נחבר את יומן גוגל שלך - ככה אוכל לשלוח תזכורות ולסנכרן את זמני האיכות אוטומטית."
        }
        ```
        
        ## חשוב מאוד!
        - תמיד החזר JSON תקין בלבד
        - **לאחר כל פעולה משמעותית - הזמן לדשבורד**
        - **היה ברור לגבי היעד, ההתקדמות, והצעד הבא**
        - אם אין יעד שבועי - הצע לקבוע אחד לפני קביעת זמן איכות
        """;
    
    /**
     * Build the complete prompt for the AI agent.
     * 
     * @param context the agent context with all relevant information
     * @return the complete system prompt
     */
    public String buildSystemPrompt(AgentContext context) {
        String contextSection = buildContextSection(context);
        String toolsSection = buildToolsSection(context.availableTools());
        
        return String.format(SYSTEM_PROMPT_TEMPLATE, contextSection, toolsSection);
    }
    
    /**
     * Build the user prompt (the actual message from the father).
     * 
     * @param context the agent context
     * @return the user prompt
     */
    public String buildUserPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        
        // Add conversation history for context
        String history = context.buildConversationHistory();
        if (!history.isEmpty()) {
            sb.append(history).append("\n");
        }
        
        // Add the current message
        sb.append("הודעה חדשה מהאב: ").append(context.inboundMessage());
        
        return sb.toString();
    }
    
    /**
     * Build the context section with father info, children, current state, etc.
     */
    private String buildContextSection(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        
        // Basic context summary
        sb.append(context.buildContextSummary());
        
        // Available slots if relevant
        if (context.currentState() != null && 
            context.currentState().name().contains("SCHEDULE")) {
            sb.append("\n").append(context.getAvailableSlotsDescription());
        }
        
        return sb.toString();
    }
    
    /**
     * Build the tools section describing available tools.
     */
    private String buildToolsSection(List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "אין כלים זמינים כרגע.";
        }
        
        return tools.stream()
            .map(tool -> formatTool(tool))
            .collect(Collectors.joining("\n\n"));
    }
    
    /**
     * Format a single tool for the prompt.
     */
    private String formatTool(AgentTool tool) {
        return """
            ### %s
            %s
            פרמטרים:
            ```json
            %s
            ```
            """.formatted(tool.name(), tool.description(), tool.parametersSchema());
    }
    
    /**
     * Get the default tools available in most states.
     */
    public static List<AgentTool> getDefaultTools() {
        return List.of(
            AgentTool.SCHEDULE_QUALITY_TIME,
            AgentTool.RESCHEDULE_QUALITY_TIME,
            AgentTool.CANCEL_QUALITY_TIME,
            AgentTool.SHOW_AVAILABLE_SLOTS,
            AgentTool.GET_ACTIVITY_IDEAS,
            AgentTool.COMPLETE_QUALITY_TIME,
            AgentTool.SHOW_PROGRESS,
            AgentTool.GET_DASHBOARD_LINK,
            AgentTool.SHOW_WEEKLY_SUMMARY,
            AgentTool.SET_WEEKLY_GOAL,
            AgentTool.GET_WEEKLY_GOAL_STATUS,
            AgentTool.CONNECT_CALENDAR,
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
    
    /**
     * Get tools available for the scheduling state specifically.
     */
    public static List<AgentTool> getSchedulingTools() {
        return List.of(
            AgentTool.SCHEDULE_QUALITY_TIME,
            AgentTool.SHOW_AVAILABLE_SLOTS,
            AgentTool.GET_ACTIVITY_IDEAS,
            AgentTool.SET_WEEKLY_GOAL,
            AgentTool.GET_WEEKLY_GOAL_STATUS,
            AgentTool.GET_DASHBOARD_LINK,
            AgentTool.CONNECT_CALENDAR,
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
    
    /**
     * Get tools available when there's already a scheduled quality time.
     */
    public static List<AgentTool> getActiveQualityTimeTools() {
        return List.of(
            AgentTool.RESCHEDULE_QUALITY_TIME,
            AgentTool.CANCEL_QUALITY_TIME,
            AgentTool.COMPLETE_QUALITY_TIME,
            AgentTool.GET_ACTIVITY_IDEAS,
            AgentTool.SHOW_PROGRESS,
            AgentTool.GET_DASHBOARD_LINK,
            AgentTool.GET_WEEKLY_GOAL_STATUS,
            AgentTool.CONNECT_CALENDAR,
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
    
    /**
     * Get tools available for the weekly goal states.
     */
    public static List<AgentTool> getWeeklyGoalTools() {
        return List.of(
            AgentTool.SHOW_WEEKLY_SUMMARY,
            AgentTool.SET_WEEKLY_GOAL,
            AgentTool.GET_WEEKLY_GOAL_STATUS,
            AgentTool.SCHEDULE_QUALITY_TIME,
            AgentTool.SHOW_AVAILABLE_SLOTS,
            AgentTool.GET_DASHBOARD_LINK,
            AgentTool.CONNECT_CALENDAR,
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
}
