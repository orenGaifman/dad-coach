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
          "response": "בוקר טוב! 🌅 מה נשמע? מוכן לתכנן זמן איכות עם הילדים?"
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
          "response": "מעולה! 🎯 קבעתי לך זמן איכות מחר ב-17:00. תזכורת תגיע אליך שעה לפני."
        }
        ```
        
        הודעה: "אפשר לשנות להיום בערב?"
        תשובה:
        ```json
        {
          "tool": "reschedule_quality_time",
          "parameters": {
            "day_selection": 1,
            "time": "19:00"
          },
          "response": "בטח! 👍 שיניתי את זמן האיכות להיום ב-19:00. תהנו!"
        }
        ```
        
        ## חשוב
        - אם אינך בטוח מה האב רוצה, השתמש בכלי "clarify" לשאול שאלת הבהרה
        - אם האב שואל משהו שלא קשור לזמני איכות, ענה בצורה ידידותית והנחה אותו חזרה
        - תמיד החזר JSON תקין בלבד - ללא טקסט נוסף
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
            AgentTool.GREET,
            AgentTool.SHOW_HELP,
            AgentTool.CLARIFY
        );
    }
}
