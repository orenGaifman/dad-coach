package com.dadcoach.statemachine;

import com.dadcoach.common.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

/**
 * Implementation of the state machine engine that uses reflection to call
 * {@code canTransitionTo()} on any enum implementing the state machine pattern.
 *
 * <p>Transition attempts are logged via SLF4J. Workflow-specific transitions
 * use workflow_state_transition_log for detailed audit trails.</p>
 */
@Service
public class StateMachineEngineImpl implements StateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngineImpl.class);

    public StateMachineEngineImpl() {
        // No-arg constructor - logging via SLF4J only
    }

    @Override
    public <S extends Enum<S>> S transition(
            String entityType, Long entityId, S currentState, S targetState, String triggerReason) {

        boolean isValid = canTransition(currentState, targetState);

        if (!isValid) {
            log.warn("Invalid state transition rejected: {}[id={}] {} → {} (reason: {})",
                    entityType, entityId, currentState.name(), targetState.name(), triggerReason);
            throw new InvalidStateTransitionException(
                    entityType, entityId, currentState.name(), targetState.name()
            );
        }

        log.info("State transition completed: {}[id={}] {} → {} (reason: {})",
                entityType, entityId, currentState.name(), targetState.name(), triggerReason);

        return targetState;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Enum<S>> Set<S> validTransitions(Class<S> stateEnum, S currentState) {
        try {
            Method method = currentState.getClass().getMethod("getValidTransitions");
            Object result = method.invoke(currentState);
            return (Set<S>) result;
        } catch (NoSuchMethodException e) {
            log.error("Enum {} does not implement getValidTransitions()", stateEnum.getSimpleName());
            return Collections.emptySet();
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("Failed to invoke getValidTransitions() on {}.{}",
                    stateEnum.getSimpleName(), currentState.name(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Uses reflection to call canTransitionTo() on the current state enum.
     * All our state-machine enums (FatherStatus, OnboardingState, MissionStatus,
     * ConversationStatus, CoachingSessionOutcome, HabitStatus) implement this method.
     */
    private <S extends Enum<S>> boolean canTransition(S currentState, S targetState) {
        try {
            Method method = currentState.getClass().getMethod("canTransitionTo", currentState.getClass());
            Object result = method.invoke(currentState, targetState);
            return Boolean.TRUE.equals(result);
        } catch (NoSuchMethodException e) {
            log.error("Enum {} does not implement canTransitionTo(). Rejecting transition.",
                    currentState.getClass().getSimpleName());
            return false;
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("Failed to invoke canTransitionTo() on {}.{}",
                    currentState.getClass().getSimpleName(), currentState.name(), e);
            return false;
        }
    }
}
