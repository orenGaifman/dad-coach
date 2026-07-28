package com.dadcoach.conversation.event;

import com.dadcoach.conversation.entity.Conversation;
import com.dadcoach.conversation.sideeffect.SideEffect;
import com.dadcoach.conversation.sideeffect.SideEffectScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationEventPublisherImpl Unit Tests")
class ConversationEventPublisherImplTest {

    @Mock
    private SideEffectScheduler sideEffectScheduler;

    private ConversationEventPublisherImpl eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new ConversationEventPublisherImpl(sideEffectScheduler);
    }

    // ─── 12.1: Publish events ────────────────────────────────────────────

    @Nested
    @DisplayName("12.1 - Event publication")
    class EventPublication {

        @Test
        @DisplayName("publishConversationStarted schedules CONVERSATION_STARTED event")
        void publishConversationStarted_schedulesCorrectEvent() {
            Conversation conversation = createTestConversation("DAILY_COACHING");

            eventPublisher.publishConversationStarted(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = captureScheduledPayload(conversation);
            assertThat(payloadCaptor.getValue().get("event_type"))
                    .isEqualTo(ConversationEvent.CONVERSATION_STARTED);
        }

        @Test
        @DisplayName("publishConversationCompleted schedules CONVERSATION_COMPLETED event")
        void publishConversationCompleted_schedulesCorrectEvent() {
            Conversation conversation = createTestConversation("DAILY_COACHING");

            eventPublisher.publishConversationCompleted(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = captureScheduledPayload(conversation);
            assertThat(payloadCaptor.getValue().get("event_type"))
                    .isEqualTo(ConversationEvent.CONVERSATION_COMPLETED);
        }

        @Test
        @DisplayName("publishConversationExpired schedules CONVERSATION_EXPIRED event")
        void publishConversationExpired_schedulesCorrectEvent() {
            Conversation conversation = createTestConversation("DAILY_COACHING");

            eventPublisher.publishConversationExpired(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = captureScheduledPayload(conversation);
            assertThat(payloadCaptor.getValue().get("event_type"))
                    .isEqualTo(ConversationEvent.CONVERSATION_EXPIRED);
        }
    }

    // ─── 12.2: Events written as mandatory side-effects ──────────────────

    @Nested
    @DisplayName("12.2 - Mandatory side-effect scheduling")
    class MandatorySideEffect {

        @Test
        @DisplayName("Events use SideEffect.EVENT_PUBLISH (mandatory, unlimited retries)")
        void publishEvent_usesMandatoryEventPublishType() {
            Conversation conversation = createTestConversation("ONBOARDING");

            eventPublisher.publishConversationStarted(conversation);

            verify(sideEffectScheduler).schedule(
                    eq(SideEffect.EVENT_PUBLISH),
                    eq(conversation.getFatherId()),
                    eq(conversation.getId()),
                    any()
            );
        }
    }

    // ─── 12.3: Event payload contents ────────────────────────────────────

    @Nested
    @DisplayName("12.3 - Event payload")
    class EventPayload {

        @Test
        @DisplayName("Payload includes conversation_id, father_id, type, and completion_reason")
        void publishEvent_payloadContainsRequiredFields() {
            Conversation conversation = createTestConversation("DAILY_COACHING");

            eventPublisher.publishConversationCompleted(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = captureScheduledPayload(conversation);
            Map<String, Object> payload = payloadCaptor.getValue();

            assertThat(payload).containsKey("conversation_id");
            assertThat(payload).containsKey("father_id");
            assertThat(payload).containsKey("conversation_type");
            assertThat(payload).containsKey("completion_reason");
            assertThat(payload).containsKey("timestamp");
            assertThat(payload.get("conversation_type")).isEqualTo("DAILY_COACHING");
        }

        @Test
        @DisplayName("Payload father_id and conversation_id match the conversation")
        void publishEvent_payloadMatchesConversation() {
            Conversation conversation = createTestConversation("REFLECTION");

            eventPublisher.publishConversationStarted(conversation);

            ArgumentCaptor<Map<String, Object>> payloadCaptor = captureScheduledPayload(conversation);
            Map<String, Object> payload = payloadCaptor.getValue();

            assertThat(payload.get("father_id")).isEqualTo(conversation.getFatherId().toString());
            assertThat(payload.get("conversation_id")).isEqualTo(conversation.getId().toString());
        }
    }

    // ─── 12.5: Event publication failure does not block response ──────────

    @Nested
    @DisplayName("12.5 - Failure tolerance")
    class FailureTolerance {

        @Test
        @DisplayName("Exception in scheduler does not propagate to caller")
        void publishEvent_schedulerThrows_doesNotPropagate() {
            Conversation conversation = createTestConversation("DAILY_COACHING");
            doThrow(new RuntimeException("DB connection lost"))
                    .when(sideEffectScheduler)
                    .schedule(any(SideEffect.class), any(UUID.class), any(), any(Map.class));

            // Should not throw
            eventPublisher.publishConversationStarted(conversation);
            eventPublisher.publishConversationCompleted(conversation);
            eventPublisher.publishConversationExpired(conversation);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Conversation createTestConversation(String type) {
        Conversation conversation = Conversation.builder()
                .fatherId(UUID.randomUUID())
                .type(type)
                .status("ACTIVE")
                .build();
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        return conversation;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureScheduledPayload(Conversation conversation) {
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sideEffectScheduler).schedule(
                eq(SideEffect.EVENT_PUBLISH),
                eq(conversation.getFatherId()),
                eq(conversation.getId()),
                payloadCaptor.capture()
        );
        return payloadCaptor;
    }
}
