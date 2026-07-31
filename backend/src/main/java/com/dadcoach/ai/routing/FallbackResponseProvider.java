package com.dadcoach.ai.routing;

import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.conversation.ConversationType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Provides pre-written fallback responses in English (default) or Hebrew
 * for use when all AI providers fail. These are static, safe responses that maintain
 * the coaching persona's warm tone and include a re-engagement hook.
 *
 * <p>Fallback responses are the last resort in the fallback chain and are guaranteed
 * to always be available (no external dependencies).
 */
@Component
public class FallbackResponseProvider {

    private static final String FALLBACK_PROVIDER = "fallback";
    private static final String FALLBACK_MODEL = "pre-written";

    private static final Map<ConversationType, String> FALLBACK_RESPONSES_EN = Map.of(
        ConversationType.ONBOARDING,
        "Hello, dad! Great to have you here. I'm ready to accompany you on this journey. " +
            "Tell me a bit about your family? 👋",

        ConversationType.DAILY_COACHING,
        "Today is a good day to connect with your kids. Sometimes the simplest things matter most: " +
            "a question, a hug, some time together. What do you think you could do together today? 💪",

        ConversationType.FOLLOW_UP,
        "I'd love to know how it went. Every attempt counts, regardless of the outcome. " +
            "How did the moment you shared feel? 🤔",

        ConversationType.REFLECTION,
        "This week you've taken important steps as a dad. Taking a moment to reflect " +
            "is part of growth. What moment this week made you feel most connected to your kids? 🌟",

        ConversationType.CELEBRATION,
        "That deserves to be celebrated! Every step you take for your kids builds something beautiful. " +
            "Keep it up, dad. You're doing great! 🎉🙌",

        ConversationType.DIFFICULT_SITUATION,
        "I understand you're going through a difficult time. Being a dad has moments like this, and it's okay " +
            "to feel overwhelmed. I'm here to accompany you. Want to tell me a bit more? 💙",

        ConversationType.MISSION_GENERATION,
        "{\"title\": \"Connection moment\", \"description\": \"Dedicate 10 minutes to being present " +
            "with your child. Ask them about their day and listen without distractions.\", " +
            "\"category\": \"COMMUNICATION\", \"difficulty\": 2, \"estimated_minutes\": 10}",

        ConversationType.INACTIVITY_CHECK,
        "Hi there! It's been a few days since we talked. Just wanted to remind you I'm here when " +
            "you want to pick up again. How have things been at home? 😊"
    );

    private static final Map<ConversationType, String> FALLBACK_RESPONSES_HE = Map.of(
        ConversationType.ONBOARDING,
        "שלום, אבא! כיף שאתה כאן. אני מוכן ללוות אותך במסע הזה. " +
            "ספר לי קצת על המשפחה שלך? 👋",

        ConversationType.DAILY_COACHING,
        "היום יום טוב להתחבר עם הילדים שלך. לפעמים הדברים הפשוטים הכי חשובים: " +
            "שאלה, חיבוק, זמן ביחד. מה אתה חושב שתוכלו לעשות ביחד היום? 💪",

        ConversationType.FOLLOW_UP,
        "אשמח לדעת איך היה. כל ניסיון נחשב, לא משנה התוצאה. " +
            "איך הרגשת ברגע שחלקתם? 🤔",

        ConversationType.REFLECTION,
        "השבוע עשית צעדים חשובים כאבא. לקחת רגע לרפלקציה " +
            "זה חלק מהצמיחה. איזה רגע השבוע גרם לך להרגיש הכי מחובר לילדים שלך? 🌟",

        ConversationType.CELEBRATION,
        "זה ראוי לחגיגה! כל צעד שאתה עושה עבור הילדים שלך בונה משהו יפה. " +
            "המשך ככה, אבא. אתה עושה עבודה מדהימה! 🎉🙌",

        ConversationType.DIFFICULT_SITUATION,
        "אני מבין שאתה עובר תקופה קשה. להיות אבא יש רגעים כאלה, וזה בסדר " +
            "להרגיש מוצף. אני כאן ללוות אותך. רוצה לספר לי קצת יותר? 💙",

        ConversationType.MISSION_GENERATION,
        "{\"title\": \"רגע חיבור\", \"description\": \"הקדש 10 דקות להיות נוכח " +
            "עם הילד שלך. שאל אותו על היום שלו והקשב בלי הסחות דעת.\", " +
            "\"category\": \"COMMUNICATION\", \"difficulty\": 2, \"estimated_minutes\": 10}",

        ConversationType.INACTIVITY_CHECK,
        "היי! עברו כמה ימים מאז שדיברנו. רק רציתי להזכיר שאני כאן כשתרצה " +
            "להמשיך. איך הדברים בבית? 😊"
    );

    /**
     * Returns a pre-written fallback response for the given conversation type.
     * These responses are always available and never fail.
     * Default language is English.
     *
     * @param conversationType the type of conversation needing a fallback
     * @return an AiProviderResponse with the pre-written content
     */
    public AiProviderResponse getFallbackResponse(ConversationType conversationType) {
        return getFallbackResponse(conversationType, "en");
    }

    /**
     * Returns a pre-written fallback response for the given conversation type and locale.
     *
     * @param conversationType the type of conversation needing a fallback
     * @param locale the locale ("en" for English, "he" for Hebrew)
     * @return an AiProviderResponse with the pre-written content
     */
    public AiProviderResponse getFallbackResponse(ConversationType conversationType, String locale) {
        String content = getFallbackText(conversationType, locale);

        return new AiProviderResponse(
            content,
            FALLBACK_MODEL,
            FALLBACK_PROVIDER,
            0,
            0,
            "fallback",
            Duration.ZERO
        );
    }

    /**
     * Returns the pre-written fallback text for the given conversation type.
     * Default language is English.
     *
     * @param conversationType the type of conversation
     * @return the fallback text content
     */
    public String getFallbackText(ConversationType conversationType) {
        return getFallbackText(conversationType, "en");
    }

    /**
     * Returns the pre-written fallback text for the given conversation type and locale.
     *
     * @param conversationType the type of conversation
     * @param locale the locale ("en" for English, "he" for Hebrew)
     * @return the fallback text content
     */
    public String getFallbackText(ConversationType conversationType, String locale) {
        Map<ConversationType, String> responses = "he".equals(locale) 
            ? FALLBACK_RESPONSES_HE 
            : FALLBACK_RESPONSES_EN;
        String defaultFallback = "he".equals(locale)
            ? "אני כאן ללוות אותך. במה אוכל לעזור לך היום? 🙂"
            : "I'm here to accompany you. How can I help you today? 🙂";

        return responses.getOrDefault(conversationType, defaultFallback);
    }

    /**
     * @return true if a specific fallback exists for the given conversation type
     */
    public boolean hasFallbackFor(ConversationType conversationType) {
        return FALLBACK_RESPONSES_EN.containsKey(conversationType);
    }
}
