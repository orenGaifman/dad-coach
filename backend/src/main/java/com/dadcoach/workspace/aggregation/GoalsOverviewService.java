package com.dadcoach.workspace.aggregation;

import com.dadcoach.mission.MissionStatus;
import com.dadcoach.workspace.ResourceNotFoundException;
import com.dadcoach.workspace.dto.response.GoalProgressResponse;
import com.dadcoach.workspace.dto.response.GoalsOverviewResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates goal data with progress calculations for the workspace goals overview.
 *
 * <p>Computes progress_percentage as min(completed/estimated * 100, 100), defaulting
 * estimated to 10 if unavailable. Supports filtering by status, category, and child_id.</p>
 */
@Service
public class GoalsOverviewService {

    private static final int DEFAULT_ESTIMATED_MISSIONS = 10;

    private final GoalDataService goalDataService;
    private final MissionDataService missionDataService;
    private final ChildDataService childDataService;

    public GoalsOverviewService(GoalDataService goalDataService,
                                MissionDataService missionDataService,
                                ChildDataService childDataService) {
        this.goalDataService = goalDataService;
        this.missionDataService = missionDataService;
        this.childDataService = childDataService;
    }

    /**
     * Returns goals overview for a father with optional filters applied.
     *
     * @param fatherId the father's unique identifier
     * @param filters  optional filtering parameters (status, category, childId)
     * @return the goals overview response with progress calculations
     */
    public GoalsOverviewResponse getGoalsOverview(UUID fatherId, GoalFilterParams filters) {
        List<GoalReadModel> goals = goalDataService.getAllGoalsByFatherId(fatherId);

        // Apply filters
        List<GoalReadModel> filteredGoals = goals.stream()
                .filter(g -> filters.status() == null || filters.status().equalsIgnoreCase(g.status()))
                .filter(g -> filters.category() == null || filters.category().equalsIgnoreCase(g.category()))
                .filter(g -> filters.childId() == null || filters.childId().equals(g.childId()))
                .toList();

        List<GoalsOverviewResponse.GoalItem> goalItems = filteredGoals.stream()
                .map(this::buildGoalItem)
                .toList();

        return new GoalsOverviewResponse(goalItems, goalItems.size());
    }

    /**
     * Returns detailed goal progress for a specific goal, including related missions.
     *
     * @param fatherId the father's unique identifier (for ownership verification)
     * @param goalId   the goal's unique identifier
     * @return detailed goal progress response
     * @throws ResourceNotFoundException if the goal is not found or doesn't belong to this father
     */
    public GoalProgressResponse getGoalProgress(UUID fatherId, UUID goalId) {
        GoalReadModel goal = goalDataService.getGoalById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("goal", goalId));

        // Verify ownership
        if (!goal.fatherId().equals(fatherId)) {
            throw new ResourceNotFoundException("goal", goalId);
        }

        int progressPercentage = calculateProgressPercentage(goal);

        // Get related missions for this goal's child
        List<MissionReadModel> relatedMissions = goal.childId() != null
                ? missionDataService.getMissionsByChildId(goal.childId())
                : List.of();

        List<GoalProgressResponse.MissionItem> missionItems = relatedMissions.stream()
                .map(m -> new GoalProgressResponse.MissionItem(
                        m.missionId(),
                        m.title(),
                        m.status().name(),
                        m.completedAt()))
                .toList();

        // Get child name if assigned
        String relatedChildName = null;
        if (goal.childId() != null) {
            relatedChildName = childDataService.getChild(goal.childId())
                    .map(ChildReadModel::name)
                    .orElse(null);
        }

        return GoalProgressResponse.builder()
                .goalId(goal.goalId())
                .description(goal.description() != null ? goal.description() : goal.title())
                .category(goal.category())
                .priority(goal.priority())
                .progressPercentage(progressPercentage)
                .relatedChild(relatedChildName)
                .missionsCompletedCount(goal.completedMissions())
                .missionsRemainingEstimate(Math.max(0, getEstimated(goal) - goal.completedMissions()))
                .missions(missionItems)
                .milestones(goal.milestones())
                .suggestedNextSteps(goal.suggestedNextSteps())
                .build();
    }

    private GoalsOverviewResponse.GoalItem buildGoalItem(GoalReadModel goal) {
        int progressPercentage = calculateProgressPercentage(goal);

        String relatedChildName = null;
        if (goal.childId() != null) {
            relatedChildName = childDataService.getChild(goal.childId())
                    .map(ChildReadModel::name)
                    .orElse(null);
        }

        int estimated = getEstimated(goal);
        int remaining = Math.max(0, estimated - goal.completedMissions());

        return new GoalsOverviewResponse.GoalItem(
                goal.goalId(),
                goal.description() != null ? goal.description() : goal.title(),
                goal.category(),
                goal.priority(),
                progressPercentage,
                relatedChildName,
                goal.completedMissions(),
                remaining
        );
    }

    /**
     * Calculates progress percentage: min(completed/estimated * 100, 100).
     * Uses DEFAULT_ESTIMATED_MISSIONS (10) if estimated is 0 or unavailable.
     */
    int calculateProgressPercentage(GoalReadModel goal) {
        int estimated = getEstimated(goal);
        if (estimated <= 0) {
            estimated = DEFAULT_ESTIMATED_MISSIONS;
        }
        int percentage = (int) Math.min((goal.completedMissions() * 100.0) / estimated, 100);
        return percentage;
    }

    private int getEstimated(GoalReadModel goal) {
        return goal.estimatedMissions() > 0 ? goal.estimatedMissions() : DEFAULT_ESTIMATED_MISSIONS;
    }
}
