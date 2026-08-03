package com.dadcoach.whatsapp;

import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.config.WhatsAppProperties;
import com.dadcoach.conversation.ConversationOrchestrator;
import com.dadcoach.conversation.dto.OutboundMessageDto;
import com.dadcoach.workflow.WorkflowEngine;
import com.dadcoach.workflow.config.FeatureFlagsConfig;
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
 * <p>Routes incoming WhatsApp messages through either the deterministic WorkflowEngine
 * (new architecture) or the ConversationOrchestrator (legacy), based on the feature flag
 * {@code dadcoach.features.deterministic-workflow-engine}.</p>
 * 
 * <p>Implements Requirement 11.1 from the deterministic-workflow-engine spec:
 * Message processing pipeline for WhatsApp messages.</p>
 * 
 * @see WorkflowEngine
 * @see ConversationOrchestrator
 * @see FeatureFlagsConfig
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
    private final WorkflowEngine workflowEngine;
    private final FeatureFlagsConfig featureFlags;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppSignatureVerifier signatureVerifier,
                                     WhatsAppProperties properties,
                                     ChannelRouter channelRouter,
                                     ConversationOrchestrator conversationOrchestrator,
                                     WorkflowEngine workflowEngine,
                                     FeatureFlagsConfig featureFlags,
                                     ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.channelRouter = channelRouter;
        this.conversationOrchestrator = conversationOrchestrator;
        this.workflowEngine = workflowEngine;
        this.featureFlags = featureFlags;
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
        log.info("Webhook POST received: sourceIp={}, signaturePresent={}, bodySize={}, webhookSecretConfigured={}", 
                 sourceIp, signatureHeader != null, rawBody.length, 
                 properties.webhookSecret() != null && !properties.webhookSecret().isBlank());

        // Log the raw payload for debugging (mask sensitive data)
        try {
            String bodyStr = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
            // Log truncated body to avoid log bloat
            log.debug("Webhook raw body (truncated): {}", 
                     bodyStr.length() > 500 ? bodyStr.substring(0, 500) + "..." : bodyStr);
        } catch (Exception e) {
            log.debug("Could not convert raw body to string for logging");
        }

        // Verify the webhook signature
        boolean signatureValid = signatureVerifier.isValid(rawBody, signatureHeader, properties.webhookSecret());
        if (!signatureValid) {
            log.warn("Webhook signature verification failed: sourceIp={}, reason={}, secretLength={}",
                    sourceIp, describeFailureReason(signatureHeader),
                    properties.webhookSecret() != null ? properties.webhookSecret().length() : 0);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Webhook signature verified successfully: sourceIp={}, bodySize={}", sourceIp, rawBody.length);

        // Process the message
        try {
            com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload payload = 
                objectMapper.readValue(rawBody, com.dadcoach.whatsapp.dto.WhatsAppWebhookPayload.class);
            ChannelAdapter adapter = channelRouter.getAdapter("WHATSAPP");
            com.dadcoach.channel.dto.InboundMessageDto inbound = adapter.normalizeInbound(payload);

            if (inbound != null) {
                log.info("Processing inbound message from: {}, text: {}", inbound.fatherChannelIdentity(), inbound.textContent());

                // Route based on feature flag: deterministic-workflow-engine
                // Implements Requirement 11.1: WhatsApp message processing pipeline
                if (featureFlags.isDeterministicWorkflowEngine()) {
                    processWithWorkflowEngine(inbound, adapter);
                } else {
                    processWithConversationOrchestrator(inbound, adapter);
                }
            } else {
                log.debug("Webhook payload did not contain a processable message (status update or other event)");
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Processes an inbound message using the new deterministic WorkflowEngine.
     * 
     * <p>This is the new architecture that implements the deterministic workflow state machine.
     * The WorkflowEngine directly uses channel DTOs (InboundMessageDto, OutboundMessageDto).</p>
     * 
     * <p>Implements Requirement 11.1: Message processing pipeline:
     * <ol>
     *   <li>Parse and validate message (already done by channel layer)</li>
     *   <li>Identify father from phone number</li>
     *   <li>Load SystemState (Read Before Write)</li>
     *   <li>Determine current workflow state</li>
     *   <li>Match message against expected patterns</li>
     *   <li>Execute business logic for matched pattern</li>
     *   <li>Generate response message (AI or fallback)</li>
     *   <li>Persist state changes</li>
     *   <li>Send response via WhatsApp</li>
     * </ol></p>
     * 
     * @param inbound the normalized inbound message from the channel
     * @param adapter the WhatsApp channel adapter for sending responses
     */
    private void processWithWorkflowEngine(com.dadcoach.channel.dto.InboundMessageDto inbound, 
                                           ChannelAdapter adapter) {
        log.info("Using deterministic WorkflowEngine for message processing (feature flag enabled)");
        
        try {
            // Process message through the deterministic workflow engine
            // WorkflowEngine.processMessage() implements the full 9-step pipeline
            com.dadcoach.channel.dto.OutboundMessageDto response = workflowEngine.processMessage(inbound);
            
            if (response != null && response.textContent() != null) {
                log.info("WorkflowEngine response generated, sending via WhatsApp to: {}", 
                        inbound.fatherChannelIdentity());
                adapter.sendMessage(response, inbound.fatherChannelIdentity());
            } else {
                log.warn("No response generated by WorkflowEngine for message from: {}", 
                        inbound.fatherChannelIdentity());
            }
        } catch (Exception e) {
            log.error("Error in WorkflowEngine processing for {}: {}", 
                    inbound.fatherChannelIdentity(), e.getMessage(), e);
            // Don't rethrow - we've already returned 200 OK to WhatsApp
            // The error is logged for monitoring
        }
    }

    /**
     * Processes an inbound message using the legacy ConversationOrchestrator.
     * 
     * <p>This is the original AI-driven architecture that is being replaced by
     * the deterministic WorkflowEngine. Kept for fallback and gradual migration.</p>
     * 
     * @param inbound the normalized inbound message from the channel
     * @param adapter the WhatsApp channel adapter for sending responses
     */
    private void processWithConversationOrchestrator(com.dadcoach.channel.dto.InboundMessageDto inbound,
                                                     ChannelAdapter adapter) {
        log.info("Using ConversationOrchestrator for message processing (feature flag disabled)");
        
        try {
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
        } catch (Exception e) {
            log.error("Error in ConversationOrchestrator processing for {}: {}", 
                    inbound.fatherChannelIdentity(), e.getMessage(), e);
            // Don't rethrow - we've already returned 200 OK to WhatsApp
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
     * Access via: GET /webhook/whatsapp/test-send?to=972503020551&message=Hello
     */
    @GetMapping("/test-send")
    public ResponseEntity<Map<String, Object>> testSend(
            @RequestParam(defaultValue = "972503020551") String to,
            @RequestParam(defaultValue = "🧪 Test message from Dad Coach server!") String message) {
        
        log.info("TEST-SEND: Attempting to send message to {} with text: {}", to, message);
        
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
            log.info("TEST-SEND: Message sent successfully to {}", to);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "to", to,
                "message", message,
                "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("TEST-SEND: Failed to send message to {}: {}", to, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "to", to,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * DEBUG ENDPOINT: Check webhook configuration status
     */
    @GetMapping("/debug-config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        return ResponseEntity.ok(Map.of(
            "phoneNumberId", properties.phoneNumberId() != null ? properties.phoneNumberId() : "NOT SET",
            "verifyToken", properties.verifyToken() != null ? "SET (length=" + properties.verifyToken().length() + ")" : "NOT SET",
            "webhookSecret", properties.webhookSecret() != null ? "SET (length=" + properties.webhookSecret().length() + ")" : "NOT SET",
            "accessToken", properties.accessToken() != null ? "SET (length=" + properties.accessToken().length() + ")" : "NOT SET",
            "apiBaseUrl", properties.apiBaseUrl() != null ? properties.apiBaseUrl() : "NOT SET",
            "apiVersion", properties.apiVersion() != null ? properties.apiVersion() : "NOT SET",
            "serverTime", Instant.now().toString()
        ));
    }

    /**
     * Get WhatsApp phone number status from Meta API.
     * Useful for dev invite page to show current phone number health.
     * Access via: GET /webhook/whatsapp/phone-status
     */
    @GetMapping("/phone-status")
    public ResponseEntity<Map<String, Object>> getPhoneStatus() {
        if (properties.phoneNumberId() == null || properties.accessToken() == null) {
            return ResponseEntity.ok(Map.of(
                "error", "WhatsApp not configured",
                "configured", false
            ));
        }

        try {
            String url = String.format("%s/%s/%s?fields=verified_name,code_verification_status,quality_rating,display_phone_number,name_status,throughput",
                    properties.apiBaseUrl(), properties.apiVersion(), properties.phoneNumberId());

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "Bearer " + properties.accessToken())
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
                data.put("configured", true);
                data.put("api_status", "connected");
                return ResponseEntity.ok(data);
            } else {
                return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "api_status", "error",
                    "error_code", response.statusCode(),
                    "error_body", response.body()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to get phone status: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "api_status", "error",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get WhatsApp Business Account info from Meta API.
     * Shows app mode (live/development) and other WABA-level info.
     * Access via: GET /webhook/whatsapp/waba-status
     */
    @GetMapping("/waba-status")
    public ResponseEntity<Map<String, Object>> getWabaStatus() {
        if (properties.wabaId() == null || properties.accessToken() == null) {
            return ResponseEntity.ok(Map.of(
                "error", "WABA not configured",
                "configured", false
            ));
        }

        try {
            String url = String.format("%s/%s/%s?fields=name,timezone_id,message_template_namespace,account_review_status,business_verification_status",
                    properties.apiBaseUrl(), properties.apiVersion(), properties.wabaId());

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "Bearer " + properties.accessToken())
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
                data.put("configured", true);
                data.put("api_status", "connected");
                return ResponseEntity.ok(data);
            } else {
                return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "api_status", "error",
                    "error_code", response.statusCode(),
                    "error_body", response.body()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to get WABA status: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "api_status", "error",
                "error", e.getMessage()
            ));
        }
    }
}
