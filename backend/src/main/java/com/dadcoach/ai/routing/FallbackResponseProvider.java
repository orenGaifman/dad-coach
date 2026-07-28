package com.dadcoach.ai.routing;

import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.conversation.ConversationType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Provides pre-written fallback responses in conversational Latin American Spanish
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

    private static final Map<ConversationType, String> FALLBACK_RESPONSES = Map.of(
        ConversationType.ONBOARDING,
        "¡Hola, papá! Qué gusto tenerte por acá. Estoy listo para acompañarte en este camino. " +
            "¿Me cuentas un poco sobre tu familia? 👋",

        ConversationType.DAILY_COACHING,
        "Hoy es un buen día para conectar con tus hijos. A veces lo más simple es lo que más vale: " +
            "una pregunta, un abrazo, un rato juntos. ¿Qué se te ocurre que podrían hacer hoy? 💪",

        ConversationType.FOLLOW_UP,
        "Me encantaría saber cómo te fue. Cada intento cuenta, sin importar el resultado. " +
            "¿Cómo se sintió el momento que compartieron? 🤔",

        ConversationType.REFLECTION,
        "Esta semana has dado pasos importantes como papá. Tomarte un momento para reflexionar " +
            "es parte del crecimiento. ¿Qué momento de esta semana te hizo sentir más conectado con tus hijos? 🌟",

        ConversationType.CELEBRATION,
        "¡Eso merece celebrarse! Cada paso que das por tus hijos construye algo hermoso. " +
            "Sigue así, papá. Lo estás haciendo genial 🎉🙌",

        ConversationType.DIFFICULT_SITUATION,
        "Entiendo que estás pasando por un momento difícil. Ser papá tiene momentos así, y está bien " +
            "sentirse abrumado. Estoy acá para acompañarte. ¿Quieres contarme un poco más? 💙",

        ConversationType.MISSION_GENERATION,
        "{\"title\": \"Momento de conexión\", \"description\": \"Dedica 10 minutos a estar presente " +
            "con tu hijo. Pregúntale sobre su día y escúchalo sin distracciones.\", " +
            "\"category\": \"COMMUNICATION\", \"difficulty\": 2, \"estimated_minutes\": 10}",

        ConversationType.INACTIVITY_CHECK,
        "¡Hola! Hace unos días que no hablamos. Solo quería recordarte que estoy acá cuando " +
            "quieras retomar. ¿Cómo han estado las cosas en casa? 😊"
    );

    /**
     * Returns a pre-written fallback response for the given conversation type.
     * These responses are always available and never fail.
     *
     * @param conversationType the type of conversation needing a fallback
     * @return an AiProviderResponse with the pre-written content
     */
    public AiProviderResponse getFallbackResponse(ConversationType conversationType) {
        String content = FALLBACK_RESPONSES.getOrDefault(
            conversationType,
            "Estoy acá para acompañarte. ¿En qué te puedo ayudar hoy? 🙂"
        );

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
     *
     * @param conversationType the type of conversation
     * @return the fallback text content
     */
    public String getFallbackText(ConversationType conversationType) {
        return FALLBACK_RESPONSES.getOrDefault(
            conversationType,
            "Estoy acá para acompañarte. ¿En qué te puedo ayudar hoy? 🙂"
        );
    }

    /**
     * @return true if a specific fallback exists for the given conversation type
     */
    public boolean hasFallbackFor(ConversationType conversationType) {
        return FALLBACK_RESPONSES.containsKey(conversationType);
    }
}
