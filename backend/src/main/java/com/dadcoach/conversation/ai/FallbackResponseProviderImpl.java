package com.dadcoach.conversation.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides pre-written fallback responses per conversation type.
 * All messages are static text in English (default) or Hebrew (never AI-generated).
 *
 * <p>Tracks consecutive fallback usage per father and alerts operations
 * when 3 consecutive fallbacks are reached for the same father.
 */
@Service
public class FallbackResponseProviderImpl implements FallbackResponseProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackResponseProviderImpl.class);

    private static final int MAX_CONSECUTIVE_FALLBACKS = 3;

    private static final String GENERIC_FALLBACK_EN =
            "Sorry, I'm experiencing technical difficulties. I'll get back to you soon.";

    private static final String GENERIC_FALLBACK_HE =
            "סליחה, אני חווה קשיים טכניים. אחזור אליך בקרוב.";

    /**
     * Static pre-written fallback messages per conversation type in English.
     * These are NEVER AI-generated — they are safe, pre-approved messages.
     */
    private static final Map<String, String> FALLBACK_MESSAGES_EN = Map.of(
            "DAILY_COACHING",
            "Hi there! I'm here to support you on your fatherhood journey. How can I help you today?",

            "ONBOARDING",
            "Welcome! I'm having a small technical issue, but I'll be ready to get to know you better in just a moment.",

            "DIFFICULT_SITUATION",
            "I understand you're going through a difficult time. I'm here to listen. Could you tell me a bit more about what's happening?",

            "FOLLOW_UP",
            "Great to see you again! I'm having a small technical hiccup, but I'll be able to continue our conversation soon.",

            "REFLECTION",
            "I love that you want to reflect on your experience as a dad. Give me a moment and we'll continue our conversation.",

            "INACTIVITY_CHECK",
            "Hi! I wanted to check in and see how you're doing. I'm having a small technical issue, but I'll be available soon.",

            "CELEBRATION",
            "How exciting! I want to celebrate with you. Give me a moment while I sort out a technical issue."
    );

    /**
     * Static pre-written fallback messages per conversation type in Hebrew.
     * These are NEVER AI-generated — they are safe, pre-approved messages.
     */
    private static final Map<String, String> FALLBACK_MESSAGES_HE = Map.of(
            "DAILY_COACHING",
            "שלום! אני כאן ללוות אותך במסע האבהות שלך. איך אוכל לעזור לך היום?",

            "ONBOARDING",
            "ברוך הבא! יש לי בעיה טכנית קטנה, אבל עוד רגע אהיה מוכן להכיר אותך יותר טוב.",

            "DIFFICULT_SITUATION",
            "אני מבין שאתה עובר תקופה קשה. אני כאן להקשיב. תוכל לספר לי קצת יותר על מה שקורה?",

            "FOLLOW_UP",
            "כיף לראות אותך שוב! יש לי תקלה טכנית קטנה, אבל בקרוב אוכל להמשיך את השיחה שלנו.",

            "REFLECTION",
            "אני שמח שאתה רוצה לעשות רפלקציה על החוויה שלך כאבא. תן לי רגע ונמשיך לדבר.",

            "INACTIVITY_CHECK",
            "היי! רציתי לבדוק איך אתה מרגיש. יש לי בעיה טכנית קטנה, אבל בקרוב אהיה זמין.",

            "CELEBRATION",
            "איזה כיף! אני רוצה לחגוג איתך. תן לי רגע בזמן שאני מסדר בעיה טכנית."
    );

    /**
     * Tracks consecutive fallback count per father.
     * Key: fatherId, Value: consecutive fallback counter.
     */
    private final ConcurrentHashMap<UUID, AtomicInteger> consecutiveFallbackCounts =
            new ConcurrentHashMap<>();

    @Override
    public String getForType(String conversationType) {
        return getForType(conversationType, "en");
    }

    /**
     * Gets a fallback response for the given conversation type and locale.
     *
     * @param conversationType the type of conversation
     * @param locale the locale code ("en" for English, "he" for Hebrew)
     * @return the appropriate fallback message
     */
    public String getForType(String conversationType, String locale) {
        if (conversationType == null || conversationType.isBlank()) {
            return getGenericFallback(locale);
        }

        Map<String, String> messages = "he".equals(locale) ? FALLBACK_MESSAGES_HE : FALLBACK_MESSAGES_EN;
        String genericFallback = "he".equals(locale) ? GENERIC_FALLBACK_HE : GENERIC_FALLBACK_EN;

        return messages.getOrDefault(conversationType.toUpperCase(), genericFallback);
    }

    @Override
    public String getGenericFallback() {
        return GENERIC_FALLBACK_EN;
    }

    /**
     * Gets the generic fallback message for the given locale.
     *
     * @param locale the locale code ("en" for English, "he" for Hebrew)
     * @return the generic fallback message
     */
    public String getGenericFallback(String locale) {
        return "he".equals(locale) ? GENERIC_FALLBACK_HE : GENERIC_FALLBACK_EN;
    }

    /**
     * Records a fallback usage for a father and checks the consecutive limit.
     * If 3 consecutive fallbacks are reached, logs an operations alert and resets the counter.
     *
     * @param fatherId the father who received a fallback response
     */
    public void recordFallbackUsage(UUID fatherId) {
        AtomicInteger counter = consecutiveFallbackCounts
                .computeIfAbsent(fatherId, k -> new AtomicInteger(0));

        int count = counter.incrementAndGet();

        if (count >= MAX_CONSECUTIVE_FALLBACKS) {
            log.error("OPERATIONS ALERT: Father {} has received {} consecutive fallback responses. " +
                    "AI service may be degraded.", fatherId, count);
            counter.set(0);
        }
    }

    /**
     * Resets the consecutive fallback counter for a father.
     * Called when the father receives a successful (non-fallback) AI response.
     *
     * @param fatherId the father who received a successful response
     */
    public void resetFallbackCount(UUID fatherId) {
        consecutiveFallbackCounts.remove(fatherId);
    }

    /**
     * Returns the current consecutive fallback count for a father.
     * Primarily for testing and monitoring.
     *
     * @param fatherId the father to check
     * @return current consecutive fallback count, or 0 if none recorded
     */
    public int getConsecutiveFallbackCount(UUID fatherId) {
        AtomicInteger counter = consecutiveFallbackCounts.get(fatherId);
        return counter != null ? counter.get() : 0;
    }
}
