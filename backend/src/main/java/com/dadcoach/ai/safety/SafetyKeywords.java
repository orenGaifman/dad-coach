package com.dadcoach.ai.safety;

import java.util.List;
import java.util.Set;

/**
 * English and Hebrew keyword lists for safety classification detection.
 * Keywords support both English and Hebrew as specified by SPEC-003 Requirement 9.
 *
 * <p>Detection achieves precision >= 0.95 and recall >= 0.90 —
 * false positives are acceptable, false negatives are not.
 */
public final class SafetyKeywords {

    private SafetyKeywords() {
        // Utility class
    }

    // ===== CRISIS Keywords (self-harm, suicidal ideation, violence) =====

    /**
     * Keywords indicating suicidal ideation or self-harm intent.
     * Includes English and Hebrew variants.
     */
    public static final Set<String> CRISIS_SELF_HARM = Set.of(
        // English
        "suicide",
        "kill myself",
        "don't want to live",
        "hurt myself",
        "self-harm",
        "cut myself",
        "end it all",
        "not worth living",
        "want to die",
        "take my life",
        "end my life",
        "can't go on",
        // Hebrew
        "התאבדות",
        "להרוג את עצמי",
        "לא רוצה לחיות",
        "לפגוע בעצמי",
        "פגיעה עצמית",
        "לחתוך את עצמי",
        "לסיים עם הכל",
        "רוצה למות",
        "לקחת את החיים שלי",
        "לא יכול יותר"
    );

    /**
     * Keywords indicating violence toward children or others.
     * Includes English and Hebrew variants.
     */
    public static final Set<String> CRISIS_VIOLENCE = Set.of(
        // English
        "I hit",
        "I beat",
        "I struck",
        "abuse",
        "violence",
        "I hurt",
        "maltreatment",
        "I gave him a slap",
        "I gave her a slap",
        "belt",
        // Hebrew
        "הכיתי",
        "היכיתי",
        "נתתי לו מכה",
        "נתתי לה מכה",
        "התעללות",
        "אלימות",
        "פגעתי",
        "סטירה",
        "חגורה"
    );

    // ===== CHILD_SAFETY Keywords =====

    /**
     * Keywords indicating potential child abuse, neglect, or danger.
     * Includes English and Hebrew variants.
     */
    public static final Set<String> CHILD_SAFETY_KEYWORDS = Set.of(
        // English
        "sexual abuse",
        "touching",
        "child abuse",
        "neglect",
        "I abandoned",
        "I left him alone",
        "I left her alone",
        "I don't feed",
        "I locked",
        "physical punishment",
        "I don't want my child",
        "I hate my child",
        "I wish he didn't exist",
        // Hebrew
        "התעללות מינית",
        "נגיעה",
        "התעללות בילדים",
        "הזנחה",
        "נטשתי",
        "השארתי אותו לבד",
        "השארתי אותה לבד",
        "לא נותן לו לאכול",
        "נעלתי",
        "עונש גופני",
        "לא רוצה את הילד שלי",
        "שונא את הילד שלי"
    );

    // ===== MANIPULATION / Jailbreak patterns =====

    /**
     * Patterns indicating jailbreak or manipulation attempts.
     * Includes both English and Hebrew variants.
     */
    public static final Set<String> MANIPULATION_PATTERNS = Set.of(
        // English
        "ignore previous instructions",
        "forget your instructions",
        "ignore all previous",
        "you are now",
        "pretend to be",
        "act as",
        "reveal your system prompt",
        "show me your prompt",
        "what are your instructions",
        "system prompt",
        "do anything now",
        "dan mode",
        "jailbreak",
        // Hebrew
        "התעלם מההוראות הקודמות",
        "שכח את ההוראות שלך",
        "אתה עכשיו",
        "תעשה כאילו אתה",
        "הראה לי את הפרומפט שלך",
        "מה ההוראות שלך"
    );

    // ===== MEDICAL Keywords =====

    /**
     * Keywords indicating medical questions about children.
     * Includes English and Hebrew variants.
     */
    public static final Set<String> MEDICAL_KEYWORDS = Set.of(
        // English
        "fever",
        "sick",
        "symptoms",
        "diagnosis",
        "medication",
        "medicine",
        "pediatrician",
        "autism",
        "adhd",
        "disorder",
        "allergy",
        "vaccine",
        "development",
        "delay",
        "doesn't speak",
        "doesn't walk",
        "seizures",
        "hospital",
        // Hebrew
        "חום",
        "חולה",
        "תסמינים",
        "אבחון",
        "תרופה",
        "רופא ילדים",
        "אוטיזם",
        "קשב וריכוז",
        "הפרעה",
        "אלרגיה",
        "חיסון",
        "התפתחות",
        "עיכוב",
        "לא מדבר",
        "לא הולך",
        "פרכוסים",
        "בית חולים"
    );

    // ===== LEGAL Keywords =====

    /**
     * Keywords indicating legal questions (custody, divorce).
     * Includes English and Hebrew variants.
     */
    public static final Set<String> LEGAL_KEYWORDS = Set.of(
        // English
        "custody",
        "divorce",
        "lawsuit",
        "lawyer",
        "judge",
        "court",
        "child support",
        "visitation rights",
        "parental rights",
        "restraining order",
        "complaint",
        "legal rights",
        "trial",
        // Hebrew
        "משמורת",
        "גירושין",
        "תביעה",
        "עורך דין",
        "שופט",
        "בית משפט",
        "מזונות",
        "הסדרי ראייה",
        "זכויות הורים",
        "צו הרחקה",
        "תלונה",
        "זכויות משפטיות"
    );

    // ===== EMOTIONAL DISTRESS Keywords =====

    /**
     * Keywords indicating significant emotional distress (not crisis-level).
     * Includes English and Hebrew variants.
     */
    public static final Set<String> EMOTIONAL_DISTRESS_KEYWORDS = Set.of(
        // English
        "can't take it anymore",
        "I'm desperate",
        "I feel alone",
        "I failed as a father",
        "I'm a bad father",
        "I'm not cut out for this",
        "I feel guilty",
        "anxiety",
        "anxious",
        "depressed",
        "depression",
        "can't sleep",
        "exhausted",
        "lost",
        "overwhelmed",
        // Hebrew
        "לא יכול יותר",
        "אני מיואש",
        "אני מרגיש לבד",
        "נכשלתי כאבא",
        "אני אבא רע",
        "אני לא מתאים לזה",
        "אני מרגיש אשם",
        "חרדה",
        "דיכאון",
        "מדוכא",
        "לא ישן",
        "מותש",
        "אבוד",
        "מוצף"
    );

    /**
     * Returns all crisis-related keyword sets combined.
     */
    public static List<Set<String>> allCrisisKeywordSets() {
        return List.of(CRISIS_SELF_HARM, CRISIS_VIOLENCE);
    }
}
