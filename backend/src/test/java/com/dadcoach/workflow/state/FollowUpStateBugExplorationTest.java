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
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workflow.pattern.PatternResult;
import com.dadcoach.workflow.pattern.WorkflowAction;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test for QUALITY_TIME_FOLLOW_UP State Bug
 * 
 * **Validates: Requirements 1.10, 1.11, 1.12**
 * 
 * <p>This test demonstrates the bug where the FollowUpStateHandler incorrectly references
 * UPCOMING scheduled Quality Time instead of COMPLETED ones that need follow-up.</p>
 * 
 * <p><strong>Bug Description:</strong></p>
 * <ul>
 *   <li>The {@code findQualityTimeForFollowUp()} method looks for QT with {@code status = "SCHEDULED"}</li>
 *   <li>For follow-ups, it SHOULD find QT where {@code end_time < now} (completed ones needing follow-up)</li>
 *   <li>The bug manifests when the follow-up message references UPCOMING scheduled QT instead of completed</li>
 * </ul>
 * 
 * <p><strong>CRITICAL:</strong> This test MUST FAIL on unfixed code - failure confirms the bug exists.
 * DO NOT attempt to fix the test or the code when it fails.</p>
 * 
 * <p><strong>Bug Condition Formal Spec:</strong></p>
 * <pre>
 * FUNCTION isFollowUpStateBug(father, response, qualityTimeEvents)
 *   completedQT ← qualityTimeEvents.filter(qt → qt.end_time &lt; NOW)
 *   upcomingQT ← qualityTimeEvents.filter(qt → qt.start_time &gt; NOW)
 *   
 *   RETURN father.currentState = QUALITY_TIME_FOLLOW_UP
 *          AND (response.referencesQT(upcomingQT) OR NOT response.asksAboutCompletedQT(completedQT))
 * END FUNCTION
 * </pre>
 * 
 * <p><strong>EXPECTED OUTCOME:</strong> Test FAILS (upcoming QT referenced instead of completed)</p>
 */
class FollowUpStateBugExplorationTest {

    // Pattern to detect references to upcoming/future time slots
    private static final Pattern UPCOMING_QT_PATTERN = Pattern.compile(
            "(?i)(tomorrow|next|upcoming|scheduled for|ready for|מחר|הבא|מתוכנן ל|מוכן ל)"
    );

    // Pattern to detect references to completed/past Quality Time
    private static final Pattern COMPLETED_QT_PATTERN = Pattern.compile(
            "(?i)(did you|how was|completed|finished|went|ended|האם עשית|איך היה|הושלם|הסתיים|עבר)"
    );

    /**
     * Property test: In QUALITY_TIME_FOLLOW_UP state with a COMPLETED QT (end_time < now)
     * and an UPCOMING QT (start_time > now), the handler should reference the COMPLETED QT,
     * not the UPCOMING one.
     * 
     * <p><strong>BUG DEMONSTRATION:</strong> This test fails on unfixed code because
     * {@code findQualityTimeForFollowUp()} filters by {@code status = "SCHEDULED"}
     * which returns the UPCOMING QT instead of the one that ended.</p>
     * 
     * **Validates: Requirements 1.10, 1.11, 1.12**
     */
    @Property(tries = 50)
    @Label("FOLLOW_UP state should reference COMPLETED QT (end_time < now), not UPCOMING QT")
    void followUpStateShouldReferenceCompletedQT(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName,
            @ForAll("hoursAgoCompleted") int hoursAgoCompleted,
            @ForAll("hoursUntilUpcoming") int hoursUntilUpcoming
    ) {
        // Arrange: Create QT events - one that ENDED (for follow-up) and one UPCOMING
        Instant now = Instant.now();
        
        // Completed QT: ended X hours ago (this should be used for follow-up)
        UUID completedQtId = UUID.randomUUID();
        QualityTimeEvent completedQT = new QualityTimeEvent(
                completedQtId,
                1L,
                childName + "_completed",
                now.minus(hoursAgoCompleted + 1, ChronoUnit.HOURS),  // started before it ended
                now.minus(hoursAgoCompleted, ChronoUnit.HOURS),      // ended X hours ago
                "SCHEDULED",  // Status is still SCHEDULED (not yet processed)
                null,
                null,
                null
        );
        
        // Upcoming QT: starts Y hours from now (should NOT be used for follow-up)
        UUID upcomingQtId = UUID.randomUUID();
        QualityTimeEvent upcomingQT = new QualityTimeEvent(
                upcomingQtId,
                2L,
                childName + "_upcoming",
                now.plus(hoursUntilUpcoming, ChronoUnit.HOURS),      // starts in future
                now.plus(hoursUntilUpcoming + 1, ChronoUnit.HOURS),  // ends after start
                "SCHEDULED",  // Status is SCHEDULED (upcoming event)
                null,
                null,
                null
        );
        
        // Create system state with father in QUALITY_TIME_FOLLOW_UP state
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                List.of(completedQT, upcomingQT)
        );
        
        // Create mocks
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mock(Mission.class));
        
        // Mock returns the same state for any UUID (handler uses context.getFatherId() which is UUID)
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        // Capture the message context to verify which QT is being referenced
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    MessageContext messageContext = invocation.getArgument(1, MessageContext.class);
                    // Return message showing which child is referenced
                    return "How was your Quality Time with " + messageContext.getChildName() + "?";
                });
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        // Create the handler
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        // Create context for MARK_COMPLETED action
        UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
        WorkflowContext context = new WorkflowContext(fatherUuid, WorkflowState.QUALITY_TIME_FOLLOW_UP, "כן, היה מעולה!");
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT: Handle the MARK_COMPLETED action
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: The handler should reference the COMPLETED QT, not the UPCOMING one
        // 
        // BUG DEMONSTRATION:
        // - The current code uses findQualityTimeForFollowUp() which filters by status="SCHEDULED"
        // - This returns the FIRST scheduled QT (which may be the upcoming one)
        // - It does NOT filter by end_time < now to find the completed one
        //
        // EXPECTED (after fix): The completed QT (childName + "_completed") should be referenced
        // ACTUAL (with bug): The upcoming QT might be referenced instead
        
        String responseMessage = action.getResponseMessage().orElse("");
        
        // Verify that the COMPLETED QT child name is referenced, not the UPCOMING one
        assertThat(responseMessage)
                .as("Response should reference the COMPLETED Quality Time (child: %s_completed), " +
                    "not the UPCOMING one (child: %s_upcoming). " +
                    "The handler should select QT where end_time < now, not the first SCHEDULED one. " +
                    "Completed QT ended %d hours ago, Upcoming QT starts in %d hours.",
                    childName, childName, hoursAgoCompleted, hoursUntilUpcoming)
                .contains(childName + "_completed");
        
        // Additionally verify that the completed QT ID was used for completion
        verify(missionService).complete(eq(completedQtId), any());
    }

    /**
     * Property test: When father is in FOLLOW_UP state, the handler should NOT ask about
     * future Quality Time (e.g., "Ready for tomorrow?").
     * 
     * <p><strong>BUG DEMONSTRATION:</strong> This test fails on unfixed code because
     * the handler may incorrectly load the next scheduled QT instead of the ended one.</p>
     * 
     * **Validates: Requirements 1.10, 1.11**
     */
    @Property(tries = 50)
    @Label("FOLLOW_UP state should NOT reference FUTURE Quality Time")
    void followUpStateShouldNotReferenceFutureQT(
            @ForAll("fatherIds") Long fatherId,
            @ForAll("childNames") String childName
    ) {
        // Arrange: QT ended 1 hour ago
        Instant now = Instant.now();
        
        QualityTimeEvent endedQT = new QualityTimeEvent(
                UUID.randomUUID(),
                1L,
                childName,
                now.minus(2, ChronoUnit.HOURS),  // started 2 hours ago
                now.minus(1, ChronoUnit.HOURS),  // ended 1 hour ago
                "SCHEDULED",  // Status still SCHEDULED (pending follow-up)
                null,
                null,
                null
        );
        
        // Create system state
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                List.of(endedQT)
        );
        
        // Create mocks
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mock(Mission.class));
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("How was your Quality Time?");
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(fatherId.toString().getBytes());
        WorkflowContext context = new WorkflowContext(fatherUuid, WorkflowState.QUALITY_TIME_FOLLOW_UP, "כן");
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: Response should NOT contain future/upcoming language
        String responseMessage = action.getResponseMessage().orElse("");
        
        assertThat(responseMessage)
                .as("Response in FOLLOW_UP state should ask about COMPLETED Quality Time, " +
                    "not reference future/upcoming events. Response: %s", responseMessage)
                .doesNotContainPattern(UPCOMING_QT_PATTERN);
    }

    /**
     * Example-based test demonstrating the exact production bug scenario.
     * 
     * <p>Scenario: QT ended at 15:00, father is in FOLLOW_UP state at 16:00.
     * There's also a QT scheduled for tomorrow at 15:00.</p>
     * 
     * <p><strong>BUG:</strong> Bot asks "Ready for your Quality Time tomorrow?" instead of
     * asking about the completed QT.</p>
     * 
     * **Validates: Requirements 1.10, 1.11, 1.12**
     */
    @Example
    @Label("Production scenario: QT ended 1 hour ago, bot should ask about completed QT")
    void productionScenario_qtEndedOneHourAgo() {
        // Arrange: Exact production scenario
        Long fatherId = 12345L;
        String childName = "יעל";  // Hebrew name from production
        Instant now = Instant.now();
        
        // QT that ENDED at 15:00 (1 hour ago) - this is what we're following up on
        UUID endedQtId = UUID.randomUUID();
        QualityTimeEvent endedQT = new QualityTimeEvent(
                endedQtId,
                1L,
                childName,
                now.minus(90, ChronoUnit.MINUTES),  // started at 14:30
                now.minus(60, ChronoUnit.MINUTES),  // ended at 15:00
                "SCHEDULED",  // Still SCHEDULED, pending follow-up confirmation
                null,
                null,
                null
        );
        
        // QT scheduled for tomorrow at 15:00 (should NOT be referenced)
        UUID tomorrowQtId = UUID.randomUUID();
        QualityTimeEvent tomorrowQT = new QualityTimeEvent(
                tomorrowQtId,
                1L,
                childName,
                now.plus(23, ChronoUnit.HOURS),     // tomorrow at 15:00
                now.plus(24, ChronoUnit.HOURS),     // tomorrow at 16:00
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                List.of(endedQT, tomorrowQT)  // Both QTs in the state
        );
        
        // Create mocks
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mock(Mission.class));
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        // Capture which QT is referenced by checking the child name in context
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    MessageContext messageContext = invocation.getArgument(1, MessageContext.class);
                    return "Great! Quality Time with " + messageContext.getChildName() + " completed!";
                });
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(Long.toString(fatherId).getBytes());
        WorkflowContext context = new WorkflowContext(fatherUuid, WorkflowState.QUALITY_TIME_FOLLOW_UP, "כן, היה מצוין!");
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT
        StateAction action = handler.handle(context, matchResult);
        
        // ASSERT: The ENDED QT should be completed, not the tomorrow one
        // This verifies the handler uses the QT that ended (end_time < now)
        verify(missionService, description(
                "BUG: The handler completed the wrong Quality Time! " +
                "Expected: completed QT (ended 1 hour ago, ID: " + endedQtId + "). " +
                "The fix should change findQualityTimeForFollowUp() to filter by scheduledEnd < now " +
                "instead of just status = SCHEDULED."
        )).complete(eq(endedQtId), any());
        
        // Also verify it did NOT try to complete the tomorrow QT
        verify(missionService, never()).complete(eq(tomorrowQtId), any());
    }

    /**
     * Example-based test: When getNextScheduledQualityTime() returns upcoming QT as fallback.
     * 
     * <p>This test demonstrates that the current code incorrectly falls back to
     * {@code getNextScheduledQualityTime()} which returns UPCOMING events.</p>
     * 
     * **Validates: Requirements 1.12**
     */
    @Example
    @Label("Bug scenario: getNextScheduledQualityTime fallback returns wrong QT")
    void bugScenario_getNextScheduledQualityTimeFallback() {
        // Arrange: SystemState.getNextScheduledQualityTime() returns upcoming events
        Long fatherId = 67890L;
        Instant now = Instant.now();
        
        // QT that ENDED - what we should follow up on
        UUID endedQtId = UUID.randomUUID();
        QualityTimeEvent endedQT = new QualityTimeEvent(
                endedQtId,
                1L,
                "Child_Ended",
                now.minus(3, ChronoUnit.HOURS),   // started 3 hours ago
                now.minus(2, ChronoUnit.HOURS),   // ended 2 hours ago
                "SCHEDULED",
                null,
                null,
                null
        );
        
        // QT scheduled for next week
        UUID futureQtId = UUID.randomUUID();
        QualityTimeEvent futureQT = new QualityTimeEvent(
                futureQtId,
                1L,
                "Child_Future",
                now.plus(7, ChronoUnit.DAYS),     // next week
                now.plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                "SCHEDULED",
                null,
                null,
                null
        );
        
        SystemState state = createSystemState(
                fatherId,
                WorkflowState.QUALITY_TIME_FOLLOW_UP,
                List.of(endedQT, futureQT)
        );
        
        // Verify that getNextScheduledQualityTime returns the FUTURE QT (demonstrating the bug path)
        QualityTimeEvent nextScheduled = state.getNextScheduledQualityTime();
        assertThat(nextScheduled)
                .as("SystemState.getNextScheduledQualityTime() should return FUTURE QT " +
                    "(this is correct for WAITING state, but WRONG for FOLLOW_UP state)")
                .extracting(QualityTimeEvent::qualityTimeId)
                .isEqualTo(futureQtId);
        
        // Now test the handler - it should NOT use getNextScheduledQualityTime() for follow-ups
        MissionServiceFactory missionServiceFactory = mock(MissionServiceFactory.class);
        MissionService missionService = mock(MissionService.class);
        when(missionServiceFactory.getDefaultService()).thenReturn(missionService);
        when(missionService.complete(any(), any())).thenReturn(mock(Mission.class));
        
        SystemStateLoader systemStateLoader = mock(SystemStateLoader.class);
        when(systemStateLoader.loadState(any(UUID.class))).thenReturn(state);
        
        MessageGenerator messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateWithFallback(any(), any(), anyLong()))
                .thenReturn("Completed!");
        
        WorkflowMetrics workflowMetrics = mock(WorkflowMetrics.class);
        
        FollowUpStateHandler handler = new FollowUpStateHandler(
                missionServiceFactory,
                systemStateLoader,
                messageGenerator,
                workflowMetrics
        );
        
        UUID fatherUuid = UUID.nameUUIDFromBytes(Long.toString(fatherId).getBytes());
        WorkflowContext context = new WorkflowContext(fatherUuid, WorkflowState.QUALITY_TIME_FOLLOW_UP, "Yes!");
        PatternResult matchResult = PatternResult.of(
                "FOLLOW_UP_COMPLETED",
                WorkflowAction.MARK_COMPLETED
        );
        
        // ACT
        handler.handle(context, matchResult);
        
        // ASSERT: Handler should complete the ENDED QT, not the future one
        verify(missionService, description(
                "BUG: Handler used getNextScheduledQualityTime() which returns upcoming QT! " +
                "Expected: ended QT (ID: " + endedQtId + "), " +
                "Actual: future QT (ID: " + futureQtId + "). " +
                "Fix: findQualityTimeForFollowUp() should filter by scheduledEnd < now"
        )).complete(eq(endedQtId), any());
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
    Arbitrary<Integer> hoursAgoCompleted() {
        // QT ended between 1-4 hours ago (reasonable follow-up window)
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<Integer> hoursUntilUpcoming() {
        // Next QT is between 12-48 hours from now
        return Arbitraries.integers().between(12, 48);
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
                List.of(new ChildInfo(1L, "Test Child", LocalDate.of(2018, 5, 15), 6, "male", List.of())),
                "he",
                "Asia/Jerusalem",
                LocalTime.of(15, 0),
                true,
                null  // welcomeStep - null means welcome flow completed
        );
        
        DashboardMetrics dashboardMetrics = new DashboardMetrics(
                Belt.WHITE,
                1,   // currentStreak - at least 1 since we're completing a QT
                1,   // longestStreak
                1,   // totalCompleted - at least 1 to avoid negative belt calculation
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
}
