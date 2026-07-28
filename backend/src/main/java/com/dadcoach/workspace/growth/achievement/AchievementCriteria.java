package com.dadcoach.workspace.growth.achievement;

import com.dadcoach.workspace.growth.belt.BeltLevel;

/**
 * Sealed interface representing the criteria for earning an achievement.
 *
 * <p>Each implementation holds only the criteria data (e.g., threshold values).
 * Actual evaluation against the database is performed by {@link AchievementCriteriaEvaluator},
 * which switches on the criteria type and performs the appropriate queries.</p>
 *
 * <p>This design keeps sealed interface implementations as pure data objects,
 * avoiding the need to inject Spring beans into them.</p>
 *
 * @see AchievementCriteriaEvaluator
 */
public sealed interface AchievementCriteria
        permits AchievementCriteria.MissionCountCriteria,
                AchievementCriteria.StreakDaysCriteria,
                AchievementCriteria.GoalCountCriteria,
                AchievementCriteria.ConversationCountCriteria,
                AchievementCriteria.BeltReachedCriteria {

    /**
     * Criteria requiring a minimum number of completed missions.
     *
     * @param threshold the minimum number of missions that must be completed
     */
    record MissionCountCriteria(int threshold) implements AchievementCriteria {
    }

    /**
     * Criteria requiring a minimum longest streak in calendar days.
     *
     * @param threshold the minimum number of consecutive streak days required
     */
    record StreakDaysCriteria(int threshold) implements AchievementCriteria {
    }

    /**
     * Criteria requiring a minimum number of completed goals.
     *
     * @param threshold the minimum number of goals that must be completed
     */
    record GoalCountCriteria(int threshold) implements AchievementCriteria {
    }

    /**
     * Criteria requiring a minimum number of meaningful conversations.
     *
     * @param threshold the minimum number of meaningful conversations required
     */
    record ConversationCountCriteria(int threshold) implements AchievementCriteria {
    }

    /**
     * Criteria requiring the father to have reached a specific belt level.
     *
     * @param requiredBelt the minimum belt level that must be achieved
     */
    record BeltReachedCriteria(BeltLevel requiredBelt) implements AchievementCriteria {
    }
}
