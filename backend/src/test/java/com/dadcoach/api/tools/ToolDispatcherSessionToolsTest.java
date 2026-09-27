package com.dadcoach.api.tools;

import com.dadcoach.calendar.GoogleCalendarService;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.systemstate.SystemStateLoader;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Session tools as used by Dad Coach 3: reminder instants in results, and self-correcting id errors. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolDispatcherSessionToolsTest {

    @Mock private QualityTimeService qualityTimeService;
    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private SystemStateLoader systemStateLoader;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;

    private ToolDispatcher dispatcher;
    private Father father;
    private static final Long FATHER_ID = 42L;
    private static final ZoneId JERUSALEM = ZoneId.of("Asia/Jerusalem");

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(qualityTimeService, qualityTimeRepository, weeklyGoalService,
                googleCalendarService, systemStateLoader, fatherRepository, childRepository);
        dispatcher.setClock(Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
        dispatcher.initializeHandlers();
        father = new Father("+972501234567");
        father.setId(FATHER_ID);
        father.setTimezone("Asia/Jerusalem");
        when(fatherRepository.findById(FATHER_ID)).thenReturn(Optional.of(father));
        when(weeklyGoalService.zoneFor(father)).thenReturn(JERUSALEM);
    }

    private ToolApiController.ResolvedToolRequest request(Map<String, Object> params) {
        return new ToolApiController.ResolvedToolRequest("exec-1", "idmp-1", FATHER_ID, params);
    }

    private QualityTime session(String startUtc) {
        Child child = new Child(father, "Noa", LocalDate.parse("2019-05-01"));
        Instant start = Instant.parse(startUtc);
        QualityTime qt = new QualityTime(father, child, start, start.plus(Duration.ofHours(1)));
        ReflectionTestUtils.setField(qt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(qt, "fatherId", FATHER_ID);
        return qt;
    }

    @Test
    @DisplayName("schedule_quality_time returns local time and the reminder instants to arm")
    void scheduleReturnsTimers() {
        UUID id = UUID.randomUUID();
        when(qualityTimeService.scheduleQualityTime(any(), any(), any(), any())).thenReturn(ScheduleQualityTimeResult.success(
                id, "evt", "Noa", Instant.parse("2026-09-29T14:00:00Z"), Instant.parse("2026-09-29T15:00:00Z")));

        ToolExecutionResponse response = dispatcher.dispatch("schedule_quality_time", request(Map.of(
                "child_id", 1, "start_time", "2026-09-29T14:00:00Z", "duration_minutes", 60)));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("local_date", "2026-09-29").containsEntry("local_start", "17:00")
                .containsEntry("timezone", "Asia/Jerusalem");
        assertThat(response.data()).doesNotContainKey("week_coverage"); // builder not wired in this test
        assertThat(response.data().get("timers")).isEqualTo(Map.of(
                "session_morning_reminder", "2026-09-29T05:00:00Z",
                "session_reminder_1h", "2026-09-29T13:00:00Z",
                "session_follow_up", "2026-09-29T15:30:00Z"));
    }

    @Test
    @DisplayName("an unknown or malformed session id fails with the father's valid session ids")
    void unknownIdListsValidSessions() {
        QualityTime real = session("2026-09-29T14:00:00Z");
        when(qualityTimeRepository.findById(any())).thenReturn(Optional.empty());
        when(qualityTimeRepository.findByFatherIdAndStatus(FATHER_ID, QualityTimeStatus.SCHEDULED)).thenReturn(List.of(real));

        for (String badId : List.of(UUID.randomUUID().toString(), "sess_20260928_1600")) {
            ToolExecutionResponse response = dispatcher.dispatch("complete_quality_time",
                    request(Map.of("quality_time_id", badId)));

            assertThat(response.success()).isFalse();
            assertThat(response.errorCode()).isEqualTo("NOT_FOUND");
            assertThat(response.errorMessage()).contains(badId).contains(real.getId().toString())
                    .contains("Tuesday 2026-09-29 17:00, upcoming").contains("Call this tool again now");
        }
        verify(qualityTimeService, never()).completeQualityTime(any(), any());
    }

    @Test
    @DisplayName("another father's session is treated as unknown")
    void otherFathersSessionIsUnknown() {
        QualityTime foreign = session("2026-09-29T14:00:00Z");
        ReflectionTestUtils.setField(foreign, "fatherId", 99L);
        when(qualityTimeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        ToolExecutionResponse response = dispatcher.dispatch("cancel_quality_time",
                request(Map.of("quality_time_id", foreign.getId().toString())));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("NOT_FOUND");
        verify(qualityTimeService, never()).cancelQualityTime(any());
    }

    @Test
    @DisplayName("session results carry the week's coverage after the change, from the weekly plan builder")
    void resultsCarryWeekCoverage() {
        com.dadcoach.weeklyplan.WeeklyPlanContextBuilder builder =
                org.mockito.Mockito.mock(com.dadcoach.weeklyplan.WeeklyPlanContextBuilder.class);
        Map<String, Object> coverage = Map.of("target_minutes", 120, "covered_minutes", 75, "uncovered_minutes", 45);
        when(builder.build(father)).thenReturn(Map.of("coverage", coverage));
        dispatcher.setWeeklyPlanContextBuilder(builder);
        when(qualityTimeService.scheduleQualityTime(any(), any(), any(), any())).thenReturn(ScheduleQualityTimeResult.success(
                UUID.randomUUID(), "evt", "Noa", Instant.parse("2026-09-29T14:00:00Z"), Instant.parse("2026-09-29T15:00:00Z")));

        ToolExecutionResponse response = dispatcher.dispatch("schedule_quality_time", request(Map.of(
                "child_id", 1, "start_time", "2026-09-29T14:00:00Z", "duration_minutes", 60)));

        assertThat(response.data().get("week_coverage")).isEqualTo(coverage);
    }
}
