package com.dadcoach.ai.mission;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces category cooldown rules and scores eligible categories for mission selection.
 *
 * <p>Category cooldown rules (from design spec):
 * <ul>
 *   <li>Same category + same child: minimum 4-day gap</li>
 *   <li>Same category + different child: minimum 2-day gap</li>
 * </ul>
 *
 * <p>Category scoring formula:
 * <pre>
 *   score = (goal_alignment × 0.4) + (child_interest_match × 0.3) + (time_appropriateness × 0.2) + (novelty × 0.1)
 * </pre>
 */
public class CategoryScorer {

    /** Minimum days between same category + same child. */
    public static final int SAME_CHILD_COOLDOWN_DAYS = 4;

    /** Minimum days between same category + different child. */
    public static final int DIFFERENT_CHILD_COOLDOWN_DAYS = 2;

    /**
     * A record of a previously assigned mission for cooldown tracking.
     *
     * @param category   the mission category
     * @param childId    the child the mission was assigned to
     * @param assignedOn the date the mission was assigned
     */
    public record MissionRecord(String category, UUID childId, LocalDate assignedOn) {
        public MissionRecord {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category must not be null or blank");
            }
            if (childId == null) {
                throw new IllegalArgumentException("childId must not be null");
            }
            if (assignedOn == null) {
                throw new IllegalArgumentException("assignedOn must not be null");
            }
        }
    }

    /**
     * Determines which categories are on cooldown and should be excluded from selection.
     *
     * @param targetChildId  the child for whom the next mission is being planned
     * @param allCategories  the full list of available categories
     * @param recentMissions the recent mission history (should cover at least last 4 days)
     * @param today          the current date for gap calculation
     * @return the list of categories that are NOT on cooldown (eligible for selection)
     */
    public List<String> getEligibleCategories(
        UUID targetChildId,
        List<String> allCategories,
        List<MissionRecord> recentMissions,
        LocalDate today
    ) {
        List<String> eligible = new ArrayList<>();

        for (String category : allCategories) {
            if (!isOnCooldown(category, targetChildId, recentMissions, today)) {
                eligible.add(category);
            }
        }

        return eligible;
    }

    /**
     * Checks if a specific category is on cooldown for the target child.
     *
     * @param category       the category to check
     * @param targetChildId  the child for whom the mission is being planned
     * @param recentMissions the recent mission history
     * @param today          the current date
     * @return true if the category is on cooldown and should be excluded
     */
    public boolean isOnCooldown(
        String category,
        UUID targetChildId,
        List<MissionRecord> recentMissions,
        LocalDate today
    ) {
        for (MissionRecord record : recentMissions) {
            if (!record.category().equalsIgnoreCase(category)) {
                continue;
            }

            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(record.assignedOn(), today);

            if (record.childId().equals(targetChildId)) {
                // Same child + same category: 4-day cooldown
                if (daysSince < SAME_CHILD_COOLDOWN_DAYS) {
                    return true;
                }
            } else {
                // Different child + same category: 2-day cooldown
                if (daysSince < DIFFERENT_CHILD_COOLDOWN_DAYS) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Scores eligible categories based on the scoring formula.
     * <pre>
     *   score = (goal_alignment × 0.4) + (child_interest_match × 0.3) + (time_appropriateness × 0.2) + (novelty × 0.1)
     * </pre>
     *
     * @param categories         eligible categories to score
     * @param goalAlignments     category → goal alignment score (0.0-1.0)
     * @param interestMatches    category → child interest match score (0.0-1.0)
     * @param timeScores         category → time appropriateness score (0.0-1.0)
     * @param noveltyScores      category → novelty score (0.0-1.0)
     * @return categories sorted by descending score (best first)
     */
    public List<ScoredCategory> scoreCategories(
        List<String> categories,
        Map<String, Double> goalAlignments,
        Map<String, Double> interestMatches,
        Map<String, Double> timeScores,
        Map<String, Double> noveltyScores
    ) {
        List<ScoredCategory> scored = new ArrayList<>();

        for (String category : categories) {
            double goalAlign = goalAlignments.getOrDefault(category, 0.0);
            double interest = interestMatches.getOrDefault(category, 0.0);
            double time = timeScores.getOrDefault(category, 0.0);
            double novelty = noveltyScores.getOrDefault(category, 0.0);

            double score = (goalAlign * 0.4) + (interest * 0.3) + (time * 0.2) + (novelty * 0.1);
            scored.add(new ScoredCategory(category, score));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored;
    }

    /**
     * A category with its computed score.
     *
     * @param category the category name
     * @param score    the computed score (0.0-1.0)
     */
    public record ScoredCategory(String category, double score) {
        public ScoredCategory {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category must not be null or blank");
            }
        }
    }
}
