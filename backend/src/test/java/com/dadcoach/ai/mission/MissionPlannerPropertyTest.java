package com.dadcoach.ai.mission;

import com.dadcoach.ai.mission.CategoryScorer.MissionRecord;
import com.dadcoach.ai.mission.DifficultyCalculator.MissionOutcome;
import com.dadcoach.ai.mission.DifficultyCalculator.Phase;
import com.dadcoach.ai.mission.MissionPlanner.PlanningContext;
import com.dadcoach.ai.mission.MissionPlanner.PlanningResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for MissionPlanner, DifficultyCalculator, and CategoryScorer.
 *
 * <p>Tests Properties 9, 10, 11 from the design specification.
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 9: Mission Difficulty Bounds")
@Tag("Feature: ai-architecture-intelligence-layer, Property 10: Mission Category Cooldown Enforcement")
@Tag("Feature: ai-architecture-intelligence-layer, Property 11: Mission Child Equity Distribution")
class MissionPlannerPropertyTest {

    private final DifficultyCalculator difficultyCalculator = new DifficultyCalculator();
    private final CategoryScorer categoryScorer = new CategoryScorer();
    private final MissionPlanner missionPlanner = new MissionPlanner();

    private static final List<String> ALL_CATEGORIES = List.of(
        "PLAY", "CONVERSATION", "ADVENTURE", "CREATIVITY",
        "LEARNING", "ROUTINE", "EMOTION", "BONDING"
    );

    // ===== Property 9: Mission Difficulty Bounds =====

    /**
     * **Validates: Requirements 7.2**
     *
     * Property 9: For any coaching phase and mission history, the calculated difficulty
     * level SHALL be >= phase minimum and <= phase maximum.
     * The difficulty SHALL never be less than 1 regardless of adjustment calculations.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 9: Mission Difficulty Bounds")
    void difficultyAlwaysWithinPhaseBounds(
        @ForAll("phases") Phase phase,
        @ForAll @IntRange(min = 1, max = 60) int phaseDay,
        @ForAll("missionOutcomes") List<MissionOutcome> outcomes
    ) {
        int difficulty = difficultyCalculator.calculate(phase, phaseDay, outcomes);

        assertThat(difficulty)
            .as("Difficulty must be >= phase min (%d) for %s", phase.min(), phase)
            .isGreaterThanOrEqualTo(phase.min());
        assertThat(difficulty)
            .as("Difficulty must be <= phase max (%d) for %s", phase.max(), phase)
            .isLessThanOrEqualTo(phase.max());
        assertThat(difficulty)
            .as("Difficulty must never be < 1")
            .isGreaterThanOrEqualTo(1);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 9: Mission Difficulty Bounds")
    void difficultyNeverBelowOneEvenWithNegativeAdjustments(
        @ForAll("phases") Phase phase,
        @ForAll("poorOutcomes") List<MissionOutcome> poorOutcomes
    ) {
        // Use day 1 (base = phase.min) with poor outcomes that would subtract 1
        int difficulty = difficultyCalculator.calculate(phase, 1, poorOutcomes);

        assertThat(difficulty)
            .as("Difficulty must never be < 1 even with negative adjustments")
            .isGreaterThanOrEqualTo(1);
        assertThat(difficulty)
            .as("Difficulty must be within phase bounds [%d, %d]", phase.min(), phase.max())
            .isBetween(phase.min(), phase.max());
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 9: Mission Difficulty Bounds")
    void difficultyNeverExceedsPhaseMaxEvenWithPositiveAdjustments(
        @ForAll("phases") Phase phase,
        @ForAll("excellentOutcomes") List<MissionOutcome> excellentOutcomes
    ) {
        // Use last day (base near phase.max) with excellent outcomes that would add 1
        int difficulty = difficultyCalculator.calculate(phase, phase.durationDays(), excellentOutcomes);

        assertThat(difficulty)
            .as("Difficulty must not exceed phase max (%d) for %s", phase.max(), phase)
            .isLessThanOrEqualTo(phase.max());
        assertThat(difficulty)
            .as("Difficulty must never be < 1")
            .isGreaterThanOrEqualTo(1);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 9: Mission Difficulty Bounds")
    void planningResultDifficultyAlwaysWithinBounds(
        @ForAll("planningContexts") PlanningContext context
    ) {
        PlanningResult result = missionPlanner.plan(context);

        assertThat(result.difficulty())
            .as("Planning result difficulty must be >= phase min (%d)", context.phase().min())
            .isGreaterThanOrEqualTo(context.phase().min());
        assertThat(result.difficulty())
            .as("Planning result difficulty must be <= phase max (%d)", context.phase().max())
            .isLessThanOrEqualTo(context.phase().max());
        assertThat(result.difficulty())
            .as("Planning result difficulty must never be < 1")
            .isGreaterThanOrEqualTo(1);
    }

    // ===== Property 10: Mission Category Cooldown Enforcement =====

    /**
     * **Validates: Requirements 7.3**
     *
     * Property 10: For any mission planning request, if a category was used for the
     * same child within the last 4 days, that category SHALL be excluded from selection.
     * If a category was used for a different child within the last 2 days, that category
     * SHALL be excluded from selection.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 10: Mission Category Cooldown Enforcement")
    void sameChildCategoryCooldownIsFourDays(
        @ForAll("categoryNames") String category,
        @ForAll @IntRange(min = 0, max = 3) int daysAgo
    ) {
        UUID childId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2024, 6, 15);
        LocalDate assignedOn = today.minusDays(daysAgo);

        List<MissionRecord> recentMissions = List.of(new MissionRecord(category, childId, assignedOn));
        List<String> allCategories = List.of(category, "other_category");

        List<String> eligible = categoryScorer.getEligibleCategories(childId, allCategories, recentMissions, today);

        assertThat(eligible)
            .as("Category '%s' used %d days ago for same child must be on cooldown (< 4 days)", category, daysAgo)
            .doesNotContain(category);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 10: Mission Category Cooldown Enforcement")
    void sameChildCategoryAvailableAfterFourDays(
        @ForAll("categoryNames") String category,
        @ForAll @IntRange(min = 4, max = 30) int daysAgo
    ) {
        UUID childId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2024, 6, 15);
        LocalDate assignedOn = today.minusDays(daysAgo);

        List<MissionRecord> recentMissions = List.of(new MissionRecord(category, childId, assignedOn));
        List<String> allCategories = List.of(category, "other_category");

        List<String> eligible = categoryScorer.getEligibleCategories(childId, allCategories, recentMissions, today);

        assertThat(eligible)
            .as("Category '%s' used %d days ago for same child should be available (>= 4 days)", category, daysAgo)
            .contains(category);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 10: Mission Category Cooldown Enforcement")
    void differentChildCategoryCooldownIsTwoDays(
        @ForAll("categoryNames") String category,
        @ForAll @IntRange(min = 0, max = 1) int daysAgo
    ) {
        UUID targetChild = UUID.randomUUID();
        UUID otherChild = UUID.randomUUID();
        LocalDate today = LocalDate.of(2024, 6, 15);
        LocalDate assignedOn = today.minusDays(daysAgo);

        List<MissionRecord> recentMissions = List.of(new MissionRecord(category, otherChild, assignedOn));
        List<String> allCategories = List.of(category, "other_category");

        List<String> eligible = categoryScorer.getEligibleCategories(targetChild, allCategories, recentMissions, today);

        assertThat(eligible)
            .as("Category '%s' used %d days ago for different child must be on cooldown (< 2 days)", category, daysAgo)
            .doesNotContain(category);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 10: Mission Category Cooldown Enforcement")
    void differentChildCategoryAvailableAfterTwoDays(
        @ForAll("categoryNames") String category,
        @ForAll @IntRange(min = 2, max = 30) int daysAgo
    ) {
        UUID targetChild = UUID.randomUUID();
        UUID otherChild = UUID.randomUUID();
        LocalDate today = LocalDate.of(2024, 6, 15);
        LocalDate assignedOn = today.minusDays(daysAgo);

        List<MissionRecord> recentMissions = List.of(new MissionRecord(category, otherChild, assignedOn));
        List<String> allCategories = List.of(category, "other_category");

        List<String> eligible = categoryScorer.getEligibleCategories(targetChild, allCategories, recentMissions, today);

        assertThat(eligible)
            .as("Category '%s' used %d days ago for different child should be available (>= 2 days)", category, daysAgo)
            .contains(category);
    }

    // ===== Property 11: Mission Child Equity Distribution =====

    /**
     * **Validates: Requirements 7.4**
     *
     * Property 11: For any father with multiple children, over any 7-day window,
     * the absolute difference in mission counts between any two children SHALL be <= 1.
     * If the constraint is violated, the next mission MUST target the child with fewer missions.
     */
    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 11: Mission Child Equity Distribution")
    void equityViolationForcesUnderservedChildSelection(
        @ForAll("equityViolatedContexts") PlanningContext context
    ) {
        PlanningResult result = missionPlanner.plan(context);

        // Find the under-served child (fewest missions in last 7 days)
        UUID underserved = missionPlanner.getUnderservedChild(
            context.children(), context.recentMissions(), context.today());

        assertThat(result.targetChildId())
            .as("When equity is violated, the target must be the under-served child")
            .isEqualTo(underserved);
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 11: Mission Child Equity Distribution")
    void equityIsDetectedWhenDifferenceExceedsOne(
        @ForAll @IntRange(min = 0, max = 10) int childAMissions,
        @ForAll @IntRange(min = 0, max = 10) int childBMissions
    ) {
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        Map<UUID, String> children = Map.of(childA, "ChildA", childB, "ChildB");
        LocalDate today = LocalDate.of(2024, 6, 15);

        List<MissionRecord> missions = new ArrayList<>();
        for (int i = 0; i < childAMissions; i++) {
            missions.add(new MissionRecord("cat" + i, childA, today.minusDays(i % 7)));
        }
        for (int i = 0; i < childBMissions; i++) {
            missions.add(new MissionRecord("cat" + i, childB, today.minusDays(i % 7)));
        }

        boolean violated = missionPlanner.isEquityViolated(children, missions, today);
        int diff = Math.abs(childAMissions - childBMissions);

        if (diff > 1) {
            assertThat(violated)
                .as("Equity must be violated when |%d - %d| = %d > 1", childAMissions, childBMissions, diff)
                .isTrue();
        } else {
            assertThat(violated)
                .as("Equity must NOT be violated when |%d - %d| = %d <= 1", childAMissions, childBMissions, diff)
                .isFalse();
        }
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 11: Mission Child Equity Distribution")
    void singleChildNeverHasEquityViolation(
        @ForAll @IntRange(min = 0, max = 20) int missionCount
    ) {
        UUID childA = UUID.randomUUID();
        Map<UUID, String> children = Map.of(childA, "OnlyChild");
        LocalDate today = LocalDate.of(2024, 6, 15);

        List<MissionRecord> missions = IntStream.range(0, missionCount)
            .mapToObj(i -> new MissionRecord("cat" + (i % 5), childA, today.minusDays(i % 7)))
            .collect(Collectors.toList());

        boolean violated = missionPlanner.isEquityViolated(children, missions, today);

        assertThat(violated)
            .as("Single child should never have equity violation")
            .isFalse();
    }

    @Property(tries = 200)
    @Tag("Feature: ai-architecture-intelligence-layer, Property 11: Mission Child Equity Distribution")
    void planningAlwaysSelectsAValidChild(
        @ForAll("planningContexts") PlanningContext context
    ) {
        PlanningResult result = missionPlanner.plan(context);

        assertThat(context.children())
            .as("Selected child must be one of the father's children")
            .containsKey(result.targetChildId());
    }

    // ===== Arbitraries =====

    @Provide
    Arbitrary<Phase> phases() {
        return Arbitraries.of(Phase.values());
    }

    @Provide
    Arbitrary<List<MissionOutcome>> missionOutcomes() {
        Arbitrary<MissionOutcome> completedArb = Arbitraries.integers().between(1, 5)
            .map(r -> new MissionOutcome(r, false));
        Arbitrary<MissionOutcome> expiredArb = Arbitraries.just(new MissionOutcome(-1, true));
        Arbitrary<MissionOutcome> outcomeArb = Arbitraries.oneOf(completedArb, expiredArb);
        return outcomeArb.list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<MissionOutcome>> poorOutcomes() {
        // Outcomes that would trigger -1 adjustment: avg <= 2 or 2+ expired
        Arbitrary<List<MissionOutcome>> lowRated = Arbitraries.integers().between(1, 2)
            .list().ofSize(3)
            .map(ratings -> ratings.stream()
                .map(r -> new MissionOutcome(r, false))
                .collect(Collectors.toList()));

        MissionOutcome expired1 = new MissionOutcome(-1, true);
        MissionOutcome expired2 = new MissionOutcome(-1, true);
        MissionOutcome ok = new MissionOutcome(3, false);
        Arbitrary<List<MissionOutcome>> twoExpired = Arbitraries.just(
            List.of(expired1, expired2, ok));

        return Arbitraries.oneOf(lowRated, twoExpired);
    }

    @Provide
    Arbitrary<List<MissionOutcome>> excellentOutcomes() {
        // 3 missions with avg rating >= 4
        return Arbitraries.integers().between(4, 5).list().ofSize(3)
            .map(ratings -> ratings.stream()
                .map(r -> new MissionOutcome(r, false))
                .collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<String> categoryNames() {
        return Arbitraries.of(
            "PLAY", "CONVERSATION", "ADVENTURE", "CREATIVITY",
            "LEARNING", "ROUTINE", "EMOTION", "BONDING"
        );
    }

    @Provide
    Arbitrary<PlanningContext> planningContexts() {
        return Arbitraries.of(Phase.values()).flatMap(phase ->
            Arbitraries.integers().between(1, 30).flatMap(phaseDay ->
                missionOutcomes().flatMap(outcomes ->
                    Arbitraries.integers().between(1, 4).map(numChildren -> {
                        UUID fatherId = UUID.randomUUID();
                        Map<UUID, String> children = new LinkedHashMap<>();
                        List<UUID> childIds = new ArrayList<>();
                        for (int i = 0; i < numChildren; i++) {
                            UUID childId = UUID.randomUUID();
                            children.put(childId, "Child" + (i + 1));
                            childIds.add(childId);
                        }

                        LocalDate today = LocalDate.of(2024, 6, 15);

                        // Generate some recent missions spread among children
                        List<MissionRecord> recentMissions = new ArrayList<>();
                        Random rng = new Random(fatherId.hashCode());
                        int missionCount = rng.nextInt(10);
                        for (int i = 0; i < missionCount; i++) {
                            UUID childId = childIds.get(rng.nextInt(childIds.size()));
                            String cat = ALL_CATEGORIES.get(rng.nextInt(ALL_CATEGORIES.size()));
                            LocalDate assignedOn = today.minusDays(rng.nextInt(7));
                            recentMissions.add(new MissionRecord(cat, childId, assignedOn));
                        }

                        return new PlanningContext(
                            fatherId, children, phase, phaseDay, outcomes,
                            recentMissions, ALL_CATEGORIES, today);
                    })
                )
            )
        );
    }

    @Provide
    Arbitrary<PlanningContext> equityViolatedContexts() {
        return Arbitraries.of(Phase.values()).flatMap(phase ->
            Arbitraries.integers().between(3, 7).map(childACount -> {
                UUID fatherId = UUID.randomUUID();
                UUID childA = UUID.randomUUID();
                UUID childB = UUID.randomUUID();
                Map<UUID, String> children = new LinkedHashMap<>();
                children.put(childA, "ChildA");
                children.put(childB, "ChildB");
                LocalDate today = LocalDate.of(2024, 6, 15);

                // Create a violation: childA gets many missions, childB gets at most 1
                List<MissionRecord> missions = new ArrayList<>();
                for (int i = 0; i < childACount; i++) {
                    missions.add(new MissionRecord(
                        ALL_CATEGORIES.get(i % ALL_CATEGORIES.size()),
                        childA, today.minusDays(i % 7)));
                }
                // childB gets at most 1 mission (ensuring |A - B| > 1)
                if (childACount > 2) {
                    missions.add(new MissionRecord("PLAY", childB, today.minusDays(6)));
                }

                return new PlanningContext(
                    fatherId, children, phase, 15, List.of(),
                    missions, ALL_CATEGORIES, today);
            })
        );
    }
}
