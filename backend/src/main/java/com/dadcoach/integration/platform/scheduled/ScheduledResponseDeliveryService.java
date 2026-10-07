package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.ProactiveSender;
import com.dadcoach.common.MaskingUtils;
import com.dadcoach.domain.father.Father;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.whatsapp.buttons.SessionButtonOffers;
import java.util.List;
import java.util.Optional;

/**
 * Delivers a Workflow Platform proactive message to a father over WhatsApp, exactly once per
 * idempotency key, respecting WhatsApp's 24-hour customer-service window.
 *
 * <p>Goes through Dad Coach's channel delivery layer ({@link DeliveryService}): free-form text is
 * sent only while the father's session window is open; when it is closed ({@code SESSION_CLOSED})
 * the configured approved template is sent instead, carrying the generated message as its
 * {@code {{1}}} parameter. With no template configured nothing is sent outside the window - the
 * delivery is recorded as FAILED rather than sending a free-form message WhatsApp would drop.</p>
 *
 * <p>Idempotency is persistent: a row is claimed under the UNIQUE {@code idempotency_key} before
 * sending, so a repeated or concurrent callback for the same trigger replays the recorded outcome
 * instead of sending again.</p>
 */
@Service
public class ScheduledResponseDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledResponseDeliveryService.class);

    private final ScheduledResponseDeliveryRepository repository;
    private final ProactiveSender sender;
    private final com.dadcoach.channel.WhatsAppEndpoints endpoints;
    private final SessionButtonOffers buttons;

    public ScheduledResponseDeliveryService(
            ScheduledResponseDeliveryRepository repository,
            ProactiveSender sender,
            com.dadcoach.channel.WhatsAppEndpoints endpoints,
            SessionButtonOffers buttons) {
        this.endpoints = endpoints;
        this.buttons = buttons;
        this.repository = repository;
        this.sender = sender;
    }

    public ScheduledResponseResult deliver(Father father, ScheduledResponseRequest request, String idempotencyKey) {
        Optional<ScheduledResponseDelivery> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Scheduled response already handled, not sending again: idempotencyKey={}, status={}",
                    idempotencyKey, existing.get().getStatus());
            return ScheduledResponseResult.of(existing.get(), true);
        }

        ScheduledResponseDelivery delivery;
        try {
            delivery = repository.saveAndFlush(new ScheduledResponseDelivery(
                    idempotencyKey, request.triggerId(), request.workflowInstanceId(), father.getId(),
                    request.targetStateKey()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Another request claimed the same key between the lookup and the insert - the UNIQUE
            // constraint picked exactly one winner; report its outcome.
            ScheduledResponseDelivery winner = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> concurrentDuplicate);
            return ScheduledResponseResult.of(winner, true);
        }

        String content = request.responseContent();
        endpoints.ensure(father); // F1: fathers onboarded on WhatsApp before the fix have no endpoint row yet
        List<OutboundMessageDto.ReplyButton> offered = buttons.forScheduledMessage(father, request.targetStateKey());
        ProactiveSender.Outcome outcome = sender.send(father, content, offered);
        DeliveryResult result = outcome.result();
        ScheduledResponseDelivery.Mode mode = outcome.mode() == ProactiveSender.Mode.TEMPLATE
                ? ScheduledResponseDelivery.Mode.TEMPLATE : ScheduledResponseDelivery.Mode.FREE_FORM;

        if (result.isSuccessful()) {
            delivery.markDelivered(mode);
            log.info("Scheduled response delivered: triggerId={}, targetStateKey={}, mode={}, buttons={}, father={}",
                    request.triggerId(), request.targetStateKey(), mode,
                    mode == ScheduledResponseDelivery.Mode.FREE_FORM ? offered.size() : 0, MaskingUtils.maskPhone(father.getPhone()));
        } else {
            delivery.markFailed(result.failureReason());
            log.warn("Scheduled response not delivered: triggerId={}, reason={}", request.triggerId(), result.failureReason());
        }
        repository.save(delivery);
        return ScheduledResponseResult.of(delivery, false);
    }

}
