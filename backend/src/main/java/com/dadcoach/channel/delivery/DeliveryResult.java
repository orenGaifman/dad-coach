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

    /**
     * Handed to the shared-number gateway, which keeps it until the father is back on Dad Coach: the provider id is
     * the gateway's {@code held:<n>}, not a Meta wamid, and the message has not reached him yet.
     */
    public boolean isHeld() {
        return isSuccessful() && providerMessageId != null
                && providerMessageId.startsWith(com.dadcoach.integration.platform.SharedNumberGate.HELD_PREFIX);
    }

    /** Meta's message id (wamid) of an accepted send - the key of its status receipts; null when held or failed. */
    public String metaMessageId() {
        return isSuccessful() && !isHeld() ? providerMessageId : null;
    }
}
