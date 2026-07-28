package com.dadcoach.onboarding.activation;

import com.dadcoach.onboarding.provisioning.ActivationRecord;
import com.dadcoach.onboarding.provisioning.ActivationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that handles activation timeouts.
 * Runs every 15 minutes to transition timed-out activations to FAILED.
 *
 * <p>Timeout rules:
 * <ul>
 *   <li>LINK_CLICKED > 30 minutes → FAILED (user clicked but didn't send message)</li>
 *   <li>PENDING > 24 hours → FAILED (user never clicked the link)</li>
 * </ul>
 */
@Component
public class ActivationTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(ActivationTimeoutJob.class);

    private static final Duration LINK_CLICKED_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration PENDING_TIMEOUT = Duration.ofHours(24);

    private final ActivationRecordRepository activationRepository;
    private final ActivationService activationService;

    public ActivationTimeoutJob(ActivationRecordRepository activationRepository,
                                 ActivationService activationService) {
        this.activationRepository = activationRepository;
        this.activationService = activationService;
    }

    /**
     * Checks for timed-out activations and transitions them to FAILED.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000) // 15 minutes in milliseconds
    @Transactional
    public void handleActivationTimeouts() {
        log.info("ActivationTimeoutJob started");
        try {
            int failedCount = 0;
            Instant now = Instant.now();
            Instant linkClickedCutoff = now.minus(LINK_CLICKED_TIMEOUT);
            Instant pendingCutoff = now.minus(PENDING_TIMEOUT);

            // Find all timed-out activations in a single query
            List<ActivationRecord> timedOut = activationRepository.findTimedOutActivations(
                    linkClickedCutoff, pendingCutoff);

            for (ActivationRecord record : timedOut) {
                try {
                    activationService.handleActivationTimeout(record.getActivationId());
                    failedCount++;
                } catch (Exception e) {
                    log.error("Failed to timeout activation {}: {}",
                            record.getActivationId(), e.getMessage());
                }
            }

            log.info("ActivationTimeoutJob completed: {} activations timed out", failedCount);
        } catch (Exception e) {
            log.error("ActivationTimeoutJob failed: {}", e.getMessage(), e);
        }
    }
}
