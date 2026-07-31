package com.dadcoach.ai.safety;

import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provides pre-written safety responses for non-SAFE classifications.
 * All responses are static text (never AI-generated) to guarantee safe content.
 * Supports English (default) and Hebrew.
 *
 * <p>Per SPEC-003 Requirement 9:
 * - CRISIS → empathetic acknowledgment + crisis hotline numbers
 * - CHILD_SAFETY → concern + child protection hotline + logged for 2h SLA human review
 * - MANIPULATION → redirect to coaching + logged
 * - MEDICAL → redirect to pediatrician
 * - LEGAL → redirect to family law attorney
 */
@Component
public class SafetyResponseProvider {

    private static final Logger log = LoggerFactory.getLogger(SafetyResponseProvider.class);

    /**
     * Returns the appropriate pre-written safety response for a given classification.
     * Default language is English.
     *
     * @param classification the safety classification result
     * @return the pre-written response text
     */
    public String getResponse(SafetyClassification classification) {
        return getResponse(classification, "en");
    }

    /**
     * Returns the appropriate pre-written safety response for a given classification and locale.
     *
     * @param classification the safety classification result
     * @param locale the locale ("en" for English, "he" for Hebrew)
     * @return the pre-written response text
     */
    public String getResponse(SafetyClassification classification, String locale) {
        boolean isHebrew = "he".equals(locale);
        return switch (classification.category()) {
            case CRISIS -> isHebrew ? getCrisisResponseHe() : getCrisisResponseEn();
            case CHILD_SAFETY -> isHebrew ? getChildSafetyResponseHe() : getChildSafetyResponseEn();
            case MANIPULATION -> isHebrew ? getManipulationResponseHe() : getManipulationResponseEn();
            case MEDICAL -> isHebrew ? getMedicalResponseHe() : getMedicalResponseEn();
            case LEGAL -> isHebrew ? getLegalResponseHe() : getLegalResponseEn();
            case EMOTIONAL_DISTRESS -> isHebrew ? getEmotionalDistressResponseHe() : getEmotionalDistressResponseEn();
            case OFF_TOPIC -> isHebrew ? getOffTopicResponseHe() : getOffTopicResponseEn();
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

    // ===== Pre-written responses - English =====

    private String getCrisisResponseEn() {
        return """
            I hear what you're telling me and I care deeply about how you're feeling. \
            What you're going through is serious and you deserve professional support right now.

            Please contact:
            📞 988 Suicide & Crisis Lifeline: call or text 988
            📞 Crisis Text Line: text HOME to 741741

            You're not alone in this. There are trained people waiting to help you right now.""";
    }

    private String getChildSafetyResponseEn() {
        return """
            Thank you for sharing this with me. I'm concerned about your child's safety \
            and I want to make sure they get the right help.

            I recommend contacting:
            📞 Childhelp National Child Abuse Hotline: 1-800-422-4453

            Asking for help is an act of courage and love. A professional can guide you \
            on the next steps to protect your family.""";
    }

    private String getManipulationResponseEn() {
        return "I'm your parenting coach. How can I help you with your kids today?";
    }

    private String getMedicalResponseEn() {
        return """
            I understand your concern. I'm not a healthcare professional and can't give you \
            a diagnosis or medical recommendation.

            I suggest consulting your child's pediatrician as soon as possible. \
            They can properly evaluate the situation.

            Is there anything else I can support you with as a dad in the meantime?""";
    }

    private String getLegalResponseEn() {
        return """
            I understand this situation is difficult. I can't give legal advice \
            because I'm not a lawyer.

            I recommend seeking a family law attorney \
            who can guide you on your options and rights.

            I'm here to support you emotionally in whatever you need. \
            How are you feeling about all this?""";
    }

    private String getEmotionalDistressResponseEn() {
        return """
            I hear that you're going through a very difficult time. \
            What you're feeling is completely valid and you're not alone in this.

            Many dads go through moments like this. Would you like to tell me more \
            about what you're experiencing?""";
    }

    private String getOffTopicResponseEn() {
        return """
            I understand, but as your parenting coach I focus on helping you \
            with your relationship with your kids and your growth as a dad.

            Is there anything related to your kids I can help you with today?""";
    }

    // ===== Pre-written responses - Hebrew =====

    private String getCrisisResponseHe() {
        return """
            אני שומע מה שאתה אומר לי ואכפת לי מאוד איך אתה מרגיש. \
            מה שאתה עובר זה רציני ומגיע לך תמיכה מקצועית עכשיו.

            אנא צור קשר:
            📞 ער"ן - קו סיוע רגשי: *2784
            📞 ע.מ.ח.ה - קו חירום: 1201

            אתה לא לבד בזה. יש אנשים מיומנים שמחכים לעזור לך עכשיו.""";
    }

    private String getChildSafetyResponseHe() {
        return """
            תודה שחלקת את זה איתי. אני מודאג לבטיחות הילד שלך \
            ורוצה לוודא שהם מקבלים את העזרה הנכונה.

            אני ממליץ ליצור קשר:
            📞 קו חירום לילדים ונוער: 1202
            📞 שירותי הרווחה: *118

            לבקש עזרה זה מעשה של אומץ ואהבה. איש מקצוע יוכל להדריך אותך \
            בצעדים הבאים להגנה על המשפחה שלך.""";
    }

    private String getManipulationResponseHe() {
        return "אני המאמן שלך להורות. איך אני יכול לעזור לך עם הילדים שלך היום?";
    }

    private String getMedicalResponseHe() {
        return """
            אני מבין את הדאגה שלך. אני לא איש מקצוע רפואי ולא יכול לתת לך \
            אבחנה או המלצה רפואית.

            אני מציע להתייעץ עם רופא הילדים שלכם בהקדם האפשרי. \
            הם יכולים להעריך את המצב בצורה נכונה.

            יש משהו אחר שאני יכול לתמוך בך כאבא בינתיים?""";
    }

    private String getLegalResponseHe() {
        return """
            אני מבין שהמצב הזה קשה. אני לא יכול לתת עצות משפטיות \
            כי אני לא עורך דין.

            אני ממליץ לפנות לעורך דין המתמחה בדיני משפחה \
            שיכול להדריך אותך באפשרויות ובזכויות שלך.

            אני כאן לתמוך בך רגשית בכל מה שתצטרך. \
            איך אתה מרגיש עם כל זה?""";
    }

    private String getEmotionalDistressResponseHe() {
        return """
            אני שומע שאתה עובר תקופה קשה מאוד. \
            מה שאתה מרגיש זה לגמרי תקף ואתה לא לבד בזה.

            הרבה אבות עוברים רגעים כאלה. רוצה לספר לי עוד \
            על מה שאתה חווה?""";
    }

    private String getOffTopicResponseHe() {
        return """
            אני מבין, אבל כמאמן להורות אני מתמקד בלעזור לך \
            עם הקשר שלך עם הילדים ובצמיחה שלך כאבא.

            יש משהו שקשור לילדים שלך שאני יכול לעזור לך בו היום?""";
    }
}
