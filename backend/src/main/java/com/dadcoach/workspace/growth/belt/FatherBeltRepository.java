package com.dadcoach.workspace.growth.belt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FatherBelt} entities.
 *
 * <p>Provides queries for belt lookup and atomic score increment.
 * This is a minimal version supporting GrowthScoreService (task 2.6)
 * and will be expanded with additional queries in task 3.4.</p>
 *
 * @see FatherBelt
 */
@Repository
public interface FatherBeltRepository extends JpaRepository<FatherBelt, UUID> {

    /**
     * Finds the belt record for a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return the belt record if one exists
     */
    Optional<FatherBelt> findByFatherId(UUID fatherId);

    /**
     * Checks whether a belt record exists for the given father.
     *
     * @param fatherId the father's unique identifier
     * @return true if a belt record exists
     */
    boolean existsByFatherId(UUID fatherId);

    /**
     * Atomically increments the cached current_score for a father by the given points.
     * Uses a native UPDATE query to ensure atomicity under concurrent signal recording.
     *
     * <p>Per Design Decision AD-9, this is an incremental cache update. The authoritative
     * score remains {@code SUM(growth_signals.points_awarded)}.</p>
     *
     * @param fatherId the father's unique identifier
     * @param points   the number of points to add (must be positive)
     * @return the number of rows updated (0 if no belt record exists)
     */
    @Modifying
    @Query("UPDATE FatherBelt fb SET fb.currentScore = fb.currentScore + :points, fb.updatedAt = CURRENT_TIMESTAMP WHERE fb.fatherId = :fatherId")
    int incrementScore(@Param("fatherId") UUID fatherId, @Param("points") int points);
}
