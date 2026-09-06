package com.dadcoach.workflow;

import com.dadcoach.channel.dto.InboundMessageDto;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.integration.platform.PlatformWorkflowClient;
import com.dadcoach.integration.platform.PlatformWorkflowConfig;
import com.dadcoach.integration.platform.PlatformWorkflowException;
import com.dadcoach.integration.platform.dto.WorkflowExecuteRequest;
import com.dadcoach.integration.platform.dto.WorkflowExecuteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkflowEngine implementation that delegates to the ai-workflow-platform.
 *
 * <p>This implementation is used when the platform integration is enabled
 * (workflow.platform.enabled=true). It translates between dad-coach's internal
 * message format and the platform's API format.</p>
 *
 * <p>The channel adapters (WhatsApp, etc.) continue to work unchanged since
 * this class implements the same WorkflowEngine interface as WorkflowEngineImpl.</p>
 *
 * <h3>Configuration</h3>
 * <pre>
 * workflow:
 *   platform:
 *     enabled: true  # Enables this implementation
 * </pre>
 *
 * @see WorkflowEngine
 * @see PlatformWorkflowClient
 */
public class PlatformWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(PlatformWorkflowEngine.class);

    private static final String DEFAULT_WORKFLOW_KEY = "dad-coach";
    private static final String FALLBACK_ERROR_MESSAGE = "I'm having trouble processing your message right now. Please try again in a moment.";

    private final PlatformWorkflowClient platformClient;
    private final PlatformWorkflowConfig config;

    /**
     * Creates a new PlatformWorkflowEngine.
     *
     * @param platformClient the client for calling the workflow platform API
     * @param config the platform configuration
     */
    public PlatformWorkflowEngine(PlatformWorkflowClient platformClient, PlatformWorkflowConfig config) {
        this.platformClient = platformClient;
        this.config = config;
        log.info("PlatformWorkflowEngine initialized: workflowId={}", config.getWorkflowId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates message processing to the ai-workflow-platform. Maps the
     * internal InboundMessageDto to the platform's request format and converts
     * the platform response back to OutboundMessageDto.</p>
     */
    @Override
    public OutboundMessageDto processMessage(InboundMessageDto message) {
        log.info("Processing message via platform: messageId={}, channel={}",
                message.messageId(), message.channel());

        try {
            WorkflowExecuteRequest request = buildRequest(message);
            WorkflowExecuteResponse response = platformClient.executeWorkflow(request);
            return mapToOutbound(message, response);
        } catch (PlatformWorkflowException e) {
            log.error("Platform workflow error: messageId={}, error={}",
                    message.messageId(), e.getMessage());
            return buildErrorResponse(message, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing message via platform: messageId={}, error={}",
                    message.messageId(), e.getMessage(), e);
            return buildErrorResponse(message, FALLBACK_ERROR_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>External triggers (scheduler jobs) are not yet supported via the platform.
     * Returns empty for now - scheduler-based transitions would require additional
     * platform API endpoints.</p>
     */
    @Override
    public Optional<OutboundMessageDto> triggerTransition(UUID fatherId, WorkflowTrigger trigger) {
        log.warn("triggerTransition called on PlatformWorkflowEngine - not yet supported: fatherId={}, trigger={}",
                fatherId, trigger);
        // External triggers not yet supported via platform API
        // This would require a separate platform endpoint for scheduler-driven transitions
        return Optional.empty();
    }

    /**
     * Builds a platform request from an inbound message.
     */
    private WorkflowExecuteRequest buildRequest(InboundMessageDto message) {
        String userId = buildUserId(message);
        String messageType = mapMessageType(message.messageType());
        String content = message.textContent() != null ? message.textContent() : "";

        return new WorkflowExecuteRequest(
                DEFAULT_WORKFLOW_KEY,
                userId,
                message.channel(),
                message.messageId().toString(),
                new WorkflowExecuteRequest.MessagePayload(messageType, content)
        );
    }

    /**
     * Builds the user ID from the inbound message.
     * Format: {channel}:{identifier} (e.g., "whatsapp:+972501234567")
     */
    private String buildUserId(InboundMessageDto message) {
        String channel = message.channel() != null ? message.channel().toLowerCase() : "unknown";
        String identity = message.fatherChannelIdentity() != null ? message.fatherChannelIdentity() : "anonymous";
        return channel + ":" + identity;
    }

    /**
     * Maps internal MessageType to platform message type string.
     */
    private String mapMessageType(MessageType messageType) {
        if (messageType == null) {
            return "text";
        }
        return switch (messageType) {
            case TEXT -> "text";
            case INTERACTIVE -> "button_reply";
            case IMAGE, AUDIO, VIDEO, DOCUMENT -> "media";
            case LOCATION -> "location";
            case REACTION -> "reaction";
        };
    }

    /**
     * Maps the platform response to an OutboundMessageDto.
     */
    private OutboundMessageDto mapToOutbound(InboundMessageDto inbound, WorkflowExecuteResponse response) {
        if (!response.success()) {
            log.warn("Platform returned error: code={}, message={}",
                    response.errorCode(), response.errorMessage());
            String errorContent = response.errorMessage() != null
                    ? response.errorMessage()
                    : FALLBACK_ERROR_MESSAGE;
            return buildErrorResponse(inbound, errorContent);
        }

        String textContent = extractTextContent(response);
        MessageType outboundType = determineOutboundType(response);

        log.debug("Platform response mapped: executionId={}, state={}, type={}",
                response.executionId(), response.currentState(), outboundType);

        return new OutboundMessageDto(
                UUID.randomUUID(),
                extractFatherId(inbound),
                inbound.channel(),
                outboundType,
                textContent,
                null,  // mediaReference - not yet supported
                false, // isTemplate
                null,  // templateName
                Map.of(), // templateParameters
                MessagePriority.IMMEDIATE,
                Instant.now()
        );
    }

    /**
     * Extracts text content from platform response.
     */
    private String extractTextContent(WorkflowExecuteResponse response) {
        if (response.response() == null) {
            return FALLBACK_ERROR_MESSAGE;
        }
        return response.response().content() != null
                ? response.response().content()
                : FALLBACK_ERROR_MESSAGE;
    }

    /**
     * Determines the outbound message type from platform response.
     */
    private MessageType determineOutboundType(WorkflowExecuteResponse response) {
        if (response.response() == null || response.response().type() == null) {
            return MessageType.TEXT;
        }
        return switch (response.response().type().toLowerCase()) {
            case "buttons", "interactive" -> MessageType.INTERACTIVE;
            default -> MessageType.TEXT;
        };
    }

    /**
     * Extracts father ID from the inbound message.
     * This is a placeholder - actual father lookup would be needed in production.
     */
    private UUID extractFatherId(InboundMessageDto inbound) {
        // Note: The actual fatherId should be resolved from fatherChannelIdentity
        // via FatherRepository. For now, we generate a deterministic UUID.
        // This should be enhanced to properly look up the father.
        return UUID.nameUUIDFromBytes(
                ("father:" + inbound.fatherChannelIdentity()).getBytes()
        );
    }

    /**
     * Builds an error response when platform call fails.
     */
    private OutboundMessageDto buildErrorResponse(InboundMessageDto inbound, String errorMessage) {
        return new OutboundMessageDto(
                UUID.randomUUID(),
                extractFatherId(inbound),
                inbound.channel(),
                MessageType.TEXT,
                errorMessage,
                null,
                false,
                null,
                Map.of(),
                MessagePriority.IMMEDIATE,
                Instant.now()
        );
    }
}
