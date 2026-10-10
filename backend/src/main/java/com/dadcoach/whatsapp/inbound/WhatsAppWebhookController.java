package com.dadcoach.whatsapp.inbound;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.StatusUpdateDto;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.idempotency.IdempotencyService;
import com.dadcoach.whatsapp.DeliveryReceipts;
import com.dadcoach.whatsapp.WhatsAppMessageParser;
import com.dadcoach.whatsapp.WhatsAppSignatureVerifier;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /webhook/whatsapp} (the path configured at Meta for Dad Coach's own number): GET is Meta's verification
 * handshake; POST is verified by X-Hub-Signature-256 over the raw body (constant time) and answered 200 at once —
 * the turn runs on the per-sender executor.
 *
 * <p>Meta's retries are deduplicated durably on its message id (survives a restart). D-038 (DC-B3): the id is claimed
 * IN_PROGRESS when the message arrives and marked done only once it was answered; when the turn could not be answered
 * (the platform unavailable, the reply refused by Meta, an error) the claim is released, so Meta's redelivery is
 * processed again. A message answered, or being processed right now, is dropped, never answered twice. A claim older
 * than the lease (its worker died) is taken over. {@code dad-coach.whatsapp.inbound.retry-unanswered=false} restores the
 * old marking on arrival.
 *
 * <p>D-038 (DC-B2): status receipts (sent / delivered / read / failed) update the scheduled message or dashboard link
 * they belong to ({@link DeliveryReceipts}); {@code dad-coach.whatsapp.receipts.enabled=false} ignores them again.
 */
@RestController
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    static final String DEDUP_SCOPE = "WHATSAPP_INBOUND";

    private final WhatsAppProperties properties;
    private final WhatsAppSignatureVerifier signatures;
    private final WhatsAppMessageParser parser;
    private final IdempotencyService idempotency;
    private final InboundTurnExecutor executor;
    private final InboundMessageHandler handler;
    private final ObjectMapper json;
    private final Clock clock;
    private final DeliveryReceipts receipts;
    private final boolean receiptsEnabled;
    private final boolean retryUnanswered;
    private final Duration claimLease;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppSignatureVerifier signatures,
                                     WhatsAppMessageParser parser, IdempotencyService idempotency,
                                     InboundTurnExecutor executor, InboundMessageHandler handler, ObjectMapper json,
                                     Clock clock, DeliveryReceipts receipts,
                                     @Value("${dad-coach.whatsapp.receipts.enabled:true}") boolean receiptsEnabled,
                                     @Value("${dad-coach.whatsapp.inbound.retry-unanswered:true}") boolean retryUnanswered,
                                     @Value("${dad-coach.whatsapp.inbound.claim-lease:PT10M}") Duration claimLease) {
        this.receipts = receipts;
        this.receiptsEnabled = receiptsEnabled;
        this.retryUnanswered = retryUnanswered;
        this.claimLease = claimLease;
        this.properties = properties;
        this.signatures = signatures;
        this.parser = parser;
        this.idempotency = idempotency;
        this.executor = executor;
        this.handler = handler;
        this.json = json;
        this.clock = clock;
    }

    @GetMapping("/webhook/whatsapp")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = properties.verifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && expected.equals(token) && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
    }

    @PostMapping("/webhook/whatsapp")
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                        @RequestBody byte[] rawBody) {
        if (!signatures.isValid(rawBody, signature, properties.webhookSecret())) {
            log.atWarn().setMessage("whatsapp.webhook.bad_signature").log();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WhatsAppWebhookPayload payload;
        try {
            payload = json.readValue(rawBody, WhatsAppWebhookPayload.class);
        } catch (Exception e) {
            log.atWarn().setMessage("whatsapp.webhook.unreadable").log();
            return ResponseEntity.ok().build();
        }
        Instant receivedAt = clock.instant();
        WhatsAppMessageParser.ParseResult parsed = parser.parse(payload);
        for (InboundMessageDto in : parsed.messages()) {
            String key = in.idempotencyKey();
            if (!retryUnanswered) {
                if (!idempotency.firstTime(DEDUP_SCOPE, key)) {
                    log.atInfo().setMessage("whatsapp.inbound.duplicate").addKeyValue("messageId", key).log();
                    continue;
                }
                executor.submit(in.fatherChannelIdentity(), () -> handler.handle(in, receivedAt));
                continue;
            }
            IdempotencyService.Claim claim = idempotency.claim(DEDUP_SCOPE, key, claimLease);
            if (claim != IdempotencyService.Claim.CLAIMED) {
                log.atInfo().setMessage("whatsapp.inbound.duplicate").addKeyValue("messageId", key)
                        .addKeyValue("state", claim).log();
                continue;
            }
            // claimed here, in arrival order; the turn still runs one at a time per sender
            executor.submit(in.fatherChannelIdentity(), () -> answer(in, receivedAt));
        }
        for (StatusUpdateDto receipt : parsed.statusUpdates()) {
            if (!receiptsEnabled) {
                log.atDebug().setMessage("whatsapp.receipts.ignored").addKeyValue("count", parsed.statusUpdates().size()).log();
                break;
            }
            try {
                receipts.apply(receipt);
            } catch (RuntimeException e) {
                // Meta always gets its 200; a lost receipt costs the status, never the webhook
                log.atWarn().setMessage("whatsapp.receipt.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Runs the claimed message; done only when it was answered - otherwise the claim goes, for the redelivery. */
    private void answer(InboundMessageDto in, Instant receivedAt) {
        InboundMessageHandler.Outcome outcome = InboundMessageHandler.Outcome.UNANSWERED;
        try {
            outcome = handler.handle(in, receivedAt);
        } finally {
            settle(in.idempotencyKey(), outcome);
        }
    }

    private void settle(String key, InboundMessageHandler.Outcome outcome) {
        try {
            if (outcome == InboundMessageHandler.Outcome.HANDLED) {
                idempotency.complete(DEDUP_SCOPE, key, true, 200, null);
            } else {
                idempotency.release(DEDUP_SCOPE, key);
                log.atWarn().setMessage("whatsapp.inbound.released").addKeyValue("messageId", key).log();
            }
        } catch (RuntimeException e) {
            // the claim stays IN_PROGRESS and is taken over after the lease
            log.atWarn().setMessage("whatsapp.inbound.settle_failed").addKeyValue("messageId", key)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }
}
