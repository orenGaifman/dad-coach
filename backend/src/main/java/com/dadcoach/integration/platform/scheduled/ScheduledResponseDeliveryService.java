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
    private ScheduledPlatformSend platformSend;

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
     * D-046: what the callback answers. {@code body} is the HTTP body - the result itself (today's shape), or, when the
     * platform's Send API has (or may have) the message ({@code MESSAGING_MODE_SCHEDULED=PLATFORM}), {@code {}} with the
     * platform's message id: the platform records the timeline itself. {@code handedOver}: it went out or is on its way
     * (the coach's mentions are noted); today: the answer is DELIVERED.
     */
    public record CallbackAnswer(ScheduledResponseResult result, Object body, boolean handedOver) {
        static CallbackAnswer of(ScheduledResponseResult result) {
            return new CallbackAnswer(result, result, "DELIVERED".equals(result.status()));
        }
    }

    /**
     * @param drafted D-039: the platform's own text for the turn (before ScheduledReplies / ReplyStyleGuard); with
     *                {@code workflow.platform.delivery-reports} on, the answer says what went out compared to it. A
     *                replayed trigger answers as before (what was sent is not stored per trigger).
     */
    public ScheduledResponseResult deliver(Father father, ScheduledResponseRequest request, String idempotencyKey,
                                           String drafted) {
        return deliverForCallback(father, request, idempotencyKey, drafted).result();
    }

    /** {@link #deliver}, with the callback's HTTP body (D-046). */
    public CallbackAnswer deliverForCallback(Father father, ScheduledResponseRequest request, String idempotencyKey,
                                             String drafted) {
        Optional<ScheduledResponseDelivery> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Scheduled response already handled, not sending again: idempotencyKey={}, status={}",
                    idempotencyKey, existing.get().getStatus());
            return CallbackAnswer.of(ScheduledResponseResult.of(existing.get(), true));
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
            return CallbackAnswer.of(ScheduledResponseResult.of(winner, true));
        }

        Optional<String> hebrew = ReplyLanguageGuard.clean(request.responseContent().strip());
        if (hebrew.isEmpty()) {
            // the model wrote its reasoning in English instead of a message - never sent (ReplyLanguageGuard)
            delivery.markFailed("BLOCKED_NOT_HEBREW", clock.instant());
            repository.save(delivery);
            log.warn("Scheduled response blocked, not Hebrew: triggerId={}, targetStateKey={}", request.triggerId(),
                    request.targetStateKey());
            ScheduledResponseResult blocked = ScheduledResponseResult.of(delivery, false);
            return CallbackAnswer.of(reporting() ? blocked.withOutcome(TimelineReports.DROPPED, "BLOCKED_NOT_HEBREW", null)
                    : blocked);
        }
        String content = hebrew.get();
        endpoints.ensure(father); // F1: fathers onboarded on WhatsApp before the fix have no endpoint row yet
        List<OutboundMessageDto.ReplyButton> offered = buttons.forScheduledMessage(father, request.targetStateKey());
        if (platformSend != null && platformSend.sends()) {
            // D-046 PLATFORM: the platform's Send API sends it; today's way only when it provably created nothing
            ScheduledResponseDelivery claimed = delivery;
            return platformSend.deliver(father, delivery, request, content, offered,
                    () -> finish(father, claimed, request, content, offered, drafted, sendDirectly(father, request, content, offered)));
        }
        ProactiveSender.Outcome outcome = sendDirectly(father, request, content, offered);
        CallbackAnswer answer = CallbackAnswer.of(finish(father, delivery, request, content, offered, drafted, outcome));
        if (platformSend != null) {
            platformSend.shadow(father, delivery, request, content, offered, outcome); // D-046 SHADOW only
        }
        return answer;
    }

    /** Today's send: free-form in the window, else the message's own template, else the general one. */
    private ProactiveSender.Outcome sendDirectly(Father father, ScheduledResponseRequest request, String content,
                                                 List<OutboundMessageDto.ReplyButton> offered) {
        return sender.send(father, content, offered,
                () -> templates.forScheduledMessage(father, request.targetStateKey()).orElse(null));
    }

    /** Today's bookkeeping of a send and its answer (D-039 report included). */
    private ScheduledResponseResult finish(Father father, ScheduledResponseDelivery delivery, ScheduledResponseRequest request,
                                           String content, List<OutboundMessageDto.ReplyButton> offered, String drafted,
                                           ProactiveSender.Outcome outcome) {
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

    /** D-046: the platform's Send API for these messages ({@code MESSAGING_MODE_SCHEDULED}); absent = direct only. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPlatformSend(ScheduledPlatformSend platformSend) {
        this.platformSend = platformSend;
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
