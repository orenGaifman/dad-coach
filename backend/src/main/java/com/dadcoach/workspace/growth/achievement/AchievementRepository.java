package com.dadcoach.workspace.growth.achievement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Achievement} definition entities.
 *
 * <p>Provides queries for retrieving achievement definitions by category
 * and listing all available achievements.</p>
 *
 * @see Achievement
 * @see AchievementCategory
 */
@Repository
public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    /**
     * Finds all achievements in a given category.
     *
     * @param category the achievement category to filter by
     * @return list of achievements in the specified category
     */
    List<Achievement> findAllByCategory(AchievementCategory category);

    /**
     * Returns the total number of achievement definitions.
     * Inherited from JpaRepository but explicitly documented for clarity.
     *
     * @return the total count of achievements
     */
    @Override
    long count();
}
