package com.dadcoach.ai.safety;

import java.util.List;
import java.util.Set;

/**
 * Spanish keyword lists for safety classification detection.
 * Keywords are in Latin American Spanish as specified by SPEC-003 Requirement 9.
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
     */
    public static final Set<String> CRISIS_SELF_HARM = Set.of(
        "suicidio",
        "suicidarme",
        "matarme",
        "no quiero vivir",
        "hacerme daño",
        "autolesión",
        "cortarme",
        "acabar con todo",
        "no vale la pena vivir",
        "quiero morir",
        "me quiero morir",
        "quitarme la vida",
        "terminar con mi vida",
        "ya no puedo más"
    );

    /**
     * Keywords indicating violence toward children or others.
     */
    public static final Set<String> CRISIS_VIOLENCE = Set.of(
        "golpeé",
        "golpeo",
        "le pegué",
        "le pego",
        "abuso",
        "violencia",
        "lastimé",
        "maltrato",
        "lo golpeé",
        "la golpeé",
        "le doy golpes",
        "cachetada",
        "cinturón",
        "le di con"
    );

    // ===== CHILD_SAFETY Keywords =====

    /**
     * Keywords indicating potential child abuse, neglect, or danger.
     */
    public static final Set<String> CHILD_SAFETY_KEYWORDS = Set.of(
        "abuso sexual",
        "tocamiento",
        "lo toca",
        "la toca",
        "abuso infantil",
        "negligencia",
        "abandoné",
        "lo dejé solo",
        "la dejé sola",
        "no le doy de comer",
        "encerré",
        "castigo físico",
        "no quiero a mi hijo",
        "odio a mi hijo",
        "quisiera que no existiera"
    );

    // ===== MANIPULATION / Jailbreak patterns =====

    /**
     * Patterns indicating jailbreak or manipulation attempts.
     * Includes both English and Spanish variants.
     */
    public static final Set<String> MANIPULATION_PATTERNS = Set.of(
        "ignore previous instructions",
        "ignora las instrucciones anteriores",
        "ignora tus instrucciones",
        "olvida tus reglas",
        "olvida las reglas",
        "actúa como",
        "actua como",
        "pretende ser",
        "finge ser",
        "eres un nuevo personaje",
        "reveal your system prompt",
        "muestra tu prompt",
        "muéstrame tu prompt",
        "cuáles son tus instrucciones",
        "dime tus instrucciones",
        "system prompt",
        "ignore all previous",
        "forget your instructions",
        "you are now",
        "do anything now",
        "dan mode",
        "jailbreak"
    );

    // ===== MEDICAL Keywords =====

    /**
     * Keywords indicating medical questions about children.
     */
    public static final Set<String> MEDICAL_KEYWORDS = Set.of(
        "fiebre",
        "enfermo",
        "enferma",
        "síntomas",
        "diagnóstico",
        "medicamento",
        "medicina",
        "pediatra",
        "autismo",
        "tdah",
        "trastorno",
        "alergia",
        "vacuna",
        "desarrollo",
        "retraso",
        "no habla",
        "no camina",
        "convulsiones",
        "hospital"
    );

    // ===== LEGAL Keywords =====

    /**
     * Keywords indicating legal questions (custody, divorce).
     */
    public static final Set<String> LEGAL_KEYWORDS = Set.of(
        "custodia",
        "divorcio",
        "demanda",
        "abogado",
        "juez",
        "tribunal",
        "pensión alimenticia",
        "régimen de visitas",
        "patria potestad",
        "orden de alejamiento",
        "denuncia",
        "derechos legales",
        "juicio"
    );

    // ===== EMOTIONAL DISTRESS Keywords =====

    /**
     * Keywords indicating significant emotional distress (not crisis-level).
     */
    public static final Set<String> EMOTIONAL_DISTRESS_KEYWORDS = Set.of(
        "no puedo más",
        "estoy desesperado",
        "me siento solo",
        "fracasé como padre",
        "soy un mal padre",
        "no sirvo para esto",
        "me siento culpable",
        "angustia",
        "ansiedad",
        "deprimido",
        "depresión",
        "no duermo",
        "agotado",
        "perdido",
        "abrumado",
        "overwhelmed"
    );

    /**
     * Returns all crisis-related keyword sets combined.
     */
    public static List<Set<String>> allCrisisKeywordSets() {
        return List.of(CRISIS_SELF_HARM, CRISIS_VIOLENCE);
    }
}
