package com.dadcoach.workspace;

import com.dadcoach.workspace.aggregation.GoalFilterParams;
import com.dadcoach.workspace.aggregation.GoalsOverviewService;
import com.dadcoach.workspace.dto.response.GoalProgressResponse;
import com.dadcoach.workspace.dto.response.GoalsOverviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * REST controller for goal-related workspace endpoints.
 *
 * <p>Provides goals overview with filtering and detailed goal progress views.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/goals")
public class GoalsOverviewController {

    private final GoalsOverviewService goalsOverviewService;

    public GoalsOverviewController(GoalsOverviewService goalsOverviewService) {
        this.goalsOverviewService = goalsOverviewService;
    }

    /**
     * Returns goals overview with optional filtering by status, category, or child.
     *
     * @param status    optional status filter
     * @param category  optional category filter
     * @param childId   optional child ID filter
     * @param principal the authenticated user
     * @return 200 OK with goals overview response
     */
    @GetMapping
    public ResponseEntity<GoalsOverviewResponse> getGoalsOverview(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "child_id", required = false) UUID childId,
            Principal principal) {
        UUID fatherId = extractFatherId(principal);
        GoalFilterParams filters = new GoalFilterParams(status, category, childId);
        GoalsOverviewResponse response = goalsOverviewService.getGoalsOverview(fatherId, filters);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns detailed progress for a specific goal including related missions.
     *
     * @param goalId    the goal's unique identifier
     * @param principal the authenticated user
     * @return 200 OK with goal progress response, or 404 if not found
     */
    @GetMapping("/{goalId}/progress")
    public ResponseEntity<GoalProgressResponse> getGoalProgress(
            @PathVariable UUID goalId,
            Principal principal) {
        UUID fatherId = extractFatherId(principal);
        GoalProgressResponse response = goalsOverviewService.getGoalProgress(fatherId, goalId);
        return ResponseEntity.ok(response);
    }

    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
