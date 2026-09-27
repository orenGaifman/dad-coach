package com.dadcoach.workspace.commitment;

import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.scheduler.ProactiveMessageOwnership;
import com.dadcoach.workspace.magiclink.DashboardLinkAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitmentReminderOwnershipTest {

    @Mock private QualityTimeCommitmentRepository repository;
    @Mock private FatherRepository fatherRepository;
    @Mock private ChildRepository childRepository;
    @Mock private DeliveryService deliveryService;
    @Mock private DashboardLinkAppender dashboardLinkAppender;

    private CommitmentReminderScheduler scheduler(ProactiveMessageOwnership.Owner owner) {
        return new CommitmentReminderScheduler(repository, fatherRepository, childRepository, deliveryService,
                dashboardLinkAppender, new ProactiveMessageOwnership(owner));
    }

    @Test
    void platformOwnerSendsNoThirtyMinuteReminder() {
        scheduler(ProactiveMessageOwnership.Owner.PLATFORM).sendCommitmentReminders();

        verifyNoInteractions(repository, deliveryService);
    }

    @Test
    void platformOwnerStillMarksMissedCommitmentsButSendsNoMissedMessage() {
        QualityTimeCommitment commitment = new QualityTimeCommitment(1L, Instant.now().minusSeconds(7200),
                LocalDate.now(), LocalTime.NOON);
        when(repository.findPastDueCommitments(any())).thenReturn(List.of(commitment));

        scheduler(ProactiveMessageOwnership.Owner.PLATFORM).checkMissedCommitments();

        assertThat(commitment.getStatus()).isEqualTo(QualityTimeCommitment.CommitmentStatus.MISSED);
        verify(repository).save(commitment);
        verifyNoInteractions(deliveryService, fatherRepository);
    }

    @Test
    void localOwnerKeepsLookingForRemindersAsBefore() {
        scheduler(ProactiveMessageOwnership.Owner.LOCAL).sendCommitmentReminders();

        verify(repository).findCommitmentsNeedingReminder(any(), any());
    }
}
