package com.dadcoach.workflow.message;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides pre-written fallback message templates for use when AI generation fails.
 * 
 * <p>Templates are loaded from the message_templates table at startup and cached
 * in memory for instant access. Each {@link MessageType} has templates in both
 * English ("en") and Hebrew ("he").</p>
 * 
 * <p>Placeholder syntax uses {@code {placeholder_name}} format. Common placeholders:</p>
 * <ul>
 *   <li>{fatherName} - Father's display name</li>
 *   <li>{childName} - Child's name</li>
 *   <li>{time} - Formatted time string</li>
 *   <li>{streak} - Current streak count</li>
 *   <li>{belt} - Current belt level</li>
 * </ul>
 * 
 * <p>Implements Requirement 10.4: Every message type SHALL have a corresponding
 * fallback template in both English and Hebrew.</p>
 * 
 * @see MessageGenerator
 * @see MessageTemplate
 */
@Component
public class FallbackMessages {

    private static final Logger log = LoggerFactory.getLogger(FallbackMessages.class);

    private final MessageTemplateRepository templateRepository;
    
    // Cache: MessageType -> (Language -> Template Text)
    private final Map<MessageType, Map<String, String>> templateCache = new ConcurrentHashMap<>();

    public FallbackMessages(MessageTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Load all active templates from database at startup.
     */
    @PostConstruct
    public void loadTemplates() {
        log.info("Loading fallback message templates from database...");
        List<MessageTemplate> templates = templateRepository.findByActive(true);
        
        for (MessageTemplate template : templates) {
            try {
                MessageType messageType = MessageType.fromTemplateKey(template.getMessageType());
                templateCache
                    .computeIfAbsent(messageType, k -> new ConcurrentHashMap<>())
                    .put(template.getLanguage(), template.getTemplateText());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown message type in database: {}", template.getMessageType());
            }
        }
        
        log.info("Loaded {} fallback templates for {} message types", 
            templates.size(), templateCache.size());
        
        // Log any missing templates
        for (MessageType type : MessageType.values()) {
            if (!templateCache.containsKey(type)) {
                log.warn("No fallback template found for message type: {}", type);
            } else {
                Map<String, String> langTemplates = templateCache.get(type);
                if (!langTemplates.containsKey(MessageContext.LOCALE_ENGLISH)) {
                    log.warn("No English fallback template for: {}", type);
                }
                if (!langTemplates.containsKey(MessageContext.LOCALE_HEBREW)) {
                    log.warn("No Hebrew fallback template for: {}", type);
                }
            }
        }
    }

    /**
     * Get the fallback template for a message type and language.
     * 
     * <p>If no template exists for the specified language, falls back to English.
     * If no English template exists, returns a generic error message.</p>
     * 
     * @param type the message type
     * @param language the target language ("en" or "he")
     * @return the template text with placeholders
     */
    public String get(MessageType type, String language) {
        Map<String, String> langTemplates = templateCache.get(type);
        
        if (langTemplates == null) {
            log.warn("No templates cached for message type: {}. Using default.", type);
            return getDefaultTemplate(type, language);
        }
        
        // Try requested language first
        String template = langTemplates.get(language);
        if (template != null) {
            return template;
        }
        
        // Fall back to English
        template = langTemplates.get(MessageContext.LOCALE_ENGLISH);
        if (template != null) {
            log.debug("No {} template for {}, using English fallback", language, type);
            return template;
        }
        
        // Last resort: return any available template
        if (!langTemplates.isEmpty()) {
            String fallbackLang = langTemplates.keySet().iterator().next();
            log.warn("Using {} template as fallback for {}", fallbackLang, type);
            return langTemplates.get(fallbackLang);
        }
        
        // No template at all - use hardcoded default
        log.error("No templates available for message type: {}", type);
        return getDefaultTemplate(type, language);
    }

    /**
     * Get the fallback template for a message type, defaulting to English.
     * 
     * <p>This is a convenience method equivalent to calling {@code get(type, "en")}.
     * Use this when the father's language preference is not available.</p>
     * 
     * @param type the message type
     * @return the English template text with placeholders
     */
    public String get(MessageType type) {
        return get(type, MessageContext.LOCALE_ENGLISH);
    }

    /**
     * Get the fallback template for a message type using the context's locale.
     * 
     * @param type the message type
     * @param context the message context containing locale information
     * @return the template text with placeholders
     */
    public String get(MessageType type, MessageContext context) {
        return get(type, context.getLocale());
    }

    /**
     * Substitute placeholders in a template with values from the context.
     * 
     * <p>Placeholder format: {@code {placeholderName}}</p>
     * 
     * <p>For time placeholders, this method uses timezone-aware formatting
     * via {@link MessageContext#formatTimeInTimezone} to ensure all times
     * are displayed in the father's configured timezone (Requirement 5.7).</p>
     * 
     * <p>For date placeholders ({tomorrow}, {today}), this method uses timezone-aware
     * date calculation via {@link MessageContext#getTomorrowInTimezone()} and
     * {@link MessageContext#getTodayInTimezone()} to ensure dates are correctly
     * computed in the father's timezone (Requirements 2.7, 2.8).</p>
     * 
     * @param template the template text with placeholders
     * @param context the message context with values
     * @return the template with placeholders replaced
     */
    public String substitute(String template, MessageContext context) {
        if (template == null) {
            return "";
        }
        
        String result = template;
        
        // Substitute common placeholders
        if (context.getFatherName() != null) {
            result = result.replace("{fatherName}", context.getFatherName());
        }
        
        // Always substitute {childName} - use fallback text if null or empty
        String childName = context.getChildName();
        if (childName != null && !childName.isBlank()) {
            result = result.replace("{childName}", childName);
        } else {
            // Fallback: use generic text based on locale
            String fallbackChildName = MessageContext.LOCALE_HEBREW.equals(context.getLocale()) 
                ? "הילד/ה" 
                : "your child";
            result = result.replace("{childName}", fallbackChildName);
        }
        
        // Time formatting: Use pre-formatted time or format using timezone-aware method
        if (context.getScheduledTimeFormatted() != null) {
            result = result.replace("{time}", context.getScheduledTimeFormatted());
        } else if (context.getScheduledStart() != null) {
            // Fallback: format using the timezone-aware utility method (Requirement 5.7)
            String formattedTime = context.formatTimeInTimezone(context.getScheduledStart());
            result = result.replace("{time}", formattedTime);
        }
        
        // Timezone-aware date placeholders (Requirements 2.7, 2.8)
        // {tomorrow} - tomorrow's date formatted for locale
        if (result.contains("{tomorrow}")) {
            LocalDate tomorrow = context.getTomorrowInTimezone();
            String formattedTomorrow = formatDateForLocale(tomorrow, context);
            
            // Validate day-of-week matches (Requirement 2.9)
            if (!context.validateDayOfWeek(tomorrow, formattedTomorrow)) {
                log.warn("Day-of-week validation failed for tomorrow: date={}, formatted={}", 
                    tomorrow, formattedTomorrow);
            }
            
            result = result.replace("{tomorrow}", formattedTomorrow);
        }
        
        // {today} - today's date formatted for locale
        if (result.contains("{today}")) {
            LocalDate today = context.getTodayInTimezone();
            String formattedToday = formatDateForLocale(today, context);
            
            // Validate day-of-week matches (Requirement 2.9)
            if (!context.validateDayOfWeek(today, formattedToday)) {
                log.warn("Day-of-week validation failed for today: date={}, formatted={}", 
                    today, formattedToday);
            }
            
            result = result.replace("{today}", formattedToday);
        }
        
        if (context.getStreakCount() != null) {
            result = result.replace("{streak}", String.valueOf(context.getStreakCount()));
        }
        if (context.getCurrentBelt() != null) {
            result = result.replace("{belt}", formatBeltName(context.getCurrentBelt().name()));
        }
        if (context.getBeltEarned() != null) {
            result = result.replace("{newBelt}", formatBeltName(context.getBeltEarned().name()));
        }
        if (context.getQualityTimeCount() != null) {
            result = result.replace("{qualityTimeCount}", String.valueOf(context.getQualityTimeCount()));
        }
        if (context.getDashboardUrl() != null) {
            result = result.replace("{dashboardUrl}", context.getDashboardUrl());
        }
        if (context.getChildAge() != null) {
            result = result.replace("{childAge}", String.valueOf(context.getChildAge()));
        }
        
        return result;
    }

    /**
     * Get the fallback message fully processed with substitutions.
     * 
     * <p>For SCHEDULE_SLOTS messages, this method appends a numbered list of 
     * available time slots to the template if slots are provided in the context.</p>
     * 
     * @param type the message type
     * @param context the message context
     * @return the fully processed message text
     */
    public String getProcessed(MessageType type, MessageContext context) {
        String template = get(type, context.getLocale());
        String result = substitute(template, context);
        
        // For SCHEDULE_SLOTS, append the formatted slot list
        if (type == MessageType.SCHEDULE_SLOTS && context.hasTimeSlots()) {
            result = appendSlotList(result, context);
        }
        
        return result;
    }
    
    /**
     * Appends a numbered list of available slots to the message.
     * 
     * <p>Format:</p>
     * <pre>
     * בחר זמן לזמן איכות עם הילד:
     * 
     * 1️⃣ יום שני, 15 בינואר, 15:30 - 17:30 (שעתיים)
     * 2️⃣ יום שלישי, 16 בינואר, 10:00 - 11:00 (שעה)
     * ...
     * 
     * הקלד מספר (1-5) לבחירה, 'דלג' לתזמון מאוחר יותר, או 'עוד' לאפשרויות נוספות.
     * </pre>
     * 
     * @param message the base message (header)
     * @param context the message context with slots
     * @return the message with appended slot list
     */
    private String appendSlotList(String message, MessageContext context) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n\n");
        
        String[] numberEmojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣"};
        
        int index = 0;
        for (var slot : context.getTimeSlots()) {
            if (index >= numberEmojis.length) break;
            
            String formattedSlot = context.formatSlotInTimezone(slot);
            sb.append(numberEmojis[index]).append(" ").append(formattedSlot).append("\n");
            index++;
        }
        
        // Add instructions at the end
        boolean isHebrew = MessageContext.LOCALE_HEBREW.equals(context.getLocale());
        int slotCount = context.getTimeSlots().size();
        
        sb.append("\n");
        if (isHebrew) {
            sb.append("הקלד מספר (1-").append(slotCount).append(") לבחירה, 'דלג' לתזמון מאוחר יותר, או 'עוד' לאפשרויות נוספות.");
        } else {
            sb.append("Reply with a number (1-").append(slotCount).append(") to select, 'skip' to schedule later, or 'more' for additional options.");
        }
        
        return sb.toString();
    }

    /**
     * Check if a template exists for a given message type and language.
     * 
     * @param type the message type
     * @param language the language code
     * @return true if a template exists
     */
    public boolean hasTemplate(MessageType type, String language) {
        Map<String, String> langTemplates = templateCache.get(type);
        return langTemplates != null && langTemplates.containsKey(language);
    }

    /**
     * Refresh templates from database.
     * Useful for runtime template updates without restart.
     */
    public void refreshTemplates() {
        templateCache.clear();
        loadTemplates();
    }

    /**
     * Provides hardcoded default templates when database templates are missing.
     */
    private String getDefaultTemplate(MessageType type, String language) {
        boolean isHebrew = MessageContext.LOCALE_HEBREW.equals(language);
        
        return switch (type) {
            case WELCOME_GREETING -> isHebrew 
                ? "שלום {fatherName}! ברוך הבא ל-Dad Coach. אני כאן לעזור לך לבנות הרגל של זמן איכות עם הילדים שלך. 👨‍👧 מוכן להתחיל?"
                : "Hi {fatherName}! Welcome to Dad Coach. I'm here to help you build a habit of quality time with your kids. 👨‍👧 Ready to get started?";
                
            case WELCOME_EXPLAIN -> isHebrew
                ? "Dad Coach עוזר לך לתכנן ולעקוב אחרי זמן איכות עם הילדים שלך. תזמן מפגשים, השלם אותם, וצבור התקדמות במערכת החגורות שלנו. פשוט וקל! מוכן לתאם את המפגש הראשון שלך?"
                : "Dad Coach helps you plan and track quality time with your kids. Schedule sessions, complete them, and earn progress in our belt system. Simple and easy! Ready to schedule your first session?";
                
            case SCHEDULE_SLOTS -> isHebrew
                ? "בחר זמן לזמן איכות עם {childName}:"
                : "Choose a time for Quality Time with {childName}:";
                
            case SCHEDULE_CONFIRM -> isHebrew
                ? "מעולה! זמן איכות עם {childName} נקבע ל-{time}. תהנו! 💪"
                : "Great! Quality Time with {childName} is scheduled for {time}. Enjoy! 💪";
                
            case SCHEDULE_NO_SLOTS -> isHebrew
                ? "לא מצאתי זמנים פנויים ביומן שלך לשבוע הקרוב. אנא בדוק את היומן שלך או נסה שוב מאוחר יותר."
                : "I couldn't find any available slots in your calendar for the next week. Please check your calendar or try again later.";
                
            case WAITING_REMINDER -> isHebrew
                ? "בוקר טוב {fatherName}! זמן איכות עם {childName} היום ב-{time}. תהנו! 💪"
                : "Good morning {fatherName}! Quality Time with {childName} today at {time}. Have a great time! 💪";
                
            case WAITING_SCHEDULE_INFO -> isHebrew
                ? "זמן האיכות הבא שלך עם {childName} הוא ב-{time}."
                : "Your next Quality Time with {childName} is at {time}.";
                
            case FOLLOW_UP_QUESTION -> isHebrew
                ? "השלמת את זמן האיכות עם {childName}?"
                : "Did you complete your Quality Time with {childName}?";
                
            case FOLLOW_UP_COMPLETED -> isHebrew
                ? "כל הכבוד {fatherName}! 🎉 הרצף שלך עכשיו {streak}. המשך כך!"
                : "Awesome {fatherName}! 🎉 Your streak is now {streak}. Keep it up!";
                
            case FOLLOW_UP_MISSED -> isHebrew
                ? "לא נורא {fatherName}, יש עוד הזדמנויות. בוא נתאם זמן איכות חדש."
                : "No worries {fatherName}, there will be more opportunities. Let's schedule another Quality Time.";
                
            case ACTIVITY_IDEAS -> isHebrew
                ? "הנה כמה רעיונות לזמן איכות עם {childName}:"
                : "Here are some ideas for Quality Time with {childName}:";
                
            case DASHBOARD_SUMMARY -> isHebrew
                ? "📊 ההתקדמות שלך:\n🥋 חגורה: {belt}\n🔥 רצף: {streak}\n✅ סה\"כ מפגשים: {qualityTimeCount}\n\nלצפייה בדשבורד המלא: {dashboardUrl}"
                : "📊 Your progress:\n🥋 Belt: {belt}\n🔥 Streak: {streak}\n✅ Total sessions: {qualityTimeCount}\n\nView full dashboard: {dashboardUrl}";
                
            case CLARIFICATION -> isHebrew
                ? "לא הבנתי את התשובה. אנא בחר אחת מהאפשרויות."
                : "I didn't understand that. Please choose one of the options.";
                
            case ERROR_GENERIC -> isHebrew
                ? "מצטער, משהו השתבש. אנא נסה שוב."
                : "Sorry, something went wrong. Please try again.";
                
            case ERROR_SCHEDULE_QUALITY_TIME -> isHebrew
                ? "מצטער, יש לי בעיה למצוא זמנים פנויים. אפשר לנסות שוב?"
                : "Sorry, I'm having trouble finding available slots. Can you try again?";
                
            case ERROR_QUALITY_TIME_FOLLOW_UP -> isHebrew
                ? "מצטער, משהו השתבש. ספר לי - האם השלמת את זמן האיכות עם {childName}?"
                : "Sorry, something went wrong. Tell me - did you complete your Quality Time with {childName}?";
                
            case ERROR_WAITING -> isHebrew
                ? "מצטער, לא הצלחתי לעבד את ההודעה. מה תרצה לעשות? אפשר לבדוק את הלו\"ז, לקבל רעיונות לפעילויות, או לתזמן זמן איכות חדש."
                : "Sorry, I couldn't process that. What would you like to do? You can check your schedule, get activity ideas, or schedule new Quality Time.";
                
            case PROCESSING -> isHebrew
                ? "רגע {fatherName}, אני מעבד את הבקשה שלך... 🔄"
                : "One moment {fatherName}, I'm processing your request... 🔄";
                
            case FRUSTRATION_ACKNOWLEDGMENT -> isHebrew
                ? "מצטער אם זה מרגיש חוזר על עצמו - אני כאן לעזור. "
                : "Sorry if this feels repetitive - I'm here to help. ";
                
            case BELT_PROMOTION -> isHebrew
                ? "🎉🥋 מזל טוב {fatherName}! הרווחת חגורה חדשה: {newBelt}! 🎉\n\nההתמדה שלך משתלמת. המשך כך! 💪"
                : "🎉🥋 Congratulations {fatherName}! You've earned a new belt: {newBelt}! 🎉\n\nYour consistency is paying off. Keep it up! 💪";
                
            case QUALITY_TIME_REMINDER -> isHebrew
                ? "היי {fatherName}! ⏰\n\nעוד שעה יש לך זמן איכות עם {childName}.\n\nתהנו מהזמן ביחד! 💪"
                : "Hey {fatherName}! ⏰\n\nYour Quality Time with {childName} starts in about an hour.\n\nEnjoy your time together! 💪";
                
            case INACTIVITY_NUDGE -> isHebrew
                ? "היי {fatherName}! 👋\n\nהכל בסדר? לא שמעתי ממך כמה ימים.\n\nאני כאן כשתרצה לחזור לתכנן זמן איכות עם הילדים. 💪"
                : "Hey {fatherName}! 👋\n\nEverything okay? I haven't heard from you in a few days.\n\nI'm here when you're ready to plan some quality time with your kids. 💪";
                
            case COACHING_PAUSED -> isHebrew
                ? "{fatherName}, אני כאן כשתרצה לחזור. 🤗\n\nפשוט שלח הודעה ונמשיך מאיפה שהפסקנו!"
                : "{fatherName}, I'm here when you're ready to continue. 🤗\n\nJust send a message and we'll pick up where we left off!";
        };
    }
    
    /**
     * Formats a belt enum name for display (e.g., "WHITE" -> "White Belt").
     */
    private String formatBeltName(String beltName) {
        if (beltName == null || beltName.isEmpty()) {
            return "";
        }
        return beltName.charAt(0) + beltName.substring(1).toLowerCase() + " Belt";
    }
    
    /**
     * Formats a date for display according to the locale and timezone.
     * 
     * <p>This method addresses Bug 3: Date Calculation Error by ensuring dates
     * are formatted with the correct day-of-week in the father's timezone
     * (Requirements 2.7, 2.8).</p>
     * 
     * <p>Format examples:</p>
     * <ul>
     *   <li>Hebrew: "יום שישי, 22/08"</li>
     *   <li>English: "Friday, August 22"</li>
     * </ul>
     * 
     * @param date the date to format
     * @param context the message context containing locale and timezone
     * @return the formatted date string with day-of-week
     */
    private String formatDateForLocale(LocalDate date, MessageContext context) {
        if (date == null) {
            return "";
        }
        
        Locale displayLocale = context.getDisplayLocale();
        boolean isHebrew = MessageContext.LOCALE_HEBREW.equals(context.getLocale());
        
        // Get the day-of-week name
        String dayOfWeekName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, displayLocale);
        
        // Format the date part
        if (isHebrew) {
            // Hebrew format: "יום שישי, 22/08"
            // Hebrew day names are prefixed with "יום " (day) for full names
            String hebrewDayName = getHebrewDayName(date);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM");
            String datePart = date.format(dateFormatter);
            return hebrewDayName + ", " + datePart;
        } else {
            // English format: "Friday, August 22"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", displayLocale);
            return date.format(formatter);
        }
    }
    
    /**
     * Gets the Hebrew day name with proper formatting.
     * 
     * <p>In Hebrew, day names are typically written as "יום + day name" (e.g., "יום שישי").
     * The standard Java DayOfWeek.getDisplayName() returns only the day name without "יום".</p>
     * 
     * @param date the date to get the Hebrew day name for
     * @return the Hebrew day name (e.g., "יום שישי" for Friday)
     */
    private String getHebrewDayName(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SUNDAY -> "יום ראשון";
            case MONDAY -> "יום שני";
            case TUESDAY -> "יום שלישי";
            case WEDNESDAY -> "יום רביעי";
            case THURSDAY -> "יום חמישי";
            case FRIDAY -> "יום שישי";
            case SATURDAY -> "יום שבת";
        };
    }
}
