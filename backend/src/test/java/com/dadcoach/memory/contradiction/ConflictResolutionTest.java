package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ConflictResolution}.
 *
 * <p>Validates: SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution)
 * <ul>
 *   <li>Criteria 5: Conflict resolution actions based on access patterns</li>
 * </ul>
 */
@DisplayName("ConflictResolution Tests")
class ConflictResolutionTest {

    private static final UUID CONFLICT_GROUP_ID = UUID.randomUUID();
    private static final UUID FATHER_ID = UUID.randomUUID();

    private Memory createMemory(String content) {
        Memory memory = new Memory(
                FATHER_ID,
                MemoryCategory.PREFERENCE,
                MemorySubjectType.CHILD,
                content,
                5,
                new BigDecimal("0.8"),
                MemorySourceType.CONVERSATION_EXTRACTION
        );
        memory.setId(UUID.randomUUID());
        return memory;
    }

    // ─── Validation Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Throws exception for null conflict group ID")
        void throwsForNullConflictGroupId() {
            assertThatThrownBy(() -> new ConflictResolution(
                    null,
                    ConflictResolution.ResolutionAction.NO_ACTION,
                    null,
                    Collections.emptyList(),
                    "reason"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conflictGroupId cannot be null");
        }

        @Test
        @DisplayName("Throws exception for null action")
        void throwsForNullAction() {
            assertThatThrownBy(() -> new ConflictResolution(
                    CONFLICT_GROUP_ID,
                    null,
                    null,
                    Collections.emptyList(),
                    "reason"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action cannot be null");
        }

        @Test
        @DisplayName("Throws exception for null reason")
        void throwsForNullReason() {
            assertThatThrownBy(() -> new ConflictResolution(
                    CONFLICT_GROUP_ID,
                    ConflictResolution.ResolutionAction.NO_ACTION,
                    null,
                    Collections.emptyList(),
                    null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason cannot be null or blank");
        }

        @Test
        @DisplayName("Throws exception for blank reason")
        void throwsForBlankReason() {
            assertThatThrownBy(() -> new ConflictResolution(
                    CONFLICT_GROUP_ID,
                    ConflictResolution.ResolutionAction.NO_ACTION,
                    null,
                    Collections.emptyList(),
                    "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason cannot be null or blank");
        }

        @Test
        @DisplayName("Converts null affected memories to empty list")
        void convertsNullAffectedMemoriesToEmptyList() {
            ConflictResolution resolution = new ConflictResolution(
                    CONFLICT_GROUP_ID,
                    ConflictResolution.ResolutionAction.NO_ACTION,
                    null,
                    null, // null affected memories
                    "reason");

            assertThat(resolution.affectedMemories()).isNotNull().isEmpty();
        }
    }

    // ─── Factory Method Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("supersedeMemories creates correct resolution")
        void supersedeMemoriesCreatesCorrectResolution() {
            Memory winner = createMemory("Winner");
            Memory loser = createMemory("Loser");

            ConflictResolution resolution = ConflictResolution.supersedeMemories(
                    CONFLICT_GROUP_ID, winner, List.of(loser), "Test reason");

            assertThat(resolution.conflictGroupId()).isEqualTo(CONFLICT_GROUP_ID);
            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.SUPERSEDE);
            assertThat(resolution.winningMemory()).isEqualTo(winner);
            assertThat(resolution.affectedMemories()).containsExactly(loser);
            assertThat(resolution.reason()).isEqualTo("Test reason");
        }

        @Test
        @DisplayName("expireMemories creates correct resolution")
        void expireMemoriesCreatesCorrectResolution() {
            Memory toExpire = createMemory("To expire");

            ConflictResolution resolution = ConflictResolution.expireMemories(
                    CONFLICT_GROUP_ID, List.of(toExpire), "Test reason");

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.EXPIRE);
            assertThat(resolution.winningMemory()).isNull();
            assertThat(resolution.affectedMemories()).containsExactly(toExpire);
        }

        @Test
        @DisplayName("clearConflictGroup creates correct resolution")
        void clearConflictGroupCreatesCorrectResolution() {
            Memory remaining = createMemory("Remaining");

            ConflictResolution resolution = ConflictResolution.clearConflictGroup(
                    CONFLICT_GROUP_ID, remaining, "Test reason");

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.CLEAR_GROUP);
            assertThat(resolution.winningMemory()).isEqualTo(remaining);
            assertThat(resolution.affectedMemories()).isEmpty();
        }

        @Test
        @DisplayName("waitForClarification creates correct resolution")
        void waitForClarificationCreatesCorrectResolution() {
            Memory memory1 = createMemory("Memory 1");
            Memory memory2 = createMemory("Memory 2");

            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    CONFLICT_GROUP_ID, List.of(memory1, memory2), "Test reason");

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION);
            assertThat(resolution.winningMemory()).isNull();
            assertThat(resolution.affectedMemories()).containsExactlyInAnyOrder(memory1, memory2);
        }

        @Test
        @DisplayName("noAction creates correct resolution")
        void noActionCreatesCorrectResolution() {
            ConflictResolution resolution = ConflictResolution.noAction(
                    CONFLICT_GROUP_ID, "Test reason");

            assertThat(resolution.action()).isEqualTo(ConflictResolution.ResolutionAction.NO_ACTION);
            assertThat(resolution.winningMemory()).isNull();
            assertThat(resolution.affectedMemories()).isEmpty();
        }
    }

    // ─── Query Method Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodTests {

        @Test
        @DisplayName("requiresAction returns true for SUPERSEDE")
        void requiresActionTrueForSupersede() {
            Memory winner = createMemory("Winner");
            ConflictResolution resolution = ConflictResolution.supersedeMemories(
                    CONFLICT_GROUP_ID, winner, List.of(createMemory("Loser")), "reason");

            assertThat(resolution.requiresAction()).isTrue();
        }

        @Test
        @DisplayName("requiresAction returns true for EXPIRE")
        void requiresActionTrueForExpire() {
            ConflictResolution resolution = ConflictResolution.expireMemories(
                    CONFLICT_GROUP_ID, List.of(createMemory("Memory")), "reason");

            assertThat(resolution.requiresAction()).isTrue();
        }

        @Test
        @DisplayName("requiresAction returns true for CLEAR_GROUP")
        void requiresActionTrueForClearGroup() {
            ConflictResolution resolution = ConflictResolution.clearConflictGroup(
                    CONFLICT_GROUP_ID, createMemory("Memory"), "reason");

            assertThat(resolution.requiresAction()).isTrue();
        }

        @Test
        @DisplayName("requiresAction returns false for WAIT_FOR_CLARIFICATION")
        void requiresActionFalseForWaitForClarification() {
            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    CONFLICT_GROUP_ID, List.of(createMemory("Memory")), "reason");

            assertThat(resolution.requiresAction()).isFalse();
        }

        @Test
        @DisplayName("requiresAction returns false for NO_ACTION")
        void requiresActionFalseForNoAction() {
            ConflictResolution resolution = ConflictResolution.noAction(
                    CONFLICT_GROUP_ID, "reason");

            assertThat(resolution.requiresAction()).isFalse();
        }

        @Test
        @DisplayName("isWaitingForClarification returns true for WAIT_FOR_CLARIFICATION")
        void isWaitingForClarificationReturnsTrueCorrectly() {
            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    CONFLICT_GROUP_ID, List.of(createMemory("Memory")), "reason");

            assertThat(resolution.isWaitingForClarification()).isTrue();
        }

        @Test
        @DisplayName("isWaitingForClarification returns false for other actions")
        void isWaitingForClarificationReturnsFalseForOthers() {
            ConflictResolution resolution = ConflictResolution.noAction(
                    CONFLICT_GROUP_ID, "reason");

            assertThat(resolution.isWaitingForClarification()).isFalse();
        }

        @Test
        @DisplayName("affectedCount returns correct count")
        void affectedCountReturnsCorrectValue() {
            Memory m1 = createMemory("M1");
            Memory m2 = createMemory("M2");
            Memory m3 = createMemory("M3");

            ConflictResolution resolution = ConflictResolution.waitForClarification(
                    CONFLICT_GROUP_ID, List.of(m1, m2, m3), "reason");

            assertThat(resolution.affectedCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("affectedCount returns 0 for empty list")
        void affectedCountReturnsZeroForEmpty() {
            ConflictResolution resolution = ConflictResolution.noAction(
                    CONFLICT_GROUP_ID, "reason");

            assertThat(resolution.affectedCount()).isEqualTo(0);
        }
    }

    // ─── Resolution Action Enum Tests ────────────────────────────────────

    @Nested
    @DisplayName("ResolutionAction Enum")
    class ResolutionActionTests {

        @Test
        @DisplayName("All expected actions are defined")
        void allExpectedActionsAreDefined() {
            ConflictResolution.ResolutionAction[] actions = ConflictResolution.ResolutionAction.values();

            assertThat(actions).containsExactlyInAnyOrder(
                    ConflictResolution.ResolutionAction.SUPERSEDE,
                    ConflictResolution.ResolutionAction.EXPIRE,
                    ConflictResolution.ResolutionAction.CLEAR_GROUP,
                    ConflictResolution.ResolutionAction.WAIT_FOR_CLARIFICATION,
                    ConflictResolution.ResolutionAction.NO_ACTION
            );
        }
    }
}
