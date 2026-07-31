package com.dadcoach.whatsapp;

import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.conversation.ConversationOrchestrator;
import com.dadcoach.conversation.dto.OutboundMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint for WhatsApp Cloud API notifications.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppSignatureVerifier signatureVerifier;
    private final WhatsAppProperties properties;
    private final ChannelRouter channelRouter;
    private final ConversationOrchestrator conversationOrchestrator;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppSignatureVerifier signatureVerifier,
                                     WhatsAppProperties properties,
                                     ChannelRouter channelRouter,
                                     ConversationOrchestrator conversationOrchestrator,
                                     ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.channelRouter = channelRouter;
        this.conversationOrchestrator = conversationOrchestrator;
        this.objectMapper = objectMapper;
    }

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

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureHeader,
            HttpServletRequest request) {

        String sourceIp = extractSourceIp(request);
        log.info("Webhook POST received: sourceIp={}, signaturePresent={}, bodySize={}", 
                 sourceIp, signatureHeader != null, rawBody.length);

        if (!signatureVerifier.isValid(rawBody, signatureHeader, properties.webhookSecret())) {
            log.warn("Webhook signature verification failed: sourceIp={}, reason={}",
                    sourceIp, describeFailureReason(signatureHeader));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Webhook received and verified: sourceIp={}, bodySize={}", sourceIp, rawBody.length);

        // Process the message asynchronously to return 200 quickly
        try {
            Object payload = objectMapper.readValue(rawBody, Object.class);
            ChannelAdapter adapter = channelRouter.getAdapter("WHATSAPP");
            com.dadcoach.channel.dto.InboundMessageDto inbound = adapter.normalizeInbound(payload);

            if (inbound != null) {
                log.info("Processing inbound message from: {}, text: {}", inbound.fatherChannelIdentity(), inbound.textContent());

                // Convert to conversation DTO and process
                com.dadcoach.conversation.dto.InboundMessageDto conversationDto =
                    new com.dadcoach.conversation.dto.InboundMessageDto(
                        "WHATSAPP",
                        inbound.fatherChannelIdentity(),
                        inbound.textContent(),
                        "TEXT",
                        inbound.idempotencyKey(),
                        Instant.now(),
                        null
                    );

                OutboundMessageDto response = conversationOrchestrator.processMessage(conversationDto);

                if (response != null && response.content() != null) {
                    log.info("Bot response generated, sending via WhatsApp to: {}", inbound.fatherChannelIdentity());
                    // Convert conversation OutboundMessageDto to channel OutboundMessageDto
                    com.dadcoach.channel.dto.OutboundMessageDto channelMessage =
                        new com.dadcoach.channel.dto.OutboundMessageDto(
                            java.util.UUID.randomUUID(),
                            null, // fatherId resolved by adapter
                            "WHATSAPP",
                            com.dadcoach.channel.dto.MessageType.TEXT,
                            response.content(),
                            null, // no media
                            false, // not a template
                            null, null,
                            com.dadcoach.channel.dto.MessagePriority.IMMEDIATE,
                            Instant.now()
                        );
                    adapter.sendMessage(channelMessage, inbound.fatherChannelIdentity());
                } else {
                    log.warn("No response generated for message from: {}", inbound.fatherChannelIdentity());
                }
            } else {
                log.debug("Webhook payload did not contain a processable message (status update or other event)");
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private String extractSourceIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String describeFailureReason(String signatureHeader) {
        if (signatureHeader == null) return "missing X-Hub-Signature-256 header";
        if (!signatureHeader.startsWith("sha256=")) return "malformed signature header";
        return "signature mismatch";
    }
}
