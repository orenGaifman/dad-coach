package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.integration.platform.timeline.TimelineReports;
import com.dadcoach.integration.platform.timeline.TimelineText;
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
import java.time.Clock;
import java.util.List;
import com.dadcoach.whatsapp.ReplyLanguageGuard;
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
    private final ScheduledMessageTemplates templates;
    private final Clock clock;
    private final com.dadcoach.integration.platform.WorkflowPlatformProperties platformProperties;

    public ScheduledResponseDeliveryService(
            ScheduledResponseDeliveryRepository repository,
            ProactiveSender sender,
            com.dadcoach.channel.WhatsAppEndpoints endpoints,
            SessionButtonOffers buttons,
            ScheduledMessageTemplates templates,
            Clock clock,
            com.dadcoach.integration.platform.WorkflowPlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
        this.clock = clock;
        this.endpoints = endpoints;
        this.buttons = buttons;
        this.templates = templates;
        this.repository = repository;
        this.sender = sender;
    }

    public ScheduledResponseResult deliver(Father father, ScheduledResponseRequest request, String idempotencyKey) {
        return deliver(father, request, idempotencyKey, request.responseContent());
    }

    /**
     * @param drafted D-039: the platform's own text for the turn (before ScheduledReplies / ReplyStyleGuard); with
     *                {@code workflow.platform.delivery-reports} on, the answer says what went out compared to it. A
     *                replayed trigger answers as before (what was sent is not stored per trigger).
     */
    public ScheduledResponseResult deliver(Father father, ScheduledResponseRequest request, String idempotencyKey,
                                           String drafted) {
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
                    request.targetStateKey(), clock.instant()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Another request claimed the same key between the lookup and the insert - the UNIQUE
            // constraint picked exactly one winner; report its outcome.
            ScheduledResponseDelivery winner = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> concurrentDuplicate);
            return ScheduledResponseResult.of(winner, true);
        }

        Optional<String> hebrew = ReplyLanguageGuard.clean(request.responseContent().strip());
        if (hebrew.isEmpty()) {
            // the model wrote its reasoning in English instead of a message - never sent (ReplyLanguageGuard)
            delivery.markFailed("BLOCKED_NOT_HEBREW", clock.instant());
            repository.save(delivery);
            log.warn("Scheduled response blocked, not Hebrew: triggerId={}, targetStateKey={}", request.triggerId(),
                    request.targetStateKey());
            ScheduledResponseResult blocked = ScheduledResponseResult.of(delivery, false);
            return reporting() ? blocked.withOutcome(TimelineReports.DROPPED, "BLOCKED_NOT_HEBREW", null) : blocked;
        }
        String content = hebrew.get();
        endpoints.ensure(father); // F1: fathers onboarded on WhatsApp before the fix have no endpoint row yet
        List<OutboundMessageDto.ReplyButton> offered = buttons.forScheduledMessage(father, request.targetStateKey());
        ProactiveSender.Outcome outcome = sender.send(father, content, offered,
                () -> templates.forScheduledMessage(father, request.targetStateKey()).orElse(null));
        DeliveryResult result = outcome.result();
        ScheduledResponseDelivery.Mode mode = outcome.mode() == ProactiveSender.Mode.TEMPLATE
                ? ScheduledResponseDelivery.Mode.TEMPLATE : ScheduledResponseDelivery.Mode.FREE_FORM;

        if (result.isSuccessful()) {
            // DC-B1/B2: ACCEPTED with Meta's wamid (receipts move it on), or HELD by the shared-number gateway
            delivery.markAccepted(mode, result, clock.instant());
            log.info("Scheduled response handed over: triggerId={}, targetStateKey={}, status={}, mode={}, template={}, buttons={}, father={}",
                    request.triggerId(), request.targetStateKey(), delivery.getStatus(), mode, outcome.template(),
                    mode == ScheduledResponseDelivery.Mode.FREE_FORM ? offered.size() : 0, MaskingUtils.maskPhone(father.getPhone()));
        } else {
            delivery.markFailed(result.failureReason(), clock.instant());
            log.warn("Scheduled response not delivered: triggerId={}, reason={}", request.triggerId(), result.failureReason());
        }
        repository.save(delivery);
        ScheduledResponseResult answer = ScheduledResponseResult.of(delivery, false);
        return reporting() ? report(answer, outcome, content, drafted) : answer;
    }

    private boolean reporting() {
        return platformProperties.isDeliveryReports();
    }

    /**
     * D-039: AS_IS when the text went out free-form exactly as the platform wrote it (identity line aside) and alone;
     * otherwise what was sent (the ready message, the cleaned text, the template as he reads it, the buttons with it).
     * A failed send is FAILED with its reason.
     */
    private static ScheduledResponseResult report(ScheduledResponseResult answer, ProactiveSender.Outcome outcome,
                                                  String content, String drafted) {
        DeliveryResult result = outcome.result();
        if (!result.isSuccessful()) {
            return answer.withOutcome(TimelineReports.FAILED, result.failureReason(), null);
        }
        String sent = outcome.sentText() != null ? outcome.sentText() : content;
        boolean template = outcome.mode() == ProactiveSender.Mode.TEMPLATE;
        List<java.util.Map<String, Object>> buttons = TimelineText.buttons(outcome.buttons());
        if (!template && buttons.isEmpty() && TimelineText.sameAsDraft(sent, drafted)) {
            return answer.withOutcome(TimelineReports.AS_IS, null, result);
        }
        return answer.withDelivered(TimelineText.withoutIdentity(sent), result,
                template ? TimelineText.template(outcome.template(), outcome.templateParams()) : null, buttons,
                TimelineReports.KIND_SCHEDULED);
    }

}
