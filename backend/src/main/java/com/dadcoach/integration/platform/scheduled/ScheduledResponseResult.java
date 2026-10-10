package com.dadcoach.integration.platform.scheduled;

/**
 * Outcome returned to the platform (HTTP 200 once a delivery attempt has been recorded). The wire vocabulary is
 * unchanged by DC-B2: a message handed over for delivery (ACCEPTED, HELD, or moved on by Meta's receipts to SENT /
 * DELIVERED / READ) answers {@code DELIVERED}; SENDING and FAILED answer as themselves.
 */
public record ScheduledResponseResult(String status, String detail) {

    static ScheduledResponseResult of(ScheduledResponseDelivery delivery, boolean replayed) {
        ScheduledResponseDelivery.Status status = delivery.getStatus();
        String detail = switch (status) {
            case SENDING -> "Delivery already in progress";
            case FAILED -> delivery.getFailureReason();
            case HELD -> replayed ? "Already held by the shared number gateway" : "Held by the shared number gateway";
            case ACCEPTED, SENT, DELIVERED, READ -> replayed ? "Already delivered" : "Delivered";
        };
        return new ScheduledResponseResult(status.handedOver() ? "DELIVERED" : status.name(), detail);
    }
}
