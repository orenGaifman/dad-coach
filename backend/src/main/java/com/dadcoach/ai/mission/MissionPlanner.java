package com.dadcoach.ai.mission;

import com.dadcoach.ai.mission.DifficultyCalculator.MissionOutcome;
import com.dadcoach.ai.mission.DifficultyCalculator.Phase;
import com.dadcoach.ai.mission.CategoryScorer.MissionRecord;
import com.dadcoach.ai.output.MissionContext;
import com.dadcoach.ai.output.MissionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Generates AI-powered mission recommendations with difficulty calculation,
 * category selection, and child equity enforcement.
 *
 * <p>The MissionPlanner is a stateless advisory component — it receives context and
 * returns a structured {@link MissionOutput} recommendation. It never directly mutates state.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Calculate difficulty within phase bounds (never &lt; 1)</li>
 *   <li>Enforce category cooldown: same child 4 days, different child 2 days</li>
 *   <li>Enforce child equity: |missions(childA) - missions(childB)| ≤ 1 over 7 days</li>
 *   <li>If equity violated → next mission MUST target under-served child</li>
 *   <li>Validate output against MissionOutput schema before return</li>
 * </ul>
 */
public class MissionPlanner {

    private static final Logger log = LoggerFactory.getLogger(MissionPlanner.class);

    private final DifficultyCalculator difficultyCalculator;
    private final CategoryScorer categoryScorer;

    public MissionPlanner(DifficultyCalculator difficultyCalculator, CategoryScorer categoryScorer) {
        this.difficultyCalculator = difficultyCalculator;
        this.categoryScorer = categoryScorer;
    }

    public MissionPlanner() {
        this(new DifficultyCalculator(), new CategoryScorer());
    }

    /**
     * Input context for the planning algorithm, containing all data needed to plan a mission.
     *
     * @param fatherId          the father's unique identifier
     * @param children          the father's children (id → name)
     * @param phase             the current coaching phase
     * @param phaseDay          the day within the current phase (1-based)
     * @param recentOutcomes    the last 3 mission outcomes for difficulty calculation
     * @param recentMissions    recent mission records for cooldown and equity calculation
     * @param availableCategories all available mission categories
     * @param today             the current date
     */
    public record PlanningContext(
        UUID fatherId,
        Map<UUID, String> children,
        Phase phase,
        int phaseDay,
        List<MissionOutcome> recentOutcomes,
        List<MissionRecord> recentMissions,
        List<String> availableCategories,
        LocalDate today
    ) {
        public PlanningContext {
            if (fatherId == null) throw new IllegalArgumentException("fatherId must not be null");
            if (children == null || children.isEmpty())
                throw new IllegalArgumentException("children must not be null or empty");
            if (phase == null) throw new IllegalArgumentException("phase must not be null");
            if (phaseDay < 1) throw new IllegalArgumentException("phaseDay must be >= 1");
            if (availableCategories == null || availableCategories.isEmpty())
                throw new IllegalArgumentException("availableCategories must not be null or empty");
            if (today == null) throw new IllegalArgumentException("today must not be null");
            recentOutcomes = recentOutcomes != null ? List.copyOf(recentOutcomes) : List.of();
            recentMissions = recentMissions != null ? List.copyOf(recentMissions) : List.of();
            availableCategories = List.copyOf(availableCategories);
            children = Map.copyOf(children);
        }
    }

    /**
     * The planning result before AI generation. Contains the selected parameters for
     * mission generation.
     *
     * @param targetChildId  the child to target with the mission
     * @param targetChildName the child's name
     * @param difficulty     the calculated difficulty level
     * @param category       the selected category (null if no eligible categories)
     * @param phase          the coaching phase used
     */
    public record PlanningResult(
        UUID targetChildId,
        String targetChildName,
        int difficulty,
        String category,
        Phase phase
    ) {
        public PlanningResult {
            if (targetChildId == null) throw new IllegalArgumentException("targetChildId must not be null");
            if (difficulty < 1) throw new IllegalArgumentException("difficulty must be >= 1");
        }
    }

    /**
     * Plans the next mission by selecting target child, calculating difficulty,
     * and choosing a category.
     *
     * @param context the planning context with all required data
     * @return the planning result with selected parameters
     */
    public PlanningResult plan(PlanningContext context) {
        // Step 1: Determine target child (equity enforcement)
        UUID targetChildId = selectTargetChild(context);
        String targetChildName = context.children().get(targetChildId);

        // Step 2: Calculate difficulty
        int difficulty = difficultyCalculator.calculate(
            context.phase(), context.phaseDay(), context.recentOutcomes());

        // Step 3: Get eligible categories (cooldown enforcement)
        List<String> eligible = categoryScorer.getEligibleCategories(
            targetChildId, context.availableCategories(), context.recentMissions(), context.today());

        // Step 4: Select category (first eligible, or first available as fallback)
        String category = eligible.isEmpty()
            ? context.availableCategories().get(0)
            : eligible.get(0);

        log.debug("Mission planned: child={}, difficulty={}, category={}", targetChildName, difficulty, category);

        return new PlanningResult(targetChildId, targetChildName, difficulty, category, context.phase());
    }

    /**
     * Validates and builds a MissionOutput from raw parameters.
     * Ensures all fields pass schema validation before returning.
     *
     * @param title            the mission title
     * @param description      the mission description
     * @param category         the mission category
     * @param difficulty       the difficulty level (must be within phase bounds)
     * @param estimatedMinutes the estimated completion time
     * @param model            the AI model that generated the mission
     * @param phase            the coaching phase for bounds validation
     * @return the validated MissionOutput
     * @throws IllegalArgumentException if validation fails
     */
    public MissionOutput validateAndBuild(
        String title,
        String description,
        String category,
        int difficulty,
        int estimatedMinutes,
        String model,
        Phase phase
    ) {
        // Enforce difficulty bounds against phase
        int clampedDifficulty = Math.max(1, Math.min(difficulty, phase.max()));
        clampedDifficulty = Math.max(phase.min(), clampedDifficulty);

        // Validate required fields
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title must not exceed 200 characters");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be null or blank");
        }
        if (estimatedMinutes < 1) {
            throw new IllegalArgumentException("estimatedMinutes must be >= 1");
        }

        MissionOutput output = new MissionOutput(
            title, description, category, clampedDifficulty, estimatedMinutes, true, model);

        log.debug("Mission validated: title='{}', difficulty={}, category={}", title, clampedDifficulty, category);
        return output;
    }

    /**
     * Selects the target child based on equity rules.
     *
     * <p>Child equity rule: |missions(childA) - missions(childB)| ≤ 1 over 7 days.
     * If violated → next mission MUST target the under-served child.
     * If not violated → target the child with fewest missions (tiebreaker: longest since last mission).
     *
     * @param context the planning context
     * @return the UUID of the target child
     */
    UUID selectTargetChild(PlanningContext context) {
        Map<UUID, String> children = context.children();

        if (children.size() == 1) {
            return children.keySet().iterator().next();
        }

        LocalDate sevenDaysAgo = context.today().minusDays(7);
        Map<UUID, Integer> missionCounts = new HashMap<>();
        Map<UUID, LocalDate> lastMissionDates = new HashMap<>();

        // Initialize all children with 0 missions
        for (UUID childId : children.keySet()) {
            missionCounts.put(childId, 0);
            lastMissionDates.put(childId, LocalDate.MIN);
        }

        // Count missions in the last 7 days per child
        for (MissionRecord record : context.recentMissions()) {
            if (!record.assignedOn().isBefore(sevenDaysAgo) && children.containsKey(record.childId())) {
                missionCounts.merge(record.childId(), 1, Integer::sum);
                if (record.assignedOn().isAfter(lastMissionDates.get(record.childId()))) {
                    lastMissionDates.put(record.childId(), record.assignedOn());
                }
            }
        }

        // Check equity violation: if |max - min| > 1, target the under-served child
        int maxMissions = missionCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minMissions = missionCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        if (maxMissions - minMissions > 1) {
            // Equity violated — find the under-served child (lowest count)
            return missionCounts.entrySet().stream()
                .filter(e -> e.getValue() == minMissions)
                .min(Comparator.comparing(e -> lastMissionDates.get(e.getKey())))
                .map(Map.Entry::getKey)
                .orElse(children.keySet().iterator().next());
        }

        // No violation — select child with fewest missions, tiebreaker: longest since last mission
        return missionCounts.entrySet().stream()
            .min(Comparator.<Map.Entry<UUID, Integer>, Integer>comparing(Map.Entry::getValue)
                .thenComparing(e -> lastMissionDates.get(e.getKey())))
            .map(Map.Entry::getKey)
            .orElse(children.keySet().iterator().next());
    }

    /**
     * Checks if child equity is currently violated.
     *
     * @param children       the father's children
     * @param recentMissions recent mission history
     * @param today          the current date
     * @return true if equity is violated (|max - min| > 1)
     */
    public boolean isEquityViolated(Map<UUID, String> children, List<MissionRecord> recentMissions, LocalDate today) {
        if (children.size() <= 1) {
            return false;
        }

        LocalDate sevenDaysAgo = today.minusDays(7);
        Map<UUID, Integer> missionCounts = new HashMap<>();

        for (UUID childId : children.keySet()) {
            missionCounts.put(childId, 0);
        }

        for (MissionRecord record : recentMissions) {
            if (!record.assignedOn().isBefore(sevenDaysAgo) && children.containsKey(record.childId())) {
                missionCounts.merge(record.childId(), 1, Integer::sum);
            }
        }

        int maxMissions = missionCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minMissions = missionCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        return (maxMissions - minMissions) > 1;
    }

    /**
     * Returns the under-served child when equity is violated.
     *
     * @param children       the father's children
     * @param recentMissions recent mission history
     * @param today          the current date
     * @return the UUID of the child with the fewest missions, or null if no violation
     */
    public UUID getUnderservedChild(Map<UUID, String> children, List<MissionRecord> recentMissions, LocalDate today) {
        if (!isEquityViolated(children, recentMissions, today)) {
            return null;
        }

        LocalDate sevenDaysAgo = today.minusDays(7);
        Map<UUID, Integer> missionCounts = new HashMap<>();

        for (UUID childId : children.keySet()) {
            missionCounts.put(childId, 0);
        }

        for (MissionRecord record : recentMissions) {
            if (!record.assignedOn().isBefore(sevenDaysAgo) && children.containsKey(record.childId())) {
                missionCounts.merge(record.childId(), 1, Integer::sum);
            }
        }

        int minMissions = missionCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        return missionCounts.entrySet().stream()
            .filter(e -> e.getValue() == minMissions)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
}
