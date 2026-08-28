package com.dadcoach.workflow.state;

import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionServiceFactory;
import com.dadcoach.systemstate.SystemState;
import com.dadcoach.systemstate.SystemState.ChildInfo;
import com.dadcoach.systemstate.SystemState.DashboardMetrics;
import com.dadcoach.systemstate.SystemState.FatherProfile;
import com.dadcoach.systemstate.SystemState.QualityTimeEvent;
import com.dadcoach.systemstate.SystemState.WeeklyGoalInfo;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.workflow.Belt;
import com.dadcoach.workflow.WorkflowState;
import com.dadcoach.workflow.message.MessageContext;
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.message.MessageType;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workflow.pattern.PatternResult;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for Bug 4 (QUALITY_TIME_FOLLOW_UP State Bug) Fix
 * 
 * <p>These tests verify the fix for Bug 4 where the FollowUpStateHandler was incorrectly
 * referencing UPCOMING scheduled Quality Time instead of COMPLETED ones that need follow-up.</p>
 * 
 * <p><strong>Bug 4 Fix Summary:</strong></p>
 * <ul>
 *   <li>Changed {@code findQualityTimeForFollowUp()} to filter by {@code scheduledEnd < now} (ended QT)</li>
 *   <li>Returns most recent ended QT instead of any SCHEDULED QT</li>
 *   <li>Removed fallback to {@code getNextScheduledQualityTime()} which returns upcoming events</li>
 *   <li>Gracefully handles case where no ended QTs are available</li>
 * </ul>
 * 
 * <p><strong>Requirements covered:</strong> 2.10, 2.11, 2.12</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FollowUpStateHandler Unit Tests for Bug 4 Fix")
class FollowUpStateHandlerUnitTest {

    @Mock
    private MissionServiceFactory missionServiceFactory;
    
    @Mock
    private MissionService missionService;
    
    @Mock
    private SystemStateLoader systemStateLoader;
    
    @Mock
    private MessageGenerator messageGenerator;
    
    @Mock
    private WorkflowMetrics workflowMetrics;
    
    private FollowUpStateHandler handler;
    
    @BeforeEach
    void setUp() {
        // Use lenient stubbing for common setup to avoid UnnecessaryStubbingException
        lenient().when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
    }

    // ============== Test: findQualityTimeForFollowUp returns ended QT ==============
    
    @Nested
    @DisplayName("findQualityTimeForFollowUp returns ended QT (scheduledEnd < now)")
    class FindQualityTimeForFollowUpTests {
        
        /**
         * Test 1: Verifies that findQualityTimeForFollowUp returns the ended QT (scheduledEnd < now)
         * and not upcoming events.
         * 
         * Requirements: 2.10, 2.11
         */
        @Test
        @DisplayName("Should select ended QT (scheduledEnd < now) for follow-up, not upcoming QT")
        void shouldSelectEndedQTForFollowUp_notUpcoming() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // QT that ENDED 1 hour ago - this should be selected for follow-up
            UUID endedQtId = UUID.randomUUID();
            QualityTimeEvent endedQT = new QualityTimeEvent(
                    endedQtId,
                    1L,
                    "EndedChild",
                    now.minus(2, ChronoUnit.HOURS),   // started 2 hours ago
                    now.minus(1, ChronoUnit.HOURS),   // ended 1 hour ago
                    "SCHEDULED",  // Still SCHEDULED, pending follow-up
                    null,
                    null,
                    null
            );
            
            // QT scheduled for tomorrow - should NOT be selected
            UUID upcomingQtId = UUID.randomUUID();
            QualityTimeEvent upcomingQT = new QualityTimeEvent(
                    upcomingQtId,
                    2L,
                    "UpcomingChild",
                    now.plus(24, ChronoUnit.HOURS),   // tomorrow
                    now.plus(25, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(endedQT, upcomingQT)
            );
            
            // Create mock Mission BEFORE setting up stubbing to avoid UnfinishedStubbingException
            Mission mockMission = createMockMission(endedQtId);
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(missionService.complete(any(), any())).thenReturn(mockMission);
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Great job!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid, 
                    WorkflowState.QUALITY_TIME_FOLLOW_UP, 
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert: Should complete the ended QT, not the upcoming one
            verify(missionService).complete(eq(endedQtId), any());
            verify(missionService, never()).complete(eq(upcomingQtId), any());
        }
        
        /**
         * Test: Verifies that QT with scheduledEnd in the future is NOT selected for follow-up.
         * 
         * Requirements: 2.10
         */
        @Test
        @DisplayName("Should NOT select QT that has not ended yet (scheduledEnd > now)")
        void shouldNotSelectQTThatHasNotEnded() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // QT that has NOT ended yet (scheduledEnd in future)
            UUID notEndedQtId = UUID.randomUUID();
            QualityTimeEvent notEndedQT = new QualityTimeEvent(
                    notEndedQtId,
                    1L,
                    "OngoingChild",
                    now.minus(30, ChronoUnit.MINUTES),  // started 30 min ago
                    now.plus(30, ChronoUnit.MINUTES),   // ends in 30 min (still ongoing)
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(notEndedQT)
            );
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Let's schedule your next Quality Time!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert: Should NOT complete the not-ended QT
            verify(missionService, never()).complete(any(), any());
        }
    }

    // ============== Test: Correct QT is referenced in follow-up message ==============
    
    @Nested
    @DisplayName("Correct QT is referenced in follow-up message")
    class CorrectQTReferencedInMessageTests {
        
        /**
         * Test 2: Verifies that the follow-up message references the child name from the
         * completed QT, not from an upcoming QT.
         * 
         * Requirements: 2.11, 2.12
         */
        @Test
        @DisplayName("Should reference child name from completed QT in follow-up message")
        void shouldReferenceCorrectChildInFollowUpMessage() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            String completedChildName = "יעל";  // Hebrew name for completed QT
            String upcomingChildName = "דניאל"; // Different child for upcoming QT
            
            // Ended QT with specific child
            UUID endedQtId = UUID.randomUUID();
            QualityTimeEvent endedQT = new QualityTimeEvent(
                    endedQtId,
                    1L,
                    completedChildName,
                    now.minus(3, ChronoUnit.HOURS),
                    now.minus(2, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            // Upcoming QT with different child
            QualityTimeEvent upcomingQT = new QualityTimeEvent(
                    UUID.randomUUID(),
                    2L,
                    upcomingChildName,
                    now.plus(20, ChronoUnit.HOURS),
                    now.plus(21, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(endedQT, upcomingQT)
            );
            
            // Create mock Mission BEFORE setting up stubbing
            Mission mockMission = createMockMission(endedQtId);
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(missionService.complete(any(), any())).thenReturn(mockMission);
            
            // Capture the MessageContext to verify the child name
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            when(messageGenerator.generateWithFallback(any(), contextCaptor.capture(), anyLong()))
                    .thenReturn("Great Quality Time!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן, היה מעולה!"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert: The message context should contain the completed child's name
            MessageContext capturedContext = contextCaptor.getValue();
            assertThat(capturedContext.getChildName())
                    .as("Message should reference child from completed QT (%s), not upcoming QT (%s)",
                            completedChildName, upcomingChildName)
                    .isEqualTo(completedChildName);
        }
    }

    // ============== Test: Edge case - Multiple ended QTs ==============
    
    @Nested
    @DisplayName("Edge case: Multiple ended QTs - should return most recent")
    class MultipleEndedQTsTests {
        
        /**
         * Test 3: When multiple QTs have ended, should select the most recent one
         * (the one with the latest scheduledEnd).
         * 
         * Requirements: 2.10, 2.11
         */
        @Test
        @DisplayName("Should select most recent ended QT when multiple QTs have ended")
        void shouldSelectMostRecentEndedQT() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // QT that ended 3 hours ago (older)
            UUID olderEndedQtId = UUID.randomUUID();
            QualityTimeEvent olderEndedQT = new QualityTimeEvent(
                    olderEndedQtId,
                    1L,
                    "OlderChild",
                    now.minus(4, ChronoUnit.HOURS),
                    now.minus(3, ChronoUnit.HOURS),  // ended 3 hours ago
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            // QT that ended 1 hour ago (more recent) - this should be selected
            UUID recentEndedQtId = UUID.randomUUID();
            QualityTimeEvent recentEndedQT = new QualityTimeEvent(
                    recentEndedQtId,
                    2L,
                    "RecentChild",
                    now.minus(2, ChronoUnit.HOURS),
                    now.minus(1, ChronoUnit.HOURS),  // ended 1 hour ago (most recent)
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            // QT that ended 5 hours ago (oldest)
            UUID oldestEndedQtId = UUID.randomUUID();
            QualityTimeEvent oldestEndedQT = new QualityTimeEvent(
                    oldestEndedQtId,
                    3L,
                    "OldestChild",
                    now.minus(6, ChronoUnit.HOURS),
                    now.minus(5, ChronoUnit.HOURS),  // ended 5 hours ago
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(olderEndedQT, recentEndedQT, oldestEndedQT)  // Mixed order
            );
            
            // Create mock Mission BEFORE setting up stubbing
            Mission mockMission = createMockMission(recentEndedQtId);
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(missionService.complete(any(), any())).thenReturn(mockMission);
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("Great job!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert: Should complete the most recent ended QT
            verify(missionService).complete(eq(recentEndedQtId), any());
            verify(missionService, never()).complete(eq(olderEndedQtId), any());
            verify(missionService, never()).complete(eq(oldestEndedQtId), any());
        }
        
        /**
         * Test: Verifies correct child name is used from most recent ended QT.
         * 
         * Requirements: 2.11
         */
        @Test
        @DisplayName("Should use child name from most recent ended QT in message")
        void shouldUseChildNameFromMostRecentEndedQT() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            String recentChildName = "RecentChild";
            
            // Older ended QT
            QualityTimeEvent olderEndedQT = new QualityTimeEvent(
                    UUID.randomUUID(),
                    1L,
                    "OlderChild",
                    now.minus(4, ChronoUnit.HOURS),
                    now.minus(3, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            // Most recent ended QT
            UUID recentEndedQtId = UUID.randomUUID();
            QualityTimeEvent recentEndedQT = new QualityTimeEvent(
                    recentEndedQtId,
                    2L,
                    recentChildName,
                    now.minus(2, ChronoUnit.HOURS),
                    now.minus(1, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(olderEndedQT, recentEndedQT)
            );
            
            // Create mock Mission BEFORE setting up stubbing
            Mission mockMission = createMockMission(recentEndedQtId);
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(missionService.complete(any(), any())).thenReturn(mockMission);
            
            ArgumentCaptor<MessageContext> contextCaptor = ArgumentCaptor.forClass(MessageContext.class);
            when(messageGenerator.generateWithFallback(any(), contextCaptor.capture(), anyLong()))
                    .thenReturn("Great!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert
            assertThat(contextCaptor.getValue().getChildName())
                    .isEqualTo(recentChildName);
        }
    }

    // ============== Test: Edge case - No ended QTs available ==============
    
    @Nested
    @DisplayName("Edge case: No ended QTs available - should return null and transition gracefully")
    class NoEndedQTsTests {
        
        /**
         * Test 4: When no ended QTs are available (only upcoming QTs), should return null
         * and transition gracefully to SCHEDULE_QUALITY_TIME.
         * 
         * Requirements: 2.10, 2.12
         */
        @Test
        @DisplayName("Should transition gracefully when no ended QTs available")
        void shouldTransitionGracefullyWhenNoEndedQTs() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // Only upcoming QT - no ended ones
            UUID upcomingQtId = UUID.randomUUID();
            QualityTimeEvent upcomingQT = new QualityTimeEvent(
                    upcomingQtId,
                    1L,
                    "UpcomingChild",
                    now.plus(24, ChronoUnit.HOURS),
                    now.plus(25, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(upcomingQT)
            );
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(messageGenerator.generateWithFallback(eq(MessageType.SCHEDULE_SLOTS), any(), anyLong()))
                    .thenReturn("Let's schedule your next Quality Time!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            StateAction action = handler.handle(context, matchResult);
            
            // Assert: Should NOT complete any QT (none available for follow-up)
            verify(missionService, never()).complete(any(), any());
            
            // Should transition to SCHEDULE_QUALITY_TIME
            assertThat(action.getActionType())
                    .isEqualTo(StateAction.ActionType.TRANSITION);
            assertThat(action.getNextState())
                    .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
        
        /**
         * Test: When QT list is empty, should handle gracefully.
         * 
         * Requirements: 2.10, 2.12
         */
        @Test
        @DisplayName("Should handle empty QT list gracefully")
        void shouldHandleEmptyQTListGracefully() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of()  // Empty list
            );
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(messageGenerator.generateWithFallback(eq(MessageType.SCHEDULE_SLOTS), any(), anyLong()))
                    .thenReturn("Let's schedule your first Quality Time!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            StateAction action = handler.handle(context, matchResult);
            
            // Assert: Should NOT throw exception, should transition gracefully
            verify(missionService, never()).complete(any(), any());
            assertThat(action.getActionType())
                    .isEqualTo(StateAction.ActionType.TRANSITION);
            assertThat(action.getNextState())
                    .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
        
        /**
         * Test: When all ended QTs have already been processed (COMPLETED status),
         * should handle gracefully.
         * 
         * Requirements: 2.10, 2.12
         */
        @Test
        @DisplayName("Should handle when all ended QTs are already COMPLETED")
        void shouldHandleWhenAllEndedQTsAreCompleted() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // Ended QT but already COMPLETED (not SCHEDULED)
            QualityTimeEvent completedQT = new QualityTimeEvent(
                    UUID.randomUUID(),
                    1L,
                    "CompletedChild",
                    now.minus(4, ChronoUnit.HOURS),
                    now.minus(3, ChronoUnit.HOURS),
                    "COMPLETED",  // Already processed
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(completedQT)
            );
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(messageGenerator.generateWithFallback(eq(MessageType.SCHEDULE_SLOTS), any(), anyLong()))
                    .thenReturn("Let's schedule your next Quality Time!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "כן"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_COMPLETED",
                    WorkflowAction.MARK_COMPLETED
            );
            
            // Act
            StateAction action = handler.handle(context, matchResult);
            
            // Assert: Should NOT complete the already-completed QT
            verify(missionService, never()).complete(any(), any());
            assertThat(action.getNextState())
                    .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        }
    }

    // ============== Test: MARK_MISSED also uses correct ended QT ==============
    
    @Nested
    @DisplayName("MARK_MISSED action also uses correct ended QT")
    class MarkMissedTests {
        
        /**
         * Test: Verifies that MARK_MISSED action also selects the ended QT,
         * not the upcoming one.
         * 
         * Requirements: 2.10, 2.11
         */
        @Test
        @DisplayName("MARK_MISSED should cancel the ended QT, not upcoming")
        void markMissedShouldCancelEndedQT() {
            // Arrange
            Long fatherId = 12345L;
            UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
            Instant now = Instant.now();
            
            // Ended QT
            UUID endedQtId = UUID.randomUUID();
            QualityTimeEvent endedQT = new QualityTimeEvent(
                    endedQtId,
                    1L,
                    "EndedChild",
                    now.minus(3, ChronoUnit.HOURS),
                    now.minus(2, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            // Upcoming QT
            UUID upcomingQtId = UUID.randomUUID();
            QualityTimeEvent upcomingQT = new QualityTimeEvent(
                    upcomingQtId,
                    2L,
                    "UpcomingChild",
                    now.plus(20, ChronoUnit.HOURS),
                    now.plus(21, ChronoUnit.HOURS),
                    "SCHEDULED",
                    null,
                    null,
                    null
            );
            
            SystemState state = createSystemState(
                    fatherId,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    List.of(endedQT, upcomingQT)
            );
            
            when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
            when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                    .thenReturn("No worries, let's reschedule!");
            
            WorkflowContext context = new WorkflowContext(
                    fatherUuid,
                    WorkflowState.QUALITY_TIME_FOLLOW_UP,
                    "לא הספקתי"
            );
            PatternResult matchResult = PatternResult.of(
                    "FOLLOW_UP_MISSED",
                    WorkflowAction.MARK_MISSED
            );
            
            // Act
            handler.handle(context, matchResult);
            
            // Assert: Should cancel the ended QT, not the upcoming one
            verify(missionService).cancel(eq(endedQtId));
            verify(missionService, never()).cancel(eq(upcomingQtId));
        }
    }

    // ============== Helper Methods ==============

    /**
     * Creates a SystemState with the specified workflow state and QT events.
     */
    private SystemState createSystemState(Long fatherId, WorkflowState workflowState,
            List<QualityTimeEvent> qualityTimeEvents) {
        
        FatherProfile fatherProfile = new FatherProfile(
                fatherId,
                "Test Father",
                "972501234567",
                List.of(new ChildInfo(1L, "Test Child", LocalDate.of(2018, 5, 15), 6, "male", List.of()),
                        new ChildInfo(2L, "Test Child 2", LocalDate.of(2020, 3, 10), 4, "female", List.of())),
                "he",
                "Asia/Jerusalem",
                LocalTime.of(15, 0),
                true,
                null  // welcomeStep - null means welcome flow completed
        );
        
        DashboardMetrics dashboardMetrics = new DashboardMetrics(
                Belt.WHITE,
                1,   // currentStreak
                1,   // longestStreak
                1,   // totalCompleted
                List.of(),
                0,
                5
        );
        
        return new SystemState(
                fatherProfile,
                workflowState,
                List.of(),
                qualityTimeEvents,
                dashboardMetrics,
                List.of(),
                WeeklyGoalInfo.noGoal()
        );
    }

    /**
     * Creates a mock Mission object for testing.
     * Note: Create the mock BEFORE using it in when().thenReturn() to avoid
     * UnfinishedStubbingException.
     */
    private Mission createMockMission(UUID qualityTimeId) {
        Mission mission = mock(Mission.class);
        lenient().when(mission.getId()).thenReturn(qualityTimeId);
        lenient().when(mission.getChildId()).thenReturn(1L);
        return mission;
    }
}
