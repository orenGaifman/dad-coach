package com.dadcoach.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Goal} entities.
 * Provides queries for active goal retrieval (max 5 per father) and progress updates.
 */
@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    /**
     * Find the top 5 active goals for a given father, ordered by priority (ascending = highest priority first).
     * Enforces the max-5-active-goals-per-father business rule via query limit.
     */
    List<Goal> findTop5ByFatherIdAndStatusOrderByPriorityAsc(Long fatherId, String status);

    /**
     * Convenience method: find active goals for a father (max 5, ordered by priority).
     */
    default List<Goal> findActiveByFatherId(Long fatherId) {
        return findTop5ByFatherIdAndStatusOrderByPriorityAsc(fatherId, "ACTIVE");
    }

    /**
     * Find all goals for a given father regardless of status.
     */
    List<Goal> findByFatherId(Long fatherId);

    /**
     * Find all goals for a given father with a specific status.
     */
    List<Goal> findByFatherIdAndStatus(Long fatherId, String status);

    /**
     * Count the number of active goals for a given father.
     * Used to enforce the max-5-active-goals business rule before creating new goals.
     */
    @Query("SELECT COUNT(g) FROM Goal g WHERE g.fatherId = :fatherId AND g.status = 'ACTIVE'")
    long countActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Increment completed_related_missions and recalculate progress_percentage for a goal.
     * Uses the formula: min(100, (completed_related_missions / estimated_total_missions) × 100)
     */
    @Modifying
    @Query("UPDATE Goal g SET g.completedRelatedMissions = g.completedRelatedMissions + 1, " +
           "g.progressPercentage = CASE " +
           "  WHEN g.estimatedTotalMissions <= 0 THEN 0 " +
           "  WHEN ((g.completedRelatedMissions + 1) * 100 / g.estimatedTotalMissions) > 100 THEN 100 " +
           "  ELSE ((g.completedRelatedMissions + 1) * 100 / g.estimatedTotalMissions) " +
           "END " +
           "WHERE g.id = :goalId")
    int incrementCompletedMissions(@Param("goalId") Long goalId);

    /**
     * Update the progress percentage for a goal directly.
     * Useful when recalculating progress externally.
     */
    @Modifying
    @Query("UPDATE Goal g SET g.progressPercentage = :progressPercentage WHERE g.id = :goalId")
    int updateProgressPercentage(@Param("goalId") Long goalId, @Param("progressPercentage") int progressPercentage);

    /**
     * Update both completed_related_missions and progress_percentage for a goal.
     */
    @Modifying
    @Query("UPDATE Goal g SET g.completedRelatedMissions = :completedMissions, " +
           "g.progressPercentage = :progressPercentage WHERE g.id = :goalId")
    int updateProgress(@Param("goalId") Long goalId,
                       @Param("completedMissions") int completedMissions,
                       @Param("progressPercentage") int progressPercentage);

    /**
     * Delete all goals for a given father.
     * Used when deleting a father account.
     */
    @Modifying
    @Query("DELETE FROM Goal g WHERE g.fatherId = :fatherId")
    void deleteByFatherId(@Param("fatherId") Long fatherId);
}
