package com.dadcoach.integration.channel;

import com.dadcoach.auth.LoginLinkPlatform;
import com.dadcoach.idempotency.IdempotencyService;
import com.dadcoach.integration.platform.messaging.MessagingSettings;
import com.dadcoach.integration.platform.messaging.PlatformSendFlow;
import com.dadcoach.integration.platform.scheduled.ScheduledPlatformSend;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * D-045 (Unified Workflow Phase 6.3-DC): the platform reports the status of each message it sends for Dad Coach
 * (platform spec PHASE6_2_SEND_API_SPEC §3.5 / §8.2). Auth: the route's report key - by default its claim key, which is
 * Dad Coach's callback key; the {@code /api/integration/channel/**} chain opens with it always (as for
 * {@code /held-outcome}, D-042).
 *
 * <p>Contract: body {@code {messageId, idempotencyKey, workerKey, kind, status, providerMessageId, reason, errorCode,
 * channelWindow, template, heldId, retryOf, resendable, at{...}, reportSeq}} (no phone, no text), header
 * {@code X-Idempotency-Key: delivery:<messageId>:<status>}. Each report is the message's full current state; it is
 * applied forward only (an older one changes nothing). Answers: 200 {@code {applied, known}} (a key no row holds is
 * {@code known:false} - never retried); the same key again 200 {@code duplicate}; 400 for a request wrong in itself
 * (the platform gives up); 404 while {@code MESSAGING_DELIVERY_STATUS_ENABLED} is off and 409 while the same key is
 * being applied (the platform retries both, for 24 h).</p>
 *
 * <p>D-046: by {@code kind} - {@code LOGIN_LINK} to {@code login_link}, {@code SCHEDULED} to
 * {@code scheduled_response_delivery}; no kind: the link first, then the scheduled message; any other kind (e.g.
 * {@code BELT_PROMOTION}, which keeps no ledger) is {@code known:false}. D-048: so are the conversational lines
 * ({@code REPLY}, {@code FIXED_LINE}, {@code BUTTON_REPLY}, {@code READY_ANSWER}): no ledger, the sync answer decided
 * whether the message was answered.</p>
 */
@RestController
public class DeliveryStatusController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusController.class);

    public static final String PATH = "/api/integration/channel/delivery-status";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    static final String KEY_PREFIX = "delivery:";
    static final String SCOPE = "PLATFORM_DELIVERY_STATUS";
    static final Duration LEASE = Duration.ofMinutes(2);

    private final MessagingSettings settings;
    private final LoginLinkPlatform loginLinks;
    private final ScheduledPlatformSend scheduled;
    private final IdempotencyService idempotency;

    public DeliveryStatusController(MessagingSettings settings, LoginLinkPlatform loginLinks, ScheduledPlatformSend scheduled,
                                    IdempotencyService idempotency) {
        this.settings = settings;
        this.loginLinks = loginLinks;
        this.scheduled = scheduled;
        this.idempotency = idempotency;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, Object>> report(@RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key,
                                                      @RequestBody(required = false) JsonNode report) {
        if (!settings.isDeliveryStatusEnabled()) {
            return answer(HttpStatus.NOT_FOUND, "DISABLED");
        }
        if (report == null || !report.isObject() || !report.hasNonNull("status")
                || !report.hasNonNull("messageId") && !report.hasNonNull("idempotencyKey")) {
            return answer(HttpStatus.BAD_REQUEST, "status and messageId or idempotencyKey are required");
        }
        String messageId = report.hasNonNull("messageId") ? report.get("messageId").asText() : null;
        if (key == null || !key.startsWith(KEY_PREFIX) || key.length() > 200
                || messageId != null && !key.startsWith(KEY_PREFIX + messageId + ":")) {
            return answer(HttpStatus.BAD_REQUEST, IDEMPOTENCY_HEADER + " must be '" + KEY_PREFIX + "<messageId>:<status>'");
        }
        IdempotencyService.Claim claim = idempotency.claim(SCOPE, key, LEASE);
        if (claim == IdempotencyService.Claim.DONE) {
            Map<String, Object> body = body(false, null);
            body.put("duplicate", true);
            return ResponseEntity.ok(body);
        }
        if (claim == IdempotencyService.Claim.BUSY) {
            return answer(HttpStatus.CONFLICT, "IN_PROGRESS");
        }
        PlatformSendFlow.Applied applied;
        try {
            String kind = report.hasNonNull("kind") ? report.get("kind").asText() : null;
            String idempotencyKey = report.hasNonNull("idempotencyKey") ? report.get("idempotencyKey").asText() : null;
            applied = new PlatformSendFlow.Applied(false, false);
            if (kind == null || MessagingSettings.LOGIN_LINK.equals(kind)) {
                applied = loginLinks.applyReport(idempotencyKey, messageId, report);
            }
            if (!applied.known() && (kind == null || MessagingSettings.SCHEDULED.equals(kind))) {
                applied = scheduled.applyReport(idempotencyKey, messageId, report);
            }
        } catch (RuntimeException e) {
            idempotency.release(SCOPE, key); // not applied: the platform's retry (same key) runs it again
            throw e;
        }
        log.atInfo().setMessage("messaging.delivery_status").addKeyValue("messageId", messageId)
                .addKeyValue("status", report.get("status").asText()).addKeyValue("known", applied.known())
                .addKeyValue("applied", applied.moved()).log();
        if (!applied.known()) {
            // D-048 review: no ledger row holds the key (a conversational line, the belt promotion) - its fate is only
            // here, e.g. a reply FAILED after Dad Coach's 5 s wait ended
            log.atInfo().setMessage("messaging.delivery_status.unmatched").addKeyValue("messageId", messageId)
                    .addKeyValue("kind", report.hasNonNull("kind") ? report.get("kind").asText() : null)
                    .addKeyValue("idempotencyKey", report.hasNonNull("idempotencyKey")
                            ? report.get("idempotencyKey").asText() : null)
                    .addKeyValue("status", report.get("status").asText())
                    .addKeyValue("reason", report.hasNonNull("reason") ? report.get("reason").asText() : null)
                    .addKeyValue("resendable", report.hasNonNull("resendable") ? report.get("resendable").asBoolean() : null)
                    .log();
        }
        Map<String, Object> body = body(applied.moved(), null);
        body.put("known", applied.known());
        idempotency.complete(SCOPE, key, true, 200, body.toString());
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> answer(HttpStatus status, String reason) {
        return ResponseEntity.status(status).body(body(false, reason));
    }

    private static Map<String, Object> body(boolean applied, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applied", applied);
        if (reason != null) {
            body.put("reason", reason);
        }
        return body;
    }
}
