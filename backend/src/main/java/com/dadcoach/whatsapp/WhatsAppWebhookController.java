package com.dadcoach.whatsapp;

import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.onboarding.activation.ActivationListener;
import com.dadcoach.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint for WhatsApp Cloud API notifications.
 * 
 * <p>Routes incoming WhatsApp messages through the WorkflowEngine
 * which implements a deterministic state machine for conversation handling.</p>
 * 
 * @see WorkflowEngine
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppSignatureVerifier signatureVerifier;
    private final WhatsAppProperties properties;
    private final ChannelRouter channelRouter;
    private final WorkflowEngine workflowEngine;
    private final ActivationListener activationListener;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppSignatureVerifier signatureVerifier,
                                     WhatsAppProperties properties,
                                     ChannelRouter channelRouter,
                                     WorkflowEngine workflowEngine,
                                     ActivationListener activationListener,
                                     ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.channelRouter = channelRouter;
        this.workflowEngine = workflowEngine;
        this.activationListener = activationListener;
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

        // Verify the webhook signature
        boolean signatureValid = signatureVerifier.isValid(rawBody, signatureHeader, properties.webhookSecret());
        if (!signatureValid) {
            log.warn("Webhook signature verification failed: sourceIp={}, reason={}",
                    sourceIp, describeFailureReason(signatureHeader));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Process the message
        try {
            com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload payload = 
                objectMapper.readValue(rawBody, com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload.class);
            ChannelAdapter adapter = channelRouter.getAdapter("WHATSAPP");
            com.dadcoach.channel.dto.InboundMessageDto inbound = adapter.normalizeInbound(payload);

            if (inbound != null) {
                log.info("Processing inbound message from: {}", inbound.fatherChannelIdentity());
                processMessage(inbound, adapter);
            } else {
                log.debug("Webhook payload did not contain a processable message (status update or other event)");
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void processMessage(com.dadcoach.channel.dto.InboundMessageDto inbound, ChannelAdapter adapter) {
        try {
            // Check if this is an ONBOARDING father - intercept for activation flow
            // This handles the first message after onboarding completion
            if (activationListener.interceptByPhoneIfOnboarding(
                    inbound.fatherChannelIdentity(), 
                    inbound.textContent())) {
                log.info("Message intercepted by activation flow for: {}", inbound.fatherChannelIdentity());
                // Activation flow handles:
                // 1. ONBOARDING → ACTIVE status transition
                // 2. Sending welcome message
                // DO NOT continue to workflow engine - activation handles the response
                return;
            }
            
            // Normal flow: process through state machine
            com.dadcoach.channel.dto.OutboundMessageDto response = workflowEngine.processMessage(inbound);
            
            if (response != null && response.textContent() != null) {
                log.info("Response generated, sending to: {}", inbound.fatherChannelIdentity());
                adapter.sendMessage(response, inbound.fatherChannelIdentity());
            } else {
                log.warn("No response generated for message from: {}", inbound.fatherChannelIdentity());
            }
        } catch (Exception e) {
            log.error("Error processing message for {}: {}", inbound.fatherChannelIdentity(), e.getMessage(), e);
        }
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

    /**
     * DEBUG ENDPOINT: Test sending a message to verify WhatsApp API connectivity.
     */
    @GetMapping("/test-send")
    public ResponseEntity<Map<String, Object>> testSend(
            @RequestParam(defaultValue = "972503020551") String to,
            @RequestParam(defaultValue = "🧪 Test message from Dad Coach!") String message) {
        
        log.info("TEST-SEND: Sending to {} with text: {}", to, message);
        
        try {
            ChannelAdapter adapter = channelRouter.getAdapter("WHATSAPP");
            com.dadcoach.channel.dto.OutboundMessageDto channelMessage =
                new com.dadcoach.channel.dto.OutboundMessageDto(
                    java.util.UUID.randomUUID(),
                    null,
                    "WHATSAPP",
                    com.dadcoach.channel.dto.MessageType.TEXT,
                    message,
                    null,
                    false,
                    null, null,
                    com.dadcoach.channel.dto.MessagePriority.IMMEDIATE,
                    Instant.now()
                );
            
            adapter.sendMessage(channelMessage, to);
            return ResponseEntity.ok(Map.of("success", true, "to", to, "message", message));
        } catch (Exception e) {
            log.error("TEST-SEND failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * DEBUG ENDPOINT: Check webhook configuration status.
     */
    @GetMapping("/debug-config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        return ResponseEntity.ok(Map.of(
            "phoneNumberId", properties.phoneNumberId() != null ? properties.phoneNumberId() : "NOT SET",
            "verifyToken", properties.verifyToken() != null ? "SET" : "NOT SET",
            "webhookSecret", properties.webhookSecret() != null ? "SET" : "NOT SET",
            "accessToken", properties.accessToken() != null ? "SET" : "NOT SET",
            "serverTime", Instant.now().toString()
        ));
    }
}
