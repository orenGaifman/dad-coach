package com.dadcoach.ai.output;

import java.util.UUID;

/**
 * Input context for reflection evaluation.
 *
 * @param fatherId         the father's unique identifier
 * @param reflectionText   the father's reflection text
 * @param currentPhase     current coaching phase
 * @param phaseDay         day count within current phase
 * @param recentMissions   number of recently completed missions
 */
public record ReflectionInput(
    UUID fatherId,
    String reflectionText,
    String currentPhase,
    int phaseDay,
    int recentMissions
) {
    public ReflectionInput {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (reflectionText == null || reflectionText.isBlank()) {
            throw new IllegalArgumentException("reflectionText must not be null or blank");
        }
    }
}
