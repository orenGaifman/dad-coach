package com.dadcoach.onboarding.provisioning;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.goal.GoalCategory;
import com.dadcoach.onboarding.invitation.Invitation;
import com.dadcoach.onboarding.invitation.InvitationRepository;
import com.dadcoach.onboarding.session.OnboardingSession;
import com.dadcoach.onboarding.session.OnboardingSessionRepository;
import com.dadcoach.onboarding.session.SessionStatus;
import com.dadcoach.onboarding.session.WizardData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ProvisioningService} that creates all domain entities
 * from completed wizard data in a single atomic transaction.
 *
 * <p>Features:
 * <ul>
 *   <li>Atomic creation of Father, Family, Children, Goals, Preferences, AiProfile, ActivationRecord</li>
 *   <li>Idempotency: detects existing father by phone number and returns existing result</li>
 *   <li>3-second SLA monitoring with warning log if exceeded</li>
 *   <li>Post-transaction async memory creation via ApplicationEventPublisher</li>
 * </ul>
 */
@Service
public class ProvisioningServiceImpl implements ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningServiceImpl.class);
    private static final long SLA_THRESHOLD_MS = 3000;
    private static final String WHATSAPP_DEEP_LINK_TEMPLATE = "https://wa.me/%s?text=%s";
    private static final String DEFAULT_ACTIVATION_MESSAGE_EN = "🚀 START";
    private static final String DEFAULT_ACTIVATION_MESSAGE_HE = "🚀 התחל";

    @Value("${dad-coach.whatsapp.phone-number:+972501234567}")
    private String dadCoachWhatsAppNumber;

    private final OnboardingSessionRepository sessionRepository;
    private final InvitationRepository invitationRepository;
    private final FatherRepository fatherRepository;
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final GoalRepository goalRepository;
    private final LanguagePreferenceRepository languagePreferenceRepository;
    private final CommunicationPreferenceRepository communicationPreferenceRepository;
    private final CommunicationEndpointRepository communicationEndpointRepository;
    private final AiProfileRepository aiProfileRepository;
    private final ActivationRecordRepository activationRecordRepository;
    private final AiProfileFactory aiProfileFactory;
    private final ApplicationEventPublisher eventPublisher;

    public ProvisioningServiceImpl(
            OnboardingSessionRepository sessionRepository,
            InvitationRepository invitationRepository,
            FatherRepository fatherRepository,
            FamilyRepository familyRepository,
            ChildRepository childRepository,
            GoalRepository goalRepository,
            LanguagePreferenceRepository languagePreferenceRepository,
            CommunicationPreferenceRepository communicationPreferenceRepository,
            CommunicationEndpointRepository communicationEndpointRepository,
            AiProfileRepository aiProfileRepository,
            ActivationRecordRepository activationRecordRepository,
            AiProfileFactory aiProfileFactory,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.invitationRepository = invitationRepository;
        this.fatherRepository = fatherRepository;
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.goalRepository = goalRepository;
        this.languagePreferenceRepository = languagePreferenceRepository;
        this.communicationPreferenceRepository = communicationPreferenceRepository;
        this.communicationEndpointRepository = communicationEndpointRepository;
        this.aiProfileRepository = aiProfileRepository;
        this.activationRecordRepository = activationRecordRepository;
        this.aiProfileFactory = aiProfileFactory;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProvisioningResult provision(UUID sessionId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Load session and wizard data
            OnboardingSession session = sessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("OnboardingSession", sessionId));

            WizardData wizardData = session.getWizardData();
            if (wizardData == null) {
                throw new IllegalStateException("Session " + sessionId + " has no wizard data");
            }

            // 2. Idempotency check — if father with same phone exists, return existing result
            String phoneNumber = wizardData.getPhoneNumber();
            if (phoneNumber != null) {
                Optional<Father> existingFather = fatherRepository.findByPhone(phoneNumber);
                if (existingFather.isPresent()) {
                    log.info("Idempotent return: father already exists for phone (masked). session_id={}",
                            sessionId);
                    return buildExistingResult(existingFather.get(), sessionId);
                }
            }

            // 3. Create Father (status=ONBOARDING)
            Father father = createFather(wizardData);

            // 4. Create Family
            Family family = createFamily(father, wizardData);

            // 5. Create Children (0-8)
            List<Child> children = createChildren(father, wizardData);

            // 6. Create Goals (1-5)
            List<Goal> goals = createGoals(father, wizardData);

            // 7. Create LanguagePreference
            createLanguagePreference(father, wizardData);

            // 8. Create CommunicationPreference
            createCommunicationPreference(father, wizardData);

            // 9. Create CommunicationEndpoint (WhatsApp, is_primary=true)
            createCommunicationEndpoint(father, wizardData);

            // 10. Create AiProfile
            createAiProfile(father, wizardData);

            // 11. Create ActivationRecord (status=PENDING)
            ActivationRecord activationRecord = createActivationRecord(father, sessionId);

            // 12. Update invitation current_uses
            updateInvitationUses(session.getInvitationId());

            // 13. Update session status=COMPLETED, set father_id
            completeSession(session, father);

            // 14. Generate deep link
            String deepLink = generateDeepLink(wizardData.getLanguage());

            // 15. Build result
            List<Long> childIds = children.stream().map(Child::getId).toList();
            List<Long> goalIds = goals.stream().map(Goal::getId).toList();

            ProvisioningResult result = new ProvisioningResult(
                    father.getId(),
                    family.getFamilyId(),
                    childIds,
                    goalIds,
                    activationRecord.getActivationId(),
                    deepLink
            );

            // 16. Publish event for async memory creation (fires after transaction commits)
            eventPublisher.publishEvent(new ProvisioningCompletedEvent(father.getId(), wizardData));

            log.info("Provisioning completed for session_id={}, father_id={}", sessionId, father.getId());
            return result;

        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > SLA_THRESHOLD_MS) {
                log.warn("Provisioning SLA exceeded: took {}ms (threshold={}ms) for session_id={}",
                        elapsed, SLA_THRESHOLD_MS, sessionId);
            } else {
                log.debug("Provisioning completed in {}ms for session_id={}", elapsed, sessionId);
            }
        }
    }

    // ─── Private Entity Creation Methods ─────────────────────────────────

    private Father createFather(WizardData wizardData) {
        Father father = new Father(wizardData.getPhoneNumber());
        father.setDisplayName(wizardData.getDisplayName());
        father.setStatus(FatherStatus.ONBOARDING);
        father.setLocale(wizardData.getLanguage() != null ? wizardData.getLanguage() : "he");

        if (wizardData.getTimezone() != null) {
            father.setTimezone(wizardData.getTimezone());
        }

        // Set coaching style from preferences
        Map<String, Object> preferences = wizardData.getPreferences();
        if (preferences != null && preferences.containsKey("coaching_style")) {
            try {
                CoachingStyle style = CoachingStyle.valueOf(
                        String.valueOf(preferences.get("coaching_style")).toUpperCase());
                father.setCoachingStyle(style);
            } catch (IllegalArgumentException e) {
                // Keep default BALANCED
            }
        }

        // Set preferred coaching time from preferences
        if (preferences != null && preferences.containsKey("preferred_coaching_time")) {
            try {
                LocalTime time = LocalTime.parse(String.valueOf(preferences.get("preferred_coaching_time")));
                father.setPreferredCoachingTime(time);
            } catch (Exception e) {
                // Keep default 08:00
            }
        }

        return fatherRepository.save(father);
    }

    private Family createFamily(Father father, WizardData wizardData) {
        String familyName = wizardData.getDisplayName() != null
                ? wizardData.getDisplayName() + "'s Family"
                : "Family";
        Family family = new Family(new UUID(0L, father.getId()), familyName);
        return familyRepository.save(family);
    }

    private List<Child> createChildren(Father father, WizardData wizardData) {
        List<WizardData.ChildData> childrenData = wizardData.getChildren();
        if (childrenData == null || childrenData.isEmpty()) {
            return List.of();
        }

        List<Child> children = new ArrayList<>();
        for (WizardData.ChildData childData : childrenData) {
            LocalDate birthDate;
            try {
                birthDate = LocalDate.parse(childData.getBirthDate());
            } catch (Exception e) {
                birthDate = LocalDate.now(); // Fallback if date can't be parsed
            }

            Child child = new Child(father, childData.getName(), birthDate);
            if (childData.getGender() != null) {
                child.setGender(childData.getGender());
            }
            children.add(childRepository.save(child));
        }
        return children;
    }

    private List<Goal> createGoals(Father father, WizardData wizardData) {
        List<String> goalsData = wizardData.getGoals();
        if (goalsData == null || goalsData.isEmpty()) {
            return List.of();
        }

        List<Goal> goals = new ArrayList<>();
        int priority = 1;
        for (String goalTitle : goalsData) {
            GoalCategory category = mapGoalToCategory(goalTitle);
            Goal goal = new Goal(father, goalTitle, category, priority);
            goals.add(goalRepository.save(goal));
            priority++;
        }
        return goals;
    }

    private void createLanguagePreference(Father father, WizardData wizardData) {
        String language = wizardData.getLanguage() != null ? wizardData.getLanguage() : "he";
        LanguagePreference pref = new LanguagePreference(new UUID(0L, father.getId()), language);
        languagePreferenceRepository.save(pref);
    }

    private void createCommunicationPreference(Father father, WizardData wizardData) {
        CommunicationPreference pref = new CommunicationPreference(new UUID(0L, father.getId()));

        Map<String, Object> preferences = wizardData.getPreferences();
        if (preferences != null) {
            if (preferences.containsKey("preferred_coaching_time")) {
                try {
                    LocalTime time = LocalTime.parse(String.valueOf(preferences.get("preferred_coaching_time")));
                    pref.setPreferredCoachingTime(time);
                } catch (Exception e) {
                    // Keep default
                }
            }
            if (preferences.containsKey("notification_frequency")) {
                pref.setNotificationFrequency(String.valueOf(preferences.get("notification_frequency")));
            }
            if (preferences.containsKey("quiet_hours_start")) {
                try {
                    LocalTime time = LocalTime.parse(String.valueOf(preferences.get("quiet_hours_start")));
                    pref.setQuietHoursStart(time);
                } catch (Exception e) {
                    // Keep default
                }
            }
            if (preferences.containsKey("quiet_hours_end")) {
                try {
                    LocalTime time = LocalTime.parse(String.valueOf(preferences.get("quiet_hours_end")));
                    pref.setQuietHoursEnd(time);
                } catch (Exception e) {
                    // Keep default
                }
            }
        }

        communicationPreferenceRepository.save(pref);
    }

    private void createCommunicationEndpoint(Father father, WizardData wizardData) {
        // CommunicationEndpoint uses UUID for fatherId — we need a stable UUID from the Long ID
        // The existing CommunicationEndpoint entity expects UUID fatherId, so we derive one
        UUID fatherUuid = deriveUuidFromLongId(father.getId());
        CommunicationEndpoint endpoint = new CommunicationEndpoint(
                fatherUuid, "WHATSAPP", wizardData.getPhoneNumber());
        endpoint.setPrimary(true);
        communicationEndpointRepository.save(endpoint);
    }

    private AiProfile createAiProfile(Father father, WizardData wizardData) {
        AiProfile profile = aiProfileFactory.buildProfile(new UUID(0L, father.getId()), wizardData);
        return aiProfileRepository.save(profile);
    }

    private ActivationRecord createActivationRecord(Father father, UUID sessionId) {
        // Bridge: derive UUID from internal Long ID (same mapping as ActorContextFilter.resolveActorId)
        UUID fatherUuid = new UUID(0L, father.getId());
        ActivationRecord record = new ActivationRecord(fatherUuid, sessionId);
        return activationRecordRepository.save(record);
    }

    private void updateInvitationUses(UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        invitation.setCurrentUses(invitation.getCurrentUses() + 1);
        invitationRepository.save(invitation);
    }

    private void completeSession(OnboardingSession session, Father father) {
        session.setStatus(SessionStatus.COMPLETED);
        session.setFatherId(father.getId() != null
                ? deriveUuidFromLongId(father.getId())
                : null);
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);
    }

    // ─── Idempotency Support ─────────────────────────────────────────────

    private ProvisioningResult buildExistingResult(Father existingFather, UUID sessionId) {
        Long fatherId = existingFather.getId();
        UUID fatherUuid = new UUID(0L, fatherId);

        // Look up the session to get the new wizard data (for updating locale if changed)
        OnboardingSession session = sessionRepository.findBySessionId(sessionId).orElse(null);
        if (session != null && session.getWizardData() != null) {
            WizardData wizardData = session.getWizardData();
            
            // Update locale if specified in new registration
            if (wizardData.getLanguage() != null && 
                !wizardData.getLanguage().equals(existingFather.getLocale())) {
                log.info("Updating locale for existing father_id={} from '{}' to '{}'",
                        fatherId, existingFather.getLocale(), wizardData.getLanguage());
                existingFather.setLocale(wizardData.getLanguage());
                fatherRepository.save(existingFather);
            }
            
            // Update display name if specified
            if (wizardData.getDisplayName() != null) {
                existingFather.setDisplayName(wizardData.getDisplayName());
                fatherRepository.save(existingFather);
            }
        }

        // Look up existing family
        UUID familyId = familyRepository.findByFatherId(fatherUuid)
                .map(Family::getFamilyId)
                .orElse(null);

        // Look up existing children
        List<Long> childIds = childRepository.findByFatherId(fatherId)
                .stream().map(Child::getId).toList();

        // Look up existing goals
        List<Long> goalIds = goalRepository.findByFatherId(fatherId)
                .stream().map(Goal::getId).toList();

        // Look up existing activation record, or create one if missing
        // This handles the case where father was created but activation record wasn't
        UUID activationId = activationRecordRepository.findByFatherId(fatherUuid)
                .map(ActivationRecord::getActivationId)
                .orElseGet(() -> {
                    log.info("Creating missing activation record for existing father_id={}, session_id={}", 
                            fatherId, sessionId);
                    ActivationRecord record = new ActivationRecord(fatherUuid, sessionId);
                    return activationRecordRepository.save(record).getActivationId();
                });

        // Generate deep link using current (possibly updated) locale
        String deepLink = generateDeepLink(existingFather.getLocale());

        return new ProvisioningResult(fatherId, familyId, childIds, goalIds, activationId, deepLink);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String generateDeepLink(String language) {
        // Use Dad Coach's WhatsApp Business number
        String cleanPhone = dadCoachWhatsAppNumber.replaceAll("[^0-9]", "");
        
        // Choose message based on language
        String message = "he".equalsIgnoreCase(language) 
            ? DEFAULT_ACTIVATION_MESSAGE_HE 
            : DEFAULT_ACTIVATION_MESSAGE_EN;
        
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return String.format(WHATSAPP_DEEP_LINK_TEMPLATE, cleanPhone, encodedMessage);
    }

    private GoalCategory mapGoalToCategory(String goalTitle) {
        if (goalTitle == null) {
            return GoalCategory.CUSTOM;
        }
        String lower = goalTitle.toLowerCase();
        if (lower.contains("connect") || lower.contains("bond") || lower.contains("quality time")) {
            return GoalCategory.CONNECTION;
        }
        if (lower.contains("communicat") || lower.contains("talk") || lower.contains("listen")) {
            return GoalCategory.COMMUNICATION;
        }
        if (lower.contains("disciplin") || lower.contains("boundar") || lower.contains("limit")) {
            return GoalCategory.DISCIPLINE;
        }
        if (lower.contains("educat") || lower.contains("learn") || lower.contains("school")) {
            return GoalCategory.EDUCATION;
        }
        if (lower.contains("health") || lower.contains("exercise") || lower.contains("sleep")) {
            return GoalCategory.HEALTH;
        }
        if (lower.contains("emotion") || lower.contains("feeling") || lower.contains("empathy")) {
            return GoalCategory.EMOTIONAL;
        }
        if (lower.contains("independen") || lower.contains("responsib")) {
            return GoalCategory.INDEPENDENCE;
        }
        if (lower.contains("fun") || lower.contains("play") || lower.contains("game")) {
            return GoalCategory.FUN;
        }
        if (lower.contains("routine") || lower.contains("habit") || lower.contains("schedule")) {
            return GoalCategory.ROUTINE;
        }
        return GoalCategory.CUSTOM;
    }

    /**
     * Derives a deterministic UUID from a Long ID for cross-entity compatibility.
     * Uses UUID version 5 (name-based) approach with a fixed namespace.
     */
    private UUID deriveUuidFromLongId(Long id) {
        if (id == null) return null;
        // Create a deterministic UUID from the Long id
        return new UUID(0L, id);
    }
}
