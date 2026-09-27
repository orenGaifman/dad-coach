package com.dadcoach.workflow.scheduler;

import com.dadcoach.domain.conversation.MessageLogService;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.whatsapp.WhatsAppService;
import com.dadcoach.workflow.WorkflowEngine;
import com.dadcoach.workflow.message.FallbackMessages;
import com.dadcoach.weeklygoal.BeltPromotionNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The proactive-messages owner switch: LOCAL (default, Dad Coach 2) keeps every job as-is;
 * PLATFORM (Dad Coach 3) stands down only the jobs that would duplicate the workflow's own
 * proactive messages, while still recording a job run and keeping data-maintenance jobs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProactiveMessageOwnershipSchedulerTest {

    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private FatherRepository fatherRepository;
    @Mock private WorkflowEngine workflowEngine;
    @Mock private WhatsAppService whatsAppService;
    @Mock private SchedulerJobLogRepository jobLogRepository;
    @Mock private FallbackMessages fallbackMessages;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private BeltPromotionNotifier beltPromotionNotifier;
    @Mock private MessageLogService messageLogService;

    private WorkflowScheduler scheduler(ProactiveMessageOwnership.Owner owner) {
        return new WorkflowScheduler(qualityTimeRepository, fatherRepository, workflowEngine, whatsAppService,
                jobLogRepository, fallbackMessages, new SchedulerConfig("0 50 7 * * *", 900000, 3600000, 100),
                weeklyGoalService, beltPromotionNotifier, messageLogService, new ProactiveMessageOwnership(owner));
    }

    @Test
    void localOwnerKeepsTheMorningReminderAndWeeklyPromptRunning() {
        when(weeklyGoalService.getCurrentWeekStart()).thenReturn(LocalDate.of(2026, 9, 27));
        WorkflowScheduler scheduler = scheduler(ProactiveMessageOwnership.Owner.LOCAL);

        scheduler.sendMorningReminders();
        scheduler.promptWeeklyGoalSetting();

        verify(qualityTimeRepository).findScheduledTodayWithoutReminder(any(), any());
        verify(fatherRepository).findActiveFathersWithoutWeeklyGoal(any());
    }

    @Test
    void platformOwnerStandsDownEveryOverlappingJobButStillRecordsACompletedRun() {
        WorkflowScheduler scheduler = scheduler(ProactiveMessageOwnership.Owner.PLATFORM);

        scheduler.sendMorningReminders();
        scheduler.processPreQtReminders();
        scheduler.processStaleStates();
        scheduler.processInactivityNudges();
        scheduler.promptWeeklyGoalSetting();

        verifyNoInteractions(qualityTimeRepository, whatsAppService, messageLogService);
        verify(fatherRepository, never()).findActiveFathersWithoutWeeklyGoal(any());
        ArgumentCaptor<SchedulerJobLog> logs = ArgumentCaptor.forClass(SchedulerJobLog.class);
        verify(jobLogRepository, atLeastOnce()).save(logs.capture());
        assertThat(logs.getAllValues()).extracting(SchedulerJobLog::getJobName)
                .contains("morning_reminder", "pre_qt_reminder", WorkflowScheduler.JOB_NAME_STALE_STATE_DETECTION,
                        "inactivity_nudge", "weekly_goal_prompt");
        assertThat(logs.getAllValues()).allMatch(l -> l.getStatus() == SchedulerJobStatus.COMPLETED);
    }

    @Test
    void platformOwnerStillRunsWeeklyGoalCompletion() {
        when(weeklyGoalService.completeWeeklyGoals()).thenReturn(List.of());
        WorkflowScheduler scheduler = scheduler(ProactiveMessageOwnership.Owner.PLATFORM);

        scheduler.processWeeklyGoalCompletions();

        verify(weeklyGoalService).completeWeeklyGoals();
    }
}
