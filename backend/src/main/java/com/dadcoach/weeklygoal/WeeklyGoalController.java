package com.dadcoach.weeklygoal;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * REST controller for Weekly Goal management.
 * 
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Getting current weekly goal and progress</li>
 *   <li>Creating a new weekly goal</li>
 *   <li>Getting goal history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/workspace/weekly-goals")
public class WeeklyGoalController {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalController.class);

    private final WeeklyGoalService weeklyGoalService;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final FatherRepository fatherRepository;

    public WeeklyGoalController(
            WeeklyGoalService weeklyGoalService,
            WeeklyGoalRepository weeklyGoalRepository,
            FatherRepository fatherRepository) {
        this.weeklyGoalService = weeklyGoalService;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Current Goal ────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/weekly-goals/current
     * 
     * Returns the current week's goal and progress.
     */
    @GetMapping("/current")
    public ResponseEntity<WeeklyGoalResponse> getCurrentGoal(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        Optional<WeeklyGoal> activeGoal = weeklyGoalService.getActiveGoal(father.getId());
        
        if (activeGoal.isEmpty()) {
            // Check if there's a pending goal for this week
            Optional<WeeklyGoal> currentWeekGoal = weeklyGoalService.getCurrentWeekGoal(father.getId());
            if (currentWeekGoal.isPresent()) {
                return ResponseEntity.ok(toResponse(currentWeekGoal.get(), father));
            }
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(activeGoal.get(), father));
    }

    /**
     * POST /api/v1/workspace/weekly-goals
     * 
     * Creates a new weekly goal for the current week.
     */
    @PostMapping
    public ResponseEntity<WeeklyGoalResponse> createGoal(
            @AuthActor ActorContext actor,
            @RequestBody CreateGoalRequest request) {
        Father father = findFatherByActorId(actor.getActorId());
        
        try {
            WeeklyGoal goal = weeklyGoalService.createWeeklyGoal(
                    father.getId(), 
                    request.targetHours()
            );
            
            // Activate immediately (in web flow, not via WhatsApp)
            if (request.activateImmediately() != null && request.activateImmediately()) {
                goal = weeklyGoalService.activateGoal(goal.getId());
            }
            
            log.info("Created weekly goal via API for father {}: {} hours", 
                     father.getId(), request.targetHours());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(goal, father));
        } catch (IllegalStateException e) {
            // Goal already exists for this week
            log.warn("Failed to create goal for father {}: {}", father.getId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/v1/workspace/weekly-goals/{id}/activate
     * 
     * Activates a pending goal.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<WeeklyGoalResponse> activateGoal(
            @AuthActor ActorContext actor,
            @PathVariable Long id) {
        Father father = findFatherByActorId(actor.getActorId());
        
        WeeklyGoal goal = weeklyGoalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyGoal", id));
        
        // Verify ownership
        if (!goal.getFatherId().equals(father.getId())) {
            return ResponseEntity.notFound().build();
        }
        
        goal = weeklyGoalService.activateGoal(id);
        return ResponseEntity.ok(toResponse(goal, father));
    }

    // ─── History ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/weekly-goals/history
     * 
     * Returns past weekly goals.
     */
    @GetMapping("/history")
    public ResponseEntity<List<WeeklyGoalResponse>> getGoalHistory(
            @AuthActor ActorContext actor,
            @RequestParam(defaultValue = "10") int limit) {
        Father father = findFatherByActorId(actor.getActorId());
        
        List<WeeklyGoal> goals = weeklyGoalService.getRecentGoals(father.getId(), limit);
        List<WeeklyGoalResponse> responses = goals.stream()
                .map(g -> toResponse(g, father))
                .toList();
        
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/v1/workspace/weekly-goals/summary
     * 
     * Returns last week's summary for display in the weekly review.
     */
    @GetMapping("/summary")
    public ResponseEntity<WeeklySummaryResponse> getWeeklySummary(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        WeeklyGoalService.WeeklySummary summary = weeklyGoalService.generateWeeklySummary(father.getId());
        
        WeeklySummaryResponse response = new WeeklySummaryResponse(
                summary.hasPreviousGoal(),
                summary.targetHours(),
                summary.actualHours(),
                summary.completedCount(),
                summary.scheduledCount(),
                summary.goalMet(),
                summary.startingBelt() != null ? summary.startingBelt().name() : null,
                summary.endingBelt() != null ? summary.endingBelt().name() : null,
                summary.wasPromoted(),
                summary.consecutiveWeeks(),
                father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0,
                father.getLongestStreakWeeks() != null ? father.getLongestStreakWeeks() : 0,
                weeklyGoalService.getWeeksUntilBlackBelt(father)
        );
        
        return ResponseEntity.ok(response);
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private Father findFatherByActorId(UUID actorId) {
        long internalId = actorId.getLeastSignificantBits();
        return fatherRepository.findById(internalId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", actorId));
    }

    private WeeklyGoalResponse toResponse(WeeklyGoal goal, Father father) {
        return new WeeklyGoalResponse(
                goal.getId(),
                goal.getWeekStartDate().toString(),
                goal.getTargetHours(),
                goal.getActualMinutes(),
                (int) Math.round(goal.getActualHours()),
                goal.getProgressPercentage(),
                goal.isGoalMet(),
                goal.getScheduledCount(),
                goal.getCompletedCount(),
                goal.getStartingBelt().name(),
                goal.getEndingBelt() != null ? goal.getEndingBelt().name() : goal.getStartingBelt().name(),
                goal.isBeltPromoted(),
                goal.getStatus().name(),
                goal.getCreatedAt(),
                goal.getCompletedAt(),
                father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0
        );
    }

    // ─── Request/Response Records ────────────────────────────────────────────────

    public record CreateGoalRequest(
            int targetHours,
            Boolean activateImmediately
    ) {}

    public record WeeklyGoalResponse(
            Long id,
            String weekStartDate,
            int targetHours,
            int actualMinutes,
            int actualHours,
            int progressPercentage,
            boolean goalMet,
            int scheduledCount,
            int completedCount,
            String startingBelt,
            String endingBelt,
            boolean beltPromoted,
            String status,
            Instant createdAt,
            Instant completedAt,
            int currentStreakWeeks
    ) {}

    public record WeeklySummaryResponse(
            boolean hasPreviousGoal,
            int targetHours,
            int actualHours,
            int completedCount,
            int scheduledCount,
            boolean goalMet,
            String startingBelt,
            String endingBelt,
            boolean wasPromoted,
            int consecutiveWeeks,
            int currentStreakWeeks,
            int longestStreakWeeks,
            int weeksUntilBlackBelt
    ) {}
}
