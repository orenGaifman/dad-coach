package com.dadcoach.workspace.statistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link StatisticsAggregate} entities.
 *
 * <p>Provides lookup by father, period type, and date range for serving
 * pre-computed statistics to the API layer.</p>
 */
@Repository
public interface StatisticsAggregateRepository extends JpaRepository<StatisticsAggregate, UUID> {

    /**
     * Finds a specific aggregate for a father, period type, and start date.
     * Used for retrieving weekly/monthly statistics for a specific period.
     */
    Optional<StatisticsAggregate> findByFatherIdAndPeriodTypeAndPeriodStart(
            UUID fatherId, StatisticsPeriodType periodType, LocalDate periodStart);

    /**
     * Finds all aggregates for a father of a specific period type, ordered by period start descending.
     */
    List<StatisticsAggregate> findByFatherIdAndPeriodTypeOrderByPeriodStartDesc(
            UUID fatherId, StatisticsPeriodType periodType);

    /**
     * Finds the most recent aggregate for a father and period type.
     */
    @Query("SELECT s FROM StatisticsAggregate s WHERE s.fatherId = :fatherId AND s.periodType = :periodType ORDER BY s.periodStart DESC LIMIT 1")
    Optional<StatisticsAggregate> findMostRecentByFatherIdAndPeriodType(
            @Param("fatherId") UUID fatherId,
            @Param("periodType") StatisticsPeriodType periodType);

    /**
     * Returns all distinct father IDs that have at least one aggregate.
     * Used by the aggregation job to know which fathers to process.
     */
    @Query("SELECT DISTINCT s.fatherId FROM StatisticsAggregate s")
    List<UUID> findDistinctFatherIds();
}
