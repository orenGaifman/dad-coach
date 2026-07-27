package com.dadcoach.ai.safety;

import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Provides pre-written safety responses for non-SAFE classifications.
 * All responses are static text (never AI-generated) to guarantee safe content.
 *
 * <p>Per SPEC-003 Requirement 9:
 * - CRISIS → empathetic acknowledgment + crisis hotline numbers
 * - CHILD_SAFETY → concern + child protection hotline + logged for 2h SLA human review
 * - MANIPULATION → redirect to coaching + logged
 * - MEDICAL → redirect to pediatrician
 * - LEGAL → redirect to family law attorney
 */
public class SafetyResponseProvider {

    private static final Logger log = LoggerFactory.getLogger(SafetyResponseProvider.class);

    /**
     * Returns the appropriate pre-written safety response for a given classification.
     *
     * @param classification the safety classification result
     * @return the pre-written response text in Latin American Spanish
     */
    public String getResponse(SafetyClassification classification) {
        return switch (classification.category()) {
            case CRISIS -> getCrisisResponse();
            case CHILD_SAFETY -> getChildSafetyResponse();
            case MANIPULATION -> getManipulationResponse();
            case MEDICAL -> getMedicalResponse();
            case LEGAL -> getLegalResponse();
            case EMOTIONAL_DISTRESS -> getEmotionalDistressResponse();
            case OFF_TOPIC -> getOffTopicResponse();
            case SAFE -> throw new IllegalArgumentException(
                "No safety response needed for SAFE classification");
        };
    }

    /**
     * Logs a safety event for human review. Returns the event ID for tracking.
     *
     * @param classification the safety classification
     * @param messageContent the original message content
     * @param fatherId       the father's identifier
     * @return the event ID for tracking
     */
    public String logForHumanReview(SafetyClassification classification,
                                    String messageContent, UUID fatherId) {
        String eventId = UUID.randomUUID().toString();
        String sla = getReviewSla(classification.category());

        log.warn("SAFETY_EVENT [{}] category={} confidence={} fatherId={} sla={} reason={}",
            eventId, classification.category(), classification.confidence(),
            fatherId, sla, classification.reason());

        // In production, this would persist to a review queue table
        // For now, structured logging captures the event for alerting
        return eventId;
    }

    /**
     * Determines if a classification requires flagging for human review.
     */
    public boolean requiresHumanReview(SafetyClassification classification) {
        return classification.category() == SafetyCategory.CRISIS
            || classification.category() == SafetyCategory.CHILD_SAFETY;
    }

    /**
     * Returns the SLA for human review based on category.
     */
    public String getReviewSla(SafetyCategory category) {
        return switch (category) {
            case CRISIS -> "4h";
            case CHILD_SAFETY -> "2h";
            default -> "24h";
        };
    }

    // ===== Pre-written responses =====

    private String getCrisisResponse() {
        return """
            Escucho lo que me estás diciendo y me importa mucho cómo te sientes. \
            Lo que estás viviendo es serio y mereces apoyo profesional ahora mismo.

            Por favor contacta:
            📞 Línea 988 (Suicide & Crisis Lifeline): llama o envía un mensaje al 988
            📞 Línea de la Vida: 800-911-2000 (México)
            📞 Crisis Text Line: envía HOLA al 741741

            No estás solo en esto. Hay personas capacitadas esperando para ayudarte en este momento.""";
    }

    private String getChildSafetyResponse() {
        return """
            Gracias por compartir esto conmigo. Me preocupa la seguridad de tu hijo/a \
            y quiero asegurarme de que reciban la ayuda adecuada.

            Te recomiendo contactar:
            📞 Childhelp National Child Abuse Hotline: 1-800-422-4453
            📞 SIPINNA (México): 800-888-4835

            Pedir ayuda es un acto de valentía y amor. Un profesional puede orientarte \
            sobre los próximos pasos para proteger a tu familia.""";
    }

    private String getManipulationResponse() {
        return "Soy tu coach de paternidad. ¿En qué te puedo ayudar con tus hijos hoy?";
    }

    private String getMedicalResponse() {
        return """
            Entiendo tu preocupación. No soy profesional de salud y no puedo darte \
            un diagnóstico o recomendación médica.

            Te sugiero consultar con el pediatra de tu hijo/a lo antes posible. \
            Ellos podrán evaluarlo correctamente.

            ¿Hay algo más en lo que pueda apoyarte como papá mientras tanto?""";
    }

    private String getLegalResponse() {
        return """
            Entiendo que esta situación es difícil. No puedo dar consejos legales \
            porque no soy abogado.

            Te recomiendo buscar un abogado especializado en derecho familiar \
            que pueda orientarte sobre tus opciones y derechos.

            Estoy aquí para apoyarte emocionalmente en lo que necesites. \
            ¿Cómo te sientes con todo esto?""";
    }

    private String getEmotionalDistressResponse() {
        return """
            Escucho que estás pasando por un momento muy difícil. \
            Lo que sientes es completamente válido y no estás solo en esto.

            Muchos papás pasan por momentos así. ¿Quieres contarme más \
            sobre lo que estás viviendo?""";
    }

    private String getOffTopicResponse() {
        return """
            Entiendo, pero como tu coach de paternidad me enfoco en ayudarte \
            con tu relación con tus hijos y tu crecimiento como papá.

            ¿Hay algo relacionado con tus hijos en lo que pueda ayudarte hoy?""";
    }
}
