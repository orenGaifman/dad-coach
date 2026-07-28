package com.dadcoach.onboarding.invitation;

import com.dadcoach.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private InvitationTokenGenerator tokenGenerator;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final String GENERATED_TOKEN = "abc123def456ghi789jkl012mno345pq";
    private static final String CLIENT_IP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        lenient().when(tokenGenerator.generateToken()).thenReturn(GENERATED_TOKEN);
        lenient().when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ─── Create Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("creates SINGLE_USE invitation with 7-day expiration")
        void create_singleUse_sets7DayExpiration() {
            var request = new InvitationCreateRequest(InvitationType.SINGLE_USE);

            Invitation result = invitationService.create(request, CREATOR_ID);

            assertThat(result.getType()).isEqualTo(InvitationType.SINGLE_USE);
            assertThat(result.getMaxUses()).isEqualTo(1);
            assertThat(result.getExpiresAt())
                    .isCloseTo(Instant.now().plus(7, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
            assertThat(result.getStatus()).isEqualTo(InvitationStatus.CREATED);
            assertThat(result.getToken()).isEqualTo(GENERATED_TOKEN);
            assertThat(result.getCreatedBy()).isEqualTo(CREATOR_ID);
            assertThat(result.getCurrentUses()).isEqualTo(0);
        }

        @Test
        @DisplayName("creates REUSABLE invitation with 90-day expiration")
        void create_reusable_sets90DayExpiration() {
            var request = new InvitationCreateRequest(InvitationType.REUSABLE);

            Invitation result = invitationService.create(request, CREATOR_ID);

            assertThat(result.getType()).isEqualTo(InvitationType.REUSABLE);
            assertThat(result.getMaxUses()).isEqualTo(50);
            assertThat(result.getExpiresAt())
                    .isCloseTo(Instant.now().plus(90, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
            assertThat(result.getStatus()).isEqualTo(InvitationStatus.CREATED);
        }

        @Test
        @DisplayName("creates invitation with custom max_uses override")
        void create_withMaxUsesOverride() {
            var request = new InvitationCreateRequest(InvitationType.REUSABLE, null, 25);

            Invitation result = invitationService.create(request, CREATOR_ID);

            assertThat(result.getMaxUses()).isEqualTo(25);
        }

        @Test
        @DisplayName("creates invitation with metadata")
        void create_withMetadata() {
            Map<String, Object> metadata = Map.of("campaign", "beta_launch", "referral_code", "REF123");
            var request = new InvitationCreateRequest(InvitationType.REUSABLE, metadata, null);

            Invitation result = invitationService.create(request, CREATOR_ID);

            assertThat(result.getMetadata()).isEqualTo(metadata);
        }

        @Test
        @DisplayName("throws on null request")
        void create_nullRequest_throws() {
            assertThatThrownBy(() -> invitationService.create(null, CREATOR_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on null type")
        void create_nullType_throws() {
            var request = new InvitationCreateRequest(null, null, null);
            assertThatThrownBy(() -> invitationService.create(request, CREATOR_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on null createdBy")
        void create_nullCreatedBy_throws() {
            var request = new InvitationCreateRequest(InvitationType.SINGLE_USE);
            assertThatThrownBy(() -> invitationService.create(request, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("persists invitation via repository")
        void create_savesViaRepository() {
            var request = new InvitationCreateRequest(InvitationType.SINGLE_USE);

            invitationService.create(request, CREATOR_ID);

            ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
            verify(invitationRepository).save(captor.capture());
            assertThat(captor.getValue().getToken()).isEqualTo(GENERATED_TOKEN);
        }
    }

    // ─── Validate Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("validate")
    class ValidateTests {

        @Test
        @DisplayName("returns NOT_FOUND for null token")
        void validate_nullToken_returnsNotFound() {
            var result = invitationService.validate(null, CLIENT_IP);
            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.NOT_FOUND);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("returns NOT_FOUND for blank token")
        void validate_blankToken_returnsNotFound() {
            var result = invitationService.validate("  ", CLIENT_IP);
            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.NOT_FOUND);
        }

        @Test
        @DisplayName("returns NOT_FOUND when token doesn't exist")
        void validate_unknownToken_returnsNotFound() {
            when(invitationRepository.findByToken("unknown")).thenReturn(Optional.empty());

            var result = invitationService.validate("unknown", CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.NOT_FOUND);
        }

        @Test
        @DisplayName("returns REVOKED when status is REVOKED")
        void validate_revokedStatus_returnsRevoked() {
            Invitation invitation = createInvitation(InvitationStatus.REVOKED);
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.REVOKED);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("returns EXPIRED when status is EXPIRED")
        void validate_expiredStatus_returnsExpired() {
            Invitation invitation = createInvitation(InvitationStatus.EXPIRED);
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.EXPIRED);
        }

        @Test
        @DisplayName("returns EXPIRED when expires_at is in the past")
        void validate_pastExpiration_returnsExpired() {
            Invitation invitation = createInvitation(InvitationStatus.SENT);
            invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.EXPIRED);
        }

        @Test
        @DisplayName("returns EXHAUSTED when current_uses >= max_uses")
        void validate_exhausted_returnsExhausted() {
            Invitation invitation = createInvitation(InvitationStatus.USED);
            invitation.setMaxUses(1);
            invitation.setCurrentUses(1);
            invitation.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.EXHAUSTED);
        }

        @Test
        @DisplayName("returns VALID for valid invitation")
        void validate_valid_returnsValid() {
            Invitation invitation = createValidInvitation();
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.VALID);
            assertThat(result.isValid()).isTrue();
            assertThat(result.invitationId()).isEqualTo(invitation.getInvitationId());
            assertThat(result.type()).isEqualTo(InvitationType.REUSABLE);
            assertThat(result.remainingUses()).isEqualTo(48);
        }

        @Test
        @DisplayName("returns VALID for SINGLE_USE invitation with 0 uses")
        void validate_singleUseUnused_returnsValid() {
            Invitation invitation = createInvitation(InvitationStatus.OPENED);
            invitation.setType(InvitationType.SINGLE_USE);
            invitation.setMaxUses(1);
            invitation.setCurrentUses(0);
            invitation.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
            when(invitationRepository.findByToken(GENERATED_TOKEN)).thenReturn(Optional.of(invitation));

            var result = invitationService.validate(GENERATED_TOKEN, CLIENT_IP);

            assertThat(result.status()).isEqualTo(InvitationValidationResult.Status.VALID);
            assertThat(result.remainingUses()).isEqualTo(1);
        }
    }

    // ─── markOpened Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("markOpened")
    class MarkOpenedTests {

        @Test
        @DisplayName("transitions SENT invitation to OPENED")
        void markOpened_fromSent_transitionsToOpened() {
            UUID id = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.SENT);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.markOpened(id);

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.OPENED);
            verify(invitationRepository).save(invitation);
        }

        @Test
        @DisplayName("throws when transition from CREATED to OPENED is not allowed")
        void markOpened_fromCreated_throws() {
            UUID id = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.CREATED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.markOpened(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREATED")
                    .hasMessageContaining("OPENED");
        }

        @Test
        @DisplayName("throws when invitation not found")
        void markOpened_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(invitationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.markOpened(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── incrementUses Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("incrementUses")
    class IncrementUsesTests {

        @Test
        @DisplayName("increments current_uses and transitions to USED")
        void incrementUses_opensedInvitation_incrementsAndTransitions() {
            UUID id = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.OPENED);
            invitation.setInvitationId(id);
            invitation.setMaxUses(1);
            invitation.setCurrentUses(0);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.incrementUses(id);

            assertThat(invitation.getCurrentUses()).isEqualTo(1);
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
            verify(invitationRepository).save(invitation);
        }

        @Test
        @DisplayName("keeps USED status for reusable invitation with more uses available")
        void incrementUses_reusableWithMoreUses_staysUsed() {
            UUID id = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.OPENED);
            invitation.setInvitationId(id);
            invitation.setType(InvitationType.REUSABLE);
            invitation.setMaxUses(50);
            invitation.setCurrentUses(0);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.incrementUses(id);

            assertThat(invitation.getCurrentUses()).isEqualTo(1);
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
        }

        @Test
        @DisplayName("throws on terminal status")
        void incrementUses_terminalStatus_throws() {
            UUID id = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.EXPIRED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.incrementUses(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }
    }

    // ─── revoke Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("revoke")
    class RevokeTests {

        @Test
        @DisplayName("transitions non-terminal invitation to REVOKED")
        void revoke_fromCreated_transitionsToRevoked() {
            UUID id = UUID.randomUUID();
            UUID revokedBy = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.CREATED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.revoke(id, revokedBy);

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
            verify(invitationRepository).save(invitation);
        }

        @Test
        @DisplayName("revokes SENT invitation")
        void revoke_fromSent_transitionsToRevoked() {
            UUID id = UUID.randomUUID();
            UUID revokedBy = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.SENT);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.revoke(id, revokedBy);

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        }

        @Test
        @DisplayName("revokes OPENED invitation")
        void revoke_fromOpened_transitionsToRevoked() {
            UUID id = UUID.randomUUID();
            UUID revokedBy = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.OPENED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            invitationService.revoke(id, revokedBy);

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        }

        @Test
        @DisplayName("throws when invitation is already expired")
        void revoke_alreadyExpired_throws() {
            UUID id = UUID.randomUUID();
            UUID revokedBy = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.EXPIRED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.revoke(id, revokedBy))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("throws when invitation is already revoked")
        void revoke_alreadyRevoked_throws() {
            UUID id = UUID.randomUUID();
            UUID revokedBy = UUID.randomUUID();
            Invitation invitation = createInvitation(InvitationStatus.REVOKED);
            invitation.setInvitationId(id);
            when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.revoke(id, revokedBy))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }
    }

    // ─── expireOverdue Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("expireOverdue")
    class ExpireOverdueTests {

        @Test
        @DisplayName("transitions overdue invitations to EXPIRED")
        void expireOverdue_withOverdueInvitations_expiresAll() {
            Invitation inv1 = createInvitation(InvitationStatus.CREATED);
            inv1.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            Invitation inv2 = createInvitation(InvitationStatus.SENT);
            inv2.setExpiresAt(Instant.now().minus(2, ChronoUnit.DAYS));

            when(invitationRepository.findExpiredInvitations(any(Instant.class), anyCollection()))
                    .thenReturn(List.of(inv1, inv2));

            int count = invitationService.expireOverdue();

            assertThat(count).isEqualTo(2);
            assertThat(inv1.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
            assertThat(inv2.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
            verify(invitationRepository, times(2)).save(any(Invitation.class));
        }

        @Test
        @DisplayName("returns 0 when no overdue invitations exist")
        void expireOverdue_noOverdue_returnsZero() {
            when(invitationRepository.findExpiredInvitations(any(Instant.class), anyCollection()))
                    .thenReturn(List.of());

            int count = invitationService.expireOverdue();

            assertThat(count).isEqualTo(0);
            verify(invitationRepository, never()).save(any(Invitation.class));
        }
    }

    // ─── Test Helpers ────────────────────────────────────────────────────

    private Invitation createInvitation(InvitationStatus status) {
        Invitation invitation = new Invitation();
        invitation.setInvitationId(UUID.randomUUID());
        invitation.setToken(GENERATED_TOKEN);
        invitation.setType(InvitationType.SINGLE_USE);
        invitation.setStatus(status);
        invitation.setCreatedBy(CREATOR_ID);
        invitation.setCreatedAt(Instant.now());
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setMaxUses(1);
        invitation.setCurrentUses(0);
        return invitation;
    }

    private Invitation createValidInvitation() {
        Invitation invitation = new Invitation();
        invitation.setInvitationId(UUID.randomUUID());
        invitation.setToken(GENERATED_TOKEN);
        invitation.setType(InvitationType.REUSABLE);
        invitation.setStatus(InvitationStatus.SENT);
        invitation.setCreatedBy(CREATOR_ID);
        invitation.setCreatedAt(Instant.now());
        invitation.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
        invitation.setMaxUses(50);
        invitation.setCurrentUses(2);
        return invitation;
    }
}
