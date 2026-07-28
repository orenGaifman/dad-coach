package com.dadcoach.whatsapp;

import com.dadcoach.config.WhatsAppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint for WhatsApp Cloud API notifications.
 * <p>
 * Handles both the GET verification challenge (used during webhook registration with Meta)
 * and POST inbound events (messages, status updates). Every POST is authenticated via
 * HMAC-SHA256 signature verification BEFORE any payload parsing occurs.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppSignatureVerifier signatureVerifier;
    private final WhatsAppProperties properties;

    public WhatsAppWebhookController(WhatsAppSignatureVerifier signatureVerifier,
                                     WhatsAppProperties properties) {
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
    }

    /**
     * Webhook verification endpoint. Meta sends a GET request with a challenge to verify
     * webhook ownership during registration.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && properties.verifyToken() != null
                && properties.verifyToken().equals(token)) {
            log.info("Webhook verification successful");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook verification failed: invalid mode or token");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Inbound webhook event handler. Verifies HMAC-SHA256 signature BEFORE any payload
     * parsing. On valid signature, forwards raw body to the message parser for processing.
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureHeader,
            HttpServletRequest request) {

        String sourceIp = extractSourceIp(request);

        if (!signatureVerifier.isValid(rawBody, signatureHeader, properties.webhookSecret())) {
            log.warn("Webhook signature verification failed: sourceIp={}, timestamp={}, reason={}",
                    sourceIp,
                    Instant.now(),
                    describeFailureReason(signatureHeader));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Signature verified — forward to message parser
        // The message parser (Task 4) will handle deserialization and normalization.
        // For now, log acceptance and return 200.
        log.info("Webhook received and verified: sourceIp={}, bodySize={}", sourceIp, rawBody.length);

        return ResponseEntity.ok().build();
    }

    private String extractSourceIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take the first IP in the chain (client IP)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String describeFailureReason(String signatureHeader) {
        if (signatureHeader == null) {
            return "missing X-Hub-Signature-256 header";
        }
        if (!signatureHeader.startsWith("sha256=")) {
            return "malformed signature header (missing sha256= prefix)";
        }
        return "signature mismatch";
    }
}
