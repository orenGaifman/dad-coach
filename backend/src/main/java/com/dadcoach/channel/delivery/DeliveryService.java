package com.dadcoach.channel.delivery;

import com.dadcoach.channel.ChannelAdapter;
import com.dadcoach.channel.ChannelRouter;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.capability.MessageDowngrader;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.channel.template.TemplateRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates outbound message delivery through the communication channel layer.
 *
 * <p>The delivery pipeline follows these steps:
 * <ol>
 *   <li>Resolve endpoint for the father (primary or explicit channel)</li>
 *   <li>Check session window (closed + non-template → rejected with SESSION_CLOSED)</li>
 *   <li>If template required, verify template is APPROVED</li>
 *   <li>Check capabilities and downgrade if needed</li>
 *   <li>Deliver via the channel adapter</li>
 * </ol>
 *
 * <p>The Conversation Engine addresses messages to a {@code father_id}; endpoint resolution
 * is fully owned by this layer. The result is a structured {@link DeliveryResult} that
 * the Conversation Engine uses to decide on follow-up actions (e.g., template conversion
 * when session is closed).
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    static final String SESSION_CLOSED = "SESSION_CLOSED";
    static final String TEMPLATE_UNAVAILABLE = "TEMPLATE_UNAVAILABLE";
    static final String ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";
    static final String UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";

    private final CommunicationEndpointRepository endpointRepository;
    private final SessionWindowService sessionWindowService;
    private final ChannelRouter channelRouter;
    private final MessageDowngrader messageDowngrader;
    private final TemplateRegistry templateRegistry;

    public DeliveryService(
            CommunicationEndpointRepository endpointRepository,
            SessionWindowService sessionWindowService,
            ChannelRouter channelRouter,
            MessageDowngrader messageDowngrader,
            TemplateRegistry templateRegistry) {
        this.endpointRepository = endpointRepository;
        this.sessionWindowService = sessionWindowService;
        this.channelRouter = channelRouter;
        this.messageDowngrader = messageDowngrader;
        this.templateRegistry = templateRegistry;
    }

    /**
     * Delivers an outbound message through the appropriate channel.
     *
     * @param message the outbound message from the Conversation Engine
     * @return structured result indicating success, failure, or rejection with reason
     */
    public DeliveryResult deliver(OutboundMessageDto message) {
        log.debug("Starting delivery for message {} to father {}", message.messageId(), message.fatherId());

        // 1. Resolve endpoint for father
        CommunicationEndpoint endpoint = resolveEndpoint(message);
        if (endpoint == null) {
            log.warn("No endpoint found for father {} (channel={})", message.fatherId(), message.channel());
            return DeliveryResult.rejected(ENDPOINT_NOT_FOUND);
        }

        // 2. Check session window
        boolean sessionOpen = sessionWindowService.isOpen(endpoint);
        if (!sessionOpen && !message.isTemplate()) {
            log.info("Delivery rejected: session closed for father {} and message is not a template",
                    message.fatherId());
            return DeliveryResult.rejected(SESSION_CLOSED);
        }

        // 3. If template required, verify template is APPROVED
        if (message.isTemplate()) {
            var templateCheck = verifyTemplate(message);
            if (templateCheck != null) {
                return templateCheck;
            }
        }

        // 4. Check capabilities and downgrade if needed
        ChannelAdapter adapter = channelRouter.getAdapter(endpoint.getChannel());
        var downgradeResult = messageDowngrader.downgradeIfNeeded(
                message, adapter.getCapabilities(), sessionOpen);

        if (downgradeResult instanceof MessageDowngrader.DowngradeResult.Rejected rejected) {
            log.warn("Message {} rejected during downgrade: {}", message.messageId(), rejected.reason());
            return DeliveryResult.rejected(UNSUPPORTED_TYPE + ": " + rejected.reason());
        }

        OutboundMessageDto finalMessage = ((MessageDowngrader.DowngradeResult.Success) downgradeResult)
                .downgradedMessage();

        // 5. Deliver via adapter
        log.debug("Sending message {} via {} to {}", finalMessage.messageId(),
                endpoint.getChannel(), endpoint.getChannelIdentity());
        return adapter.sendMessage(finalMessage, endpoint.getChannelIdentity());
    }

    /**
     * Resolves the delivery endpoint for the message.
     * If the message specifies an explicit channel, finds the endpoint for that channel.
     * Otherwise, uses the father's primary endpoint.
     */
    private CommunicationEndpoint resolveEndpoint(OutboundMessageDto message) {
        if (message.channel() != null && !message.channel().isBlank()) {
            // Explicit channel specified — find the father's endpoint for that channel
            return endpointRepository.findByFatherId(message.fatherId()).stream()
                    .filter(e -> e.getChannel().equals(message.channel()))
                    .findFirst()
                    .orElse(null);
        }
        // Use the father's primary endpoint
        return endpointRepository.findPrimaryByFatherId(message.fatherId()).orElse(null);
    }

    /**
     * Verifies that the requested template is approved.
     * Returns a rejection result if the template is not available, or null if verification passes.
     */
    private DeliveryResult verifyTemplate(OutboundMessageDto message) {
        if (message.templateName() == null || message.templateName().isBlank()) {
            log.warn("Template message {} has no template name specified", message.messageId());
            return DeliveryResult.rejected(TEMPLATE_UNAVAILABLE);
        }

        var template = templateRegistry.findApprovedTemplate(message.templateName());
        if (template.isEmpty()) {
            log.warn("Template '{}' not found or not APPROVED for message {}",
                    message.templateName(), message.messageId());
            return DeliveryResult.rejected(TEMPLATE_UNAVAILABLE);
        }

        return null; // Template is valid
    }
}
