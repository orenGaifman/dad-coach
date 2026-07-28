package com.dadcoach.workspace.statistics;

import com.dadcoach.workspace.dto.response.MetricsDashboardResponse;
import com.dadcoach.workspace.dto.response.MonthlyStatisticsResponse;
import com.dadcoach.workspace.dto.response.WeeklyStatisticsResponse;
import com.dadcoach.workspace.growth.signal.GrowthSignal;
import com.dadcoach.workspace.growth.signal.GrowthSignalService;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for retrieving statistics and metrics for the father's workspace.
 *
 * <p>Returns pre-computed aggregates when available, or computes on-demand
 * for the current (incomplete) period. Provides weekly, monthly, and
 * dashboard-level metrics.</p>
 */
@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    private final StatisticsAggregateRepository statisticsAggregateRepository;
    private final GrowthSignalService growthSignalService;
    private final ObjectMapper objectMapper;

    public StatisticsService(StatisticsAggregateRepository statisticsAggregateRepository,
                             GrowthSignalService growthSignalService,
                             ObjectMapper objectMapper) {
        this.statisticsAggregateRepository = statisticsAggregateRepository;
        this.growthSignalService = growthSignalService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns weekly statistics for the given father and week start date.
     * Uses pre-computed aggregate if available; otherwise computes on-demand.
     *
     * @param fatherId  the father's ID
     * @param weekStart the Monday of the desired week (null for current week)
     * @return weekly statistics response
     */
    public WeeklyStatisticsResponse getWeeklyStatistics(UUID fatherId, LocalDate weekStart) {
        LocalDate start = weekStart != null ? weekStart : LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);

        Optional<StatisticsAggregate> aggregate = statisticsAggregateRepository
                .findByFatherIdAndPeriodTypeAndPeriodStart(fatherId, StatisticsPeriodType.WEEKLY, start);

        if (aggregate.isPresent()) {
            return parseWeeklyFromAggregate(aggregate.get());
        }

        // Compute on-demand for current/recent week
        return computeWeeklyOnDemand(fatherId, start, end);
    }

    /**
     * Returns monthly statistics for the given father and month.
     * Uses pre-computed aggregate if available; otherwise computes on-demand.
     *
     * @param fatherId   the father's ID
     * @param monthStart the first day of the desired month (null for current month)
     * @return monthly statistics response
     */
    public MonthlyStatisticsResponse getMonthlyStatistics(UUID fatherId, LocalDate monthStart) {
        LocalDate start = monthStart != null ? monthStart : LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());

        Optional<StatisticsAggregate> aggregate = statisticsAggregateRepository
                .findByFatherIdAndPeriodTypeAndPeriodStart(fatherId, StatisticsPeriodType.MONTHLY, start);

        if (aggregate.isPresent()) {
            return parseMonthlyFromAggregate(aggregate.get());
        }

        return computeMonthlyOnDemand(fatherId, start, end);
    }

    /**
     * Returns the metrics dashboard for the father, providing high-level
     * engagement and progress indicators.
     *
     * @param fatherId the father's ID
     * @return metrics dashboard response
     */
    public MetricsDashboardResponse getMetricsDashboard(UUID fatherId) {
        // Compute engagement score from recent signals (last 30 days)
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
        List<GrowthSignal> recentSignals = growthSignalService.getSignalsInPeriod(fatherId, thirtyDaysAgo, now);

        int totalPoints = recentSignals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum();
        double engagementScore = Math.min(100.0, (totalPoints / 500.0) * 100.0);

        // Quality time total (last 30 days)
        long qualityTimeReports = recentSignals.stream()
                .filter(s -> s.getSignalType() == GrowthSignalType.QUALITY_TIME_REPORTED)
                .count();
        int qualityTimeTotal = (int) (qualityTimeReports * 30); // estimate 30 min per report

        // Completion rate (missions completed / missions assigned)
        long missionsCompleted = recentSignals.stream()
                .filter(s -> s.getSignalType() == GrowthSignalType.MISSION_COMPLETED)
                .count();
        // Estimate a completion rate based on activity
        double completionRate = missionsCompleted > 0 ? Math.min(100.0, missionsCompleted * 10.0) : 0.0;

        // Week over week growth
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
        Instant fourteenDaysAgo = now.minus(Duration.ofDays(14));
        List<GrowthSignal> thisWeekSignals = growthSignalService.getSignalsInPeriod(fatherId, sevenDaysAgo, now);
        List<GrowthSignal> lastWeekSignals = growthSignalService.getSignalsInPeriod(fatherId, fourteenDaysAgo, sevenDaysAgo);

        int thisWeekPoints = thisWeekSignals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum();
        int lastWeekPoints = lastWeekSignals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum();
        double weekOverWeekGrowth = lastWeekPoints > 0
                ? ((thisWeekPoints - lastWeekPoints) / (double) lastWeekPoints) * 100.0
                : (thisWeekPoints > 0 ? 100.0 : 0.0);

        return MetricsDashboardResponse.builder()
                .engagementScore(Math.round(engagementScore * 10.0) / 10.0)
                .qualityTimeTotal(qualityTimeTotal)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .weekOverWeekGrowth(Math.round(weekOverWeekGrowth * 10.0) / 10.0)
                .build();
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private WeeklyStatisticsResponse computeWeeklyOnDemand(UUID fatherId, LocalDate weekStart, LocalDate weekEnd) {
        Instant start = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<GrowthSignal> signals = growthSignalService.getSignalsInPeriod(fatherId, start, end);

        long missionsCompleted = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.MISSION_COMPLETED).count();
        long conversations = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.MEANINGFUL_CONVERSATION).count();
        long goalsProgressed = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.GOAL_PROGRESS || s.getSignalType() == GrowthSignalType.GOAL_COMPLETED).count();
        int growthScoreDelta = signals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum();
        long streakDays = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.DAILY_ENGAGEMENT).count();
        long qualityTimeReports = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.QUALITY_TIME_REPORTED).count();

        return WeeklyStatisticsResponse.builder()
                .missionsCompleted((int) missionsCompleted)
                .conversationsCount((int) conversations)
                .goalsProgressed((int) goalsProgressed)
                .growthScoreDelta(growthScoreDelta)
                .streakDaysThisWeek((int) streakDays)
                .qualityTimeMinutes((int) (qualityTimeReports * 30))
                .build();
    }

    private MonthlyStatisticsResponse computeMonthlyOnDemand(UUID fatherId, LocalDate monthStart, LocalDate monthEnd) {
        Instant start = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = monthEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<GrowthSignal> signals = growthSignalService.getSignalsInPeriod(fatherId, start, end);

        long missionsCompleted = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.MISSION_COMPLETED).count();
        long goalsCompleted = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.GOAL_COMPLETED).count();
        long conversations = signals.stream().filter(s -> s.getSignalType() == GrowthSignalType.MEANINGFUL_CONVERSATION).count();
        int totalPoints = signals.stream().mapToInt(GrowthSignal::getPointsAwarded).sum();

        // Average daily engagement = points / days in month
        int daysInMonth = monthEnd.getDayOfMonth();
        double averageDailyEngagement = daysInMonth > 0 ? (double) totalPoints / daysInMonth : 0.0;

        return MonthlyStatisticsResponse.builder()
                .missionsCompleted((int) missionsCompleted)
                .goalsCompleted((int) goalsCompleted)
                .conversationsCount((int) conversations)
                .averageDailyEngagement(Math.round(averageDailyEngagement * 10.0) / 10.0)
                .growthScoreStart(0) // Placeholder — requires historical lookup
                .growthScoreEnd(totalPoints)
                .achievementsEarned(0) // Placeholder — enhanced in integration
                .longestStreakInMonth(0) // Placeholder — enhanced in integration
                .build();
    }

    private WeeklyStatisticsResponse parseWeeklyFromAggregate(StatisticsAggregate aggregate) {
        Map<String, Object> data = parseJsonData(aggregate.getData());

        return WeeklyStatisticsResponse.builder()
                .missionsCompleted(getIntValue(data, "missionsCompleted"))
                .conversationsCount(getIntValue(data, "conversationsCount"))
                .goalsProgressed(getIntValue(data, "goalsProgressed"))
                .growthScoreDelta(getIntValue(data, "growthScoreDelta"))
                .streakDaysThisWeek(getIntValue(data, "streakDaysThisWeek"))
                .qualityTimeMinutes(getIntValue(data, "qualityTimeMinutes"))
                .build();
    }

    private MonthlyStatisticsResponse parseMonthlyFromAggregate(StatisticsAggregate aggregate) {
        Map<String, Object> data = parseJsonData(aggregate.getData());

        return MonthlyStatisticsResponse.builder()
                .missionsCompleted(getIntValue(data, "missionsCompleted"))
                .goalsCompleted(getIntValue(data, "goalsCompleted"))
                .conversationsCount(getIntValue(data, "conversationsCount"))
                .averageDailyEngagement(getDoubleValue(data, "averageDailyEngagement"))
                .growthScoreStart(getIntValue(data, "growthScoreStart"))
                .growthScoreEnd(getIntValue(data, "totalPoints"))
                .achievementsEarned(getIntValue(data, "achievementsEarned"))
                .longestStreakInMonth(getIntValue(data, "longestStreakInMonth"))
                .build();
    }

    private Map<String, Object> parseJsonData(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse aggregate data JSON", e);
            return Map.of();
        }
    }

    private int getIntValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
