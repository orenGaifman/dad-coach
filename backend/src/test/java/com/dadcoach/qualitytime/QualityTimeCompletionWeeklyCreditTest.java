package com.dadcoach.qualitytime;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.workflow.metrics.WorkflowMetrics;
import com.dadcoach.workspace.commitment.CommitmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Completing a session credits the weekly goal of the session's own week (its scheduled start). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QualityTimeCompletionWeeklyCreditTest {

    @Mock private QualityTimeRepository qualityTimeRepository;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;
    @Mock private WorkflowMetrics workflowMetrics;
    @Mock private CommitmentService commitmentService;
    @Mock private WeeklyGoalService weeklyGoalService;
    @Mock private RestTemplate restTemplate;

    @Test
    void completionPassesTheSessionsScheduledStartAndDuration() {
        QualityTimeServiceImpl service = new QualityTimeServiceImpl(
                qualityTimeRepository, fatherRepository, childRepository,
                workflowMetrics, commitmentService, weeklyGoalService, restTemplate);
        Father father = new Father("+972501234567");
        father.setId(42L);
        Child child = new Child(father, "Itamar", LocalDate.of(2018, 1, 1));
        Instant start = Instant.parse("2026-10-03T18:00:00Z"); // Saturday 21:00 IDT
        QualityTime session = new QualityTime(father, child, start, start.plusSeconds(3600));
        UUID id = UUID.randomUUID();
        session.setId(id);
        when(qualityTimeRepository.findById(id)).thenReturn(Optional.of(session));
        when(qualityTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fatherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeQualityTime(id, "great");

        verify(weeklyGoalService).recordCompletedQualityTime(42L, start, 60);
    }
}
