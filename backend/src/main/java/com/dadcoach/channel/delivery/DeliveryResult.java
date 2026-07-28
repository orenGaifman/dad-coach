package com.dadcoach.channel.delivery;

/**
 * Result of an outbound message delivery attempt.
 *
 * @param status            the delivery status after the attempt
 * @param providerMessageId the provider-assigned message identifier (null if delivery failed before reaching provider)
 * @param failureReason     human-readable failure reason (null on success)
 */
public record DeliveryResult(
    DeliveryStatus status,
    String providerMessageId,
    String failureReason
) {

    /**
     * Creates a successful delivery result.
     */
    public static DeliveryResult sent(String providerMessageId) {
        return new DeliveryResult(DeliveryStatus.SENT, providerMessageId, null);
    }

    /**
     * Creates a failed delivery result.
     */
    public static DeliveryResult failed(String reason) {
        return new DeliveryResult(DeliveryStatus.FAILED, null, reason);
    }

    /**
     * Creates a rejected delivery result (e.g., session closed, unsupported type).
     */
    public static DeliveryResult rejected(String reason) {
        return new DeliveryResult(DeliveryStatus.FAILED, null, reason);
    }

    public boolean isSuccessful() {
        return status == DeliveryStatus.SENT;
    }
}
