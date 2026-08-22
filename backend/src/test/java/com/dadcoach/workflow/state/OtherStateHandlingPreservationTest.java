package com.dadcoach.workflow.state;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
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
import com.dadcoach.workflow.message.MessageGenerator;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.WorkflowAction;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for Other State Handling
 * 
 * **Validates: Requirements 3.7, 3.8**
 * 
 * <p>These tests ensure that non-QUALITY_TIME_FOLLOW_UP states continue to work correctly
 * after the Bug 4 fix is applied. The fix changes how Quality Time is selected for follow-ups,
 * but should NOT affect other states.</p>
 * 
 * <p><strong>Preservation Requirements from bugfix.md:</strong></p>
 * <ul>
 *   <li>3.7: WHEN the father is in WAITING state with upcoming Quality Time 
 *        THEN the system SHALL CONTINUE TO send morning reminders about the scheduled event</li>
 *   <li>3.8: WHEN a father completes their feedback in QUALITY_TIME_FOLLOW_UP 
 *        THEN the system SHALL CONTINUE TO transition to SCHEDULE_QUALITY_TIME to schedule the next session</li>
 * </ul>
 * 
 * <p><strong>EXPECTED BEHAVIOR:</strong></p>
 * <ul>
 *   <li>These tests MUST PASS on unfixed code (current behavior is correct for non-FOLLOW_UP states)</li>
 *   <li>These tests MUST PASS after the fix is applied (no regression for other states)</li>
 * </ul>
 * 
 * <p><strong>Key Distinction from Exploration Tests:</strong></p>
 * <ul>
 *   <li>Exploration tests: Test bug condition (FOLLOW_UP with wrong QT) - expected to FAIL on unfixed code</li>
 *   <li>Preservation tests: Test non-bug scenarios (other states) - expected to PASS on all code</li>
 * </ul>
 */
class OtherStateHandlingPreservationTest {

    // ============== Property: WAITING State Uses getNextScheduledQualityTime ==============

    /**
     * Property test: In WAITING state, getNextScheduledQualityTime() should correctly return
     * the next UPCOMING Quality Time (start_time > now).
     * 
     * <p>This behavior is CORRECT for WAITING state and should remain unchanged after the fix.
     * The fix only changes behavior for QUALITY_TIME_FOLLOW_UP state.</p>
     * 
     * **Validates: Requirements 3.7**
     */
    @Property(tries = 100)
    @Label("WAITING state uses getNextScheduledQualityTime correctly for upcoming events")
    void waitingStateUsesGetNextScheduledQualityTimeForUpcomingEvents(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName,
            @ForAll("hoursUntilUpcoming") int hoursUntilQT
    ) {
        // Arrange: Create system state with father in WAITING state and upcoming QT
        Instant now = Instant.now();
        
        // Upcoming QT scheduled for hoursUntilQT hours from now
        UUID upcomingQtId = UUID.randomUUID();
        QualityTimeEvent upcomingQT = new QualityTimeEvent(
                upcomingQtId,
                1L,
                childName,
                now.plus(hoursUntilQT, ChronoUnit.HOURS),
                now.plus(hoursUntilQT + 1, ChronoUnit.HOURS),
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.WAITING,
                List.of(upcomingQT)
        );
        
        // ACT: Get next scheduled QT (this is the method WAITING state uses)
        QualityTimeEvent nextScheduled = state.getNextScheduledQualityTime();
        
        // ASSERT: Should return the upcoming QT
        // This behavior should remain unchanged after the fix
        assertThat(nextScheduled)
                .as("WAITING state should use getNextScheduledQualityTime() which returns UPCOMING QT. " +
                    "This behavior must remain unchanged after the FOLLOW_UP fix.")
                .isNotNull()
                .extracting(QualityTimeEvent::qualityTimeId)
                .isEqualTo(upcomingQtId);
    }

    /**
     * Property test: WAITING state handler should correctly show schedule information
     * for UPCOMING Quality Time events.
     * 
     * **Validates: Requirements 3.7**
     */
    @Property(tries = 50)
    @Label("WAITING state handler shows schedule for upcoming QT correctly")
    void waitingStateHandlerShowsScheduleForUpcomingQT(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName,
            @ForAll("hoursUntilUpcoming") int hoursUntilQT
    ) {
        // Arrange
        Instant now = Instant.now();
        
        UUID upcomingQtId = UUID.randomUUID();
        QualityTimeEvent upcomingQT = new QualityTimeEvent(
                upcomingQtId,
                1L,
                childName,
                now.plus(hoursUntilQT, ChronoUnit.HOURS),
                now.plus(hoursUntilQT + 1, ChronoUnit.HOURS),
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.WAITING,
                List.of(upcomingQT)
        );
        
        // Create mocks - create mock mission BEFORE setting up stubbing
        Mission mockMission = createMockMission(upcomingQtId);
        
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.getNextScheduled(anyLong())).thenReturn(Optional.of(mockMission));
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("Your Quality Time with " + childName + " is scheduled.");
        
        FatherRepository fatherRepository = mock(FatherRepository.class);
        
        WaitingStateHandler handler = new WaitingStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                fatherRepository
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
        WorkflowContext context = new WorkflowContext(fatherUuid, WorkflowState.WAITING, "מתי");
        PatternResult matchResult = PatternResult.of(
                "SHOW_SCHEDULE",
                WorkflowAction.SHOW_SCHEDULE
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: Should respond with schedule info (not transition)
        assertThat(action.getActionType())
                .as("WAITING state SHOW_SCHEDULE should respond, not transition")
                .isEqualTo(StateAction.ActionType.RESPOND);
        
        String response = action.getResponseMessage().orElse("");
        assertThat(response)
                .as("WAITING state schedule info should reference the correct child")
                .contains(childName);
    }

    // ============== Property: Completed Feedback Transitions to SCHEDULE_QUALITY_TIME ==============

    /**
     * Property test: When feedback is completed in QUALITY_TIME_FOLLOW_UP state,
     * the system should transition to SCHEDULE_QUALITY_TIME.
     * 
     * <p>This test verifies that the transition behavior is preserved after the fix.
     * The fix changes WHICH QT is referenced, but not WHERE we transition to.</p>
     * 
     * **Validates: Requirements 3.8**
     */
    @Property(tries = 50)
    @Label("Completed feedback transitions to SCHEDULE_QUALITY_TIME")
    void completedFeedbackTransitionsToScheduleQualityTime(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName,
            @ForAll("completionMessages") String completionMessage
    ) {
        // Arrange: Create state with a QT that could be followed up
        Instant now = Instant.now();
        
        // A QT in SCHEDULED status (regardless of timing for this test)
        UUID qtId = UUID.randomUUID();
        QualityTimeEvent qt = new QualityTimeEvent(
                qtId,
                1L,
                childName,
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
                List.of(qt)
        );
        
        // Create mocks - create mock mission BEFORE setting up stubbing
        Mission mockMission = createMockMission(qtId);
        
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mockMission);
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("Great job completing Quality Time!");
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
        WorkflowContext context = new WorkflowContext(
                fatherUuid, 
                WorkflowState.QUALITY_TIME_FOLLOW_UP, 
                completionMessage
        );
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: Should transition to SCHEDULE_QUALITY_TIME
        // This behavior must remain unchanged after the fix
        assertThat(action.getActionType())
                .as("Completed feedback should TRANSITION (not just respond)")
                .isEqualTo(StateAction.ActionType.TRANSITION);
        
        assertThat(action.getNextState())
                .as("After feedback completion, system should transition to SCHEDULE_QUALITY_TIME. " +
                    "This behavior must remain unchanged after the FOLLOW_UP fix.")
                .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
    }

    /**
     * Property test: When feedback indicates QT was missed, system should also
     * transition to SCHEDULE_QUALITY_TIME.
     * 
     * **Validates: Requirements 3.8**
     */
    @Property(tries = 50)
    @Label("Missed feedback also transitions to SCHEDULE_QUALITY_TIME")
    void missedFeedbackTransitionsToScheduleQualityTime(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName
    ) {
        // Arrange
        Instant now = Instant.now();
        
        UUID qtId = UUID.randomUUID();
        QualityTimeEvent qt = new QualityTimeEvent(
                qtId,
                1L,
                childName,
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
                List.of(qt)
        );
        
        // Create mocks
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("No worries, let's schedule another one!");
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
        WorkflowContext context = new WorkflowContext(
                fatherUuid, 
                WorkflowState.QUALITY_TIME_FOLLOW_UP, 
                "לא הספקתי"
        );
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_MISSED",
                WorkflowAction.MARK_MISSED
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: Should transition to SCHEDULE_QUALITY_TIME
        assertThat(action.getActionType())
                .as("Missed feedback should TRANSITION (not just respond)")
                .isEqualTo(StateAction.ActionType.TRANSITION);
        
        assertThat(action.getNextState())
                .as("After missed feedback, system should transition to SCHEDULE_QUALITY_TIME")
                .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
    }

    // ============== Property: Non-Follow-Up States QT Selection Unchanged ==============

    /**
     * Property test: For all fathers NOT in QUALITY_TIME_FOLLOW_UP, the Quality Time
     * selection logic should remain unchanged.
     * 
     * <p>This tests that SystemState.getNextScheduledQualityTime() behavior is preserved
     * for all states except QUALITY_TIME_FOLLOW_UP (which uses a different method).</p>
     * 
     * **Validates: Requirements 3.7, 3.8**
     */
    @Property(tries = 100)
    @Label("For non-FOLLOW_UP states, QT selection uses getNextScheduledQualityTime unchanged")
    void nonFollowUpStatesQTSelectionUnchanged(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName,
            @ForAll("nonFollowUpStates") WorkflowState workflowState,
            @ForAll("hoursUntilUpcoming") int hoursUntilQT
    ) {
        // Pre-condition: state is NOT QUALITY_TIME_FOLLOW_UP
        Assume.that(workflowState != WorkflowState.QUALITY_TIME_FOLLOW_UP);
        
        // Arrange
        Instant now = Instant.now();
        
        // Older ended QT (should be ignored by getNextScheduledQualityTime)
        QualityTimeEvent endedQT = new QualityTimeEvent(
                UUID.randomUUID(),
                1L,
                childName + "_ended",
                now.minus(5, ChronoUnit.HOURS),
                now.minus(4, ChronoUnit.HOURS),
                "SCHEDULED",
                null,
                null,
                null
        );
        
        // Upcoming QT (should be returned by getNextScheduledQualityTime)
        UUID upcomingQtId = UUID.randomUUID();
        QualityTimeEvent upcomingQT = new QualityTimeEvent(
                upcomingQtId,
                2L,
                childName + "_upcoming",
                now.plus(hoursUntilQT, ChronoUnit.HOURS),
                now.plus(hoursUntilQT + 1, ChronoUnit.HOURS),
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                workflowState,
                List.of(endedQT, upcomingQT)
        );
        
        // ACT: Get next scheduled QT using the method that non-FOLLOW_UP states use
        QualityTimeEvent nextScheduled = state.getNextScheduledQualityTime();
        
        // ASSERT: For non-FOLLOW_UP states, should always get the UPCOMING QT
        // The fix should NOT change this behavior
        assertThat(nextScheduled)
                .as("Non-FOLLOW_UP state (%s) should use getNextScheduledQualityTime() " +
                    "which returns UPCOMING QT (start_time > now). " +
                    "This behavior must remain unchanged after the FOLLOW_UP fix.",
                    workflowState)
                .isNotNull()
                .extracting(QualityTimeEvent::qualityTimeId)
                .isEqualTo(upcomingQtId);
        
        assertThat(nextScheduled.childName())
                .as("Should return the upcoming QT's child, not the ended one")
                .isEqualTo(childName + "_upcoming");
    }

    // ============== Example Tests for Specific Scenarios ==============

    /**
     * Example: WAITING state morning reminder scenario uses correct upcoming QT.
     * 
     * **Validates: Requirements 3.7**
     */
    @Example
    @Label("WAITING state morning reminder uses upcoming QT (not ended)")
    void waitingStateMorningReminderUsesUpcomingQT() {
        // Arrange: Scenario - father has ended QT and upcoming QT
        Long fatherId = 12345L;
        Instant now = Instant.now();
        
        // QT that ended yesterday - should NOT be used for morning reminder
        QualityTimeEvent endedYesterday = new QualityTimeEvent(
                UUID.randomUUID(),
                1L,
                "Child_Yesterday",
                now.minus(25, ChronoUnit.HOURS),
                now.minus(24, ChronoUnit.HOURS),
                "COMPLETED",  // Already completed
                null,
                null,
                null
        );
        
        // QT scheduled for today at 3pm - this is what morning reminder should reference
        UUID todayQtId = UUID.randomUUID();
        QualityTimeEvent todayQT = new QualityTimeEvent(
                todayQtId,
                1L,
                "Child_Today",
                now.plus(6, ChronoUnit.HOURS),  // 3pm today
                now.plus(7, ChronoUnit.HOURS),  // 4pm today
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.WAITING,
                List.of(endedYesterday, todayQT)
        );
        
        // ACT: Get next scheduled QT (what morning reminder would use)
        QualityTimeEvent nextScheduled = state.getNextScheduledQualityTime();
        
        // ASSERT: Morning reminder should reference today's upcoming QT
        assertThat(nextScheduled)
                .as("Morning reminder should use upcoming QT for today, not yesterday's ended one")
                .isNotNull()
                .extracting(QualityTimeEvent::qualityTimeId)
                .isEqualTo(todayQtId);
        
        assertThat(nextScheduled.childName())
                .isEqualTo("Child_Today");
    }

    /**
     * Example: Transition to SCHEDULE_QUALITY_TIME after completing feedback.
     * 
     * **Validates: Requirements 3.8**
     */
    @Example
    @Label("Completion transitions to SCHEDULE_QUALITY_TIME for next session scheduling")
    void completionTransitionsToScheduleQualityTime() {
        // Arrange
        Long fatherId = 67890L;
        Instant now = Instant.now();
        
        UUID qtId = UUID.randomUUID();
        QualityTimeEvent qt = new QualityTimeEvent(
                qtId,
                1L,
                "יעל",
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
                List.of(qt)
        );
        
        // Create mocks - create mock mission BEFORE setting up stubbing
        Mission mockMission = createMockMission(qtId);
        
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mockMission);
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("מעולה! כל הכבוד על זמן האיכות!");
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(Long.toString(fatherId).getBytes());
        WorkflowContext context = new WorkflowContext(
                fatherUuid, 
                WorkflowState.QUALITY_TIME_FOLLOW_UP, 
                "כן, היה מצוין!"
        );
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT
        assertThat(action.getNextState())
                .as("After completing feedback, should transition to SCHEDULE_QUALITY_TIME " +
                    "so father can schedule their next session")
                .contains(WorkflowState.SCHEDULE_QUALITY_TIME);
        
        assertThat(action.getResponseMessage())
                .as("Should have a response message for the completion")
                .isPresent();
    }

    // ============== Generators ==============

    @Provide
    Arbitrary<Long> fatherIds() {
        return Arbitraries.longs().between(1L, 100000L);
    }

    @Provide
    Arbitrary<String> childNames() {
        return Arbitraries.of(
                "יעל", "אורי", "נועם", "מיכל", "דניאל",
                "Emma", "Liam", "Olivia", "Noah", "Ava"
        );
    }

    @Provide
    Arbitrary<Integer> hoursUntilUpcoming() {
        // QT scheduled between 2-48 hours from now
        return Arbitraries.integers().between(2, 48);
    }

    @Provide
    Arbitrary<String> completionMessages() {
        return Arbitraries.of(
                "כן",
                "כן, היה מצוין",
                "Yes",
                "Done!",
                "We had a great time!",
                "סיימתי",
                "עשינו את זה"
        );
    }

    /**
     * Generator for all workflow states EXCEPT QUALITY_TIME_FOLLOW_UP.
     */
    @Provide
    Arbitrary<WorkflowState> nonFollowUpStates() {
        return Arbitraries.of(
                WorkflowState.WELCOME,
                WorkflowState.SCHEDULE_QUALITY_TIME,
                WorkflowState.WAITING,
                WorkflowState.ACTIVITY_IDEAS
        );
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
                true
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
     */
    private Mission createMockMission(UUID qualityTimeId) {
        Mission mission = mock(Mission.class);
        when(mission.getId()).thenReturn(qualityTimeId);
        when(mission.getChildId()).thenReturn(1L);
        // Add scheduled times for WaitingStateHandler
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(6, ChronoUnit.HOURS);
        when(mission.getScheduledStart()).thenReturn(start);
        when(mission.getScheduledEnd()).thenReturn(end);
        return mission;
    }
}
