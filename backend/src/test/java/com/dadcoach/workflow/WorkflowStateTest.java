package com.dadcoach.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WorkflowState enum transitions.
 * Validates Requirements 1.1 and 1.3 from the deterministic-workflow-engine spec.
 */
@DisplayName("WorkflowState")
class WorkflowStateTest {
    
    @Nested
    @DisplayName("WELCOME state")
    class WelcomeState {
        
        @Test
        @DisplayName("should have exactly one valid transition to SCHEDULE_QUALITY_TIME")
        void shouldHaveOneValidTransition() {
            Set<WorkflowState> transitions = WorkflowState.WELCOME.getValidTransitions();
            
            assertThat(transitions)
                .containsExactly(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
        
        @Test
        @DisplayName("canTransitionTo SCHEDULE_QUALITY_TIME returns true")
        void canTransitionToScheduleQualityTime() {
            assertThat(WorkflowState.WELCOME.canTransitionTo(WorkflowState.SCHEDULE_QUALITY_TIME))
                .isTrue();
        }
        
        @Test
        @DisplayName("canTransitionTo other states returns false")
        void cannotTransitionToOtherStates() {
            assertThat(WorkflowState.WELCOME.canTransitionTo(WorkflowState.WAITING)).isFalse();
            assertThat(WorkflowState.WELCOME.canTransitionTo(WorkflowState.QUALITY_TIME_FOLLOW_UP)).isFalse();
            assertThat(WorkflowState.WELCOME.canTransitionTo(WorkflowState.ACTIVITY_IDEAS)).isFalse();
            assertThat(WorkflowState.WELCOME.canTransitionTo(WorkflowState.DASHBOARD)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("SCHEDULE_QUALITY_TIME state")
    class ScheduleQualityTimeState {
        
        @Test
        @DisplayName("should have valid transitions to WAITING and ACTIVITY_IDEAS")
        void shouldHaveValidTransitions() {
            Set<WorkflowState> transitions = WorkflowState.SCHEDULE_QUALITY_TIME.getValidTransitions();
            
            assertThat(transitions)
                .containsExactlyInAnyOrder(WorkflowState.WAITING, WorkflowState.ACTIVITY_IDEAS);
        }
        
        @Test
        @DisplayName("canTransitionTo WAITING returns true")
        void canTransitionToWaiting() {
            assertThat(WorkflowState.SCHEDULE_QUALITY_TIME.canTransitionTo(WorkflowState.WAITING))
                .isTrue();
        }
        
        @Test
        @DisplayName("canTransitionTo ACTIVITY_IDEAS returns true")
        void canTransitionToActivityIdeas() {
            assertThat(WorkflowState.SCHEDULE_QUALITY_TIME.canTransitionTo(WorkflowState.ACTIVITY_IDEAS))
                .isTrue();
        }
    }
    
    @Nested
    @DisplayName("WAITING state")
    class WaitingState {
        
        @Test
        @DisplayName("should have valid transitions to QUALITY_TIME_FOLLOW_UP, SCHEDULE_QUALITY_TIME, and ACTIVITY_IDEAS")
        void shouldHaveValidTransitions() {
            Set<WorkflowState> transitions = WorkflowState.WAITING.getValidTransitions();
            
            assertThat(transitions)
                .containsExactlyInAnyOrder(
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    WorkflowState.SCHEDULE_QUALITY_TIME,
                    WorkflowState.ACTIVITY_IDEAS
                );
        }
        
        @Test
        @DisplayName("canTransitionTo QUALITY_TIME_FOLLOW_UP returns true")
        void canTransitionToFollowUp() {
            assertThat(WorkflowState.WAITING.canTransitionTo(WorkflowState.QUALITY_TIME_FOLLOW_UP))
                .isTrue();
        }
        
        @Test
        @DisplayName("canTransitionTo SCHEDULE_QUALITY_TIME returns true (reschedule)")
        void canTransitionToSchedule() {
            assertThat(WorkflowState.WAITING.canTransitionTo(WorkflowState.SCHEDULE_QUALITY_TIME))
                .isTrue();
        }
    }
    
    @Nested
    @DisplayName("QUALITY_TIME_FOLLOW_UP state")
    class QualityTimeFollowUpState {
        
        @Test
        @DisplayName("should have exactly one valid transition to SCHEDULE_QUALITY_TIME")
        void shouldHaveOneValidTransition() {
            Set<WorkflowState> transitions = WorkflowState.QUALITY_TIME_FOLLOW_UP.getValidTransitions();
            
            assertThat(transitions)
                .containsExactly(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
        
        @Test
        @DisplayName("canTransitionTo SCHEDULE_QUALITY_TIME returns true")
        void canTransitionToSchedule() {
            assertThat(WorkflowState.QUALITY_TIME_FOLLOW_UP.canTransitionTo(WorkflowState.SCHEDULE_QUALITY_TIME))
                .isTrue();
        }
    }
    
    @Nested
    @DisplayName("ACTIVITY_IDEAS state")
    class ActivityIdeasState {
        
        @Test
        @DisplayName("should return to any of the four main states")
        void shouldReturnToMainStates() {
            Set<WorkflowState> transitions = WorkflowState.ACTIVITY_IDEAS.getValidTransitions();
            
            assertThat(transitions)
                .containsExactlyInAnyOrder(
                    WorkflowState.WELCOME,
                    WorkflowState.SCHEDULE_QUALITY_TIME,
                    WorkflowState.WAITING,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP
                );
        }
        
        @Test
        @DisplayName("cannot transition to DASHBOARD")
        void cannotTransitionToDashboard() {
            assertThat(WorkflowState.ACTIVITY_IDEAS.canTransitionTo(WorkflowState.DASHBOARD))
                .isFalse();
        }
    }
    
    @Nested
    @DisplayName("DASHBOARD state")
    class DashboardState {
        
        @Test
        @DisplayName("should have no valid transitions (terminal state)")
        void shouldHaveNoValidTransitions() {
            Set<WorkflowState> transitions = WorkflowState.DASHBOARD.getValidTransitions();
            
            assertThat(transitions).isEmpty();
        }
        
        @ParameterizedTest
        @EnumSource(WorkflowState.class)
        @DisplayName("canTransitionTo any state returns false")
        void cannotTransitionToAnyState(WorkflowState target) {
            assertThat(WorkflowState.DASHBOARD.canTransitionTo(target)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("General enum properties")
    class GeneralProperties {
        
        @Test
        @DisplayName("enum has exactly six states")
        void enumHasExactlySixStates() {
            assertThat(WorkflowState.values())
                .hasSize(6)
                .containsExactly(
                    WorkflowState.WELCOME,
                    WorkflowState.SCHEDULE_QUALITY_TIME,
                    WorkflowState.WAITING,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    WorkflowState.ACTIVITY_IDEAS,
                    WorkflowState.DASHBOARD
                );
        }
        
        @ParameterizedTest
        @EnumSource(WorkflowState.class)
        @DisplayName("getValidTransitions returns non-null unmodifiable set")
        void getValidTransitionsReturnsUnmodifiableSet(WorkflowState state) {
            Set<WorkflowState> transitions = state.getValidTransitions();
            
            assertThat(transitions).isNotNull();
            // Verify unmodifiable by checking it's wrapped
            assertThat(transitions.getClass().getName())
                .satisfiesAnyOf(
                    name -> assertThat(name).contains("Unmodifiable"),
                    name -> assertThat(name).contains("EmptySet")
                );
        }
        
        @ParameterizedTest
        @EnumSource(WorkflowState.class)
        @DisplayName("no state can transition to itself")
        void noSelfTransitions(WorkflowState state) {
            assertThat(state.canTransitionTo(state)).isFalse();
        }
    }
}
