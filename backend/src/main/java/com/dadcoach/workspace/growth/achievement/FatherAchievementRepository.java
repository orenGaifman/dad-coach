package com.dadcoach.workspace.growth.achievement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FatherAchievement} earned record entities.
 *
 * <p>Provides queries for tracking which achievements a father has earned,
 * checking for existing awards, and counting earned achievements.</p>
 *
 * @see FatherAchievement
 */
@Repository
public interface FatherAchievementRepository extends JpaRepository<FatherAchievement, UUID> {

    /**
     * Finds all earned achievements for a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return list of earned achievement records
     */
    List<FatherAchievement> findByFatherId(UUID fatherId);

    /**
     * Counts the number of achievements earned by a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return the count of earned achievements
     */
    long countByFatherId(UUID fatherId);

    /**
     * Checks whether a specific father has already earned a specific achievement.
     * Used for idempotent award logic — prevents duplicate awards.
     *
     * @param fatherId      the father's unique identifier
     * @param achievementId the achievement's unique identifier
     * @return true if the father has already earned this achievement
     */
    boolean existsByFatherIdAndAchievementId(UUID fatherId, UUID achievementId);
}
