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
import com.dadcoach.father.FatherStatus;
import com.dadcoach.onboarding.invitation.Invitation;
import com.dadcoach.onboarding.invitation.InvitationRepository;
import com.dadcoach.onboarding.session.OnboardingSession;
import com.dadcoach.onboarding.session.OnboardingSessionRepository;
import com.dadcoach.onboarding.session.SessionStatus;
import com.dadcoach.onboarding.session.WizardData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvisioningServiceImplTest {

    @Mock private OnboardingSessionRepository sessionRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private FatherRepository fatherRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private ChildRepository childRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private LanguagePreferenceRepository languagePreferenceRepository;
    @Mock private CommunicationPreferenceRepository communicationPreferenceRepository;
    @Mock private CommunicationEndpointRepository communicationEndpointRepository;
    @Mock private AiProfileRepository aiProfileRepository;
    @Mock private ActivationRecordRepository activationRecordRepository;
    @Mock private AiProfileFactory aiProfileFactory;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProvisioningServiceImpl provisioningService;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID INVITATION_ID = UUID.randomUUID();
    private static final String PHONE_NUMBER = "+972501234567";

    @BeforeEach
    void setUp() throws Exception {
        provisioningService = new ProvisioningServiceImpl(
                sessionRepository, invitationRepository, fatherRepository,
                familyRepository, childRepository, goalRepository,
                languagePreferenceRepository, communicationPreferenceRepository,
                communicationEndpointRepository, aiProfileRepository,
                activationRecordRepository, aiProfileFactory, eventPublisher);
        
        // Set the @Value field via reflection since we're using MockitoExtension (not Spring)
        var field = ProvisioningServiceImpl.class.getDeclaredField("dadCoachWhatsAppNumber");
        field.setAccessible(true);
        field.set(provisioningService, "+972501234567");
    }

    private OnboardingSession createSession(WizardData wizardData) {
        try {
            var constructor = OnboardingSession.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            OnboardingSession session = constructor.newInstance();
            session.setSessionId(SESSION_ID);
            session.setInvitationId(INVITATION_ID);
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setWizardData(wizardData);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WizardData createCompleteWizardData() {
        WizardData data = new WizardData();
        data.setDisplayName("David");
        data.setPhoneNumber(PHONE_NUMBER);
        data.setEmail("david@example.com");
        data.setTimezone("Asia/Jerusalem");
        data.setLanguage("he");
        data.setChildren(List.of(
                new WizardData.ChildData("Yoav", "2018-05-15", "male"),
                new WizardData.ChildData("Noa", "2020-11-03", "female")
        ));
        data.setGoals(List.of("Better communication", "More quality time"));
        data.getPreferences().put("coaching_style", "BALANCED");
        data.getPreferences().put("preferred_coaching_time", "08:00");
        data.getPreferences().put("notification_frequency", "DAILY");
        return data;
    }

    private Invitation createInvitation() {
        try {
            var constructor = Invitation.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Invitation inv = constructor.newInstance();
            inv.setInvitationId(INVITATION_ID);
            inv.setCurrentUses(2);
            inv.setMaxUses(50);
            return inv;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── Provision Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("provision")
    class ProvisionTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provisioningService.provision(SESSION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("OnboardingSession");
        }

        @Test
        @DisplayName("should throw IllegalStateException when wizard data is null")
        void shouldThrowWhenWizardDataNull() {
            OnboardingSession session = createSession(null);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> provisioningService.provision(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no wizard data");
        }

        @Test
        @DisplayName("should return existing result when father with same phone exists (idempotency)")
        void shouldReturnExistingResultWhenFatherExists() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

            Father existingFather = new Father(PHONE_NUMBER);
            existingFather.setId(42L);
            existingFather.setLocale("he"); // Same as in wizard data, so no save needed for locale
            existingFather.setDisplayName("David"); // Same as in wizard data
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.of(existingFather));
            when(fatherRepository.save(any(Father.class))).thenAnswer(inv -> inv.getArgument(0));

            // Setup existing entities for idempotent result
            Family existingFamily = new Family(new UUID(0L, 42L), "David's Family");
            when(familyRepository.findByFatherId(new UUID(0L, 42L))).thenReturn(Optional.of(existingFamily));
            when(childRepository.findByFatherId(42L)).thenReturn(List.of());
            when(goalRepository.findByFatherId(42L)).thenReturn(List.of());
            when(activationRecordRepository.findByFatherId(new UUID(0L, 42L))).thenReturn(Optional.empty());
            
            // Mock save for creating missing activation record (service creates one if missing)
            ActivationRecord mockActivation = new ActivationRecord(new UUID(0L, 42L), SESSION_ID);
            when(activationRecordRepository.save(any(ActivationRecord.class))).thenReturn(mockActivation);

            ProvisioningResult result = provisioningService.provision(SESSION_ID);

            assertThat(result.fatherId()).isEqualTo(42L);
            // No new family should be created (existing father case)
            verify(familyRepository, never()).save(any(Family.class));
        }

        @Test
        @DisplayName("should create all entities atomically on new provisioning")
        void shouldCreateAllEntitiesAtomically() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            // Mock save operations
            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            savedFather.setDisplayName("David");
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);

            Family savedFamily = new Family(new UUID(0L, 1L), "David's Family");
            when(familyRepository.save(any(Family.class))).thenReturn(savedFamily);

            when(childRepository.save(any(Child.class))).thenAnswer(inv -> {
                Child c = inv.getArgument(0);
                c.setId(100L);
                return c;
            });

            when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
                Goal g = inv.getArgument(0);
                g.setId(200L);
                return g;
            });

            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AiProfile mockProfile = new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "context", "goals", "brief");
            when(aiProfileFactory.buildProfile(eq(new UUID(0L, 1L)), any(WizardData.class))).thenReturn(mockProfile);
            when(aiProfileRepository.save(any())).thenReturn(mockProfile);

            ActivationRecord mockActivation = new ActivationRecord(new UUID(0L, 1L), SESSION_ID);
            when(activationRecordRepository.save(any())).thenReturn(mockActivation);

            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProvisioningResult result = provisioningService.provision(SESSION_ID);

            // Verify all entities were created
            assertThat(result.fatherId()).isEqualTo(1L);
            assertThat(result.childIds()).hasSize(2);
            assertThat(result.goalIds()).hasSize(2);
            assertThat(result.deepLink()).contains("wa.me");

            verify(fatherRepository).save(any(Father.class));
            verify(familyRepository).save(any(Family.class));
            verify(childRepository, times(2)).save(any(Child.class));
            verify(goalRepository, times(2)).save(any(Goal.class));
            verify(languagePreferenceRepository).save(any(LanguagePreference.class));
            verify(communicationPreferenceRepository).save(any(CommunicationPreference.class));
            verify(communicationEndpointRepository).save(any(CommunicationEndpoint.class));
            verify(aiProfileRepository).save(any(AiProfile.class));
            verify(activationRecordRepository).save(any(ActivationRecord.class));
        }

        @Test
        @DisplayName("should set father status to ONBOARDING")
        void shouldSetFatherStatusOnboarding() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(childRepository.save(any())).thenAnswer(inv -> { Child c = inv.getArgument(0); c.setId(1L); return c; });
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            provisioningService.provision(SESSION_ID);

            ArgumentCaptor<Father> fatherCaptor = ArgumentCaptor.forClass(Father.class);
            verify(fatherRepository).save(fatherCaptor.capture());
            assertThat(fatherCaptor.getValue().getStatus()).isEqualTo(FatherStatus.ONBOARDING);
        }

        @Test
        @DisplayName("should update invitation current_uses")
        void shouldUpdateInvitationUses() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(childRepository.save(any())).thenAnswer(inv -> { Child c = inv.getArgument(0); c.setId(1L); return c; });
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            provisioningService.provision(SESSION_ID);

            ArgumentCaptor<Invitation> invCaptor = ArgumentCaptor.forClass(Invitation.class);
            verify(invitationRepository).save(invCaptor.capture());
            assertThat(invCaptor.getValue().getCurrentUses()).isEqualTo(3); // was 2, now 3
        }

        @Test
        @DisplayName("should update session status to COMPLETED")
        void shouldUpdateSessionToCompleted() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(childRepository.save(any())).thenAnswer(inv -> { Child c = inv.getArgument(0); c.setId(1L); return c; });
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            provisioningService.provision(SESSION_ID);

            ArgumentCaptor<OnboardingSession> sessionCaptor = ArgumentCaptor.forClass(OnboardingSession.class);
            verify(sessionRepository).save(sessionCaptor.capture());
            assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(sessionCaptor.getValue().getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should publish provisioning completed event for async memory creation")
        void shouldPublishProvisioningCompletedEvent() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(childRepository.save(any())).thenAnswer(inv -> { Child c = inv.getArgument(0); c.setId(1L); return c; });
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            provisioningService.provision(SESSION_ID);

            ArgumentCaptor<ProvisioningCompletedEvent> eventCaptor =
                    ArgumentCaptor.forClass(ProvisioningCompletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().fatherId()).isEqualTo(1L);
            assertThat(eventCaptor.getValue().wizardData()).isEqualTo(wizardData);
        }

        @Test
        @DisplayName("should handle zero children gracefully")
        void shouldHandleZeroChildren() {
            WizardData wizardData = createCompleteWizardData();
            wizardData.setChildren(List.of()); // No children
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProvisioningResult result = provisioningService.provision(SESSION_ID);

            assertThat(result.childIds()).isEmpty();
            verify(childRepository, never()).save(any(Child.class));
        }

        @Test
        @DisplayName("should generate WhatsApp deep link with correct format")
        void shouldGenerateDeepLink() {
            WizardData wizardData = createCompleteWizardData();
            OnboardingSession session = createSession(wizardData);
            when(sessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            when(fatherRepository.findByPhone(PHONE_NUMBER)).thenReturn(Optional.empty());

            Father savedFather = new Father(PHONE_NUMBER);
            savedFather.setId(1L);
            when(fatherRepository.save(any(Father.class))).thenReturn(savedFather);
            when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(childRepository.save(any())).thenAnswer(inv -> { Child c = inv.getArgument(0); c.setId(1L); return c; });
            when(goalRepository.save(any())).thenAnswer(inv -> { Goal g = inv.getArgument(0); g.setId(1L); return g; });
            when(languagePreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(communicationEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(aiProfileFactory.buildProfile(any(), any())).thenReturn(new AiProfile(new UUID(0L, 1L), "BALANCED", "he", "", "", ""));
            when(aiProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Invitation invitation = createInvitation();
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(any())).thenReturn(invitation);
            when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProvisioningResult result = provisioningService.provision(SESSION_ID);

            assertThat(result.deepLink()).startsWith("https://wa.me/972501234567");
            assertThat(result.deepLink()).contains("text=");
        }
    }
}
