package com.dadcoach.statemachine;

import java.util.Set;

/**
 * Centralized state transition validation and audit logging for all domain entities.
 *
 * <p>All state-machine enums (FatherStatus, OnboardingState, MissionStatus,
 * ConversationStatus, CoachingSessionOutcome, HabitStatus) implement a
 * {@code canTransitionTo()} method. This engine uses reflection to call that method
 * generically, validate the transition, and log every attempt (successful or invalid)
 * to the state_transition_log table for audit purposes.</p>
 */
public interface StateMachineEngine {

    /**
     * Attempt a state transition. Returns the new state if the transition is valid,
     * or throws InvalidStateTransitionException if it is not.
     *
     * <p>Both successful and invalid transitions are logged to the audit table.</p>
     *
     * @param entityType    the type of entity (e.g., "Father", "Mission")
     * @param entityId      the ID of the entity
     * @param currentState  the current state of the entity
     * @param targetState   the desired target state
     * @param triggerReason a human-readable reason for the transition
     * @param <S>           the enum type representing the state
     * @return the target state if the transition is valid
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is not allowed
     */
    <S extends Enum<S>> S transition(
            String entityType, Long entityId, S currentState, S targetState, String triggerReason
    );

    /**
     * Get valid transitions from a given state.
     *
     * <p>Uses reflection to call {@code getValidTransitions()} on the enum instance.</p>
     *
     * @param stateEnum    the enum class
     * @param currentState the current state
     * @param <S>          the enum type representing the state
     * @return set of valid target states
     */
    <S extends Enum<S>> Set<S> validTransitions(Class<S> stateEnum, S currentState);
}
