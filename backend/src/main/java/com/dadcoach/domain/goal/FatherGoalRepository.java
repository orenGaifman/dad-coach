package com.dadcoach.domain.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for father goals.
 */
@Repository
public interface FatherGoalRepository extends JpaRepository<FatherGoal, Long> {

    /**
     * Find active goal for a father by type.
     */
    @Query("SELECT g FROM FatherGoal g WHERE g.fatherId = :fatherId " +
           "AND g.goalType = :goalType AND g.status = 'ACTIVE' " +
           "AND :today BETWEEN g.periodStart AND g.periodEnd")
    Optional<FatherGoal> findActiveGoal(@Param("fatherId") Long fatherId,
                                        @Param("goalType") FatherGoal.GoalType goalType,
                                        @Param("today") LocalDate today);

    /**
     * Find current week's goal for a father.
     */
    default Optional<FatherGoal> findCurrentWeeklyGoal(Long fatherId) {
        return findActiveGoal(fatherId, FatherGoal.GoalType.WEEKLY, LocalDate.now());
    }

    /**
     * Find current month's goal for a father.
     */
    default Optional<FatherGoal> findCurrentMonthlyGoal(Long fatherId) {
        return findActiveGoal(fatherId, FatherGoal.GoalType.MONTHLY, LocalDate.now());
    }

    /**
     * Find all active goals that have ended (need to be marked as missed).
     */
    @Query("SELECT g FROM FatherGoal g WHERE g.status = 'ACTIVE' AND g.periodEnd < :today")
    List<FatherGoal> findEndedActiveGoals(@Param("today") LocalDate today);

    /**
     * Find completed goals for a father (for stats).
     */
    @Query("SELECT g FROM FatherGoal g WHERE g.fatherId = :fatherId " +
           "AND g.status = 'COMPLETED' ORDER BY g.completedAt DESC")
    List<FatherGoal> findCompletedGoals(@Param("fatherId") Long fatherId);

    /**
     * Count consecutive completed weekly goals (for streak calculation).
     */
    @Query("SELECT COUNT(g) FROM FatherGoal g WHERE g.fatherId = :fatherId " +
           "AND g.goalType = 'WEEKLY' AND g.status = 'COMPLETED' " +
           "AND g.periodStart >= :since")
    long countCompletedWeeklyGoalsSince(@Param("fatherId") Long fatherId,
                                        @Param("since") LocalDate since);

    /**
     * Find goals by father ordered by period.
     */
    List<FatherGoal> findByFatherIdOrderByPeriodStartDesc(Long fatherId);
}
