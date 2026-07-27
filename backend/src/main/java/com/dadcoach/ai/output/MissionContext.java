package com.dadcoach.ai.output;

import java.util.List;
import java.util.UUID;

/**
 * Input context for generating a mission.
 * Contains all information the MissionPlanner needs to select and generate
 * a personalized mission for a father's child.
 *
 * @param fatherId          the father's unique identifier
 * @param childName         the target child's name
 * @param childAge          the target child's computed age
 * @param childInterests    known interests of the child
 * @param category          the selected mission category
 * @param difficulty        the target difficulty (1-5)
 * @param coachingStyle     the father's preferred coaching style
 * @param dayOfWeek         current day of the week (e.g., "Monday")
 * @param timeContext       time of day context (e.g., "evening", "morning")
 * @param primaryGoal       the father's primary goal description
 * @param recentCategories  categories used in recent missions this week
 * @param cooldownCategories categories currently on cooldown
 */
public record MissionContext(
    UUID fatherId,
    String childName,
    int childAge,
    List<String> childInterests,
    String category,
    int difficulty,
    String coachingStyle,
    String dayOfWeek,
    String timeContext,
    String primaryGoal,
    List<String> recentCategories,
    List<String> cooldownCategories
) {
    public MissionContext {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (childName == null || childName.isBlank()) {
            throw new IllegalArgumentException("childName must not be null or blank");
        }
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        childInterests = childInterests != null ? List.copyOf(childInterests) : List.of();
        recentCategories = recentCategories != null ? List.copyOf(recentCategories) : List.of();
        cooldownCategories = cooldownCategories != null ? List.copyOf(cooldownCategories) : List.of();
    }
}
