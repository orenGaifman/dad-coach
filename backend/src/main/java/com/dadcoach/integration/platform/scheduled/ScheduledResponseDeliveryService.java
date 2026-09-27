package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.common.MaskingUtils;
import com.dadcoach.domain.conversation.MessageLogService;
import com.dadcoach.domain.father.Father;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final DeliveryService deliveryService;
    private final MessageLogService messageLogService;
    private final ScheduledResponseCallbackConfig config;

    public ScheduledResponseDeliveryService(
            ScheduledResponseDeliveryRepository repository,
            DeliveryService deliveryService,
            MessageLogService messageLogService,
            ScheduledResponseCallbackConfig config) {
        this.repository = repository;
        this.deliveryService = deliveryService;
        this.messageLogService = messageLogService;
        this.config = config;
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

        UUID fatherUuid = new UUID(0L, father.getId());
        String content = request.responseContent();
        ScheduledResponseDelivery.Mode mode = ScheduledResponseDelivery.Mode.FREE_FORM;
        DeliveryResult result;
        try {
            result = deliveryService.deliver(freeForm(fatherUuid, content));
            if (!result.isSuccessful() && DeliveryService.SESSION_CLOSED.equals(result.failureReason())) {
                if (hasTemplate()) {
                    mode = ScheduledResponseDelivery.Mode.TEMPLATE;
                    result = deliveryService.deliver(template(fatherUuid, config.getTemplateName(), content));
                } else {
                    result = DeliveryResult.failed(DeliveryService.SESSION_CLOSED
                            + ": 24h window closed and no approved template configured; not sent");
                }
            }
        } catch (Exception e) {
            result = DeliveryResult.failed("Delivery error: " + e.getMessage());
        }

        if (result.isSuccessful()) {
            messageLogService.logOutbound(father.getId(), content);
            delivery.markDelivered(mode);
            log.info("Scheduled response delivered: triggerId={}, targetStateKey={}, mode={}, father={}",
                    request.triggerId(), request.targetStateKey(), mode, MaskingUtils.maskPhone(father.getPhone()));
        } else {
            delivery.markFailed(result.failureReason());
            log.warn("Scheduled response not delivered: triggerId={}, reason={}", request.triggerId(), result.failureReason());
        }
        repository.save(delivery);
        return ScheduledResponseResult.of(delivery, false);
    }

    private boolean hasTemplate() {
        return config.getTemplateName() != null && !config.getTemplateName().isBlank();
    }

    private static OutboundMessageDto freeForm(UUID fatherUuid, String content) {
        return new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT, content,
                null, false, null, null, MessagePriority.IMMEDIATE, Instant.now());
    }

    private static OutboundMessageDto template(UUID fatherUuid, String templateName, String content) {
        return new OutboundMessageDto(UUID.randomUUID(), fatherUuid, null, MessageType.TEXT, content,
                null, true, templateName, Map.of("1", asTemplateParameter(content)), MessagePriority.IMMEDIATE, Instant.now());
    }

    /**
     * WhatsApp rejects template text parameters containing newlines, tabs or more than four
     * consecutive spaces; flatten the message into a single line.
     */
    static String asTemplateParameter(String content) {
        return content.replaceAll("\\s*[\\r\\n\\t]+\\s*", " ").replaceAll(" {4,}", "   ").trim();
    }
}
