package com.dadcoach.workspace.statistics;

import com.dadcoach.workspace.growth.signal.GrowthSignal;
import com.dadcoach.workspace.growth.signal.GrowthSignalService;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Scheduled job that computes daily, weekly, and monthly statistics aggregates.
 *
 * <p>Runs nightly at 02:00 UTC. Computes aggregates for the previous day, current
 * week, and current month for all active fathers. Stores results as JSONB in the
 * statistics_aggregates table for fast read access.</p>
 *
 * <p><strong>Idempotency:</strong> This operation IS idempotent. The {@code upsertAggregate}
 * method checks for an existing record with the same (father_id, period_type, period_start)
 * and updates it if found, or inserts a new one otherwise. The database UNIQUE constraint
 * {@code uq_father_period} prevents duplicates even under concurrent execution. Re-running
 * the job overwrites the aggregate with freshly computed data (harmless). Safe for
 * multi-instance deployments.</p>
 *
 * <p>Requirement 8.4: Statistics are pre-computed nightly for performance.</p>
 */
@Component
public class StatisticsAggregationJob {

    private static final Logger log = LoggerFactory.getLogger(StatisticsAggregationJob.class);

    private final StatisticsAggregateRepository statisticsAggregateRepository;
    private final GrowthSignalService growthSignalService;
    private final ObjectMapper objectMapper;

    public StatisticsAggregationJob(StatisticsAggregateRepository statisticsAggregateRepository,
                                    GrowthSignalService growthSignalService,
                                    ObjectMapper objectMapper) {
        this.statisticsAggregateRepository = statisticsAggregateRepository;
        this.growthSignalService = growthSignalService;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the statistics aggregation at 02:00 UTC daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void computeAggregates() {
        log.info("Starting statistics aggregation job");
        try {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            LocalDate weekStart = yesterday.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDate monthStart = yesterday.with(TemporalAdjusters.firstDayOfMonth());
            LocalDate monthEnd = yesterday.with(TemporalAdjusters.lastDayOfMonth());

            List<UUID> fatherIds = statisticsAggregateRepository.findDistinctFatherIds();
            int processed = 0;

            for (UUID fatherId : fatherIds) {
                try {
                    computeDailyAggregate(fatherId, yesterday);
                    computeWeeklyAggregate(fatherId, weekStart, weekEnd);
                    computeMonthlyAggregate(fatherId, monthStart, monthEnd);
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to compute aggregates for father {}", fatherId, e);
                }
            }

            log.info("Statistics aggregation job completed. Processed {} fathers.", processed);
        } catch (Exception e) {
            log.error("Statistics aggregation job failed", e);
        }
    }

    private void computeDailyAggregate(UUID fatherId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<GrowthSignal> signals = growthSignalService.getSignalsInPeriod(fatherId, dayStart, dayEnd);
        Map<String, Object> data = buildDailyData(signals);

        upsertAggregate(fatherId, StatisticsPeriodType.DAILY, date, date, data);
    }

    private void computeWeeklyAggregate(UUID fatherId, LocalDate weekStart, LocalDate weekEnd) {
        Instant start = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<GrowthSignal> signals = growthSignalService.getSignalsInPeriod(fatherId, start, end);
        Map<String, Object> data = buildWeeklyData(signals);

        upsertAggregate(fatherId, StatisticsPeriodType.WEEKLY, weekStart, weekEnd, data);
    }

    private void computeMonthlyAggregate(UUID fatherId, LocalDate monthStart, LocalDate monthEnd) {
        Instant start = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = monthEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<GrowthSignal> signals = growthSignalService.getSignalsInPeriod(fatherId, start, end);
        Map<String, Object> data = buildMonthlyData(signals);

        upsertAggregate(fatherId, StatisticsPeriodType.MONTHLY, monthStart, monthEnd, data);
    }

    private Map<String, Object> buildDailyData(List<GrowthSignal> signals) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalSignals", signals.size());
        data.put("totalPoints", signals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum());
        data.put("missionsCompleted", countByType(signals, GrowthSignalType.MISSION_COMPLETED));
        data.put("conversationsCount", countByType(signals, GrowthSignalType.MEANINGFUL_CONVERSATION));
        data.put("qualityTimeReports", countByType(signals, GrowthSignalType.QUALITY_TIME_REPORTED));
        return data;
    }

    private Map<String, Object> buildWeeklyData(List<GrowthSignal> signals) {
        Map<String, Object> data = new HashMap<>();
        data.put("missionsCompleted", countByType(signals, GrowthSignalType.MISSION_COMPLETED));
        data.put("conversationsCount", countByType(signals, GrowthSignalType.MEANINGFUL_CONVERSATION));
        data.put("goalsProgressed", countByType(signals, GrowthSignalType.GOAL_PROGRESS)
                + countByType(signals, GrowthSignalType.GOAL_COMPLETED));
        data.put("growthScoreDelta", signals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum());
        data.put("qualityTimeMinutes", countByType(signals, GrowthSignalType.QUALITY_TIME_REPORTED) * 30); // estimate
        data.put("streakDaysThisWeek", countStreakDays(signals));
        return data;
    }

    private Map<String, Object> buildMonthlyData(List<GrowthSignal> signals) {
        Map<String, Object> data = new HashMap<>();
        data.put("missionsCompleted", countByType(signals, GrowthSignalType.MISSION_COMPLETED));
        data.put("goalsCompleted", countByType(signals, GrowthSignalType.GOAL_COMPLETED));
        data.put("conversationsCount", countByType(signals, GrowthSignalType.MEANINGFUL_CONVERSATION));
        data.put("totalPoints", signals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum());
        data.put("achievementsEarned", 0); // Placeholder — enhanced in integration
        data.put("longestStreakInMonth", 0); // Placeholder — enhanced in integration
        return data;
    }

    private long countByType(List<GrowthSignal> signals, GrowthSignalType type) {
        return signals.stream().filter(s -> s.getSignalType() == type).count();
    }

    private long countStreakDays(List<GrowthSignal> signals) {
        return signals.stream()
                .filter(s -> s.getSignalType() == GrowthSignalType.DAILY_ENGAGEMENT)
                .count();
    }

    private void upsertAggregate(UUID fatherId, StatisticsPeriodType periodType,
                                 LocalDate periodStart, LocalDate periodEnd,
                                 Map<String, Object> data) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize aggregate data for father {} period {}", fatherId, periodType, e);
            return;
        }

        Optional<StatisticsAggregate> existing = statisticsAggregateRepository
                .findByFatherIdAndPeriodTypeAndPeriodStart(fatherId, periodType, periodStart);

        if (existing.isPresent()) {
            StatisticsAggregate aggregate = existing.get();
            aggregate.setData(jsonData);
            aggregate.setComputedAt(Instant.now());
            statisticsAggregateRepository.save(aggregate);
        } else {
            StatisticsAggregate aggregate = StatisticsAggregate.builder()
                    .fatherId(fatherId)
                    .periodType(periodType)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .data(jsonData)
                    .computedAt(Instant.now())
                    .build();
            statisticsAggregateRepository.save(aggregate);
        }
    }
}
