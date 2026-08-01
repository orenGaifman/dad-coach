package com.dadcoach.onboarding.activation;

import com.dadcoach.ai.IntelligenceLayer;
import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.channel.delivery.DeliveryResult;
import com.dadcoach.channel.delivery.DeliveryService;
import com.dadcoach.channel.session.SessionWindowService;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.onboarding.provisioning.ActivationRecord;
import com.dadcoach.onboarding.provisioning.ActivationRecordRepository;
import com.dadcoach.onboarding.provisioning.ActivationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivationServiceImpl Unit Tests")
class ActivationServiceImplTest {

    @Mock private ActivationRecordRepository activationRecordRepository;
    @Mock private FatherService fatherService;
    @Mock private SessionWindowService sessionWindowService;
    @Mock private CommunicationEndpointRepository endpointRepository;
    @Mock private IntelligenceLayer intelligenceLayer;
    @Mock private DeliveryService deliveryService;

    private ActivationServiceImpl activationService;

    private static final Long FATHER_ID = 1L;
    private static final UUID FATHER_UUID = new UUID(0L, FATHER_ID);
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ACTIVATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        activationService = new ActivationServiceImpl(
                activationRecordRepository,
                fatherService,
                sessionWindowService,
                endpointRepository,
                intelligenceLayer,
                deliveryService
        );
        // Set the @Value field that Spring would normally inject
        org.springframework.test.util.ReflectionTestUtils.setField(
                activationService, "dadCoachPhoneNumber", "+972501234567");
    }

    @Nested
    @DisplayName("createPendingActivation")
    class CreatePendingActivationTests {

        @Test
        @DisplayName("creates new activation record with PENDING status")
        void createsNewRecord() {
            when(activationRecordRepository.findByFatherId(FATHER_UUID)).thenReturn(Optional.empty());
            when(activationRecordRepository.save(any(ActivationRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ActivationRecord result = activationService.createPendingActivation(FATHER_UUID, SESSION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getFatherId()).isEqualTo(FATHER_UUID);
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getStatus()).isEqualTo(ActivationStatus.PENDING);
            verify(activationRecordRepository).save(any(ActivationRecord.class));
        }

        @Test
        @DisplayName("returns existing record if already exists (idempotent)")
        void returnsExistingRecord() {
            ActivationRecord existing = new ActivationRecord(FATHER_UUID, SESSION_ID);
            when(activationRecordRepository.findByFatherId(FATHER_UUID)).thenReturn(Optional.of(existing));

            ActivationRecord result = activationService.createPendingActivation(FATHER_UUID, SESSION_ID);

            assertThat(result).isSameAs(existing);
            verify(activationRecordRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markLinkClicked")
    class MarkLinkClickedTests {

        @Test
        @DisplayName("transitions PENDING to LINK_CLICKED")
        void transitionsPendingToLinkClicked() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            activationService.markLinkClicked(ACTIVATION_ID);

            assertThat(record.getStatus()).isEqualTo(ActivationStatus.LINK_CLICKED);
            assertThat(record.getLinkClickedAt()).isNotNull();
            verify(activationRecordRepository).save(record);
        }

        @Test
        @DisplayName("ignores if already in LINK_CLICKED state")
        void ignoresIfAlreadyClicked() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.LINK_CLICKED);
            record.setLinkClickedAt(Instant.now().minusSeconds(60));
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));

            activationService.markLinkClicked(ACTIVATION_ID);

            verify(activationRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when activation record not found")
        void throwsWhenNotFound() {
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> activationService.markLinkClicked(ACTIVATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Activation record not found");
        }
    }

    @Nested
    @DisplayName("handleActivationMessage")
    class HandleActivationMessageTests {

        @Test
        @DisplayName("completes full activation flow on first message")
        void completesActivationFlow() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            when(activationRecordRepository.findByFatherId(FATHER_UUID)).thenReturn(Optional.of(record));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Father father = new Father("+972501234567");
            father.setId(FATHER_ID);
            father.setDisplayName("Test Dad");
            father.setLocale("en");
            father.setStatus(FatherStatus.ACTIVE);
            when(fatherService.activateFather(FATHER_ID)).thenReturn(father);

            CommunicationEndpoint endpoint = new CommunicationEndpoint(FATHER_UUID, "WHATSAPP", "+972501234567");
            when(endpointRepository.findPrimaryByFatherId(FATHER_UUID)).thenReturn(Optional.of(endpoint));

            activationService.handleActivationMessage(FATHER_ID, "🚀 START");

            // Verify delegation to FatherService
            verify(fatherService).activateFather(FATHER_ID);

            // Verify session window opened
            verify(sessionWindowService).onInboundMessage(endpoint);

            // Welcome message is NOT sent here anymore - ConversationOrchestrator handles it
            // to prevent duplicate messages
            verify(intelligenceLayer, never()).generateCoachingResponse(any());
            verify(deliveryService, never()).deliver(any());

            // Verify final status
            assertThat(record.getStatus()).isEqualTo(ActivationStatus.CONVERSATION_STARTED);
            assertThat(record.getMessageReceivedAt()).isNotNull();
            assertThat(record.getConversationStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("skips already-completed activation")
        void skipsCompletedActivation() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setStatus(ActivationStatus.CONVERSATION_STARTED);
            when(activationRecordRepository.findByFatherId(FATHER_UUID)).thenReturn(Optional.of(record));

            activationService.handleActivationMessage(FATHER_ID, "hello");

            verify(fatherService, never()).activateFather(any());
            verify(deliveryService, never()).deliver(any());
        }

        @Test
        @DisplayName("any message triggers activation, not just START")
        void anyMessageTriggersActivation() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            when(activationRecordRepository.findByFatherId(FATHER_UUID)).thenReturn(Optional.of(record));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Father father = new Father("+972501234567");
            father.setId(FATHER_ID);
            father.setDisplayName("Test Dad");
            father.setLocale("he");
            father.setStatus(FatherStatus.ACTIVE);
            when(fatherService.activateFather(FATHER_ID)).thenReturn(father);

            when(endpointRepository.findPrimaryByFatherId(FATHER_UUID)).thenReturn(Optional.empty());
            when(endpointRepository.findByFatherId(FATHER_UUID)).thenReturn(List.of());

            // Use a random message instead of "🚀 START"
            activationService.handleActivationMessage(FATHER_ID, "שלום, אני מוכן להתחיל!");

            verify(fatherService).activateFather(FATHER_ID);
            assertThat(record.getStatus()).isEqualTo(ActivationStatus.CONVERSATION_STARTED);
            
            // Welcome message is NOT sent here anymore - ConversationOrchestrator handles it
            verify(intelligenceLayer, never()).generateCoachingResponse(any());
            verify(deliveryService, never()).deliver(any());
        }
    }

    @Nested
    @DisplayName("handleActivationTimeout")
    class HandleActivationTimeoutTests {

        @Test
        @DisplayName("transitions to FAILED on timeout")
        void transitionsToFailed() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.PENDING);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            activationService.handleActivationTimeout(ACTIVATION_ID);

            assertThat(record.getStatus()).isEqualTo(ActivationStatus.FAILED);
            assertThat(record.getFailureReason()).isEqualTo("Activation timed out");
        }

        @Test
        @DisplayName("ignores timeout for terminal activation")
        void ignoresTerminalActivation() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.CONVERSATION_STARTED);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));

            activationService.handleActivationTimeout(ACTIVATION_ID);

            verify(activationRecordRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("generateDeepLink")
    class GenerateDeepLinkTests {

        @Test
        @DisplayName("generates deep link with English message")
        void generatesEnglishLink() {
            String link = activationService.generateDeepLink(FATHER_ID, "en");

            assertThat(link).startsWith("https://wa.me/");
            assertThat(link).contains("text=");
            // URL-encoded "🚀 START"
            assertThat(link).contains("%F0%9F%9A%80");
            assertThat(link).contains("START");
        }

        @Test
        @DisplayName("generates deep link with Hebrew message")
        void generatesHebrewLink() {
            String link = activationService.generateDeepLink(FATHER_ID, "he");

            assertThat(link).startsWith("https://wa.me/");
            assertThat(link).contains("text=");
            // URL-encoded "🚀 התחל"
            assertThat(link).contains("%F0%9F%9A%80");
            // Hebrew characters are URL-encoded
            assertThat(link).doesNotContain("START");
        }

        @Test
        @DisplayName("defaults to English for unknown language")
        void defaultsToEnglish() {
            String link = activationService.generateDeepLink(FATHER_ID, "fr");

            assertThat(link).contains("START");
        }

        @Test
        @DisplayName("strips non-digit characters from phone number")
        void stripsNonDigits() {
            String link = activationService.generateDeepLink(FATHER_ID, "en");

            // The link should not contain '+' in the phone number part
            String phoneInLink = link.replace("https://wa.me/", "").split("\\?")[0];
            assertThat(phoneInLink).matches("\\d+");
        }
    }

    @Nested
    @DisplayName("retryActivation")
    class RetryActivationTests {

        @Test
        @DisplayName("retries failed activation by transitioning FAILED→PENDING")
        void retriesFailed() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.FAILED);
            record.setRetryCount(1);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));
            when(activationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Father father = new Father("+972501234567");
            father.setId(FATHER_ID);
            father.setLocale("en");
            when(fatherService.getFather(FATHER_ID)).thenReturn(father);

            String deepLink = activationService.retryActivation(ACTIVATION_ID);

            assertThat(deepLink).startsWith("https://wa.me/");
            assertThat(record.getStatus()).isEqualTo(ActivationStatus.PENDING);
            assertThat(record.getRetryCount()).isEqualTo(2);
            assertThat(record.getFailureReason()).isNull();
            assertThat(record.getDeepLinkGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("throws when max retries exceeded")
        void throwsWhenMaxRetriesExceeded() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.FAILED);
            record.setRetryCount(3);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> activationService.retryActivation(ACTIVATION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maximum retries");
        }

        @Test
        @DisplayName("throws when status is not FAILED")
        void throwsWhenNotFailed() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setActivationId(ACTIVATION_ID);
            record.setStatus(ActivationStatus.PENDING);
            when(activationRecordRepository.findById(ACTIVATION_ID)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> activationService.retryActivation(ACTIVATION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot retry activation in status: PENDING");
        }
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @Test
        @DisplayName("returns immediately when status has changed")
        void returnsImmediatelyWhenChanged() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setStatus(ActivationStatus.LINK_CLICKED);
            when(activationRecordRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(record));

            var response = activationService.getStatus(SESSION_ID, "PENDING");

            assertThat(response).isPresent();
            assertThat(response.get().status()).isEqualTo(ActivationStatus.LINK_CLICKED);
        }

        @Test
        @DisplayName("returns immediately when no lastStatus provided")
        void returnsImmediatelyWhenNoLastStatus() {
            ActivationRecord record = new ActivationRecord(FATHER_UUID, SESSION_ID);
            record.setStatus(ActivationStatus.PENDING);
            when(activationRecordRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(record));

            var response = activationService.getStatus(SESSION_ID, null);

            assertThat(response).isPresent();
            assertThat(response.get().status()).isEqualTo(ActivationStatus.PENDING);
        }

        @Test
        @DisplayName("returns empty when session not found")
        void returnsEmptyWhenSessionNotFound() {
            when(activationRecordRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            var response = activationService.getStatus(SESSION_ID, null);

            assertThat(response).isEmpty();
        }
    }
}
