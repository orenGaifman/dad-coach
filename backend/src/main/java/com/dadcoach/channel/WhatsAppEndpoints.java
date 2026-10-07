package com.dadcoach.channel;

import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.domain.father.Father;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A father's WhatsApp endpoint: the row every proactive delivery resolves ({@code DeliveryService}). It exists for
 * every father who wrote to Dad Coach - created when he is created on WhatsApp and re-ensured on every inbound
 * message (F1: fathers who onboarded on WhatsApp had none, so every reminder failed ENDPOINT_NOT_FOUND).
 * Idempotent: (channel, channel_identity) is unique in the database.
 */
@Component
public class WhatsAppEndpoints {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppEndpoints.class);
    public static final String CHANNEL = "WHATSAPP";

    private final CommunicationEndpointRepository endpoints;
    private final SessionWindowService sessionWindows;

    public WhatsAppEndpoints(CommunicationEndpointRepository endpoints, SessionWindowService sessionWindows) {
        this.endpoints = endpoints;
        this.sessionWindows = sessionWindows;
    }

    /** The father's WhatsApp endpoint, created if missing. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommunicationEndpoint ensure(Father father) {
        UUID fatherUuid = new UUID(0L, father.getId());
        var existing = endpoints.findByChannelAndChannelIdentity(CHANNEL, father.getPhone());
        if (existing.isPresent()) {
            CommunicationEndpoint endpoint = existing.get();
            if (!fatherUuid.equals(endpoint.getFatherId())) {
                // the number is this father's now (phone is unique per father); an endpoint of an earlier owner is stale
                log.warn("WhatsApp endpoint re-linked to the father who owns the number: fatherId={}", father.getId());
                endpoints.delete(endpoint);
                endpoints.flush();
                return create(fatherUuid, father);
            }
            return endpoint;
        }
        return create(fatherUuid, father);
    }

    private CommunicationEndpoint create(UUID fatherUuid, Father father) {
        try {
            CommunicationEndpoint created = endpoints.saveAndFlush(new CommunicationEndpoint(fatherUuid, CHANNEL, father.getPhone()));
            log.info("WhatsApp endpoint created: fatherId={}", father.getId());
            return created;
        } catch (DataIntegrityViolationException raceLost) {
            return endpoints.findByChannelAndChannelIdentity(CHANNEL, father.getPhone()).orElseThrow(() -> raceLost);
        }
    }

    /** The father just wrote: his endpoint exists and his 24-hour window is open from now. Never throws. */
    public void recordInbound(Father father) {
        try {
            sessionWindows.onInboundMessage(ensure(father));
        } catch (RuntimeException e) {
            log.warn("Could not record the inbound session window: fatherId={}, error={}", father.getId(), e.getClass().getSimpleName());
        }
    }
}
