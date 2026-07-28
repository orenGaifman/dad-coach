package com.dadcoach.onboarding.activation;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Intercepts inbound messages from fathers in ONBOARDING status and delegates
 * to the {@link ActivationService} for activation handling.
 *
 * <p>Key behavior (Req 4 criteria 9): ANY first message from an ONBOARDING father
 * triggers activation — not just the "🚀 START" pattern. This means the listener
 * does NOT match message content; it only checks the father's status.
 *
 * <p>Integration point: This listener is designed to be called by the
 * ConversationOrchestrator (or WhatsApp webhook pipeline) early in the
 * message processing pipeline, BEFORE standard conversation routing.
 *
 * <p>If the father is in ONBOARDING status, the message is intercepted and
 * routed to the ActivationService. The method returns {@code true} to indicate
 * the message was handled and should NOT continue through the normal pipeline.
 */
@Component
public class ActivationListener {

    private static final Logger log = LoggerFactory.getLogger(ActivationListener.class);

    private final FatherRepository fatherRepository;
    private final ActivationService activationService;

    public ActivationListener(FatherRepository fatherRepository, ActivationService activationService) {
        this.fatherRepository = fatherRepository;
        this.activationService = activationService;
    }

    /**
     * Checks if an inbound message should be intercepted for activation.
     * Returns true if the message was handled (father is ONBOARDING), false otherwise.
     *
     * <p>Call this early in the message processing pipeline:
     * <pre>
     * if (activationListener.interceptIfOnboarding(fatherId, messageContent)) {
     *     return; // Message handled by activation flow
     * }
     * // Continue normal pipeline...
     * </pre>
     *
     * @param fatherId       the father's ID (resolved from phone number)
     * @param messageContent the inbound message content
     * @return true if the message was intercepted for activation, false for normal processing
     */
    public boolean interceptIfOnboarding(Long fatherId, String messageContent) {
        if (fatherId == null) {
            return false;
        }

        Optional<Father> fatherOpt = fatherRepository.findById(fatherId);
        if (fatherOpt.isEmpty()) {
            return false;
        }

        Father father = fatherOpt.get();
        if (father.getStatus() != FatherStatus.ONBOARDING) {
            return false;
        }

        // Father is ONBOARDING — intercept and activate
        log.info("Intercepting message from ONBOARDING father {} for activation", fatherId);

        try {
            activationService.handleActivationMessage(fatherId, messageContent);
        } catch (Exception e) {
            log.error("Error handling activation message for father {}: {}", fatherId, e.getMessage(), e);
            // Don't let activation failure block the message — return true to prevent
            // re-processing, but log the error for investigation
        }

        return true;
    }

    /**
     * Checks if an inbound message from a phone number should be intercepted for activation.
     * Resolves the father by phone number first.
     *
     * @param phoneNumber    the sender's phone number
     * @param messageContent the inbound message content
     * @return true if the message was intercepted for activation, false for normal processing
     */
    public boolean interceptByPhoneIfOnboarding(String phoneNumber, String messageContent) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        Optional<Father> fatherOpt = fatherRepository.findByPhone(phoneNumber);
        if (fatherOpt.isEmpty()) {
            return false;
        }

        return interceptIfOnboarding(fatherOpt.get().getId(), messageContent);
    }
}
