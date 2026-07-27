package com.dadcoach.statemachine;

import com.dadcoach.common.HabitStatus;
import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.conversation.ConversationStatus;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.mission.MissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateMachineEngineImplTest {

    @Mock
    private StateTransitionLogRepository transitionLogRepository;

    private StateMachineEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new StateMachineEngineImpl(transitionLogRepository);
        lenient().when(transitionLogRepository.save(any(StateTransitionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Valid FatherStatus transition returns target state")
    void validFatherTransition() {
        FatherStatus result = engine.transition(
                "Father", 1L, FatherStatus.NOT_STARTED, FatherStatus.ONBOARDING, "Onboarding initiated");

        assertThat(result).isEqualTo(FatherStatus.ONBOARDING);
    }

    @Test
    @DisplayName("Valid FatherStatus transition logs to audit table")
    void validTransitionLogsAudit() {
        engine.transition("Father", 42L, FatherStatus.ACTIVE, FatherStatus.PAUSED, "Father requests pause");

        ArgumentCaptor<StateTransitionLog> captor = ArgumentCaptor.forClass(StateTransitionLog.class);
        verify(transitionLogRepository).save(captor.capture());

        StateTransitionLog log = captor.getValue();
        assertThat(log.getEntityType()).isEqualTo("Father");
        assertThat(log.getEntityId()).isEqualTo(42L);
        assertThat(log.getFromState()).isEqualTo("ACTIVE");
        assertThat(log.getToState()).isEqualTo("PAUSED");
        assertThat(log.getTriggerReason()).isEqualTo("Father requests pause");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Invalid FatherStatus transition throws exception")
    void invalidFatherTransition() {
        assertThatThrownBy(() ->
                engine.transition("Father", 1L, FatherStatus.NOT_STARTED, FatherStatus.ACTIVE, "Direct skip"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Father")
                .hasMessageContaining("NOT_STARTED")
                .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("Invalid transition still logs the attempt for audit")
    void invalidTransitionStillLogs() {
        try {
            engine.transition("Father", 5L, FatherStatus.DELETED, FatherStatus.ACTIVE, "Attempted resurrection");
        } catch (InvalidStateTransitionException ignored) {
            // expected
        }

        ArgumentCaptor<StateTransitionLog> captor = ArgumentCaptor.forClass(StateTransitionLog.class);
        verify(transitionLogRepository).save(captor.capture());

        StateTransitionLog log = captor.getValue();
        assertThat(log.getFromState()).isEqualTo("DELETED");
        assertThat(log.getToState()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Valid MissionStatus transition works")
    void validMissionTransition() {
        MissionStatus result = engine.transition(
                "Mission", 10L, MissionStatus.ASSIGNED, MissionStatus.ACCEPTED, "Father acknowledged");

        assertThat(result).isEqualTo(MissionStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Invalid MissionStatus transition throws")
    void invalidMissionTransition() {
        assertThatThrownBy(() ->
                engine.transition("Mission", 10L, MissionStatus.ASSIGNED, MissionStatus.COMPLETED, "Skip to done"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Valid HabitStatus transition works")
    void validHabitTransition() {
        HabitStatus result = engine.transition(
                "Habit", 3L, HabitStatus.ACTIVE, HabitStatus.PAUSED, "Father stopped the habit");

        assertThat(result).isEqualTo(HabitStatus.PAUSED);
    }

    @Test
    @DisplayName("Valid ConversationStatus transition works")
    void validConversationTransition() {
        ConversationStatus result = engine.transition(
                "Conversation", 7L, ConversationStatus.ACTIVE, ConversationStatus.COMPLETED, "Objective met");

        assertThat(result).isEqualTo(ConversationStatus.COMPLETED);
    }

    @Test
    @DisplayName("validTransitions returns correct set for FatherStatus.ACTIVE")
    void validTransitionsForActive() {
        Set<FatherStatus> result = engine.validTransitions(FatherStatus.class, FatherStatus.ACTIVE);

        assertThat(result).containsExactlyInAnyOrder(
                FatherStatus.PAUSED, FatherStatus.CHURNED, FatherStatus.DELETED);
    }

    @Test
    @DisplayName("validTransitions returns empty set for terminal state")
    void validTransitionsForTerminalState() {
        Set<FatherStatus> result = engine.validTransitions(FatherStatus.class, FatherStatus.DELETED);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validTransitions for MissionStatus.IN_PROGRESS")
    void validTransitionsForMissionInProgress() {
        Set<MissionStatus> result = engine.validTransitions(MissionStatus.class, MissionStatus.IN_PROGRESS);

        assertThat(result).containsExactlyInAnyOrder(MissionStatus.COMPLETED, MissionStatus.ABANDONED);
    }
}
