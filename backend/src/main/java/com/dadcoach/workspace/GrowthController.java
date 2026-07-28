package com.dadcoach.workspace;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.workspace.dto.response.AchievementsResponse;
import com.dadcoach.workspace.dto.response.BeltProgressionResponse;
import com.dadcoach.workspace.dto.response.GrowthScoreBreakdownResponse;
import com.dadcoach.workspace.dto.response.StreakResponse;
import com.dadcoach.workspace.growth.achievement.AchievementEvaluator;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.signal.GrowthSignal;
import com.dadcoach.workspace.growth.signal.GrowthSignalService;
import com.dadcoach.workspace.growth.signal.GrowthSignalType;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for growth-related workspace endpoints.
 *
 * <p>Provides access to belt progression, growth score breakdown, streak status,
 * and achievements for the authenticated father.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/growth")
public class GrowthController {

    private final BeltProgressionService beltProgressionService;
    private final GrowthSignalService growthSignalService;
    private final StreakService streakService;
    private final AchievementEvaluator achievementEvaluator;

    public GrowthController(BeltProgressionService beltProgressionService,
                            GrowthSignalService growthSignalService,
                            StreakService streakService,
                            AchievementEvaluator achievementEvaluator) {
        this.beltProgressionService = beltProgressionService;
        this.growthSignalService = growthSignalService;
        this.streakService = streakService;
        this.achievementEvaluator = achievementEvaluator;
    }

    /**
     * Returns the father's belt progression including current belt, score, and next belt progress.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with belt progression response
     */
    @GetMapping("/belt")
    public ResponseEntity<BeltProgressionResponse> getBeltProgression(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        BeltProgressionResponse response = beltProgressionService.getProgression(fatherId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the father's growth score breakdown by signal type, with period counts.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with growth score breakdown response
     */
    @GetMapping("/score")
    public ResponseEntity<GrowthScoreBreakdownResponse> getScoreBreakdown(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();

        // Get score breakdown by signal type
        Map<GrowthSignalType, Integer> breakdown = growthSignalService.getScoreBreakdown(fatherId);
        Map<String, Integer> breakdownByName = breakdown.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        Map.Entry::getValue
                ));

        // Get total score
        int totalScore = growthSignalService.getTotalScore(fatherId);

        // Count signals this week and this month
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
        LocalDate startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());

        Instant weekStart = startOfWeek.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = startOfMonth.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<GrowthSignal> weekSignals = growthSignalService.getSignalsInPeriod(fatherId, weekStart, now);
        List<GrowthSignal> monthSignals = growthSignalService.getSignalsInPeriod(fatherId, monthStart, now);

        // Get recent signals (last 10)
        List<GrowthSignal> recentSignals = growthSignalService.getRecentSignals(fatherId, 10);
        List<GrowthScoreBreakdownResponse.RecentSignalItem> recentItems = recentSignals.stream()
                .map(s -> new GrowthScoreBreakdownResponse.RecentSignalItem(
                        s.getSignalId(),
                        s.getSignalType().name(),
                        s.getPointsAwarded(),
                        s.getSourceEntityType(),
                        s.getCreatedAt()
                ))
                .toList();

        GrowthScoreBreakdownResponse response = GrowthScoreBreakdownResponse.builder()
                .totalScore(totalScore)
                .scoreBySignalType(breakdownByName)
                .signalsThisWeek(weekSignals.size())
                .signalsThisMonth(monthSignals.size())
                .recentSignals(recentItems)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the father's streak status including current/longest streak and at-risk indicator.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with streak response
     */
    @GetMapping("/streak")
    public ResponseEntity<StreakResponse> getStreak(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        StreakResponse response = streakService.getStreakResponse(fatherId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the father's achievements with earned status and next achievable.
     *
     * @param actor the authenticated actor context
     * @return 200 OK with achievements response
     */
    @GetMapping("/achievements")
    public ResponseEntity<AchievementsResponse> getAchievements(@AuthActor ActorContext actor) {
        UUID fatherId = actor.getActorId();
        AchievementsResponse response = achievementEvaluator.getAchievements(fatherId);
        return ResponseEntity.ok(response);
    }
}
