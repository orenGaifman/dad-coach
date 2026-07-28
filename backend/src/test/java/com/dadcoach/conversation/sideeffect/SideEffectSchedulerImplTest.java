package com.dadcoach.conversation.sideeffect;

import com.dadcoach.conversation.entity.SideEffectOutbox;
import com.dadcoach.conversation.repository.SideEffectOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SideEffectSchedulerImpl Unit Tests")
class SideEffectSchedulerImplTest {

    @Mock
    private SideEffectOutboxRepository outboxRepository;

    private SideEffectSchedulerImpl scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SideEffectSchedulerImpl(outboxRepository);
    }

    @Test
    @DisplayName("schedule with enum type persists entry with correct fields and PENDING status")
    void schedule_enumType_persistsCorrectEntry() {
        UUID fatherId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("transcript", "hello");

        scheduler.schedule(SideEffect.MEMORY_EXTRACTION, fatherId, conversationId, payload);

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        SideEffectOutbox saved = captor.getValue();
        assertThat(saved.getEffectType()).isEqualTo("MEMORY_EXTRACTION");
        assertThat(saved.getFatherId()).isEqualTo(fatherId);
        assertThat(saved.getConversationId()).isEqualTo(conversationId);
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("schedule mandatory effect sets maxRetries to Integer.MAX_VALUE")
    void schedule_mandatoryEffect_setsUnlimitedRetries() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule(SideEffect.MEMORY_EXTRACTION, fatherId, null, Map.of());

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("schedule best-effort effect sets maxRetries to 3")
    void schedule_bestEffortEffect_setsMaxRetriesToThree() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule(SideEffect.METRIC_UPDATE, fatherId, null, Map.of());

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("schedule with string type resolves maxRetries from SideEffect enum")
    void schedule_stringType_resolvesMaxRetriesFromEnum() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule("EVENT_PUBLISH", fatherId, null, Map.of("event", "COMPLETED"));

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        // EVENT_PUBLISH is mandatory → Integer.MAX_VALUE retries
        assertThat(captor.getValue().getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("schedule with unknown string type defaults to maxRetries=3")
    void schedule_unknownStringType_defaultsToThreeRetries() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule("UNKNOWN_EFFECT", fatherId, null, Map.of());

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getMaxRetries()).isEqualTo(3);
        assertThat(captor.getValue().getEffectType()).isEqualTo("UNKNOWN_EFFECT");
    }

    @Test
    @DisplayName("schedule with null payload uses empty map")
    void schedule_nullPayload_usesEmptyMap() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule(SideEffect.METRIC_UPDATE, fatherId, null, null);

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("schedule with null conversationId persists null conversationId")
    void schedule_nullConversationId_persistsNull() {
        UUID fatherId = UUID.randomUUID();

        scheduler.schedule(SideEffect.METRIC_UPDATE, fatherId, null, Map.of());

        ArgumentCaptor<SideEffectOutbox> captor = ArgumentCaptor.forClass(SideEffectOutbox.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getConversationId()).isNull();
    }
}
