package com.dadcoach.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MemoryState transitions.
 * 
 * <p>Validates: REQ-7 (Memory Lifecycle States)
 * Each memory SHALL follow a defined lifecycle with exactly these states:
 * ACTIVE (new memories), CONFIRMED (user-verified), SUPERSEDED (replaced by newer),
 * ARCHIVED (storage-managed), EXPIRED (time-based), DELETED (user-requested).
 */
@DisplayName("MemoryState Transition Tests")
class MemoryStateTest {

    // ─── Valid Transitions from ACTIVE ───────────────────────────────────

    @Nested
    @DisplayName("ACTIVE state transitions")
    class ActiveStateTransitions {

        @Test
        @DisplayName("ACTIVE → CONFIRMED is valid (on user approval)")
        void activeToConfirmedIsValid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.CONFIRMED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → SUPERSEDED is valid (when new memory replaces it)")
        void activeToSupersededIsValid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.SUPERSEDED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → ARCHIVED is valid (capacity management or admin action)")
        void activeToArchivedIsValid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → EXPIRED is valid (TTL reached)")
        void activeToExpiredIsValid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.EXPIRED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → DELETED is valid (user deletion request)")
        void activeToDeletedIsValid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE → ACTIVE is invalid (no self-transition)")
        void activeToActiveIsInvalid() {
            assertThat(MemoryState.ACTIVE.canTransitionTo(MemoryState.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("ACTIVE has exactly 5 valid transitions")
        void activeHasExactlyFiveValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.ACTIVE.getValidTransitions();
            assertThat(validTransitions).hasSize(5);
            assertThat(validTransitions).containsExactlyInAnyOrder(
                    MemoryState.CONFIRMED,
                    MemoryState.SUPERSEDED,
                    MemoryState.ARCHIVED,
                    MemoryState.EXPIRED,
                    MemoryState.DELETED
            );
        }
    }

    // ─── Valid Transitions from CONFIRMED ────────────────────────────────

    @Nested
    @DisplayName("CONFIRMED state transitions")
    class ConfirmedStateTransitions {

        @Test
        @DisplayName("CONFIRMED → SUPERSEDED is valid (when explicitly contradicted by new info)")
        void confirmedToSupersededIsValid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.SUPERSEDED)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED → ARCHIVED is valid (by admin, unusual)")
        void confirmedToArchivedIsValid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED → DELETED is valid (user deletion request)")
        void confirmedToDeletedIsValid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED → ACTIVE is invalid (no downgrade)")
        void confirmedToActiveIsInvalid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED → CONFIRMED is invalid (no self-transition)")
        void confirmedToConfirmedIsInvalid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED → EXPIRED is invalid (confirmed memories don't expire directly)")
        void confirmedToExpiredIsInvalid() {
            assertThat(MemoryState.CONFIRMED.canTransitionTo(MemoryState.EXPIRED)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED has exactly 3 valid transitions")
        void confirmedHasExactlyThreeValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.CONFIRMED.getValidTransitions();
            assertThat(validTransitions).hasSize(3);
            assertThat(validTransitions).containsExactlyInAnyOrder(
                    MemoryState.SUPERSEDED,
                    MemoryState.ARCHIVED,
                    MemoryState.DELETED
            );
        }
    }

    // ─── Valid Transitions from SUPERSEDED ───────────────────────────────

    @Nested
    @DisplayName("SUPERSEDED state transitions")
    class SupersededStateTransitions {

        @Test
        @DisplayName("SUPERSEDED → ARCHIVED is valid (cleanup job)")
        void supersededToArchivedIsValid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("SUPERSEDED → DELETED is valid (user deletion request)")
        void supersededToDeletedIsValid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("SUPERSEDED → ACTIVE is invalid (superseded memories cannot be reactivated)")
        void supersededToActiveIsInvalid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("SUPERSEDED → CONFIRMED is invalid (superseded memories cannot be confirmed)")
        void supersededToConfirmedIsInvalid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("SUPERSEDED → SUPERSEDED is invalid (no self-transition)")
        void supersededToSupersededIsInvalid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.SUPERSEDED)).isFalse();
        }

        @Test
        @DisplayName("SUPERSEDED → EXPIRED is invalid")
        void supersededToExpiredIsInvalid() {
            assertThat(MemoryState.SUPERSEDED.canTransitionTo(MemoryState.EXPIRED)).isFalse();
        }

        @Test
        @DisplayName("SUPERSEDED has exactly 2 valid transitions")
        void supersededHasExactlyTwoValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.SUPERSEDED.getValidTransitions();
            assertThat(validTransitions).hasSize(2);
            assertThat(validTransitions).containsExactlyInAnyOrder(
                    MemoryState.ARCHIVED,
                    MemoryState.DELETED
            );
        }
    }

    // ─── Valid Transitions from ARCHIVED ─────────────────────────────────

    @Nested
    @DisplayName("ARCHIVED state transitions")
    class ArchivedStateTransitions {

        @Test
        @DisplayName("ARCHIVED → ACTIVE is valid (reactivation when father re-references)")
        void archivedToActiveIsValid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ARCHIVED → DELETED is valid (explicit cleanup)")
        void archivedToDeletedIsValid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("ARCHIVED → CONFIRMED is invalid")
        void archivedToConfirmedIsInvalid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("ARCHIVED → SUPERSEDED is invalid")
        void archivedToSupersededIsInvalid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.SUPERSEDED)).isFalse();
        }

        @Test
        @DisplayName("ARCHIVED → ARCHIVED is invalid (no self-transition)")
        void archivedToArchivedIsInvalid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.ARCHIVED)).isFalse();
        }

        @Test
        @DisplayName("ARCHIVED → EXPIRED is invalid")
        void archivedToExpiredIsInvalid() {
            assertThat(MemoryState.ARCHIVED.canTransitionTo(MemoryState.EXPIRED)).isFalse();
        }

        @Test
        @DisplayName("ARCHIVED has exactly 2 valid transitions")
        void archivedHasExactlyTwoValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.ARCHIVED.getValidTransitions();
            assertThat(validTransitions).hasSize(2);
            assertThat(validTransitions).containsExactlyInAnyOrder(
                    MemoryState.ACTIVE,
                    MemoryState.DELETED
            );
        }
    }

    // ─── Valid Transitions from EXPIRED ──────────────────────────────────

    @Nested
    @DisplayName("EXPIRED state transitions")
    class ExpiredStateTransitions {

        @Test
        @DisplayName("EXPIRED → ACTIVE is valid (reactivation when father re-references)")
        void expiredToActiveIsValid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("EXPIRED → ARCHIVED is valid (preservation)")
        void expiredToArchivedIsValid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("EXPIRED → DELETED is valid (cleanup job)")
        void expiredToDeletedIsValid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("EXPIRED → CONFIRMED is invalid")
        void expiredToConfirmedIsInvalid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("EXPIRED → SUPERSEDED is invalid")
        void expiredToSupersededIsInvalid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.SUPERSEDED)).isFalse();
        }

        @Test
        @DisplayName("EXPIRED → EXPIRED is invalid (no self-transition)")
        void expiredToExpiredIsInvalid() {
            assertThat(MemoryState.EXPIRED.canTransitionTo(MemoryState.EXPIRED)).isFalse();
        }

        @Test
        @DisplayName("EXPIRED has exactly 3 valid transitions")
        void expiredHasExactlyThreeValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.EXPIRED.getValidTransitions();
            assertThat(validTransitions).hasSize(3);
            assertThat(validTransitions).containsExactlyInAnyOrder(
                    MemoryState.ACTIVE,
                    MemoryState.ARCHIVED,
                    MemoryState.DELETED
            );
        }
    }

    // ─── Valid Transitions from DELETED ──────────────────────────────────

    @Nested
    @DisplayName("DELETED state transitions")
    class DeletedStateTransitions {

        @ParameterizedTest(name = "DELETED → {0} is invalid")
        @EnumSource(MemoryState.class)
        @DisplayName("DELETED cannot transition to any state")
        void deletedCannotTransitionToAnyState(MemoryState targetState) {
            assertThat(MemoryState.DELETED.canTransitionTo(targetState)).isFalse();
        }

        @Test
        @DisplayName("DELETED has no valid transitions")
        void deletedHasNoValidTransitions() {
            Set<MemoryState> validTransitions = MemoryState.DELETED.getValidTransitions();
            assertThat(validTransitions).isEmpty();
        }
    }

    // ─── Invalid Transition Matrix Tests ─────────────────────────────────

    @Nested
    @DisplayName("Invalid transition tests")
    class InvalidTransitionTests {

        /**
         * Provides all invalid state transitions as test arguments.
         */
        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    // ACTIVE invalid transitions (only self-transition is invalid)
                    Arguments.of(MemoryState.ACTIVE, MemoryState.ACTIVE),
                    
                    // CONFIRMED invalid transitions
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.ACTIVE),
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.EXPIRED),
                    
                    // SUPERSEDED invalid transitions
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.ACTIVE),
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.EXPIRED),
                    
                    // ARCHIVED invalid transitions
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.EXPIRED),
                    
                    // EXPIRED invalid transitions
                    Arguments.of(MemoryState.EXPIRED, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.EXPIRED, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.EXPIRED, MemoryState.EXPIRED),
                    
                    // DELETED invalid transitions (all)
                    Arguments.of(MemoryState.DELETED, MemoryState.ACTIVE),
                    Arguments.of(MemoryState.DELETED, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.DELETED, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.DELETED, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.DELETED, MemoryState.EXPIRED),
                    Arguments.of(MemoryState.DELETED, MemoryState.DELETED)
            );
        }

        @ParameterizedTest(name = "{0} → {1} should be rejected")
        @MethodSource("invalidTransitions")
        @DisplayName("Invalid transitions are correctly rejected")
        void invalidTransitionsAreRejected(MemoryState fromState, MemoryState toState) {
            assertThat(fromState.canTransitionTo(toState)).isFalse();
        }
    }

    // ─── Valid Transition Matrix Tests ───────────────────────────────────

    @Nested
    @DisplayName("Valid transition tests")
    class ValidTransitionTests {

        /**
         * Provides all valid state transitions as test arguments.
         */
        static Stream<Arguments> validTransitions() {
            return Stream.of(
                    // ACTIVE valid transitions
                    Arguments.of(MemoryState.ACTIVE, MemoryState.CONFIRMED),
                    Arguments.of(MemoryState.ACTIVE, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.ACTIVE, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.ACTIVE, MemoryState.EXPIRED),
                    Arguments.of(MemoryState.ACTIVE, MemoryState.DELETED),
                    
                    // CONFIRMED valid transitions
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.SUPERSEDED),
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.CONFIRMED, MemoryState.DELETED),
                    
                    // SUPERSEDED valid transitions
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.SUPERSEDED, MemoryState.DELETED),
                    
                    // ARCHIVED valid transitions
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.ACTIVE),
                    Arguments.of(MemoryState.ARCHIVED, MemoryState.DELETED),
                    
                    // EXPIRED valid transitions
                    Arguments.of(MemoryState.EXPIRED, MemoryState.ACTIVE),
                    Arguments.of(MemoryState.EXPIRED, MemoryState.ARCHIVED),
                    Arguments.of(MemoryState.EXPIRED, MemoryState.DELETED)
            );
        }

        @ParameterizedTest(name = "{0} → {1} should be allowed")
        @MethodSource("validTransitions")
        @DisplayName("Valid transitions are correctly allowed")
        void validTransitionsAreAllowed(MemoryState fromState, MemoryState toState) {
            assertThat(fromState.canTransitionTo(toState)).isTrue();
        }
    }

    // ─── Comprehensive Transition Count Tests ────────────────────────────

    @Nested
    @DisplayName("Transition count verification")
    class TransitionCountVerification {

        @Test
        @DisplayName("Total valid transitions count is exactly 15")
        void totalValidTransitionsCountIs15() {
            int totalTransitions = 0;
            for (MemoryState state : MemoryState.values()) {
                totalTransitions += state.getValidTransitions().size();
            }
            // ACTIVE: 5 + CONFIRMED: 3 + SUPERSEDED: 2 + ARCHIVED: 2 + EXPIRED: 3 + DELETED: 0 = 15
            assertThat(totalTransitions).isEqualTo(15);
        }

        @Test
        @DisplayName("No state can transition to itself")
        void noStateSelfTransition() {
            for (MemoryState state : MemoryState.values()) {
                assertThat(state.canTransitionTo(state))
                        .as("State %s should not be able to transition to itself", state)
                        .isFalse();
            }
        }
    }
}
