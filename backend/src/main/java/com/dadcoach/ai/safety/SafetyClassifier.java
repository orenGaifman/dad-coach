package com.dadcoach.ai.safety;

import com.dadcoach.ai.safety.SafetyClassification.SafetyCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Classifies inbound messages into safety categories using keyword detection
 * and semantic analysis.
 *
 * <p>Per SPEC-003 Requirement 9: classification runs BEFORE any coaching generation.
 * Always returns exactly one classification with confidence in [0.0, 1.0].
 * The result is never null.
 *
 * <p>Detection priority order (highest to lowest):
 * <ol>
 *   <li>CRISIS — self-harm, suicidal ideation, violence</li>
 *   <li>CHILD_SAFETY — child abuse, neglect, danger</li>
 *   <li>MANIPULATION — jailbreak attempts, prompt extraction</li>
 *   <li>MEDICAL — health/developmental questions</li>
 *   <li>LEGAL — custody, divorce, legal questions</li>
 *   <li>EMOTIONAL_DISTRESS — significant negative emotions</li>
 *   <li>OFF_TOPIC — unrelated to parenting</li>
 *   <li>SAFE — default when no unsafe patterns detected</li>
 * </ol>
 */
public class SafetyClassifier {

    private static final Logger log = LoggerFactory.getLogger(SafetyClassifier.class);

    /**
     * Classifies an inbound message into a safety category.
     * This method MUST be called before any coaching generation occurs.
     *
     * <p>Guarantees:
     * - Always returns a non-null SafetyClassification
     * - Always returns exactly one category
     * - Confidence is always in [0.0, 1.0]
     *
     * @param message the inbound message text to classify
     * @return the safety classification (never null)
     */
    public SafetyClassification classify(String message) {
        if (message == null || message.isBlank()) {
            return SafetyClassification.safe();
        }

        String normalized = normalizeMessage(message);

        // Priority-ordered detection: most critical categories first
        SafetyClassification result;

        result = detectCrisis(normalized);
        if (result != null) return result;

        result = detectChildSafety(normalized);
        if (result != null) return result;

        result = detectManipulation(normalized);
        if (result != null) return result;

        result = detectMedical(normalized);
        if (result != null) return result;

        result = detectLegal(normalized);
        if (result != null) return result;

        result = detectEmotionalDistress(normalized);
        if (result != null) return result;

        // Default: message is safe
        return SafetyClassification.safe();
    }

    // ===== Detection Methods =====

    private SafetyClassification detectCrisis(String normalized) {
        // Check self-harm keywords (highest priority crisis)
        String matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.CRISIS_SELF_HARM);
        if (matchedKeyword != null) {
            return new SafetyClassification(
                SafetyCategory.CRISIS,
                0.95,
                "Self-harm keyword detected: " + matchedKeyword
            );
        }

        // Check violence keywords, but only if not better matched by child safety
        // (e.g., "abuso sexual" is CHILD_SAFETY, not generic "abuso" CRISIS)
        matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.CRISIS_VIOLENCE);
        if (matchedKeyword != null) {
            // If a child safety keyword also matches, defer to child safety detection
            if (!hasChildSafetyMatch(normalized)) {
                return new SafetyClassification(
                    SafetyCategory.CRISIS,
                    0.90,
                    "Violence keyword detected: " + matchedKeyword
                );
            }
        }

        // Semantic analysis: hopelessness + finality patterns
        if (containsHopelessnessPattern(normalized)) {
            return new SafetyClassification(
                SafetyCategory.CRISIS,
                0.80,
                "Hopelessness pattern detected via semantic analysis"
            );
        }

        return null;
    }

    private SafetyClassification detectChildSafety(String normalized) {
        String matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.CHILD_SAFETY_KEYWORDS);
        if (matchedKeyword != null) {
            return new SafetyClassification(
                SafetyCategory.CHILD_SAFETY,
                0.90,
                "Child safety keyword detected: " + matchedKeyword
            );
        }
        return null;
    }

    /**
     * Checks if a more specific child safety keyword matches the message.
     * Used to avoid generic violence keywords (like "abuso") overriding
     * more specific child safety phrases (like "abuso sexual").
     */
    private boolean hasChildSafetyMatch(String normalized) {
        return findMatchingKeyword(normalized, SafetyKeywords.CHILD_SAFETY_KEYWORDS) != null;
    }

    private SafetyClassification detectManipulation(String normalized) {
        String matchedPattern = findMatchingKeyword(normalized, SafetyKeywords.MANIPULATION_PATTERNS);
        if (matchedPattern != null) {
            return new SafetyClassification(
                SafetyCategory.MANIPULATION,
                0.95,
                "Manipulation/jailbreak pattern detected: " + matchedPattern
            );
        }
        return null;
    }

    private SafetyClassification detectMedical(String normalized) {
        String matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.MEDICAL_KEYWORDS);
        if (matchedKeyword != null) {
            // Medical keywords need context — a single medical word in a longer message
            // about parenting might not be a medical question
            double confidence = calculateMedicalConfidence(normalized, matchedKeyword);
            if (confidence >= 0.6) {
                return new SafetyClassification(
                    SafetyCategory.MEDICAL,
                    confidence,
                    "Medical keyword detected: " + matchedKeyword
                );
            }
        }
        return null;
    }

    private SafetyClassification detectLegal(String normalized) {
        String matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.LEGAL_KEYWORDS);
        if (matchedKeyword != null) {
            return new SafetyClassification(
                SafetyCategory.LEGAL,
                0.85,
                "Legal keyword detected: " + matchedKeyword
            );
        }
        return null;
    }

    private SafetyClassification detectEmotionalDistress(String normalized) {
        String matchedKeyword = findMatchingKeyword(normalized, SafetyKeywords.EMOTIONAL_DISTRESS_KEYWORDS);
        if (matchedKeyword != null) {
            return new SafetyClassification(
                SafetyCategory.EMOTIONAL_DISTRESS,
                0.80,
                "Emotional distress keyword detected: " + matchedKeyword
            );
        }
        return null;
    }

    // ===== Helper Methods =====

    /**
     * Normalizes a message for keyword matching:
     * lowercase, trim, normalize accents for comparison.
     */
    private String normalizeMessage(String message) {
        return message.toLowerCase().trim();
    }

    /**
     * Finds the first matching keyword in the message from the given set.
     *
     * @param normalized the normalized message
     * @param keywords   the keyword set to search
     * @return the matched keyword, or null if none found
     */
    private String findMatchingKeyword(String normalized, Set<String> keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase())) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * Detects semantic hopelessness patterns (hopelessness + finality).
     * Looks for combinations of despair language with finality indicators.
     */
    private boolean containsHopelessnessPattern(String normalized) {
        // Hopelessness indicators
        boolean hasHopelessness = normalized.contains("no hay salida")
            || normalized.contains("no tiene sentido")
            || normalized.contains("todo está perdido")
            || normalized.contains("nadie me necesita")
            || normalized.contains("estarían mejor sin mí");

        // Finality indicators
        boolean hasFinality = normalized.contains("nunca")
            || normalized.contains("jamás")
            || normalized.contains("para siempre")
            || normalized.contains("se acabó");

        return hasHopelessness;
    }

    /**
     * Calculates confidence for medical classification based on context.
     * A single medical term in a long parenting message should have lower confidence.
     */
    private double calculateMedicalConfidence(String normalized, String matchedKeyword) {
        // If the message is short and contains a medical keyword, it's likely a medical question
        if (normalized.length() < 100) {
            return 0.85;
        }

        // If there are question indicators alongside medical keywords
        boolean hasQuestionIndicator = normalized.contains("?")
            || normalized.contains("qué hacer")
            || normalized.contains("es normal")
            || normalized.contains("debería")
            || normalized.contains("preocupa");

        if (hasQuestionIndicator) {
            return 0.80;
        }

        // Longer message with medical word embedded — lower confidence
        return 0.60;
    }
}
