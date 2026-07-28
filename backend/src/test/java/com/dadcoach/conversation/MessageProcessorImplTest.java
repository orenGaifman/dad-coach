package com.dadcoach.conversation;

import com.dadcoach.conversation.dto.InboundMessageDto;
import com.dadcoach.conversation.entity.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageProcessorImpl Unit Tests")
class MessageProcessorImplTest {

    @Mock
    private FatherResolver fatherResolver;

    @Mock
    private ConversationService conversationService;

    private MessageProcessorImpl messageProcessor;

    @BeforeEach
    void setUp() {
        messageProcessor = new MessageProcessorImpl(fatherResolver, conversationService);
    }

    // ─── 15.1: Validate inbound message format ───────────────────────────

    @Nested
    @DisplayName("15.1 - Message validation")
    class MessageValidation {

        @Test
        @DisplayName("Rejects message with empty content")
        void validateAndRoute_emptyContent_throwsValidationException() {
            assertThatThrownBy(() -> new InboundMessageDto("ch1", "sender1", "", "TEXT", "key1", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content is required");
        }

        @Test
        @DisplayName("Rejects message with content exceeding 4096 characters")
        void validateAndRoute_contentTooLong_throwsValidationException() {
            String longContent = "x".repeat(4097);
            InboundMessageDto message = new InboundMessageDto(
                    "ch1", "sender1", longContent, "TEXT", "key1", Instant.now(), Map.of());

            assertThatThrownBy(() -> messageProcessor.validateAndRoute(message))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("exceeds maximum length");
        }

        @Test
        @DisplayName("Rejects null message")
        void validateAndRoute_nullMessage_throwsValidationException() {
            assertThatThrownBy(() -> messageProcessor.validateAndRoute(null))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("Accepts valid message within 4096 chars")
        void validateAndRoute_validMessage_noException() {
            String content = "Hello, I need help with my son.";
            InboundMessageDto message = new InboundMessageDto(
                    "ch1", "sender1", content, "TEXT", "key1", Instant.now(), Map.of());

            UUID fatherId = UUID.randomUUID();
            FatherResolver.ResolvedFather father = new FatherResolver.ResolvedFather(fatherId, "ACTIVE");
            when(fatherResolver.findBySenderIdentity("sender1", "ch1")).thenReturn(Optional.of(father));

            Conversation conversation = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                    .build();
            when(conversationService.findActiveConversation(fatherId)).thenReturn(Optional.of(conversation));
            when(conversationService.isExpired(conversation)).thenReturn(false);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            assertThat(result).isNotNull();
            assertThat(result.fatherId()).isEqualTo(fatherId);
        }
    }

    // ─── 15.2: Resolve father by channel identity ────────────────────────

    @Nested
    @DisplayName("15.2 - Father resolution")
    class FatherResolution {

        @Test
        @DisplayName("Resolves existing father by senderId and channelId")
        void validateAndRoute_knownSender_resolvesExistingFather() {
            UUID fatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("sender1", "ch1");
            FatherResolver.ResolvedFather father = new FatherResolver.ResolvedFather(fatherId, "ACTIVE");

            when(fatherResolver.findBySenderIdentity("sender1", "ch1")).thenReturn(Optional.of(father));

            Conversation conversation = createActiveConversation(fatherId);
            when(conversationService.findActiveConversation(fatherId)).thenReturn(Optional.of(conversation));
            when(conversationService.isExpired(conversation)).thenReturn(false);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            assertThat(result.fatherId()).isEqualTo(fatherId);
            assertThat(result.isNewFather()).isFalse();
        }

        @Test
        @DisplayName("Creates new father for unknown sender")
        void validateAndRoute_unknownSender_createsNewFather() {
            UUID newFatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("unknown_sender", "ch1");

            when(fatherResolver.findBySenderIdentity("unknown_sender", "ch1")).thenReturn(Optional.empty());
            FatherResolver.ResolvedFather newFather = new FatherResolver.ResolvedFather(newFatherId, "NOT_STARTED");
            when(fatherResolver.createNewFather("unknown_sender", "ch1")).thenReturn(newFather);

            Conversation onboarding = createActiveConversation(newFatherId);
            when(conversationService.createConversation(newFatherId, "ONBOARDING")).thenReturn(onboarding);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            assertThat(result.fatherId()).isEqualTo(newFatherId);
            assertThat(result.isNewFather()).isTrue();
        }
    }

    // ─── 15.3: Route to active conversation ──────────────────────────────

    @Nested
    @DisplayName("15.3 - Route to active conversation")
    class RouteToActive {

        @Test
        @DisplayName("Routes to existing active conversation when not expired")
        void validateAndRoute_activeConversationExists_routesToIt() {
            UUID fatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("sender1", "ch1");
            FatherResolver.ResolvedFather father = new FatherResolver.ResolvedFather(fatherId, "ACTIVE");

            when(fatherResolver.findBySenderIdentity("sender1", "ch1")).thenReturn(Optional.of(father));

            Conversation conversation = createActiveConversation(fatherId);
            when(conversationService.findActiveConversation(fatherId)).thenReturn(Optional.of(conversation));
            when(conversationService.isExpired(conversation)).thenReturn(false);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            assertThat(result.conversation()).isEqualTo(conversation);
        }

        @Test
        @DisplayName("Expires and creates new conversation when active is expired")
        void validateAndRoute_activeButExpired_expiresAndCreatesNew() {
            UUID fatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("sender1", "ch1");
            FatherResolver.ResolvedFather father = new FatherResolver.ResolvedFather(fatherId, "ACTIVE");

            when(fatherResolver.findBySenderIdentity("sender1", "ch1")).thenReturn(Optional.of(father));

            UUID expiredConvId = UUID.randomUUID();
            Conversation expiredConversation = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("ACTIVE")
                    .expiresAt(Instant.now().minus(Duration.ofHours(1)))
                    .build();
            ReflectionTestUtils.setField(expiredConversation, "id", expiredConvId);

            when(conversationService.findActiveConversation(fatherId))
                    .thenReturn(Optional.of(expiredConversation));
            when(conversationService.isExpired(expiredConversation)).thenReturn(true);

            Conversation expiredResult = Conversation.builder()
                    .fatherId(fatherId)
                    .type("DAILY_COACHING")
                    .status("EXPIRED")
                    .build();
            ReflectionTestUtils.setField(expiredResult, "id", expiredConvId);
            when(conversationService.expireConversation(expiredConvId)).thenReturn(expiredResult);

            Conversation newConversation = createActiveConversation(fatherId);
            when(conversationService.createConversation(fatherId, "DAILY_COACHING")).thenReturn(newConversation);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            verify(conversationService).expireConversation(expiredConvId);
            assertThat(result.conversation()).isEqualTo(newConversation);
        }
    }

    // ─── 15.4: Create new conversation if none active ────────────────────

    @Nested
    @DisplayName("15.4 - Create new conversation")
    class CreateNewConversation {

        @Test
        @DisplayName("Creates new conversation when no active exists")
        void validateAndRoute_noActiveConversation_createsNew() {
            UUID fatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("sender1", "ch1");
            FatherResolver.ResolvedFather father = new FatherResolver.ResolvedFather(fatherId, "ACTIVE");

            when(fatherResolver.findBySenderIdentity("sender1", "ch1")).thenReturn(Optional.of(father));
            when(conversationService.findActiveConversation(fatherId)).thenReturn(Optional.empty());

            Conversation newConversation = createActiveConversation(fatherId);
            when(conversationService.createConversation(fatherId, "DAILY_COACHING")).thenReturn(newConversation);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            verify(conversationService).createConversation(fatherId, "DAILY_COACHING");
            assertThat(result.conversation()).isEqualTo(newConversation);
        }

        @Test
        @DisplayName("Creates ONBOARDING conversation for new father")
        void validateAndRoute_newFather_createsOnboarding() {
            UUID newFatherId = UUID.randomUUID();
            InboundMessageDto message = createValidMessage("new_sender", "ch1");

            when(fatherResolver.findBySenderIdentity("new_sender", "ch1")).thenReturn(Optional.empty());
            FatherResolver.ResolvedFather newFather = new FatherResolver.ResolvedFather(newFatherId, "NOT_STARTED");
            when(fatherResolver.createNewFather("new_sender", "ch1")).thenReturn(newFather);

            Conversation onboarding = Conversation.builder()
                    .fatherId(newFatherId)
                    .type("ONBOARDING")
                    .status("ACTIVE")
                    .build();
            when(conversationService.createConversation(newFatherId, "ONBOARDING")).thenReturn(onboarding);

            MessageProcessor.RoutingResult result = messageProcessor.validateAndRoute(message);

            verify(conversationService).createConversation(newFatherId, "ONBOARDING");
            assertThat(result.conversation().getType()).isEqualTo("ONBOARDING");
        }
    }

    // ─── 15.6: Reject malformed messages ─────────────────────────────────

    @Nested
    @DisplayName("15.6 - Malformed message rejection")
    class MalformedRejection {

        @Test
        @DisplayName("Provides clear error message for content validation failure")
        void validateAndRoute_tooLongContent_clearErrorMessage() {
            String longContent = "x".repeat(4097);
            InboundMessageDto message = new InboundMessageDto(
                    "ch1", "sender1", longContent, "TEXT", "key1", Instant.now(), Map.of());

            assertThatThrownBy(() -> messageProcessor.validateAndRoute(message))
                    .isInstanceOf(MessageValidationException.class)
                    .satisfies(ex -> {
                        MessageValidationException mve = (MessageValidationException) ex;
                        assertThat(mve.getField()).isEqualTo("content");
                    });
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private InboundMessageDto createValidMessage(String senderId, String channelId) {
        return new InboundMessageDto(
                channelId, senderId, "Hello, I need coaching advice.",
                "TEXT", "idem-" + UUID.randomUUID(), Instant.now(), Map.of());
    }

    private Conversation createActiveConversation(UUID fatherId) {
        Conversation conversation = Conversation.builder()
                .fatherId(fatherId)
                .type("DAILY_COACHING")
                .status("ACTIVE")
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        return conversation;
    }
}
