package com.dadcoach.api.tools;

import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoal;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.Belt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolDispatcherWeeklyGoalTest {

    @Mock private QualityTimeService qualityTimeService;
    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private SystemStateLoader systemStateLoader;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;

    private ToolDispatcher dispatcher;
    private static final Long FATHER_ID = 42L;

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(qualityTimeService, qualityTimeRepository, weeklyGoalService,
                googleCalendarService, systemStateLoader, fatherRepository, childRepository);
        dispatcher.initializeHandlers();
    }

    private ToolApiController.ResolvedToolRequest request(Map<String, Object> params) {
        return new ToolApiController.ResolvedToolRequest("exec-1", "idmp-1", FATHER_ID, params);
    }

    private static WeeklyGoal activeGoal(int hours) {
        WeeklyGoal goal = new WeeklyGoal(new Father("+972501234567"), LocalDate.of(2026, 9, 27), hours, Belt.WHITE);
        goal.setId(5L);
        goal.activate();
        return goal;
    }

    @Test
    void setWeeklyGoalLeavesTheGoalActive() {
        when(weeklyGoalService.createAndActivateWeeklyGoal(FATHER_ID, 3)).thenReturn(activeGoal(3));

        ToolExecutionResponse response = dispatcher.dispatch("set_weekly_goal", request(Map.of("target_hours", 3)));

        assertThat(response.success()).isTrue();
        assertThat(response.data().get("status")).isEqualTo("ACTIVE");
        verify(weeklyGoalService, never()).createWeeklyGoal(anyLong(), anyInt());
    }

    @Test
    void secondGoalForTheSameWeekIsStillRejected() {
        when(weeklyGoalService.createAndActivateWeeklyGoal(FATHER_ID, 4))
                .thenThrow(new IllegalStateException("A weekly goal already exists for week starting 2026-09-27"));

        ToolExecutionResponse response = dispatcher.dispatch("set_weekly_goal", request(Map.of("target_hours", 4)));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    void weeklyGoalStatusReportsTheCurrentWeeksActiveGoal() {
        when(weeklyGoalService.getActiveGoal(FATHER_ID)).thenReturn(Optional.of(activeGoal(3)));

        ToolExecutionResponse response = dispatcher.dispatch("get_weekly_goal_status", request(Map.of()));

        assertThat(response.data().get("has_goal")).isEqualTo(true);
        assertThat(response.data().get("target_hours")).isEqualTo(3);
        assertThat(response.data().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void repeatingThisWeeksGoalWithTheSameTargetIsIdempotent() {
        when(weeklyGoalService.getCurrentWeekGoal(FATHER_ID)).thenReturn(java.util.Optional.of(activeGoal(3)));

        ToolExecutionResponse response = dispatcher.dispatch("set_weekly_goal", request(Map.of("target_hours", 3)));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("already_existed", true).containsEntry("status", "ACTIVE");
        verify(weeklyGoalService, never()).createAndActivateWeeklyGoal(anyLong(), anyInt());
    }

    @Test
    void aDifferentTargetForAnExistingGoalIsRejectedWithoutChangingIt() {
        when(weeklyGoalService.getCurrentWeekGoal(FATHER_ID)).thenReturn(java.util.Optional.of(activeGoal(3)));

        ToolExecutionResponse response = dispatcher.dispatch("set_weekly_goal", request(Map.of("target_hours", 5)));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("INVALID_STATE");
        assertThat(response.errorMessage()).contains("3 hours").contains("cannot be changed this week");
        verify(weeklyGoalService, never()).createAndActivateWeeklyGoal(anyLong(), anyInt());
    }
}
