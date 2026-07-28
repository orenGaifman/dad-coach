package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the achievements endpoint (GET /api/v1/workspace/growth/achievements).
 *
 * <p>Returns the father's achievement status including total available, total earned,
 * the list of all achievements with earned status, and the next achievable achievement.</p>
 *
 * @see com.dadcoach.workspace.growth.achievement.AchievementEvaluator
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AchievementsResponse {

    @JsonProperty("total_available")
    private final long totalAvailable;

    @JsonProperty("total_earned")
    private final long totalEarned;

    @JsonProperty("achievements")
    private final List<AchievementItem> achievements;

    @JsonProperty("next_achievable")
    private final AchievementItem nextAchievable;

    public AchievementsResponse(long totalAvailable, long totalEarned,
                                List<AchievementItem> achievements,
                                AchievementItem nextAchievable) {
        this.totalAvailable = totalAvailable;
        this.totalEarned = totalEarned;
        this.achievements = achievements;
        this.nextAchievable = nextAchievable;
    }

    public long getTotalAvailable() {
        return totalAvailable;
    }

    public long getTotalEarned() {
        return totalEarned;
    }

    public List<AchievementItem> getAchievements() {
        return achievements;
    }

    public AchievementItem getNextAchievable() {
        return nextAchievable;
    }

    /**
     * Represents an individual achievement item within the response.
     *
     * <p>When {@code earnedAt} is null, the achievement has not yet been earned.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AchievementItem {

        @JsonProperty("achievement_id")
        private final UUID achievementId;

        @JsonProperty("name")
        private final String name;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("category")
        private final String category;

        @JsonProperty("icon_key")
        private final String iconKey;

        @JsonProperty("earned_at")
        private final Instant earnedAt;

        public AchievementItem(UUID achievementId, String name, String description,
                               String category, String iconKey, Instant earnedAt) {
            this.achievementId = achievementId;
            this.name = name;
            this.description = description;
            this.category = category;
            this.iconKey = iconKey;
            this.earnedAt = earnedAt;
        }

        public UUID getAchievementId() {
            return achievementId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getCategory() {
            return category;
        }

        public String getIconKey() {
            return iconKey;
        }

        public Instant getEarnedAt() {
            return earnedAt;
        }
    }
}
