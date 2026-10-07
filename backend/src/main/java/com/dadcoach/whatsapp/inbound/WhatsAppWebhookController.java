package com.dadcoach.whatsapp.inbound;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.idempotency.IdempotencyService;
import com.dadcoach.whatsapp.WhatsAppMessageParser;
import com.dadcoach.whatsapp.WhatsAppSignatureVerifier;
import com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * the turn runs on the per-sender executor. Meta's retries are deduplicated durably on its message id (survives a
 * restart): a duplicate is dropped, never answered again. Status receipts are acknowledged and ignored.
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

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppSignatureVerifier signatures,
                                     WhatsAppMessageParser parser, IdempotencyService idempotency,
                                     InboundTurnExecutor executor, InboundMessageHandler handler, ObjectMapper json,
                                     Clock clock) {
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
            if (!idempotency.firstTime(DEDUP_SCOPE, in.idempotencyKey())) {
                log.atInfo().setMessage("whatsapp.inbound.duplicate").addKeyValue("messageId", in.idempotencyKey()).log();
                continue;
            }
            executor.submit(in.fatherChannelIdentity(), () -> handler.handle(in, receivedAt));
        }
        if (!parsed.statusUpdates().isEmpty()) {
            log.atDebug().setMessage("whatsapp.receipts.ignored").addKeyValue("count", parsed.statusUpdates().size()).log();
        }
        return ResponseEntity.ok().build();
    }
}
