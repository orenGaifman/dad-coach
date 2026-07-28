package com.dadcoach.onboarding.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that expires overdue invitations.
 * Runs daily at 02:00 UTC to transition invitations past their expires_at to EXPIRED status.
 */
@Component
public class InvitationExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(InvitationExpirationJob.class);

    private final InvitationService invitationService;

    public InvitationExpirationJob(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /**
     * Batch transitions all non-terminal invitations past their expiration to EXPIRED.
     * Runs daily at 02:00 UTC (off-peak) to avoid lock contention with active flows.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void expireOverdueInvitations() {
        log.info("InvitationExpirationJob started");
        try {
            int expired = invitationService.expireOverdue();
            log.info("InvitationExpirationJob completed: {} invitations expired", expired);
        } catch (Exception e) {
            log.error("InvitationExpirationJob failed: {}", e.getMessage(), e);
        }
    }
}
