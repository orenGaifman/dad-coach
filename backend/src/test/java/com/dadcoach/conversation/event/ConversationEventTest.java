package com.dadcoach.conversation.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConversationEvent Record Tests")
class ConversationEventTest {

    @Test
    @DisplayName("Creates valid event with all fields")
    void createEvent_allFields_succeeds() {
        UUID conversationId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();
        Instant now = Instant.now();

        ConversationEvent event = new ConversationEvent(
                ConversationEvent.CONVERSATION_STARTED,
                conversationId,
                fatherId,
                "DAILY_COACHING",
                null,
                now
        );

        assertThat(event.eventType()).isEqualTo("CONVERSATION_STARTED");
        assertThat(event.conversationId()).isEqualTo(conversationId);
        assertThat(event.fatherId()).isEqualTo(fatherId);
        assertThat(event.conversationType()).isEqualTo("DAILY_COACHING");
        assertThat(event.completionReason()).isNull();
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("Defaults timestamp to now when null")
    void createEvent_nullTimestamp_defaultsToNow() {
        Instant before = Instant.now();

        ConversationEvent event = new ConversationEvent(
                ConversationEvent.CONVERSATION_COMPLETED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_COACHING",
                "OBJECTIVE_MET",
                null
        );

        assertThat(event.timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("Throws on null eventType")
    void createEvent_nullEventType_throws() {
        assertThatThrownBy(() -> new ConversationEvent(
                null, UUID.randomUUID(), UUID.randomUUID(), "DAILY_COACHING", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType is required");
    }

    @Test
    @DisplayName("Throws on null conversationId")
    void createEvent_nullConversationId_throws() {
        assertThatThrownBy(() -> new ConversationEvent(
                "CONVERSATION_STARTED", null, UUID.randomUUID(), "DAILY_COACHING", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId is required");
    }

    @Test
    @DisplayName("Throws on null fatherId")
    void createEvent_nullFatherId_throws() {
        assertThatThrownBy(() -> new ConversationEvent(
                "CONVERSATION_STARTED", UUID.randomUUID(), null, "DAILY_COACHING", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fatherId is required");
    }

    @Test
    @DisplayName("Throws on null conversationType")
    void createEvent_nullConversationType_throws() {
        assertThatThrownBy(() -> new ConversationEvent(
                "CONVERSATION_STARTED", UUID.randomUUID(), UUID.randomUUID(), null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationType is required");
    }

    @Test
    @DisplayName("Event type constants are defined correctly")
    void eventTypeConstants_definedCorrectly() {
        assertThat(ConversationEvent.CONVERSATION_STARTED).isEqualTo("CONVERSATION_STARTED");
        assertThat(ConversationEvent.CONVERSATION_COMPLETED).isEqualTo("CONVERSATION_COMPLETED");
        assertThat(ConversationEvent.CONVERSATION_EXPIRED).isEqualTo("CONVERSATION_EXPIRED");
    }
}
