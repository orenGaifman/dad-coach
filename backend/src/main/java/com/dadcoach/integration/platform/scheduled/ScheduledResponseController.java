package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.PlatformUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Receives the Workflow Platform's scheduled-response callback: a scheduled/proactive workflow
 * execution produced a user-facing message (the platform never calls when its agent suppressed
 * output) and Dad Coach delivers it to the father's WhatsApp.
 *
 * <p>Same contract and path as Big Boss's receiver. Authentication is the {@code X-API-Key} checked
 * by {@link ScheduledResponseAuthFilter}; idempotency is {@code X-Idempotency-Key =
 * scheduled-response:{triggerId}}, enforced persistently by {@link ScheduledResponseDeliveryService}.</p>
 */
@RestController
public class ScheduledResponseController {

    private static final Logger log = LoggerFactory.getLogger(ScheduledResponseController.class);

    public static final String PATH = "/api/integration/workflow/scheduled-response";
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_PREFIX = "scheduled-response:";

    private final PlatformUserResolver userResolver;
    private final ScheduledResponseDeliveryService deliveryService;

    public ScheduledResponseController(PlatformUserResolver userResolver, ScheduledResponseDeliveryService deliveryService) {
        this.userResolver = userResolver;
        this.deliveryService = deliveryService;
    }

    @PostMapping(value = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScheduledResponseResult> handle(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKeyHeader,
            @RequestBody ScheduledResponseRequest request) {
        if (request.triggerId() == null || request.triggerId().isBlank()) {
            return badRequest("triggerId is required");
        }
        String expectedKey = IDEMPOTENCY_KEY_PREFIX + request.triggerId();
        if (!expectedKey.equals(idempotencyKeyHeader)) {
            return badRequest(IDEMPOTENCY_KEY_HEADER + " must equal '" + IDEMPOTENCY_KEY_PREFIX + "{triggerId}'");
        }
        if (request.responseContent() == null || request.responseContent().isBlank()) {
            // The platform does not call back for suppressed output; never send an empty message.
            return badRequest("responseContent is required and must not be blank");
        }

        Optional<Father> father = userResolver.resolve(request.userId());
        if (father.isEmpty()) {
            log.atInfo().setMessage("proactive.callback.result").addKeyValue("triggerId", request.triggerId())
                    .addKeyValue("status", "REJECTED").addKeyValue("reason", "UNKNOWN_RECIPIENT").log();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ScheduledResponseResult("REJECTED", "Unknown recipient"));
        }

        ScheduledResponseResult result = deliveryService.deliver(father.get(), request, expectedKey);
        log.atInfo().setMessage("proactive.callback.result")
                .addKeyValue("triggerId", request.triggerId())
                .addKeyValue("targetStateKey", request.targetStateKey())
                .addKeyValue("fatherId", father.get().getId())
                .addKeyValue("status", result.status())
                .log();
        return ResponseEntity.ok(result);
    }

    private static ResponseEntity<ScheduledResponseResult> badRequest(String detail) {
        return ResponseEntity.badRequest().body(new ScheduledResponseResult("REJECTED", detail));
    }
}
