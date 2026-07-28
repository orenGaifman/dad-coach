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
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SideEffectProcessor Unit Tests")
class SideEffectProcessorTest {

    @Mock
    private SideEffectOutboxRepository outboxRepository;

    private SideEffectProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SideEffectProcessor(outboxRepository, List.of());
    }

    @Test
    @DisplayName("poll fetches batch of 20 pending entries")
    void poll_fetchesBatchOfTwenty() {
        when(outboxRepository.findPending(any(Limit.class))).thenReturn(Collections.emptyList());

        processor.poll();

        verify(outboxRepository).findPending(Limit.of(20));
    }

    @Test
    @DisplayName("poll transitions entry to PROCESSING then COMPLETED on success")
    void poll_successfulEntry_transitionsToCompleted() {
        SideEffectOutbox entry = buildEntry("METRIC_UPDATE", 3);
        when(outboxRepository.findPending(any(Limit.class))).thenReturn(List.of(entry));

        // Track the status at each save invocation
        List<String> statusHistory = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            SideEffectOutbox saved = invocation.getArgument(0);
            statusHistory.add(saved.getStatus());
            return saved;
        }).when(outboxRepository).save(any(SideEffectOutbox.class));

        processor.poll();

        // Entry should be saved twice: PENDING → PROCESSING → COMPLETED
        assertThat(statusHistory).containsExactly("PROCESSING", "COMPLETED");
        assertThat(entry.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("poll does nothing when no pending entries exist")
    void poll_noPendingEntries_doesNothing() {
        when(outboxRepository.findPending(any(Limit.class))).thenReturn(Collections.emptyList());

        processor.poll();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("poll never crashes the polling loop on unexpected exception")
    void poll_unexpectedException_doesNotCrash() {
        when(outboxRepository.findPending(any(Limit.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should not throw
        processor.poll();
    }

    @Test
    @DisplayName("onApplicationReady resets stale PROCESSING entries and triggers poll")
    void onApplicationReady_resetsStaleEntries() {
        SideEffectOutbox staleEntry = buildEntry("EVENT_PUBLISH", Integer.MAX_VALUE);
        staleEntry.setStatus("PROCESSING");

        when(outboxRepository.findAll()).thenReturn(List.of(staleEntry));
        when(outboxRepository.findPending(any(Limit.class))).thenReturn(Collections.emptyList());

        processor.onApplicationReady();

        // Should reset the stale entry
        assertThat(staleEntry.getStatus()).isEqualTo("PENDING");
        verify(outboxRepository).saveAll(List.of(staleEntry));
    }

    @Test
    @DisplayName("onApplicationReady triggers immediate poll after reset")
    void onApplicationReady_triggersImmediatePoll() {
        when(outboxRepository.findAll()).thenReturn(Collections.emptyList());
        when(outboxRepository.findPending(any(Limit.class))).thenReturn(Collections.emptyList());

        processor.onApplicationReady();

        // findPending is called during poll()
        verify(outboxRepository).findPending(any(Limit.class));
    }

    // --- Helper methods ---

    private SideEffectOutbox buildEntry(String effectType, int maxRetries) {
        return SideEffectOutbox.builder()
                .fatherId(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .effectType(effectType)
                .payload(Map.of("key", "value"))
                .status("PENDING")
                .maxRetries(maxRetries)
                .build();
    }
}
