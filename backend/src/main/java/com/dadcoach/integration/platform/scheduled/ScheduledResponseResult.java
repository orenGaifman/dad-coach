package com.dadcoach.integration.platform.scheduled;

/** Outcome returned to the platform (HTTP 200 once a delivery attempt has been recorded). */
public record ScheduledResponseResult(String status, String detail) {

    static ScheduledResponseResult of(ScheduledResponseDelivery delivery, boolean replayed) {
        String detail = switch (delivery.getStatus()) {
            case DELIVERED -> replayed ? "Already delivered" : "Delivered";
            case SENDING -> "Delivery already in progress";
            case FAILED -> delivery.getFailureReason();
        };
        return new ScheduledResponseResult(delivery.getStatus().name(), detail);
    }
}
