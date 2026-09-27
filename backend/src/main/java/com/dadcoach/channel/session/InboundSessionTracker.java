package com.dadcoach.channel.session;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.workflow.scheduler.ProactiveMessageOwnership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the WhatsApp 24-hour customer-service window current: every inbound message from a father
 * re-opens/extends the window on his primary endpoint via {@link SessionWindowService}, which
 * {@code DeliveryService} consults before sending free-form text.
 *
 * <p>Recorded only while the Workflow Platform owns proactive messages
 * ({@link ProactiveMessageOwnership.Owner#PLATFORM}). Under {@code LOCAL} the window is deliberately
 * left as it is today (opened at activation only): feeding it would make {@code DeliveryService}
 * start delivering {@code CommitmentReminderScheduler}'s messages, which are currently dropped as
 * SESSION_CLOSED - a behavior change for Dad Coach 2 users. Under {@code PLATFORM} those jobs stand
 * down, so recording the window has no such side effect.</p>
 */
@Component
public class InboundSessionTracker {

    private static final Logger log = LoggerFactory.getLogger(InboundSessionTracker.class);

    private final FatherRepository fatherRepository;
    private final CommunicationEndpointRepository endpointRepository;
    private final SessionWindowService sessionWindowService;
    private final ProactiveMessageOwnership proactiveMessageOwnership;

    public InboundSessionTracker(
            FatherRepository fatherRepository,
            CommunicationEndpointRepository endpointRepository,
            SessionWindowService sessionWindowService,
            ProactiveMessageOwnership proactiveMessageOwnership) {
        this.fatherRepository = fatherRepository;
        this.endpointRepository = endpointRepository;
        this.sessionWindowService = sessionWindowService;
        this.proactiveMessageOwnership = proactiveMessageOwnership;
    }

    /**
     * Records an inbound WhatsApp message. Never throws - window bookkeeping must not break
     * conversation handling.
     *
     * @param senderPhone the sender's phone in E.164 form (the inbound message's channel identity)
     */
    public void onInboundWhatsAppMessage(String senderPhone) {
        if (proactiveMessageOwnership.localSchedulerSends()) {
            return;
        }
        try {
            fatherRepository.findByPhone(senderPhone)
                    .flatMap(father -> endpointFor(new UUID(0L, father.getId())))
                    .ifPresent(sessionWindowService::onInboundMessage);
        } catch (Exception e) {
            log.warn("Failed to record inbound session window: {}", e.getMessage());
        }
    }

    // Same endpoint resolution as ActivationServiceImpl.openSessionWindow (primary, else any).
    private Optional<CommunicationEndpoint> endpointFor(UUID fatherUuid) {
        return endpointRepository.findPrimaryByFatherId(fatherUuid).or(() -> {
            List<CommunicationEndpoint> endpoints = endpointRepository.findByFatherId(fatherUuid);
            return endpoints.isEmpty() ? Optional.empty() : Optional.of(endpoints.get(0));
        });
    }
}
