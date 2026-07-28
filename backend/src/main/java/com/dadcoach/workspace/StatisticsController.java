package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.dto.response.MetricsDashboardResponse;
import com.dadcoach.workspace.dto.response.MonthlyStatisticsResponse;
import com.dadcoach.workspace.dto.response.WeeklyStatisticsResponse;
import com.dadcoach.workspace.statistics.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for statistics and metrics endpoints.
 *
 * <p>Provides access to weekly statistics, monthly statistics, and an
 * overall metrics dashboard for the authenticated father.</p>
 *
 * <p>Requirements 8.1, 8.2, 15.4: Statistics and metrics endpoints.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Returns weekly statistics for the father.
     *
     * @param actor     the authenticated actor context
     * @param weekStart optional Monday date for the desired week (defaults to current week)
     * @return 200 OK with weekly statistics
     */
    @GetMapping("/statistics/weekly")
    public ResponseEntity<WeeklyStatisticsResponse> getWeeklyStatistics(
            @AuthActor ActorContext actor,
            @RequestParam(value = "week_start", required = false) String weekStart) {

        UUID fatherId = actor.getActorId();
        LocalDate start = weekStart != null ? LocalDate.parse(weekStart) : null;

        WeeklyStatisticsResponse response = statisticsService.getWeeklyStatistics(fatherId, start);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns monthly statistics for the father.
     *
     * @param actor      the authenticated actor context
     * @param monthStart optional first day of month (defaults to current month)
     * @return 200 OK with monthly statistics
     */
    @GetMapping("/statistics/monthly")
    public ResponseEntity<MonthlyStatisticsResponse> getMonthlyStatistics(
            @AuthActor ActorContext actor,
            @RequestParam(value = "month_start", required = false) String monthStart) {

        UUID fatherId = actor.getActorId();
        LocalDate start = monthStart != null ? LocalDate.parse(monthStart) : null;

        MonthlyStatisticsResponse response = statisticsService.getMonthlyStatistics(fatherId, start);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the metrics dashboard for the father.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with dashboard metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsDashboardResponse> getMetricsDashboard(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        MetricsDashboardResponse response = statisticsService.getMetricsDashboard(fatherId);
        return ResponseEntity.ok(response);
    }
}
