package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionServiceFactory;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.StatePattern;
import com.dadcoach.workflow.pattern.StatePatterns;
import com.dadcoach.workflow.pattern.WorkflowAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WaitingStateHandler.
 * 
 * Validates Requirements 6.1, 6.4, 6.5 from the deterministic-workflow-engine spec:
 * - 6.1: WAITING state responds to father-initiated messages
 * - 6.4: Schedule inquiry shows next Quality Time details
 * - 6.5: Reschedule cancels existing and transitions to SCHEDULE_QUALITY_TIME
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingStateHandler")
class WaitingStateHandlerTest {
    
    @Mock
    private MissionServiceFactory missionServiceFactory;
    
    @Mock
    private MissionService missionService;
    
    @Mock
    private SystemStateLoader systemStateLoader;
    
    @Mock
    private MessageGenerator messageGenerator;
    
    @Mock
    private FatherRepository fatherRepository;
    
    private WaitingStateHandler handler;
    
    private static final UUID FATHER_UUID = UUID.randomUUID();
    private static final Long FATHER_ID = 1L;
    private static final Long CHILD_ID = 10L;
    private static final UUID QUALITY_TIME_ID = UUID.randomUUID();
    
    @BeforeEach
    void setUp() {
        // Set up MissionServiceFactory with lenient stubbing - not all tests use this mock
        lenient().when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        
        handler = new WaitingStateHandler(
                missionServiceFactory, 
                systemStateLoader, 
                messageGenerator, 
                fatherRepository);
    }
    
    @Nested
    @DisplayName("getState()")
    class GetStateTests {
        
        @Test
        @DisplayName("should return WAITING state")
        void shouldReturnWaitingState() {
            assertThat(handler.getState()).isEqualTo(WorkflowState.WAITING);
        }
    }
    
    @Nested
    @DisplayName("getExpectedPatterns()")
    class GetExpectedPatternsTests {
        
        @Test
        @DisplayName("should return WAITING_PATTERNS from StatePatterns")
        void shouldReturnWaitingPatterns() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).isEqualTo(StatePatterns.WAITING_PATTERNS);
        }
        
        @Test
        @DisplayName("patterns should include REQUEST_IDEAS, RESCHEDULE, SCHEDULE_INQUIRY, and DASHBOARD")
        void patternsShouldIncludeExpectedActions() {
            List<StatePattern> patterns = handler.getExpectedPatterns();
            
            assertThat(patterns).extracting(StatePattern::action)
                    .contains(
                            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS,
                            WorkflowAction.RESCHEDULE,
                            WorkflowAction.SHOW_SCHEDULE,
                            WorkflowAction.SHOW_DASHBOARD_SUMMARY
                    );
        }
    }
    
    @Nested
    @DisplayName("handle() - TRANSITION_TO_ACTIVITY_IDEAS")
    class HandleActivityIdeasTransitionTests {
        
        @Test
        @DisplayName("should store previous state WAITING and transition to ACTIVITY_IDEAS")
        void shouldStorePreviousStateAndTransition() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "ideas");
            PatternResult match = PatternResult.of("REQUEST_IDEAS", WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Father father = new Father("1234567890");
            father.setId(FATHER_ID);
            when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));
            
            when(messageGenerator.generateWithFallback(eq(MessageType.ACTIVITY_IDEAS), any(), anyLong()))
                    .thenReturn("Here are some activity ideas!");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.ACTIVITY_IDEAS);
            assertThat(action.hasResponse()).isTrue();
            
            // Verify previous state was stored
            ArgumentCaptor<Father> fatherCaptor = ArgumentCaptor.forClass(Father.class);
            verify(fatherRepository).save(fatherCaptor.capture());
            assertThat(fatherCaptor.getValue().getPreviousWorkflowState()).isEqualTo(WorkflowState.WAITING);
        }
    }
    
    @Nested
    @DisplayName("handle() - RESCHEDULE")
    class HandleRescheduleTests {
        
        @Test
        @DisplayName("should cancel existing Mission and transition to SCHEDULE_QUALITY_TIME")
        void shouldCancelMissionAndTransition() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "reschedule");
            PatternResult match = PatternResult.of("RESCHEDULE", WorkflowAction.RESCHEDULE);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Mission mockMission = mock(Mission.class);
            when(mockMission.getId()).thenReturn(QUALITY_TIME_ID);
            when(missionService.getNextScheduled(FATHER_ID)).thenReturn(Optional.of(mockMission));
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.SCHEDULE_QUALITY_TIME);
            
            // Verify Mission was cancelled
            verify(missionService).cancel(QUALITY_TIME_ID);
        }
        
        @Test
        @DisplayName("should transition even when no Mission exists")
        void shouldTransitionEvenWhenNoMissionExists() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "reschedule");
            PatternResult match = PatternResult.of("RESCHEDULE", WorkflowAction.RESCHEDULE);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(missionService.getNextScheduled(FATHER_ID)).thenReturn(Optional.empty());
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.isTransition()).isTrue();
            assertThat(action.getNextState()).contains(WorkflowState.SCHEDULE_QUALITY_TIME);
            
            // Verify cancel was NOT called
            verify(missionService, never()).cancel(any());
        }
    }
    
    @Nested
    @DisplayName("handle() - SHOW_SCHEDULE")
    class HandleShowScheduleTests {
        
        @Test
        @DisplayName("should respond with schedule info when Mission exists")
        void shouldRespondWithScheduleInfo() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "when");
            PatternResult match = PatternResult.of("SCHEDULE_INQUIRY", WorkflowAction.SHOW_SCHEDULE);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            Mission mockMission = mock(Mission.class);
            lenient().when(mockMission.getId()).thenReturn(QUALITY_TIME_ID);
            when(mockMission.getChildId()).thenReturn(CHILD_ID);
            when(mockMission.getScheduledStart()).thenReturn(Instant.now().plusSeconds(3600));
            when(mockMission.getScheduledEnd()).thenReturn(Instant.now().plusSeconds(5400));
            when(missionService.getNextScheduled(FATHER_ID)).thenReturn(Optional.of(mockMission));
            
            when(messageGenerator.generateWithFallback(eq(MessageType.WAITING_SCHEDULE_INFO), any(), anyLong()))
                    .thenReturn("Your Quality Time is scheduled for...");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
        }
        
        @Test
        @DisplayName("should respond with no-schedule message when no Mission exists")
        void shouldRespondWithNoScheduleMessage() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "when");
            PatternResult match = PatternResult.of("SCHEDULE_INQUIRY", WorkflowAction.SHOW_SCHEDULE);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            when(missionService.getNextScheduled(FATHER_ID)).thenReturn(Optional.empty());
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("handle() - SHOW_DASHBOARD_SUMMARY")
    class HandleDashboardTests {
        
        @Test
        @DisplayName("should respond with dashboard summary")
        void shouldRespondWithDashboardSummary() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "dashboard");
            PatternResult match = PatternResult.of("DASHBOARD", WorkflowAction.SHOW_DASHBOARD_SUMMARY);
            
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            when(messageGenerator.generateWithFallback(eq(MessageType.DASHBOARD_SUMMARY), any(), anyLong()))
                    .thenReturn("Your progress: Green belt, 5 streak!");
            
            // Act
            StateAction action = handler.handle(context, match);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.RESPOND);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("handleUnmatched()")
    class HandleUnmatchedTests {
        
        @Test
        @DisplayName("should return clarification with valid options in English")
        void shouldReturnClarificationInEnglish() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "random message");
            SystemState systemState = createMockSystemState("en");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            // Act - handleUnmatched uses buildWaitingClarificationMessage (not messageGenerator)
            StateAction action = handler.handleUnmatched(context);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
            assertThat(action.hasResponse()).isTrue();
            assertThat(action.isTransition()).isFalse();
        }
        
        @Test
        @DisplayName("should return clarification with valid options in Hebrew")
        void shouldReturnClarificationInHebrew() {
            // Arrange
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "הודעה אקראית");
            SystemState systemState = createMockSystemState("he");
            when(systemStateLoader.loadState(FATHER_UUID)).thenReturn(systemState);
            
            // Act - handleUnmatched uses buildWaitingClarificationMessage (not messageGenerator)
            StateAction action = handler.handleUnmatched(context);
            
            // Assert
            assertThat(action.getActionType()).isEqualTo(StateAction.ActionType.CLARIFY);
            assertThat(action.hasResponse()).isTrue();
        }
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            assertThatThrownBy(() -> handler.handleUnmatched(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("context must not be null");
        }
    }
    
    @Nested
    @DisplayName("handle() - validation")
    class HandleValidationTests {
        
        @Test
        @DisplayName("should throw when context is null")
        void shouldThrowWhenContextIsNull() {
            PatternResult match = PatternResult.of("TEST", WorkflowAction.SHOW_SCHEDULE);
            
            assertThatThrownBy(() -> handler.handle(null, match))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("context must not be null");
        }
        
        @Test
        @DisplayName("should throw when match is null")
        void shouldThrowWhenMatchIsNull() {
            WorkflowContext context = new WorkflowContext(FATHER_UUID, WorkflowState.WAITING, "test");
            
            assertThatThrownBy(() -> handler.handle(context, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("match must not be null");
        }
    }
    
    // ─── Helper Methods ──────────────────────────────────────────────────────
    
    private SystemState createMockSystemState(String locale) {
        SystemState.FatherProfile fatherProfile = new SystemState.FatherProfile(
                FATHER_ID,
                "David",
                "1234567890",
                List.of(new SystemState.ChildInfo(CHILD_ID, "Maya", LocalDate.of(2019, 5, 15), 5, "female", List.of())),
                locale,
                "Asia/Jerusalem",
                null,
                true
        );
        
        SystemState.DashboardMetrics dashboardMetrics = new SystemState.DashboardMetrics(
                Belt.GREEN,
                5, // currentStreak
                10, // longestStreak
                25, // totalCompleted
                List.of(), // recentAchievements
                50, // progressToNextBelt
                25 // qualityTimesToNextBelt
        );
        
        return new SystemState(
                fatherProfile,
                WorkflowState.WAITING,
                List.of(), // calendarEvents
                List.of(), // qualityTimeEvents
                dashboardMetrics,
                List.of() // conversationContext
        );
    }
}
