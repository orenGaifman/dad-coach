package com.dadcoach.onboarding;

import com.dadcoach.onboarding.activation.ActivationService;
import com.dadcoach.onboarding.activation.ActivationTimeoutJob;
import com.dadcoach.onboarding.invitation.InvitationExpirationJob;
import com.dadcoach.onboarding.invitation.InvitationService;
import com.dadcoach.onboarding.provisioning.ActivationRecord;
import com.dadcoach.onboarding.provisioning.ActivationRecordRepository;
import com.dadcoach.onboarding.provisioning.ActivationStatus;
import com.dadcoach.onboarding.security.AuditLogCleanupJob;
import com.dadcoach.onboarding.security.InvitationAuditLogRepository;
import com.dadcoach.onboarding.security.RateLimitCleanupJob;
import com.dadcoach.onboarding.security.RateLimitEntryRepository;
import com.dadcoach.onboarding.session.OnboardingSessionService;
import com.dadcoach.onboarding.session.SessionCleanupJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Scheduled Jobs Unit Tests")
class ScheduledJobsTest {

    @Nested
    @DisplayName("InvitationExpirationJob")
    class InvitationExpirationJobTests {

        @Mock private InvitationService invitationService;
        @InjectMocks private InvitationExpirationJob job;

        @Test
        @DisplayName("calls expireOverdue on invitation service")
        void callsExpireOverdue() {
            when(invitationService.expireOverdue()).thenReturn(5);

            job.expireOverdueInvitations();

            verify(invitationService).expireOverdue();
        }

        @Test
        @DisplayName("handles exceptions gracefully without rethrowing")
        void handlesExceptions() {
            when(invitationService.expireOverdue()).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            job.expireOverdueInvitations();

            verify(invitationService).expireOverdue();
        }
    }

    @Nested
    @DisplayName("SessionCleanupJob")
    class SessionCleanupJobTests {

        @Mock private OnboardingSessionService sessionService;
        @InjectMocks private SessionCleanupJob job;

        @Test
        @DisplayName("calls expireInactiveSessions on session service")
        void callsExpireInactive() {
            job.expireInactiveSessions();

            verify(sessionService).expireInactiveSessions();
        }

        @Test
        @DisplayName("handles exceptions gracefully")
        void handlesExceptions() {
            doThrow(new RuntimeException("DB error")).when(sessionService).expireInactiveSessions();

            // Should not throw
            job.expireInactiveSessions();
        }
    }

    @Nested
    @DisplayName("ActivationTimeoutJob")
    class ActivationTimeoutJobTests {

        @Mock private ActivationRecordRepository activationRepository;
        @Mock private ActivationService activationService;
        @InjectMocks private ActivationTimeoutJob job;

        @Test
        @DisplayName("processes timed-out activations")
        void processesTimedOutActivations() {
            ActivationRecord record = mock(ActivationRecord.class);
            when(record.getActivationId()).thenReturn(UUID.randomUUID());
            when(activationRepository.findTimedOutActivations(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(record));

            job.handleActivationTimeouts();

            verify(activationService).handleActivationTimeout(record.getActivationId());
        }

        @Test
        @DisplayName("continues processing even if one activation fails")
        void continuesOnIndividualFailure() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            ActivationRecord record1 = mock(ActivationRecord.class);
            ActivationRecord record2 = mock(ActivationRecord.class);
            when(record1.getActivationId()).thenReturn(id1);
            when(record2.getActivationId()).thenReturn(id2);
            when(activationRepository.findTimedOutActivations(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(record1, record2));
            doThrow(new RuntimeException("fail")).when(activationService).handleActivationTimeout(id1);

            job.handleActivationTimeouts();

            verify(activationService).handleActivationTimeout(id1);
            verify(activationService).handleActivationTimeout(id2);
        }
    }

    @Nested
    @DisplayName("RateLimitCleanupJob")
    class RateLimitCleanupJobTests {

        @Mock private RateLimitEntryRepository repository;
        @InjectMocks private RateLimitCleanupJob job;

        @Test
        @DisplayName("deletes expired entries")
        void deletesExpiredEntries() {
            when(repository.deleteExpiredEntries(any(Instant.class))).thenReturn(10);

            job.cleanupExpiredEntries();

            verify(repository).deleteExpiredEntries(any(Instant.class));
        }
    }

    @Nested
    @DisplayName("AuditLogCleanupJob")
    class AuditLogCleanupJobTests {

        @Mock private InvitationAuditLogRepository repository;
        @InjectMocks private AuditLogCleanupJob job;

        @Test
        @DisplayName("deletes entries older than 90 days")
        void deletesOldEntries() {
            when(repository.deleteOlderThan(any(Instant.class))).thenReturn(100);

            job.cleanupOldEntries();

            verify(repository).deleteOlderThan(any(Instant.class));
        }
    }
}
