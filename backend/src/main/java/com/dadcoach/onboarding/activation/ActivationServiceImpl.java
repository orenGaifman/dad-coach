package com.dadcoach.onboarding.activation;

import com.dadcoach.ai.IntelligenceLayer;
import com.dadcoach.ai.output.CoachingContext;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.domain.conversation.ConversationType;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.onboarding.provisioning.ActivationRecord;
import com.dadcoach.onboarding.provisioning.ActivationRecordRepository;
import com.dadcoach.onboarding.provisioning.ActivationStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ActivationService} that orchestrates the WhatsApp activation flow.
 *
 * <p>This is an ORCHESTRATOR — it delegates business logic to:
 * <ul>
 *   <li>{@link FatherService#activateFather(Long)} for ONBOARDING→ACTIVE transition</li>
 *   <li>{@link SessionWindowService#onInboundMessage(CommunicationEndpoint)} for opening session window</li>
 *   <li>{@link IntelligenceLayer#generateCoachingResponse(CoachingContext)} for welcome conversation</li>
 *   <li>{@link DeliveryService#deliver(OutboundMessageDto)} for sending messages</li>
 * </ul>
 */
@Service
public class ActivationServiceImpl implements ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationServiceImpl.class);

    private static final int MAX_RETRIES = 3;
    private static final String DEEP_LINK_TEMPLATE = "https://wa.me/%s?text=%s";
    private static final long LONG_POLL_TIMEOUT_MS = 25_000;
    private static final long POLL_INTERVAL_MS = 500;

    /** Localized activation messages. */
    private static final Map<String, String> ACTIVATION_MESSAGES = Map.of(
            "en", "\uD83D\uDE80 START",
            "he", "\uD83D\uDE80 התחל"
    );

    private final ActivationRecordRepository activationRecordRepository;
    private final FatherService fatherService;
    private final SessionWindowService sessionWindowService;
    private final CommunicationEndpointRepository endpointRepository;
    private final IntelligenceLayer intelligenceLayer;
    private final DeliveryService deliveryService;

    @Value("${dad-coach.whatsapp.phone-number:+972501234567}")
    private String dadCoachPhoneNumber;

    public ActivationServiceImpl(
            ActivationRecordRepository activationRecordRepository,
            FatherService fatherService,
            SessionWindowService sessionWindowService,
            CommunicationEndpointRepository endpointRepository,
            IntelligenceLayer intelligenceLayer,
            DeliveryService deliveryService) {
        this.activationRecordRepository = activationRecordRepository;
        this.fatherService = fatherService;
        this.sessionWindowService = sessionWindowService;
        this.endpointRepository = endpointRepository;
        this.intelligenceLayer = intelligenceLayer;
        this.deliveryService = deliveryService;
    }

    @Override
    @Transactional
    public ActivationRecord createPendingActivation(UUID fatherId, UUID sessionId) {
        log.info("Creating pending activation for father {} session {}", fatherId, sessionId);

        // Check if activation already exists (idempotent)
        var existing = activationRecordRepository.findByFatherId(fatherId);
        if (existing.isPresent()) {
            log.info("Activation record already exists for father {}, returning existing", fatherId);
            return existing.get();
        }

        ActivationRecord record = new ActivationRecord(fatherId, sessionId);
        return activationRecordRepository.save(record);
    }

    @Override
    @Transactional
    public void markLinkClicked(UUID activationId) {
        log.info("Marking activation {} as link clicked", activationId);

        ActivationRecord record = findActivationOrThrow(activationId);

        if (record.getStatus() == ActivationStatus.LINK_CLICKED) {
            log.debug("Activation {} already in LINK_CLICKED state, ignoring", activationId);
            return;
        }

        record.getStatus().transitionTo(ActivationStatus.LINK_CLICKED);
        record.setStatus(ActivationStatus.LINK_CLICKED);
        record.setLinkClickedAt(Instant.now());
        activationRecordRepository.save(record);
    }

    @Override
    public Optional<ActivationStatusResponse> getStatus(UUID sessionId, String lastStatus) {
        Optional<ActivationRecord> optRecord = activationRecordRepository.findBySessionId(sessionId);
        
        if (optRecord.isEmpty()) {
            // No record found - return empty Optional (caller handles 404)
            return Optional.empty();
        }
        
        ActivationRecord record = optRecord.get();

        // If no lastStatus provided or status already changed, return immediately
        if (lastStatus == null || !record.getStatus().name().equals(lastStatus)) {
            return Optional.of(ActivationStatusResponse.from(record));
        }

        // Long-polling: wait up to 30 seconds for a status change
        long deadline = System.currentTimeMillis() + LONG_POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            record = activationRecordRepository.findBySessionId(sessionId).orElse(record);
            if (!record.getStatus().name().equals(lastStatus)) {
                return Optional.of(ActivationStatusResponse.from(record));
            }
        }

        // Timeout — return current status
        return Optional.of(ActivationStatusResponse.from(record));
    }

    @Override
    @Transactional
    public void handleActivationMessage(Long fatherId, String messageContent) {
        log.info("Handling activation message from father {}: '{}'", fatherId, messageContent);

        ActivationRecord record = activationRecordRepository.findByFatherId(new UUID(0L, fatherId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No activation record found for father: " + fatherId));

        // Skip if already completed
        if (record.getStatus().isTerminal()) {
            log.info("Activation for father {} already completed, ignoring message", fatherId);
            return;
        }

        // 1. Transition activation to MESSAGE_SENT
        record.setStatus(ActivationStatus.MESSAGE_SENT);
        record.setMessageReceivedAt(Instant.now());
        activationRecordRepository.save(record);

        // 2. Delegate father status transition to FatherService (ONBOARDING → ACTIVE)
        fatherService.activateFather(fatherId);

        // 3. Open session window via SessionWindowService
        openSessionWindow(fatherId);

        // 4. Deliver welcome conversation to the father
        //    This sends the initial greeting message before the workflow takes over
        deliverWelcomeConversation(fatherId, messageContent);

        // 5. Mark activation as CONVERSATION_STARTED
        record.setStatus(ActivationStatus.CONVERSATION_STARTED);
        record.setConversationStartedAt(Instant.now());
        activationRecordRepository.save(record);

        log.info("Activation completed successfully for father {}", fatherId);
    }

    @Override
    @Transactional
    public void handleActivationTimeout(UUID activationId) {
        log.info("Handling activation timeout for {}", activationId);

        ActivationRecord record = findActivationOrThrow(activationId);

        if (record.getStatus().isTerminal()) {
            log.info("Activation {} already terminal, ignoring timeout", activationId);
            return;
        }

        record.getStatus().transitionTo(ActivationStatus.FAILED);
        record.setStatus(ActivationStatus.FAILED);
        record.setFailureReason("Activation timed out");
        activationRecordRepository.save(record);
    }

    @Override
    public String generateDeepLink(Long fatherId, String language) {
        String normalizedLang = (language != null && language.startsWith("he")) ? "he" : "en";
        String message = ACTIVATION_MESSAGES.getOrDefault(normalizedLang, ACTIVATION_MESSAGES.get("en"));
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        // Strip + prefix and any non-digit chars from the phone number for wa.me link
        String cleanNumber = dadCoachPhoneNumber.replaceAll("[^\\d]", "");

        return String.format(DEEP_LINK_TEMPLATE, cleanNumber, encodedMessage);
    }

    @Override
    @Transactional
    public String retryActivation(UUID activationId) {
        log.info("Retrying activation {}", activationId);

        ActivationRecord record = findActivationOrThrow(activationId);

        if (record.getStatus() != ActivationStatus.FAILED) {
            throw new IllegalStateException(
                    "Cannot retry activation in status: " + record.getStatus() + ". Must be FAILED.");
        }

        if (record.getRetryCount() >= MAX_RETRIES) {
            throw new IllegalStateException(
                    "Maximum retries (" + MAX_RETRIES + ") exceeded for activation: " + activationId);
        }

        // Transition FAILED → PENDING
        record.getStatus().transitionTo(ActivationStatus.PENDING);
        record.setStatus(ActivationStatus.PENDING);
        record.setRetryCount(record.getRetryCount() + 1);
        record.setDeepLinkGeneratedAt(Instant.now());
        record.setFailureReason(null);
        activationRecordRepository.save(record);

        // Regenerate deep link (determine language from father's locale)
        var father = fatherService.getFather(record.getFatherId().getLeastSignificantBits());
        String language = father.getLocale() != null ? father.getLocale() : "he";

        String deepLink = generateDeepLink(record.getFatherId().getLeastSignificantBits(), language);
        log.info("Regenerated deep link for activation {} (retry {})", activationId, record.getRetryCount());

        return deepLink;
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private ActivationRecord findActivationOrThrow(UUID activationId) {
        return activationRecordRepository.findById(activationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Activation record not found: " + activationId));
    }

    private void openSessionWindow(Long fatherId) {
        // Find the father's primary WhatsApp endpoint and open the session window
        // Use same UUID derivation as ProvisioningServiceImpl for consistency
        UUID fatherUuid = new UUID(0L, fatherId);
        endpointRepository.findPrimaryByFatherId(fatherUuid)
                .or(() -> {
                    // Fall back to finding any endpoint for this father
                    var endpoints = endpointRepository.findByFatherId(fatherUuid);
                    return endpoints.isEmpty() ? Optional.empty()
                            : Optional.of(endpoints.get(0));
                })
                .ifPresent(endpoint -> {
                    sessionWindowService.onInboundMessage(endpoint);
                    log.info("Opened session window for father {}", fatherId);
                });
    }

    private void deliverWelcomeConversation(Long fatherId, String messageContent) {
        try {
            // Build coaching context for welcome message
            var father = fatherService.getFather(fatherId);
            String fatherName = father.getDisplayName() != null ? father.getDisplayName() : "Dad";

            CoachingContext context = new CoachingContext(
                    new UUID(0L, fatherId), // Use derived UUID for the coaching context
                    ConversationType.ONBOARDING,
                    messageContent,
                    List.of(),
                    buildWelcomeSystemPrompt(fatherName, father.getLocale()),
                    null,
                    null,
                    ""
            );

            CoachingResponse response = intelligenceLayer.generateCoachingResponse(context);

            // Deliver the welcome message
            UUID fatherUuid = new UUID(0L, fatherId);
            OutboundMessageDto welcomeMessage = new OutboundMessageDto(
                    UUID.randomUUID(),
                    fatherUuid,
                    null, // Use primary endpoint
                    MessageType.TEXT,
                    response.message(),
                    null,
                    false,
                    null,
                    null,
                    MessagePriority.IMMEDIATE,
                    Instant.now()
            );

            deliveryService.deliver(welcomeMessage);
            log.info("Welcome conversation delivered to father {}", fatherId);

        } catch (Exception e) {
            // Welcome delivery failure should not fail the activation
            log.error("Failed to deliver welcome conversation to father {}: {}", fatherId, e.getMessage(), e);
        }
    }

    private String buildWelcomeSystemPrompt(String fatherName, String locale) {
        if (locale != null && locale.startsWith("he")) {
            return String.format("""
                אתה "מאמן אבא" - מאמן הורות חם ותומך לאבות.
                צור הודעת פתיחה מותאמת אישית ל%s שזה עתה הפעיל את החשבון שלו.
                
                ההודעה צריכה להיות:
                - חמה ומעודדת
                - מסבירה שאתה כאן לעזור לו להיות אבא טוב יותר
                - קצרה (2-3 משפטים)
                
                אל תציע עזרה בנושאים שאינם קשורים להורות.
                """, fatherName);
        } else {
            return String.format("""
                You are "Dad Coach" - a warm and supportive parenting coach for fathers.
                Generate a personalized welcome message for %s who just activated their account.
                
                The message should be:
                - Warm and encouraging
                - Explain that you're here to help them be a better dad
                - Concise (2-3 sentences)
                
                Do not offer help with topics unrelated to parenting.
                """, fatherName);
        }
    }
}
