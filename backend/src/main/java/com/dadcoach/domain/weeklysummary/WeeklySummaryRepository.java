package com.dadcoach.domain.weeklysummary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link WeeklySummary} entities.
 *
 * <p>The UNIQUE(father_id, week_start) constraint ensures one summary per father per week.</p>
 */
@Repository
public interface WeeklySummaryRepository extends JpaRepository<WeeklySummary, Long> {

    /**
     * Find a weekly summary for a specific father and week start date.
     *
     * @param fatherId  the father ID
     * @param weekStart the Monday that starts the week
     * @return the weekly summary if it exists
     */
    Optional<WeeklySummary> findByFatherIdAndWeekStart(Long fatherId, LocalDate weekStart);

    /**
     * Check if a weekly summary already exists for a father and week.
     *
     * @param fatherId  the father ID
     * @param weekStart the Monday that starts the week
     * @return true if a summary exists
     */
    boolean existsByFatherIdAndWeekStart(Long fatherId, LocalDate weekStart);

    /**
     * Find all weekly summaries for a father ordered by week_start descending.
     *
     * @param fatherId the father ID
     * @return list of weekly summaries
     */
    List<WeeklySummary> findByFatherIdOrderByWeekStartDesc(Long fatherId);

    /**
     * Find undelivered weekly summaries.
     *
     * @return list of summaries that have not been delivered yet
     */
    @Query("SELECT ws FROM WeeklySummary ws WHERE ws.deliveredAt IS NULL")
    List<WeeklySummary> findUndelivered();
}
