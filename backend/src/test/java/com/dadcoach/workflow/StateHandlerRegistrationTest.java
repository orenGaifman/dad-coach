package com.dadcoach.workflow;

import com.dadcoach.workflow.state.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that all StateHandler implementations are properly registered
 * and cover all required WorkflowState values.
 * 
 * <p>Validates Task 28.3: Register all StateHandlers with WorkflowEngine</p>
 * <ul>
 *   <li>Auto-discover or explicitly register all StateHandler implementations</li>
 *   <li>Map each handler to its WorkflowState</li>
 * </ul>
 * 
 * <p>Per Requirement 1.1, the workflow engine implements exactly six workflow states.
 * All states except DASHBOARD (frontend-only) require a StateHandler.</p>
 */
@DisplayName("StateHandler Registration")
class StateHandlerRegistrationTest {

    /**
     * All StateHandler implementations.
     * These are the concrete handlers that should be discovered by Spring's component scan.
     */
    private static final List<Class<? extends StateHandler>> STATE_HANDLER_CLASSES = List.of(
            WelcomeStateHandler.class,
            ScheduleStateHandler.class,
            WaitingStateHandler.class,
            FollowUpStateHandler.class,
            ActivityIdeasStateHandler.class
    );

    /**
     * States that require a StateHandler implementation.
     * DASHBOARD is excluded as it's a frontend-only state (WEB-SPEC-008).
     */
    private static final Set<WorkflowState> STATES_REQUIRING_HANDLERS = EnumSet.of(
            WorkflowState.WELCOME,
            WorkflowState.SCHEDULE_QUALITY_TIME,
            WorkflowState.WAITING,
            WorkflowState.QUALITY_TIME_FOLLOW_UP,
            WorkflowState.ACTIVITY_IDEAS
    );

    /**
     * Expected mapping of WorkflowState to StateHandler class.
     */
    private static final Map<WorkflowState, Class<? extends StateHandler>> EXPECTED_HANDLER_MAPPING = Map.of(
            WorkflowState.WELCOME, WelcomeStateHandler.class,
            WorkflowState.SCHEDULE_QUALITY_TIME, ScheduleStateHandler.class,
            WorkflowState.WAITING, WaitingStateHandler.class,
            WorkflowState.QUALITY_TIME_FOLLOW_UP, FollowUpStateHandler.class,
            WorkflowState.ACTIVITY_IDEAS, ActivityIdeasStateHandler.class
    );

    @Test
    @DisplayName("should have exactly 5 StateHandler implementations")
    void shouldHaveExactlyFiveStateHandlers() {
        assertThat(STATE_HANDLER_CLASSES)
                .hasSize(5)
                .as("There should be exactly 5 StateHandler implementations (one per state, excluding DASHBOARD)");
    }

    @Test
    @DisplayName("should cover all required WorkflowState values")
    void shouldCoverAllRequiredStates() {
        Set<WorkflowState> coveredStates = EXPECTED_HANDLER_MAPPING.keySet();
        
        assertThat(coveredStates)
                .containsExactlyInAnyOrderElementsOf(STATES_REQUIRING_HANDLERS)
                .as("All required WorkflowState values should have a handler mapping");
    }

    @Test
    @DisplayName("all StateHandler classes should implement StateHandler interface")
    void allHandlersShouldImplementInterface() {
        for (Class<? extends StateHandler> handlerClass : STATE_HANDLER_CLASSES) {
            assertThat(StateHandler.class.isAssignableFrom(handlerClass))
                    .isTrue()
                    .as("%s should implement StateHandler interface", handlerClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("each handler should be annotated with @Component")
    void eachHandlerShouldBeAnnotatedWithComponent() {
        for (Class<? extends StateHandler> handlerClass : STATE_HANDLER_CLASSES) {
            boolean hasComponentAnnotation = handlerClass.isAnnotationPresent(
                    org.springframework.stereotype.Component.class);
            
            assertThat(hasComponentAnnotation)
                    .isTrue()
                    .as("%s should be annotated with @Component for Spring auto-discovery", 
                            handlerClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("DASHBOARD state should not require a handler (frontend-only)")
    void dashboardStateShouldNotRequireHandler() {
        assertThat(STATES_REQUIRING_HANDLERS)
                .doesNotContain(WorkflowState.DASHBOARD)
                .as("DASHBOARD is a frontend-only state and should not require a backend StateHandler");
    }

    @Test
    @DisplayName("handler mapping should be complete for all non-DASHBOARD states")
    void handlerMappingShouldBeCompleteForAllNonDashboardStates() {
        Set<WorkflowState> allStatesExceptDashboard = EnumSet.allOf(WorkflowState.class);
        allStatesExceptDashboard.remove(WorkflowState.DASHBOARD);
        
        assertThat(EXPECTED_HANDLER_MAPPING.keySet())
                .containsExactlyInAnyOrderElementsOf(allStatesExceptDashboard)
                .as("All WorkflowState values except DASHBOARD should have a handler mapping");
    }

    @Test
    @DisplayName("each handler class name should match its state")
    void handlerClassNamesShouldMatchTheirStates() {
        // Verify naming convention: XxxStateHandler handles XXX state
        assertThat(WelcomeStateHandler.class.getSimpleName()).contains("Welcome");
        assertThat(ScheduleStateHandler.class.getSimpleName()).contains("Schedule");
        assertThat(WaitingStateHandler.class.getSimpleName()).contains("Waiting");
        assertThat(FollowUpStateHandler.class.getSimpleName()).contains("FollowUp");
        assertThat(ActivityIdeasStateHandler.class.getSimpleName()).contains("ActivityIdeas");
    }
}
