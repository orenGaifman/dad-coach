package com.dadcoach.workspace.growth.streak;

import com.dadcoach.workspace.growth.signal.GrowthSignalType;

import java.util.Set;

/**
 * Defines which growth signal types qualify as streak-maintaining interactions.
 *
 * <p>A father's streak increments by one day when at least one qualifying interaction
 * is recorded on a given calendar day (in the father's timezone). The qualifying
 * interactions are (Requirement 12.2):</p>
 *
 * <ul>
 *   <li><b>MISSION_COMPLETED</b> — Mission completed by the father</li>
 *   <li><b>MISSION_REFLECTED</b> — Mission reflected upon after completion</li>
 *   <li><b>MEANINGFUL_CONVERSATION</b> — Coaching conversation with ≥3+ exchanges
 *       (validated upstream: the signal is only emitted when the conversation
 *       has quality rating &gt; 0.6 and &gt; 5 exchanges)</li>
 *   <li><b>QUALITY_TIME_REPORTED</b> — Quality time with child reported</li>
 *   <li><b>POSITIVE_ACTIVITY</b> — Positive parenting activity reported</li>
 * </ul>
 *
 * <p>Non-qualifying signal types (e.g., DAILY_ENGAGEMENT, STREAK_BONUS_*, GOAL_PROGRESS)
 * do not contribute to streak calculation.</p>
 */
public final class QualifyingInteraction {

    private QualifyingInteraction() {
        // utility class — no instantiation
    }

    /**
     * The set of signal types that qualify as streak-maintaining interactions.
     */
    public static final Set<GrowthSignalType> QUALIFYING_SIGNAL_TYPES = Set.of(
            GrowthSignalType.MISSION_COMPLETED,
            GrowthSignalType.MISSION_REFLECTED,
            GrowthSignalType.MEANINGFUL_CONVERSATION,
            GrowthSignalType.QUALITY_TIME_REPORTED,
            GrowthSignalType.POSITIVE_ACTIVITY
    );

    /**
     * Checks whether the given signal type qualifies for streak maintenance.
     *
     * @param signalType the signal type to check
     * @return true if the signal type is a qualifying interaction
     */
    public static boolean isQualifying(GrowthSignalType signalType) {
        return QUALIFYING_SIGNAL_TYPES.contains(signalType);
    }
}
