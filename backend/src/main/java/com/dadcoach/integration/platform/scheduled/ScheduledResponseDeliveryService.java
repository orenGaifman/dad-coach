package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.integration.platform.timeline.TimelineReports;
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
 * Delivers a Workflow Platform proactive message to a father over WhatsApp, exactly once per idempotency key.
 *
 * <p>Since D-053 (Unified Workflow 6.4) the platform's Send API is the only sender ({@link ScheduledPlatformSend}): it
 * decides free-form inside the 24-hour window or the approved template outside it (its kind→template mapping), holds
 * on the shared number, and reports what became of the message. Dad Coach decides what is said and whether.</p>
 *
 * <p>Idempotency is persistent: a row is claimed under the UNIQUE {@code idempotency_key} before sending, so a repeated
 * or concurrent callback for the same trigger replays the recorded outcome instead of sending again.</p>
 */
@Service
public class ScheduledResponseDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledResponseDeliveryService.class);

    private final ScheduledResponseDeliveryRepository repository;
    private final com.dadcoach.channel.WhatsAppEndpoints endpoints;
    private final SessionButtonOffers buttons;
    private final Clock clock;
    private final com.dadcoach.integration.platform.WorkflowPlatformProperties platformProperties;
    private final ScheduledPlatformSend platformSend;
    private final SessionTimerCard timerCards;

    public ScheduledResponseDeliveryService(
            ScheduledResponseDeliveryRepository repository,
            com.dadcoach.channel.WhatsAppEndpoints endpoints,
            SessionButtonOffers buttons,
            Clock clock,
            com.dadcoach.integration.platform.WorkflowPlatformProperties platformProperties,
            ScheduledPlatformSend platformSend,
            SessionTimerCard timerCards) {
        this.platformProperties = platformProperties;
        this.clock = clock;
        this.endpoints = endpoints;
        this.buttons = buttons;
        this.repository = repository;
        this.platformSend = platformSend;
        this.timerCards = timerCards;
    }

    public ScheduledResponseResult deliver(Father father, ScheduledResponseRequest request, String idempotencyKey) {
        return deliver(father, request, idempotencyKey, request.responseContent());
    }

    /**
     * D-046: what the callback answers. {@code body} is the HTTP body - the result itself (a replay, a blocked or refused
     * message), or, when the platform's Send API has (or may have) the message, {@code {}} with the platform's message id:
     * the platform records the timeline itself. {@code handedOver}: it went out or is on its way (the coach's mentions
     * are noted).
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
        return deliverForCallback(father, request, idempotencyKey, drafted, List.of());
    }

    /**
     * @param morning D-058: the sessions a morning reminder names - claimed for today right before it is sent; nothing is
     *                sent when every one was already claimed today, and the claim is given back when the message was
     *                provably not handed over. Empty for any other message.
     */
    public CallbackAnswer deliverForCallback(Father father, ScheduledResponseRequest request, String idempotencyKey,
                                             String drafted, List<java.util.UUID> morning) {
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
        Optional<SessionTimerCard.MorningClaim> claim = Optional.empty();
        if (!morning.isEmpty()) {
            claim = timerCards.claimMorning(father, morning);
            if (claim.isEmpty()) {
                // D-058: another morning reminder of today already named these sessions - he gets one
                delivery.markFailed(SessionTimerCard.ALREADY_REMINDED_TODAY, clock.instant());
                repository.save(delivery);
                log.info("Morning reminder already sent today, not sending again: triggerId={}", request.triggerId());
                ScheduledResponseResult skipped = ScheduledResponseResult.of(delivery, false);
                return CallbackAnswer.of(reporting()
                        ? skipped.withOutcome(TimelineReports.DROPPED, SessionTimerCard.ALREADY_REMINDED_TODAY, null)
                        : skipped);
            }
        }
        CallbackAnswer answer = platformSend.deliver(father, delivery, request, content, offered);
        if (!answer.handedOver()) {
            claim.ifPresent(timerCards::releaseMorning);
        }
        return answer;
    }

    private boolean reporting() {
        return platformProperties.isDeliveryReports();
    }
}
