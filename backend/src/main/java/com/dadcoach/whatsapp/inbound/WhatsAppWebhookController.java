package com.dadcoach.whatsapp.inbound;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.idempotency.IdempotencyService;
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
 * <p>D-053 (Unified Workflow 6.4): Meta's status receipts (sent / delivered / read / failed) are the platform's - it
 * sends every message and reports each one's fate ({@code /api/integration/channel/delivery-status}). A payload that
 * still carries {@code statuses} (the platform forwarding them during the rollout) is answered 200 and its statuses are
 * ignored.</p>
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
    /** D-063: keeps a claimed message until its turn settled, replayed after a crash. */
    private PendingInbound pending;
    private final boolean retryUnanswered;
    private final Duration claimLease;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppSignatureVerifier signatures,
                                     WhatsAppMessageParser parser, IdempotencyService idempotency,
                                     InboundTurnExecutor executor, InboundMessageHandler handler, ObjectMapper json,
                                     Clock clock,
                                     @Value("${dad-coach.whatsapp.inbound.retry-unanswered:true}") boolean retryUnanswered,
                                     @Value("${dad-coach.whatsapp.inbound.claim-lease:PT10M}") Duration claimLease) {
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPendingInbound(PendingInbound pending) {
        this.pending = pending;
        pending.replayWith(this::replay);
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
            // D-063: the claim and his pending message in one transaction - a crash before the turn settled replays it
            IdempotencyService.Claim claim = pending == null ? idempotency.claim(DEDUP_SCOPE, key, claimLease)
                    : idempotency.claim(DEDUP_SCOPE, key, claimLease, () -> pending.record(in));
            if (claim != IdempotencyService.Claim.CLAIMED) {
                log.atInfo().setMessage("whatsapp.inbound.duplicate").addKeyValue("messageId", key)
                        .addKeyValue("state", claim).log();
                continue;
            }
            // claimed here, in arrival order; the turn still runs one at a time per sender
            executor.submit(in.fatherChannelIdentity(), () -> answer(in, receivedAt));
        }
        if (!parsed.statusUpdates().isEmpty()) {
            // D-053: the platform's (it reports each message's fate); ignored here, the payload still answered 200
            log.atDebug().setMessage("whatsapp.receipts.ignored").addKeyValue("count", parsed.statusUpdates().size()).log();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Runs the claimed message; done only when it was answered - otherwise the claim goes, for the redelivery. The
     * handler reports the outcome itself also when its processing threw (HANDLED once something reached him), so only
     * an Error escaping it leaves the default UNANSWERED.
     */
    private void answer(InboundMessageDto in, Instant receivedAt) {
        InboundMessageHandler.Outcome outcome = InboundMessageHandler.Outcome.UNANSWERED;
        try {
            outcome = handler.handle(in, receivedAt);
        } finally {
            settle(in.idempotencyKey(), outcome);
            if (pending != null) {
                pending.done(in.idempotencyKey());
            }
        }
    }

    /**
     * D-063: a message whose turn never settled (its instance died after the 200) runs now, as it would have: its claim is
     * taken over once older than the pending lease; settled meanwhile (a redelivery answered it), only the row goes. A turn
     * that had reached the platform is a duplicate there; its cached reply goes once (D-038's reply guard).
     */
    void replay(InboundMessageDto in) {
        String key = in.idempotencyKey();
        IdempotencyService.Claim claim = idempotency.claim(DEDUP_SCOPE, key, PendingInbound.LEASE);
        if (claim == IdempotencyService.Claim.DONE) {
            pending.done(key);
            return;
        }
        if (claim == IdempotencyService.Claim.BUSY) {
            log.atInfo().setMessage("whatsapp.inbound.replay_busy").addKeyValue("messageId", key).log();
            return;
        }
        pending.hold(key);
        executor.submit(in.fatherChannelIdentity(), () -> answer(in, clock.instant()));
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
