package com.dadcoach.conversation;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationServiceImpl Unit Tests")
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationProperties conversationProperties;
    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationProperties = new ConversationProperties();
        conversationProperties.setExpirationWindows(Map.of(
                "ONBOARDING", Duration.ofHours(48),
                "DAILY_COACHING", Duration.ofHours(24),
                "FOLLOW_UP", Duration.ofHours(24),
                "REFLECTION", Duration.ofHours(24),
                "INACTIVITY_CHECK", Duration.ofHours(48),
                "CELEBRATION", Duration.ofHours(24)
        ));
        conversationService = new ConversationServiceImpl(conversationRepository, conversationProperties);
    }

    // ─── 5.1: Enforce maximum 1 ACTIVE conversation per father ───────────

    @Nested
    @DisplayName("5.1 - Maximum 1 ACTIVE conversation per father")
    class MaxOneActiveConversation {

        @Test
        @DisplayName("Creates conversation when no active conversation exists")
        void createConversation_noActiveExists_createsSuccessfully() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "DAILY_COACHING");

            assertThat(result).isNotNull();
            assertThat(result.getFatherId()).isEqualTo(fatherId);
            assertThat(result.getType()).isEqualTo("DAILY_COACHING");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Throws IllegalStateException when father already has active conversation (non-DIFFICULT_SITUATION)")
        void createConversation_activeExists_throwsIllegalState() {
            UUID fatherId = UUID.randomUUID();
            Conversation existingActive = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(existingActive));

            assertThatThrownBy(() -> conversationService.createConversation(fatherId, "FOLLOW_UP"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has an active conversation");
        }

        @Test
        @DisplayName("Prevents creating second active conversation of same type")
        void createConversation_sameTypeActiveExists_throwsIllegalState() {
            UUID fatherId = UUID.randomUUID();
            Conversation existingActive = Conversation.builder()
                    .fatherId(fatherId)
                    .type("ONBOARDING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(existingActive));

            assertThatThrownBy(() -> conversationService.createConversation(fatherId, "ONBOARDING"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── 5.2: DIFFICULT_SITUATION preempts existing active conversation ──

    @Nested
    @DisplayName("5.2 - DIFFICULT_SITUATION preemption")
    class DifficultSituationPreemption {

        @Test
        @DisplayName("DIFFICULT_SITUATION preempts existing active conversation")
        void createConversation_difficultSituation_preemptsExisting() {
            UUID fatherId = UUID.randomUUID();
            UUID existingId = UUID.randomUUID();
            Conversation existingActive = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(existingActive));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "DIFFICULT_SITUATION");

            // Verify existing conversation was completed with PREEMPTED reason
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository, times(2)).save(captor.capture());

            Conversation preempted = captor.getAllValues().get(0);
            assertThat(preempted.getStatus()).isEqualTo("COMPLETED");
            assertThat(preempted.getCompletionReason()).isEqualTo("PREEMPTED");
            assertThat(preempted.getCompletedAt()).isNotNull();

            // Verify new DIFFICULT_SITUATION conversation was created
            assertThat(result.getType()).isEqualTo("DIFFICULT_SITUATION");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("DIFFICULT_SITUATION has no expiration window (expiresAt is null)")
        void createConversation_difficultSituation_noExpiration() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "DIFFICULT_SITUATION");

            assertThat(result.getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Non-DIFFICULT_SITUATION type cannot preempt active conversation")
        void createConversation_nonDifficultSituation_cannotPreempt() {
            UUID fatherId = UUID.randomUUID();
            Conversation existingActive = Conversation.builder()
                    .fatherId(fatherId)
                    .type("ONBOARDING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(existingActive));

            assertThatThrownBy(() -> conversationService.createConversation(fatherId, "DAILY_COACHING"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only DIFFICULT_SITUATION can preempt");
        }
    }

    // ─── 5.3: Validate status transitions ────────────────────────────────

    @Nested
    @DisplayName("5.3 - Status transition validation")
    class StatusTransitions {

        @Test
        @DisplayName("ACTIVE → COMPLETED is valid")
        void completeConversation_activeToCompleted_succeeds() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.completeConversation(conversationId, "OBJECTIVE_MET");

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("ACTIVE → EXPIRED is valid")
        void expireConversation_activeToExpired_succeeds() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.expireConversation(conversationId);

            assertThat(result.getStatus()).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("ACTIVE → ABANDONED is valid")
        void abandonConversation_activeToAbandoned_succeeds() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.abandonConversation(conversationId);

            assertThat(result.getStatus()).isEqualTo("ABANDONED");
        }

        @Test
        @DisplayName("COMPLETED → COMPLETED is invalid and throws IllegalStateException")
        void completeConversation_alreadyCompleted_throwsException() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("COMPLETED")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

            assertThatThrownBy(() -> conversationService.completeConversation(conversationId, "OBJECTIVE_MET"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot transition from COMPLETED to COMPLETED");
        }

        @Test
        @DisplayName("EXPIRED → ABANDONED is invalid and throws IllegalStateException")
        void abandonConversation_alreadyExpired_throwsException() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("EXPIRED")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

            assertThatThrownBy(() -> conversationService.abandonConversation(conversationId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot transition from EXPIRED to ABANDONED");
        }

        @Test
        @DisplayName("ABANDONED → EXPIRED is invalid and throws IllegalStateException")
        void expireConversation_alreadyAbandoned_throwsException() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ABANDONED")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

            assertThatThrownBy(() -> conversationService.expireConversation(conversationId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot transition from ABANDONED to EXPIRED");
        }
    }

    // ─── 5.4: Expiration windows configurable per conversation type ──────

    @Nested
    @DisplayName("5.4 - Configurable expiration windows")
    class ExpirationWindows {

        @Test
        @DisplayName("ONBOARDING gets 48h expiration window")
        void createConversation_onboarding_gets48hExpiration() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "ONBOARDING");

            assertThat(result.getExpiresAt()).isNotNull();
            // Should be approximately 48 hours from now
            Instant expectedMin = Instant.now().plus(Duration.ofHours(47).plusMinutes(59));
            Instant expectedMax = Instant.now().plus(Duration.ofHours(48).plusMinutes(1));
            assertThat(result.getExpiresAt()).isBetween(expectedMin, expectedMax);
        }

        @Test
        @DisplayName("DAILY_COACHING gets 24h expiration window")
        void createConversation_dailyCoaching_gets24hExpiration() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "DAILY_COACHING");

            assertThat(result.getExpiresAt()).isNotNull();
            Instant expectedMin = Instant.now().plus(Duration.ofHours(23).plusMinutes(59));
            Instant expectedMax = Instant.now().plus(Duration.ofHours(24).plusMinutes(1));
            assertThat(result.getExpiresAt()).isBetween(expectedMin, expectedMax);
        }

        @Test
        @DisplayName("DIFFICULT_SITUATION has no expiration (null expiresAt)")
        void createConversation_difficultSituation_hasNoExpiration() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "DIFFICULT_SITUATION");

            assertThat(result.getExpiresAt()).isNull();
        }

        @Test
        @DisplayName("INACTIVITY_CHECK gets 48h expiration window")
        void createConversation_inactivityCheck_gets48hExpiration() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.createConversation(fatherId, "INACTIVITY_CHECK");

            assertThat(result.getExpiresAt()).isNotNull();
            Instant expectedMin = Instant.now().plus(Duration.ofHours(47).plusMinutes(59));
            Instant expectedMax = Instant.now().plus(Duration.ofHours(48).plusMinutes(1));
            assertThat(result.getExpiresAt()).isBetween(expectedMin, expectedMax);
        }

        @Test
        @DisplayName("Custom expiration windows can be configured via properties")
        void createConversation_customProperties_usesConfiguredWindows() {
            ConversationProperties customProps = new ConversationProperties();
            customProps.setExpirationWindows(Map.of("DAILY_COACHING", Duration.ofHours(12)));
            ConversationServiceImpl customService = new ConversationServiceImpl(conversationRepository, customProps);

            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId)).thenReturn(Optional.empty());
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = customService.createConversation(fatherId, "DAILY_COACHING");

            assertThat(result.getExpiresAt()).isNotNull();
            Instant expectedMin = Instant.now().plus(Duration.ofHours(11).plusMinutes(59));
            Instant expectedMax = Instant.now().plus(Duration.ofHours(12).plusMinutes(1));
            assertThat(result.getExpiresAt()).isBetween(expectedMin, expectedMax);
        }
    }

    // ─── 5.5: Track completion reasons ───────────────────────────────────

    @Nested
    @DisplayName("5.5 - Completion reason tracking")
    class CompletionReasons {

        @Test
        @DisplayName("completeConversation stores OBJECTIVE_MET reason")
        void completeConversation_objectiveMet_storesReason() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.completeConversation(conversationId, "OBJECTIVE_MET");

            assertThat(result.getCompletionReason()).isEqualTo("OBJECTIVE_MET");
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("completeConversation stores MAX_MESSAGES reason")
        void completeConversation_maxMessages_storesReason() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.completeConversation(conversationId, "MAX_MESSAGES");

            assertThat(result.getCompletionReason()).isEqualTo("MAX_MESSAGES");
        }

        @Test
        @DisplayName("Preemption stores PREEMPTED reason on old conversation")
        void createConversation_preemption_storesPreemptedReason() {
            UUID fatherId = UUID.randomUUID();
            Conversation existingActive = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(existingActive));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            conversationService.createConversation(fatherId, "DIFFICULT_SITUATION");

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository, times(2)).save(captor.capture());

            Conversation preempted = captor.getAllValues().get(0);
            assertThat(preempted.getCompletionReason()).isEqualTo("PREEMPTED");
        }

        @Test
        @DisplayName("expireConversation stores EXPIRATION reason")
        void expireConversation_storesExpirationReason() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.expireConversation(conversationId);

            assertThat(result.getCompletionReason()).isEqualTo("EXPIRATION");
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("abandonConversation stores ABANDONED reason")
        void abandonConversation_storesAbandonedReason() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Conversation result = conversationService.abandonConversation(conversationId);

            assertThat(result.getCompletionReason()).isEqualTo("ABANDONED");
            assertThat(result.getCompletedAt()).isNotNull();
        }
    }

    // ─── 5.6: Update message count on each new message ───────────────────

    @Nested
    @DisplayName("5.6 - Message count updates")
    class MessageCounting {

        @Test
        @DisplayName("INBOUND increments total messageCount and fatherMessageCount")
        void incrementMessageCount_inbound_incrementsFatherCount() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            conversationService.incrementMessageCount(conversationId, "INBOUND");

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            Conversation saved = captor.getValue();
            assertThat(saved.getMessageCount()).isEqualTo(1);
            assertThat(saved.getFatherMessageCount()).isEqualTo(1);
            assertThat(saved.getSystemMessageCount()).isEqualTo(0);
            assertThat(saved.getLastMessageAt()).isNotNull();
        }

        @Test
        @DisplayName("OUTBOUND increments total messageCount and systemMessageCount")
        void incrementMessageCount_outbound_incrementsSystemCount() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            conversationService.incrementMessageCount(conversationId, "OUTBOUND");

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            Conversation saved = captor.getValue();
            assertThat(saved.getMessageCount()).isEqualTo(1);
            assertThat(saved.getFatherMessageCount()).isEqualTo(0);
            assertThat(saved.getSystemMessageCount()).isEqualTo(1);
            assertThat(saved.getLastMessageAt()).isNotNull();
        }

        @Test
        @DisplayName("Invalid direction throws IllegalArgumentException")
        void incrementMessageCount_invalidDirection_throwsException() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

            assertThatThrownBy(() -> conversationService.incrementMessageCount(conversationId, "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid direction");
        }

        @Test
        @DisplayName("lastMessageAt is updated on each message")
        void incrementMessageCount_updatesLastMessageAt() {
            UUID conversationId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Instant before = Instant.now();
            conversationService.incrementMessageCount(conversationId, "INBOUND");

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());

            assertThat(captor.getValue().getLastMessageAt()).isAfterOrEqualTo(before);
        }
    }

    // ─── isExpired tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("isExpired behavior")
    class IsExpiredTests {

        @Test
        @DisplayName("Returns false when expiresAt is null (no expiration)")
        void isExpired_nullExpiresAt_returnsFalse() {
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DIFFICULT_SITUATION")
                    .status("ACTIVE")
                    .build();

            assertThat(conversationService.isExpired(conversation)).isFalse();
        }

        @Test
        @DisplayName("Returns false when expiresAt is in the future")
        void isExpired_futureExpiresAt_returnsFalse() {
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                    .build();

            assertThat(conversationService.isExpired(conversation)).isFalse();
        }

        @Test
        @DisplayName("Returns true when expiresAt is in the past")
        void isExpired_pastExpiresAt_returnsTrue() {
            Conversation conversation = Conversation.builder()
                    .fatherId(UUID.randomUUID())
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .expiresAt(Instant.now().minus(Duration.ofHours(1)))
                    .build();

            assertThat(conversationService.isExpired(conversation)).isTrue();
        }
    }

    // ─── findActiveConversation tests ────────────────────────────────────

    @Nested
    @DisplayName("findActiveConversation behavior")
    class FindActiveConversationTests {

        @Test
        @DisplayName("Returns active conversation when one exists")
        void findActiveConversation_exists_returnsConversation() {
            UUID fatherId = UUID.randomUUID();
            Conversation conversation = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .build();

            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.of(conversation));

            Optional<Conversation> result = conversationService.findActiveConversation(fatherId);

            assertThat(result).isPresent();
            assertThat(result.get().getFatherId()).isEqualTo(fatherId);
        }

        @Test
        @DisplayName("Returns empty when no active conversation exists")
        void findActiveConversation_notExists_returnsEmpty() {
            UUID fatherId = UUID.randomUUID();
            when(conversationRepository.findActiveByFatherId(fatherId))
                    .thenReturn(Optional.empty());

            Optional<Conversation> result = conversationService.findActiveConversation(fatherId);

            assertThat(result).isEmpty();
        }
    }
}
