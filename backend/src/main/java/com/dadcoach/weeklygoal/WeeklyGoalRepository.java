package com.dadcoach.weeklygoal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for WeeklyGoal entities.
 */
@Repository
public interface WeeklyGoalRepository extends JpaRepository<WeeklyGoal, Long> {

    /**
     * Find the weekly goal for a father and specific week.
     */
    Optional<WeeklyGoal> findByFatherIdAndWeekStartDate(Long fatherId, LocalDate weekStartDate);

    /**
     * Find the current active goal for a father.
     */
    Optional<WeeklyGoal> findByFatherIdAndStatus(Long fatherId, WeeklyGoalStatus status);

    /**
     * Find all goals for a father, ordered by week start date descending.
     */
    List<WeeklyGoal> findByFatherIdOrderByWeekStartDateDesc(Long fatherId);

    /**
     * Find recent goals for a father (last N weeks).
     */
    @Query("SELECT wg FROM WeeklyGoal wg WHERE wg.fatherId = :fatherId " +
           "ORDER BY wg.weekStartDate DESC LIMIT :limit")
    List<WeeklyGoal> findRecentGoals(@Param("fatherId") Long fatherId, @Param("limit") int limit);

    /**
     * Find all active goals (for the weekly scheduler to process).
     */
    List<WeeklyGoal> findByStatus(WeeklyGoalStatus status);

    /**
     * Find goals that need to be completed (active goals where week has ended).
     */
    @Query("SELECT wg FROM WeeklyGoal wg WHERE wg.status = 'ACTIVE' " +
           "AND wg.weekStartDate < :currentWeekStart")
    List<WeeklyGoal> findGoalsToComplete(@Param("currentWeekStart") LocalDate currentWeekStart);

    /**
     * Count completed goals (met target) for a father.
     */
    long countByFatherIdAndStatus(Long fatherId, WeeklyGoalStatus status);

    /**
     * Count consecutive weeks where goal was met, going back from a given week.
     */
    @Query(value = """
        WITH ranked AS (
            SELECT week_start_date, status,
                   ROW_NUMBER() OVER (ORDER BY week_start_date DESC) as rn
            FROM weekly_goal
            WHERE father_id = :fatherId AND week_start_date <= :fromDate
        )
        SELECT COUNT(*) FROM ranked
        WHERE status = 'COMPLETED'
        AND rn <= (
            SELECT MIN(rn) FROM ranked WHERE status != 'COMPLETED'
            UNION ALL SELECT (SELECT MAX(rn) FROM ranked) + 1
        ) - 1
        """, nativeQuery = true)
    int countConsecutiveCompletedWeeks(@Param("fatherId") Long fatherId, @Param("fromDate") LocalDate fromDate);

    /**
     * Get the last completed or missed goal (for showing in weekly summary).
     */
    @Query("SELECT wg FROM WeeklyGoal wg WHERE wg.fatherId = :fatherId " +
           "AND wg.status IN ('COMPLETED', 'MISSED') " +
           "ORDER BY wg.weekStartDate DESC LIMIT 1")
    Optional<WeeklyGoal> findLastCompletedOrMissedGoal(@Param("fatherId") Long fatherId);

    /**
     * Find all goals for a father ordered by creation date descending.
     */
    List<WeeklyGoal> findByFatherIdOrderByCreatedAtDesc(Long fatherId);
}
