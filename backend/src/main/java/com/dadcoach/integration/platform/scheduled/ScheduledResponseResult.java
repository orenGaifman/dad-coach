package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Outcome returned to the platform (HTTP 200 once a delivery attempt has been recorded). The wire vocabulary is
 * unchanged by DC-B2: a message handed over for delivery (ACCEPTED, HELD, or moved on by Meta's receipts to SENT /
 * DELIVERED / READ) answers {@code DELIVERED}; SENDING and FAILED answer as themselves.
 *
 * <p>D-039 (Phase 3.4, {@code workflow.platform.delivery-reports} on): the answer also says what the father got - the
 * platform applies it to the scheduled turn (implementation spec §3.2 (5)): {@code outcome} AS_IS / DROPPED / FAILED,
 * or {@code deliveredContent} (what was sent instead of the turn's text) with its template / buttons / kind; the
 * provider message id and delivery status of the send. Absent fields are never written, so with the switch off the
 * body is exactly {@code {status, detail}}.</p>
 */
public record ScheduledResponseResult(
        String status,
        String detail,
        @JsonInclude(JsonInclude.Include.NON_NULL) String outcome,
        @JsonInclude(JsonInclude.Include.NON_NULL) String deliveredContent,
        @JsonInclude(JsonInclude.Include.NON_NULL) String providerMessageId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String deliveryStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> template,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> buttons,
        @JsonInclude(JsonInclude.Include.NON_NULL) String kind) {

    public ScheduledResponseResult(String status, String detail) {
        this(status, detail, null, null, null, null, null, null, null, null);
    }

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

    /** D-039: the turn's text went out as written (AS_IS), was not sent (DROPPED) or could not be (FAILED). */
    ScheduledResponseResult withOutcome(String outcome, String reason, DeliveryResult sent) {
        return new ScheduledResponseResult(status, detail, outcome, null, providerId(sent), deliveryStatus(sent), reason,
                null, null, null);
    }

    /** D-039: something else went out instead of the turn's text (a ready message, a template, buttons added). */
    ScheduledResponseResult withDelivered(String content, DeliveryResult sent, Map<String, Object> template,
                                          List<Map<String, Object>> buttons, String kind) {
        return new ScheduledResponseResult(status, detail, null, content, providerId(sent), deliveryStatus(sent), null,
                template, buttons == null || buttons.isEmpty() ? null : buttons, kind);
    }

    private static String providerId(DeliveryResult sent) {
        return sent != null && sent.isSuccessful() ? sent.providerMessageId() : null;
    }

    private static String deliveryStatus(DeliveryResult sent) {
        return sent == null || !sent.isSuccessful() ? null : sent.isHeld() ? "HELD" : "ACCEPTED";
    }
}
