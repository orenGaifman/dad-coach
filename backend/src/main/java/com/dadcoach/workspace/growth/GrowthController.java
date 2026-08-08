package com.dadcoach.workspace.growth;

import com.dadcoach.api.auth.ActorContext;
import com.dadcoach.api.auth.AuthActor;
import com.dadcoach.api.error.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalRepository;
import com.dadcoach.weeklygoal.WeeklyGoalStatus;
import com.dadcoach.workflow.Belt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for Growth-related endpoints.
 * 
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Belt progression display</li>
 *   <li>Streak information (weeks)</li>
 *   <li>Celebrations (belt promotions)</li>
 * </ul>
 * 
 * <p>These endpoints match the dad-coach-web frontend expectations
 * defined in src/types/growth.ts</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/growth")
public class GrowthController {

    private static final Logger log = LoggerFactory.getLogger(GrowthController.class);

    private final FatherRepository fatherRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;

    public GrowthController(
            FatherRepository fatherRepository,
            WeeklyGoalRepository weeklyGoalRepository) {
        this.fatherRepository = fatherRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
    }

    // ─── Belt Progression ────────────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/growth/belt
     * 
     * Returns belt progression data including current belt, progress to next belt,
     * and weeks remaining in the 7-week program.
     */
    @GetMapping("/belt")
    public ResponseEntity<BeltProgressionResponse> getBeltProgression(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        Belt currentBelt = father.getCurrentBelt() != null ? father.getCurrentBelt() : Belt.WHITE;
        Belt nextBelt = currentBelt.getNextBelt();
        int currentStreakWeeks = father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0;
        
        // Calculate weeks to BLACK belt
        int weeksToBlack = calculateWeeksToBlackBelt(currentBelt);
        
        // Progress percentage (based on 7-week program)
        int totalBelts = Belt.values().length - 1; // Exclude WHITE as starting point
        int currentBeltIndex = currentBelt.ordinal();
        Integer progressPercentage = nextBelt != null 
                ? (int) Math.round((currentBeltIndex * 100.0) / totalBelts) 
                : 100;

        BeltProgressionResponse response = new BeltProgressionResponse(
                currentBelt.name(),
                currentStreakWeeks, // Using streak as "score" in context of weekly program
                nextBelt != null ? nextBelt.name() : null,
                nextBelt != null ? 1 : null, // 1 week to next belt (if goal met)
                progressPercentage,
                father.getActivationDate() != null 
                        ? father.getActivationDate().atStartOfDay(java.time.ZoneId.of("Asia/Jerusalem")).toInstant()
                        : father.getCreatedAt(),
                weeksToBlack,
                currentBelt == Belt.BLACK
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/workspace/growth/streak
     * 
     * Returns streak data in weeks (consecutive weeks meeting goal).
     */
    @GetMapping("/streak")
    public ResponseEntity<StreakResponse> getStreak(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        int currentStreakWeeks = father.getCurrentStreakWeeks() != null ? father.getCurrentStreakWeeks() : 0;
        int longestStreakWeeks = father.getLongestStreakWeeks() != null ? father.getLongestStreakWeeks() : 0;
        
        // Find when streak started
        String streakStartDate = null;
        if (currentStreakWeeks > 0) {
            // Calculate approximate streak start based on current streak weeks
            java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jerusalem"));
            java.time.LocalDate streakStart = now.minusWeeks(currentStreakWeeks - 1)
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
            streakStartDate = streakStart.toString();
        }

        StreakResponse response = new StreakResponse(
                currentStreakWeeks,
                longestStreakWeeks,
                streakStartDate,
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jerusalem")).toString()
        );

        return ResponseEntity.ok(response);
    }

    // ─── Celebrations ────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/workspace/growth/celebrations
     * 
     * Returns belt promotion celebrations (completed goals with promotion).
     */
    @GetMapping("/celebrations")
    public ResponseEntity<CelebrationsResponse> getCelebrations(@AuthActor ActorContext actor) {
        Father father = findFatherByActorId(actor.getActorId());
        
        // Find completed goals with belt promotions
        List<WeeklyGoal> promotionGoals = weeklyGoalRepository
                .findByFatherIdOrderByCreatedAtDesc(father.getId())
                .stream()
                .filter(WeeklyGoal::isBeltPromoted)
                .limit(20)
                .toList();

        List<Celebration> celebrations = promotionGoals.stream()
                .map(goal -> new Celebration(
                        "belt-" + goal.getId(),
                        "BELT_LEVEL_UP",
                        "עלית ל" + goal.getEndingBelt().getDisplayName("he") + "!",
                        getBeltEncouragement(goal.getEndingBelt()),
                        goal.getCompletedAt(),
                        true, // Assume displayed via WhatsApp
                        null,
                        new BeltInfo(
                                goal.getEndingBelt().name(),
                                goal.getStartingBelt().name()
                        ),
                        null
                ))
                .toList();

        // Check for program completion
        boolean programCompleted = father.getCurrentBelt() == Belt.BLACK;
        
        CelebrationsResponse response = new CelebrationsResponse(
                celebrations,
                false, // has_undisplayed - all displayed via WhatsApp
                programCompleted
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/workspace/growth/celebrations/mark-displayed
     * 
     * Marks celebrations as displayed (no-op since we display via WhatsApp).
     */
    @PostMapping("/celebrations/mark-displayed")
    public ResponseEntity<MarkDisplayedResponse> markCelebrationsDisplayed(
            @AuthActor ActorContext actor,
            @RequestBody MarkDisplayedRequest request) {
        // No-op - celebrations are displayed via WhatsApp
        return ResponseEntity.ok(new MarkDisplayedResponse(
                true,
                request.celebrationIds() != null ? request.celebrationIds().size() : 0
        ));
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private Father findFatherByActorId(UUID actorId) {
        long internalId = actorId.getLeastSignificantBits();
        return fatherRepository.findById(internalId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", actorId));
    }

    private int calculateWeeksToBlackBelt(Belt currentBelt) {
        int weeks = 0;
        Belt belt = currentBelt;
        while (belt != null && belt != Belt.BLACK) {
            belt = belt.getNextBelt();
            weeks++;
        }
        return weeks;
    }

    private String getBeltEncouragement(Belt belt) {
        return switch (belt) {
            case YELLOW -> "התחלת את המסע! כל חגורה מקרבת אותך לאבא מעולה יותר.";
            case ORANGE -> "אתה בדרך הנכונה. עוד 5 שבועות לחגורה שחורה!";
            case GREEN -> "חצי דרך לפסגה! הילדים מרגישים את זה.";
            case BLUE -> "אתה אבא מסור. עוד 3 שבועות לסיום!";
            case BROWN -> "כמעט שם! עוד שבוע אחד לחגורה שחורה!";
            case BLACK -> "השגת את הפסגה! חגורה שחורה - אבא אלוף!";
            default -> "כל הכבוד על ההתקדמות!";
        };
    }

    // ─── Response Records ────────────────────────────────────────────────────────

    public record BeltProgressionResponse(
            String current_belt,
            int current_score,
            String next_belt,
            Integer points_to_next_belt,
            Integer progress_percentage_to_next_belt,
            Instant belt_earned_at,
            int weeks_to_black_belt,
            boolean program_completed
    ) {}

    public record StreakResponse(
            int current_streak_weeks,
            int longest_streak_weeks,
            String streak_start_date,
            String last_qualifying_interaction_date
    ) {}

    public record Celebration(
            String celebration_id,
            String event_type,
            String title,
            String encouragement_message,
            Instant earned_at,
            boolean displayed,
            Object achievement,
            BeltInfo belt,
            Integer points_awarded
    ) {}

    public record BeltInfo(
            String new_belt,
            String previous_belt
    ) {}

    public record CelebrationsResponse(
            List<Celebration> celebrations,
            boolean has_undisplayed,
            boolean program_completed
    ) {}

    public record MarkDisplayedRequest(
            List<String> celebrationIds
    ) {}

    public record MarkDisplayedResponse(
            boolean success,
            int marked_count
    ) {}
}
