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
 * All messages are static text in conversational Latin American Spanish (never AI-generated).
 *
 * <p>Tracks consecutive fallback usage per father and alerts operations
 * when 3 consecutive fallbacks are reached for the same father.
 */
@Service
public class FallbackResponseProviderImpl implements FallbackResponseProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackResponseProviderImpl.class);

    private static final int MAX_CONSECUTIVE_FALLBACKS = 3;

    private static final String GENERIC_FALLBACK =
            "Disculpa, estoy experimentando dificultades técnicas. Volveré contigo pronto.";

    /**
     * Static pre-written fallback messages per conversation type.
     * These are NEVER AI-generated — they are safe, pre-approved messages
     * in conversational Latin American Spanish.
     */
    private static final Map<String, String> FALLBACK_MESSAGES = Map.of(
            "DAILY_COACHING",
            "¡Hola! Estoy aquí para acompañarte en tu camino como papá. ¿En qué puedo ayudarte hoy?",

            "ONBOARDING",
            "¡Bienvenido! Estoy teniendo un pequeño problema técnico, pero en unos minutos estaré listo para conocerte mejor.",

            "DIFFICULT_SITUATION",
            "Entiendo que estás pasando por un momento difícil. Estoy aquí para escucharte. ¿Podrías contarme un poco más?",

            "FOLLOW_UP",
            "¡Qué bueno verte de vuelta! Tengo un pequeño inconveniente técnico, pero pronto podré retomar nuestra conversación.",

            "REFLECTION",
            "Me encanta que quieras reflexionar sobre tu experiencia como papá. Dame un momento y seguimos conversando.",

            "INACTIVITY_CHECK",
            "¡Hola! Quería saber cómo estás. Estoy teniendo un pequeño problema técnico, pero pronto estaré disponible.",

            "CELEBRATION",
            "¡Qué emoción! Quiero celebrar contigo. Dame un momento mientras soluciono un tema técnico."
    );

    /**
     * Tracks consecutive fallback count per father.
     * Key: fatherId, Value: consecutive fallback counter.
     */
    private final ConcurrentHashMap<UUID, AtomicInteger> consecutiveFallbackCounts =
            new ConcurrentHashMap<>();

    @Override
    public String getForType(String conversationType) {
        if (conversationType == null || conversationType.isBlank()) {
            return GENERIC_FALLBACK;
        }
        return FALLBACK_MESSAGES.getOrDefault(conversationType.toUpperCase(), GENERIC_FALLBACK);
    }

    @Override
    public String getGenericFallback() {
        return GENERIC_FALLBACK;
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
