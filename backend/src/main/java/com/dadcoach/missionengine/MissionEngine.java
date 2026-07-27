package com.dadcoach.missionengine;

import com.dadcoach.domain.mission.Mission;

/**
 * Engine responsible for mission generation, difficulty adaptation,
 * equitable distribution across children, and child selection.
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Generate age-appropriate, context-aware missions with difficulty adaptation</li>
 *   <li>Adapt difficulty based on recent mission outcomes (Req 6.16-6.17)</li>
 *   <li>Ensure equitable distribution across children (Req 6.13)</li>
 *   <li>Select next child for mission assignment ensuring fairness (Req 10.8)</li>
 *   <li>Enforce category non-repetition (max 2 per category per 7-day window per child, Req 6.7)</li>
 * </ul>
 */
public interface MissionEngine {

    /**
     * Generate a mission for a specific child considering all context factors.
     *
     * @param fatherId the father receiving the mission
     * @param childId  the child the mission targets
     * @return the generated Mission entity
     */
    Mission generateMission(Long fatherId, Long childId);

    /**
     * Adapt difficulty based on recent mission outcomes.
     *
     * <p>Rules:
     * <ul>
     *   <li>Rating 4-5 → +1 (capped at phase maximum)</li>
     *   <li>Rating 1-2 → -1 (minimum 1)</li>
     *   <li>Rating 3 → unchanged</li>
     *   <li>After 3 consecutive skipped/expired missions → -1 and switch category</li>
     * </ul>
     *
     * @param fatherId          the father ID
     * @param childId           the child ID
     * @param currentDifficulty the current difficulty level
     * @return the adapted difficulty level
     */
    int adaptDifficulty(Long fatherId, Long childId, int currentDifficulty);

    /**
     * Validate equitable distribution across children.
     *
     * <p>Over any windowDays period, each child should receive at least
     * {@code floor(total_missions / num_children) - 1} missions.</p>
     *
     * @param fatherId   the father ID
     * @param windowDays the time window in days (typically 7)
     * @return true if distribution is equitable
     */
    boolean isDistributionEquitable(Long fatherId, int windowDays);

    /**
     * Select next child for mission assignment ensuring fairness.
     *
     * <p>Algorithm: least missions in 7 days, tiebreaker = longest since last mission.</p>
     *
     * @param fatherId the father ID
     * @return the ID of the child that should receive the next mission
     */
    Long selectNextChild(Long fatherId);
}
