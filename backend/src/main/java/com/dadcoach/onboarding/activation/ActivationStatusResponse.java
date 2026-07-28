package com.dadcoach.onboarding.activation;

import com.dadcoach.onboarding.provisioning.ActivationStatus;

import java.time.Instant;

/**
 * Response DTO for the activation status endpoint.
 * Returned by the long-polling status endpoint.
 *
 * @param status                the current activation status
 * @param deepLinkGeneratedAt   when the deep link was generated
 * @param linkClickedAt         when the link was clicked (null if not yet)
 * @param messageReceivedAt     when the activation message was received (null if not yet)
 * @param conversationStartedAt when the welcome conversation started (null if not yet)
 * @param retryCount            number of retry attempts so far
 * @param failureReason         reason for failure (null if not failed)
 */
public record ActivationStatusResponse(
    ActivationStatus status,
    Instant deepLinkGeneratedAt,
    Instant linkClickedAt,
    Instant messageReceivedAt,
    Instant conversationStartedAt,
    int retryCount,
    String failureReason
) {

    /**
     * Creates a response from an ActivationRecord entity.
     */
    public static ActivationStatusResponse from(com.dadcoach.onboarding.provisioning.ActivationRecord record) {
        return new ActivationStatusResponse(
                record.getStatus(),
                record.getDeepLinkGeneratedAt(),
                record.getLinkClickedAt(),
                record.getMessageReceivedAt(),
                record.getConversationStartedAt(),
                record.getRetryCount(),
                record.getFailureReason()
        );
    }
}
