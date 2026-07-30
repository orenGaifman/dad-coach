package com.dadcoach.onboarding.invitation;

import com.dadcoach.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link InvitationService} managing invitation lifecycle.
 *
 * <p>Enforces expiration policies (SINGLE_USE=7 days, REUSABLE=90 days per Req 1 criteria 5),
 * validates tokens against 4 conditions (Req 1 criteria 7), and handles state transitions
 * following the invitation state machine.
 *
 * @see InvitationStatus
 * @see InvitationType
 */
@Service
@Transactional
public class InvitationServiceImpl implements InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);

    private static final Set<InvitationStatus> TERMINAL_STATUSES =
            Set.of(InvitationStatus.EXPIRED, InvitationStatus.REVOKED);

    private final InvitationRepository invitationRepository;
    private final InvitationTokenGenerator tokenGenerator;

    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                 InvitationTokenGenerator tokenGenerator) {
        this.invitationRepository = invitationRepository;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Invitation create(InvitationCreateRequest request, UUID createdBy) {
        if (request == null) {
            throw new IllegalArgumentException("InvitationCreateRequest must not be null");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("InvitationType must not be null");
        }
        if (createdBy == null) {
            throw new IllegalArgumentException("createdBy must not be null");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(request.type().getExpirationDays(), ChronoUnit.DAYS);

        Invitation invitation = new Invitation();
        invitation.setToken(tokenGenerator.generateToken());
        invitation.setType(request.type());
        invitation.setStatus(InvitationStatus.SENT);
        invitation.setCreatedBy(createdBy);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(expiresAt);
        invitation.setMaxUses(request.resolveMaxUses());
        invitation.setCurrentUses(0);
        invitation.setMetadata(request.metadata());

        Invitation saved = invitationRepository.save(invitation);
        log.info("Created {} invitation [id={}] expiring at {}",
                request.type(), saved.getInvitationId(), expiresAt);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationValidationResult validate(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            return InvitationValidationResult.notFound();
        }

        // Condition (a): token exists
        var optionalInvitation = invitationRepository.findByToken(token);
        if (optionalInvitation.isEmpty()) {
            log.debug("Invitation validation failed: token not found [ip={}]", clientIp);
            return InvitationValidationResult.notFound();
        }

        Invitation invitation = optionalInvitation.get();

        // Condition (b): status is not terminal (not EXPIRED or REVOKED)
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            log.debug("Invitation validation failed: revoked [id={}, ip={}]",
                    invitation.getInvitationId(), clientIp);
            return InvitationValidationResult.revoked();
        }

        if (invitation.getStatus() == InvitationStatus.EXPIRED) {
            log.debug("Invitation validation failed: expired status [id={}, ip={}]",
                    invitation.getInvitationId(), clientIp);
            return InvitationValidationResult.expired();
        }

        // Condition (d): expires_at > now (check before uses — expired invitations can't be used)
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            log.debug("Invitation validation failed: past expiration [id={}, expires_at={}, ip={}]",
                    invitation.getInvitationId(), invitation.getExpiresAt(), clientIp);
            return InvitationValidationResult.expired();
        }

        // Condition (c): current_uses < max_uses
        if (invitation.getCurrentUses() >= invitation.getMaxUses()) {
            log.debug("Invitation validation failed: exhausted [id={}, uses={}/{}, ip={}]",
                    invitation.getInvitationId(), invitation.getCurrentUses(),
                    invitation.getMaxUses(), clientIp);
            return InvitationValidationResult.exhausted();
        }

        log.debug("Invitation validation succeeded [id={}, ip={}]",
                invitation.getInvitationId(), clientIp);
        return InvitationValidationResult.valid(invitation);
    }

    @Override
    public void markOpened(UUID invitationId) {
        Invitation invitation = findByIdOrThrow(invitationId);
        transitionStatus(invitation, InvitationStatus.OPENED);
        invitationRepository.save(invitation);
        log.info("Invitation marked as opened [id={}]", invitationId);
    }

    @Override
    public void incrementUses(UUID invitationId) {
        Invitation invitation = findByIdOrThrow(invitationId);

        if (invitation.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot increment uses on invitation in terminal state: " + invitation.getStatus());
        }

        invitation.setCurrentUses(invitation.getCurrentUses() + 1);

        // Transition to USED if uses are exhausted
        if (invitation.getCurrentUses() >= invitation.getMaxUses()) {
            transitionStatus(invitation, InvitationStatus.USED);
        } else if (invitation.getStatus() != InvitationStatus.USED) {
            // For REUSABLE invitations already in USED state, keep USED
            // For first use, transition to USED
            transitionStatus(invitation, InvitationStatus.USED);
        }

        invitationRepository.save(invitation);
        log.info("Invitation uses incremented [id={}, uses={}/{}]",
                invitationId, invitation.getCurrentUses(), invitation.getMaxUses());
    }

    @Override
    public void revoke(UUID invitationId, UUID revokedBy) {
        Invitation invitation = findByIdOrThrow(invitationId);

        if (invitation.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot revoke invitation in terminal state: " + invitation.getStatus());
        }

        transitionStatus(invitation, InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        log.info("Invitation revoked [id={}, revokedBy={}]", invitationId, revokedBy);
    }

    @Override
    public int expireOverdue() {
        Instant now = Instant.now();
        List<Invitation> overdueInvitations =
                invitationRepository.findExpiredInvitations(now, TERMINAL_STATUSES);

        int count = 0;
        for (Invitation invitation : overdueInvitations) {
            if (invitation.getStatus().canTransitionTo(InvitationStatus.EXPIRED)) {
                invitation.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invitation);
                count++;
            }
        }

        if (count > 0) {
            log.info("Expired {} overdue invitations", count);
        }
        return count;
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    @Override
    public String getTokenById(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .map(Invitation::getToken)
                .orElse("");
    }

    private Invitation findByIdOrThrow(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
    }

    private void transitionStatus(Invitation invitation, InvitationStatus targetStatus) {
        InvitationStatus currentStatus = invitation.getStatus();
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(String.format(
                    "Invalid invitation status transition: %s → %s [id=%s]",
                    currentStatus, targetStatus, invitation.getInvitationId()));
        }
        invitation.setStatus(targetStatus);
    }
}
