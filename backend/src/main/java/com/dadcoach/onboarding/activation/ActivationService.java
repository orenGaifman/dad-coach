package com.dadcoach.onboarding.activation;

import com.dadcoach.onboarding.provisioning.ActivationRecord;

import java.util.UUID;

/**
 * Orchestrator for the activation flow. Coordinates between domain services
 * (FatherService, SessionWindowService, ConversationEngine) without owning
 * business logic itself.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create and manage activation records</li>
 *   <li>Generate WhatsApp deep links with localized messages</li>
 *   <li>Handle inbound activation messages from ONBOARDING fathers</li>
 *   <li>Manage retries (max 3) with deep link regeneration</li>
 *   <li>Support long-polling for activation status</li>
 * </ul>
 */
public interface ActivationService {

    /**
     * Creates a pending activation record for the given father and session.
     *
     * @param fatherId  the father's ID
     * @param sessionId the onboarding session ID
     * @return the created activation record with PENDING status
     */
    ActivationRecord createPendingActivation(Long fatherId, UUID sessionId);

    /**
     * Marks the activation as link-clicked (father tapped the WhatsApp deep link).
     *
     * @param activationId the activation record ID
     */
    void markLinkClicked(UUID activationId);

    /**
     * Gets the current activation status for a session. Supports long-polling:
     * holds the connection up to 30 seconds waiting for a status change.
     *
     * @param sessionId  the onboarding session ID
     * @param lastStatus the last known status (for change detection); may be null
     * @return the current activation status response
     */
    ActivationStatusResponse getStatus(UUID sessionId, String lastStatus);

    /**
     * Handles an inbound activation message from an ONBOARDING father.
     * Any first message triggers activation, not just the "🚀 START" pattern.
     *
     * <p>Delegates to:
     * <ul>
     *   <li>FatherService.activateFather() for ONBOARDING→ACTIVE transition</li>
     *   <li>SessionWindowService.onInboundMessage() for opening the WhatsApp session window</li>
     *   <li>IntelligenceLayer.generateCoachingResponse() for welcome conversation</li>
     *   <li>DeliveryService.deliver() for sending welcome messages</li>
     * </ul>
     *
     * @param fatherId       the father's ID
     * @param messageContent the inbound message content
     */
    void handleActivationMessage(Long fatherId, String messageContent);

    /**
     * Handles activation timeout. Transitions activation to FAILED with a reason.
     *
     * @param activationId the activation record ID
     */
    void handleActivationTimeout(UUID activationId);

    /**
     * Generates a WhatsApp deep link for the father.
     * Format: https://wa.me/{dad_coach_number}?text={url_encoded_activation_message}
     *
     * @param fatherId the father's ID
     * @param language the father's preferred language ("en" or "he")
     * @return the deep link URL
     */
    String generateDeepLink(Long fatherId, String language);

    /**
     * Retries activation for a father. Max 3 retries allowed.
     * Regenerates the deep link and transitions FAILED→PENDING.
     *
     * @param activationId the activation record ID
     * @return the regenerated deep link
     * @throws IllegalStateException if max retries (3) exceeded or status is not FAILED
     */
    String retryActivation(UUID activationId);
}
