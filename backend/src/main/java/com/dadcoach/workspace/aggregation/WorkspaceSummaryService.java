package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.dto.response.PartialResponse;
import com.dadcoach.workspace.dto.response.WorkspaceSummaryResponse;
import com.dadcoach.workspace.growth.belt.BeltLevel;
import com.dadcoach.workspace.growth.belt.BeltProgressionService;
import com.dadcoach.workspace.growth.belt.FatherBelt;
import com.dadcoach.workspace.growth.score.GrowthScoreService;
import com.dadcoach.workspace.growth.streak.FatherStreak;
import com.dadcoach.workspace.growth.streak.StreakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Composes the workspace summary from multiple domain services.
 *
 * <p>Owns NO state — reads from Father, Growth, Notification services.
 * Implements partial degradation (Design Decision AD-6): returns available data
 * when sources fail, tracking which sections are degraded.</p>
 *
 * <p>Uses CompletableFuture for parallel fetching of independent data sources
 * with a per-source timeout. Each fetch is wrapped in try-catch — a failure
 * in one source does not cascade to others.</p>
 */
@Service
public class WorkspaceSummaryService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSummaryService.class);
    private static final long FETCH_TIMEOUT_SECONDS = 5;

    private final FatherDataService fatherDataService;
    private final GrowthScoreService growthScoreService;
    private final BeltProgressionService beltProgressionService;
    private final StreakService streakService;
    private final NotificationDataService notificationDataService;
    private final ChildDataService childDataService;
    private final GoalDataService goalDataService;
    private final MissionDataService missionDataService;

    public WorkspaceSummaryService(
            FatherDataService fatherDataService,
            GrowthScoreService growthScoreService,
            BeltProgressionService beltProgressionService,
            StreakService streakService,
            NotificationDataService notificationDataService,
            ChildDataService childDataService,
            GoalDataService goalDataService,
            MissionDataService missionDataService) {
        this.fatherDataService = fatherDataService;
        this.growthScoreService = growthScoreService;
        this.beltProgressionService = beltProgressionService;
        this.streakService = streakService;
        this.notificationDataService = notificationDataService;
        this.childDataService = childDataService;
        this.goalDataService = goalDataService;
        this.missionDataService = missionDataService;
    }

    /**
     * Fetches workspace summary with partial degradation support.
     *
     * <p>Each data source is fetched independently. If a source fails (exception or timeout),
     * the corresponding field is set to null/default and the section name is added to the
     * degraded_sections list. The response is wrapped in a {@link PartialResponse} with
     * status "complete" or "partial" depending on whether all sources succeeded.</p>
     *
     * @param fatherId the father's unique identifier
     * @return the workspace summary wrapped in a PartialResponse
     */
    public PartialResponse<WorkspaceSummaryResponse> getSummary(UUID fatherId) {
        List<String> degradedSections = new ArrayList<>();

        // Parallel fetch all data sources
        CompletableFuture<FatherReadModel> fatherFuture = CompletableFuture.supplyAsync(() ->
                fatherDataService.getFather(fatherId).orElse(null));

        CompletableFuture<Integer> scoreFuture = CompletableFuture.supplyAsync(() ->
                growthScoreService.getTotalScore(fatherId));

        CompletableFuture<FatherBelt> beltFuture = CompletableFuture.supplyAsync(() ->
                beltProgressionService.getCurrentBelt(fatherId));

        CompletableFuture<FatherStreak> streakFuture = CompletableFuture.supplyAsync(() ->
                streakService.getStreak(fatherId));

        CompletableFuture<Integer> notificationFuture = CompletableFuture.supplyAsync(() ->
                notificationDataService.getUnreadCount(fatherId));

        CompletableFuture<Integer> childrenCountFuture = CompletableFuture.supplyAsync(() ->
                childDataService.getChildrenByFatherId(fatherId).size());

        CompletableFuture<Integer> goalsCountFuture = CompletableFuture.supplyAsync(() ->
                goalDataService.countActiveGoalsByFatherId(fatherId));

        CompletableFuture<MissionReadModel> activeMissionFuture = CompletableFuture.supplyAsync(() ->
                missionDataService.getActiveMission(fatherId).orElse(null));

        // Collect results with partial degradation
        String displayName = fetchWithDegradation(fatherFuture, "father_profile", degradedSections,
                father -> father != null ? father.displayName() : null);
        String coachingPhase = fetchWithDegradation(fatherFuture, "father_profile", degradedSections,
                father -> father != null && father.coachingPhase() != null ? father.coachingPhase().name() : null);

        Integer growthScore = fetchWithDegradation(scoreFuture, "growth_score", degradedSections);
        BeltLevel currentBelt = fetchBeltWithDegradation(beltFuture, "belt", degradedSections);
        Integer currentStreakDays = fetchStreakWithDegradation(streakFuture, "streak", degradedSections);
        Integer unreadNotificationsCount = fetchWithDegradation(notificationFuture, "notifications", degradedSections);
        Integer activeChildrenCount = fetchWithDegradation(childrenCountFuture, "children", degradedSections);
        Integer activeGoalsCount = fetchWithDegradation(goalsCountFuture, "goals", degradedSections);
        WorkspaceSummaryResponse.ActiveMissionSummary activeMission =
                fetchActiveMissionWithDegradation(activeMissionFuture, "active_mission", degradedSections);

        // Remove duplicates from degraded sections
        List<String> uniqueDegraded = degradedSections.stream().distinct().toList();

        WorkspaceSummaryResponse response = WorkspaceSummaryResponse.builder()
                .displayName(displayName)
                .coachingPhase(coachingPhase)
                .currentBelt(currentBelt != null ? currentBelt.name() : null)
                .growthScore(growthScore)
                .activeChildrenCount(activeChildrenCount)
                .activeGoalsCount(activeGoalsCount)
                .currentStreakDays(currentStreakDays)
                .unreadNotificationsCount(unreadNotificationsCount)
                .activeMission(activeMission)
                .build();

        if (uniqueDegraded.isEmpty()) {
            return PartialResponse.complete(response);
        } else {
            return PartialResponse.partial(response, uniqueDegraded);
        }
    }

    private <T> T fetchWithDegradation(CompletableFuture<T> future, String sectionName,
                                        List<String> degradedSections) {
        try {
            return future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fetch interrupted for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Fetch failed for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        }
    }

    private <T, R> R fetchWithDegradation(CompletableFuture<T> future, String sectionName,
                                           List<String> degradedSections,
                                           java.util.function.Function<T, R> mapper) {
        try {
            T result = future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return mapper.apply(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fetch interrupted for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Fetch failed for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        }
    }

    private BeltLevel fetchBeltWithDegradation(CompletableFuture<FatherBelt> future,
                                                String sectionName,
                                                List<String> degradedSections) {
        try {
            FatherBelt belt = future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return belt != null ? belt.getBeltLevel() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fetch interrupted for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Fetch failed for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        }
    }

    private Integer fetchStreakWithDegradation(CompletableFuture<FatherStreak> future,
                                               String sectionName,
                                               List<String> degradedSections) {
        try {
            FatherStreak streak = future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return streak != null ? streak.getCurrentStreakDays() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fetch interrupted for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Fetch failed for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        }
    }

    private WorkspaceSummaryResponse.ActiveMissionSummary fetchActiveMissionWithDegradation(
            CompletableFuture<MissionReadModel> future,
            String sectionName,
            List<String> degradedSections) {
        try {
            MissionReadModel mission = future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (mission == null) {
                return null;
            }
            return new WorkspaceSummaryResponse.ActiveMissionSummary(
                    mission.missionId(),
                    mission.title(),
                    mission.status().name()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Fetch interrupted for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Fetch failed for section '{}': {}", sectionName, e.getMessage());
            degradedSections.add(sectionName);
            return null;
        }
    }
}
