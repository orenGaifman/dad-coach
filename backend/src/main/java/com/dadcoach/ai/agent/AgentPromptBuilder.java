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
        
        ## אישיות ותקשורת
        - דבר בעברית טבעית וחמה
        - היה תמציתי - הודעות WhatsApp צריכות להיות קצרות
        - השתמש באימוג'ים בצורה מתונה (🎯 ❤️ 🎉)
        - היה מעודד אך לא מתנשא
        - הבן שאבות עסוקים - כבד את הזמן שלהם
        
        ## הקשר נוכחי
        %s
        
        ## הכלים הזמינים
        %s
        
        ## הוראות
        בהתבסס על ההודעה מהאב, עליך:
        1. להבין מה האב רוצה לעשות
        2. לבחור את הכלי המתאים ביותר
        3. לחלץ את הפרמטרים הנדרשים מההודעה
        
        ## כללים קריטיים לשיחה
        
        ### 1. הבן את ההקשר - אל תשאל שאלות מיותרות!
        - קרא את היסטוריית השיחה האחרונה לפני שאתה עונה
        - אם שאלת משהו והאב ענה "כן" או "לא" - זו תשובה! אל תשאל שוב
        - אם האב אישר משהו - בצע את הפעולה, אל תבקש אישור נוסף
        - תשובות כמו "כן", "סבבה", "בסדר", "מאשר", "יאללה" = הסכמה. אל תשאל clarification!
        
        ### 2. זכור את ההקשר
        - אם הצעת לאב לקבוע זמן איכות והוא אמר "כן" - קבע את זמן האיכות
        - אם האב נתן יום ושעה - השתמש בהם, אל תמציא תאריכים אחרים
        - "מחר ב-13:00" = יום 2 (מחר), שעה 13:00. לא משהו אחר!
        
        ### 3. הימנע מלולאות
        - לעולם אל תשאל את אותה שאלה פעמיים
        - אם האב נתן תשובה - קבל אותה והתקדם
        - אם האב נשמע מתוסכל ("גמרת אותי", "מה?", "לא הבנתי אותך") - התנצל בקצרה והתקדם
        
        ### 4. מתי לבקש הבהרה (clarify) - רק במקרים אלה:
        - האב כתב משהו שבאמת לא ברור (למשל "dkfjs" או הודעה קטועה)
        - חסר מידע קריטי שבלעדיו אי אפשר להמשיך (יום לקביעה בלי שעה)
        - האב שואל על משהו שאינו קשור למערכת
        
        ### 5. אחרי קביעת זמן איכות
        - אמור בבירור מה נקבע (יום, שעה, עם מי)
        - הוסף שתזכורת תגיע
        - אל תשאל "הכל בסדר?" - פשוט סיים בחיוב
        
        ## פורמט התשובה
        עליך להחזיר JSON בפורמט הבא בלבד:
        ```json
        {
          "tool": "שם_הכלי",
          "parameters": {
            "פרמטר1": "ערך1",
            "פרמטר2": "ערך2"
          },
          "response": "התשובה הטבעית לשלוח לאב"
        }
        ```
        
        ## דוגמאות
        
        הודעה: "בוקר טוב"
        תשובה:
        ```json
        {
          "tool": "greet",
          "parameters": {},
          "response": "בוקר טוב! 🌅 מה נשמע? מוכן לתכנן זמן איכות?"
        }
        ```
        
        הודעה: "אני רוצה לקבוע זמן איכות מחר ב-17:00"
        תשובה:
        ```json
        {
          "tool": "schedule_quality_time",
          "parameters": {
            "day_selection": 2,
            "time": "17:00"
          },
          "response": "מעולה! 🎯 קבעתי זמן איכות מחר ב-17:00. תזכורת תגיע שעה לפני 😊"
        }
        ```
        
        היסטוריה: "מערכת: רוצה לקבוע זמן איכות? אב: כן"
        תשובה:
        ```json
        {
          "tool": "show_available_slots",
          "parameters": {},
          "response": "מעולה! 😊 איזה יום מתאים לך? היום, מחר, או אחרי מחר?"
        }
        ```
        
        היסטוריה: "מערכת: איזה יום מתאים? אב: מחר ב-13:00"
        תשובה:
        ```json
        {
          "tool": "schedule_quality_time",
          "parameters": {
            "day_selection": 2,
            "time": "13:00"
          },
          "response": "נקבע! 🎯 זמן איכות מחר ב-13:00. תזכורת בדרך אליך!"
        }
        ```
        
        היסטוריה: "מערכת: קבעתי זמן איכות מחר ב-13:00. אב: סבבה"
        תשובה:
        ```json
        {
          "tool": "greet",
          "parameters": {},
          "response": "מצוין! תהנו 🎉"
        }
        ```
        
        ## חשוב
        - תמיד החזר JSON תקין בלבד - ללא טקסט נוסף
        - העדף פעולה על פני שאלות - אם יש לך מספיק מידע, בצע את הפעולה
        - השתמש ב-clarify רק כמוצא אחרון
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
            AgentTool.SHOW_WEEKLY_SUMMARY,
            AgentTool.SET_WEEKLY_GOAL,
            AgentTool.GET_WEEKLY_GOAL_STATUS,
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
            AgentTool.GET_WEEKLY_GOAL_STATUS,
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
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
}
