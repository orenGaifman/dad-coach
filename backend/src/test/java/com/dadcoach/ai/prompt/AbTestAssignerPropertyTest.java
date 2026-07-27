package com.dadcoach.ai.prompt;

import net.jqwik.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for A/B test group assignment determinism.
 *
 * <p>Tests Property 18 from the design document: For any father_id, the A/B test group
 * assignment SHALL be deterministic — same father_id always produces same group.
 */
@Tag("Feature: ai-architecture-intelligence-layer, Property 18: A/B Test Group Assignment Determinism")
class AbTestAssignerPropertyTest {

    /**
     * **Validates: Requirements 8.3**
     *
     * Property 18: For any father_id, calling assignGroup multiple times
     * SHALL always return the same group ("A" or "B").
     */
    @Property(tries = 1000)
    @Tag("Property 18: A/B Test Group Assignment Determinism")
    void sameUuidAlwaysReturnsSameGroup(@ForAll("randomUuids") UUID fatherId) {
        String firstCall = AbTestAssigner.assignGroup(fatherId);
        String secondCall = AbTestAssigner.assignGroup(fatherId);
        String thirdCall = AbTestAssigner.assignGroup(fatherId);

        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(secondCall).isEqualTo(thirdCall);
    }

    /**
     * **Validates: Requirements 8.3**
     *
     * The result SHALL always be exactly "A" or "B".
     */
    @Property(tries = 1000)
    @Tag("Property 18: A/B Test Group Assignment Determinism")
    void resultIsAlwaysAOrB(@ForAll("randomUuids") UUID fatherId) {
        String group = AbTestAssigner.assignGroup(fatherId);
        assertThat(group).isIn("A", "B");
    }

    /**
     * **Validates: Requirements 8.3**
     *
     * String-based assignment is also deterministic for same input.
     */
    @Property(tries = 1000)
    @Tag("Property 18: A/B Test Group Assignment Determinism")
    void sameStringIdAlwaysReturnsSameGroup(@ForAll("randomFatherIds") String fatherId) {
        String firstCall = AbTestAssigner.assignGroup(fatherId);
        String secondCall = AbTestAssigner.assignGroup(fatherId);
        String thirdCall = AbTestAssigner.assignGroup(fatherId);

        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(secondCall).isEqualTo(thirdCall);
    }

    /**
     * **Validates: Requirements 8.3**
     *
     * String-based assignment also produces only "A" or "B".
     */
    @Property(tries = 1000)
    @Tag("Property 18: A/B Test Group Assignment Determinism")
    void stringResultIsAlwaysAOrB(@ForAll("randomFatherIds") String fatherId) {
        String group = AbTestAssigner.assignGroup(fatherId);
        assertThat(group).isIn("A", "B");
    }

    /**
     * **Validates: Requirements 8.3**
     *
     * Over many random UUIDs, both groups should be assigned
     * (statistical check — the split should be roughly even).
     */
    @Property(tries = 100)
    @Tag("Property 18: A/B Test Group Assignment Determinism")
    void distributionCoversBothGroups(@ForAll("batchOfUuids") java.util.List<UUID> uuids) {
        long countA = uuids.stream()
            .map(AbTestAssigner::assignGroup)
            .filter("A"::equals)
            .count();
        long countB = uuids.size() - countA;

        // With 50+ UUIDs, both groups should appear
        assertThat(countA).isGreaterThan(0);
        assertThat(countB).isGreaterThan(0);
    }

    // ===== Arbitraries =====

    @Provide
    Arbitrary<UUID> randomUuids() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()));
    }

    @Provide
    Arbitrary<String> randomFatherIds() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()))
            .map(UUID::toString);
    }

    @Provide
    Arbitrary<java.util.List<UUID>> batchOfUuids() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()))
            .list()
            .ofMinSize(50)
            .ofMaxSize(200);
    }
}
