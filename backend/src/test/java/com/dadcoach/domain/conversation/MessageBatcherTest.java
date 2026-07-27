package com.dadcoach.domain.conversation;

import com.dadcoach.domain.conversation.MessageBatcher.BatchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageBatcher.
 * Verifies batching behavior (Requirement 12.13).
 */
class MessageBatcherTest {

    private MessageBatcher batcher;

    @BeforeEach
    void setUp() {
        batcher = new MessageBatcher();
    }

    @Test
    void threeMessagesWithin10Seconds_triggersBatching() {
        Long fatherId = 1L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");

        batcher.addMessage(fatherId, "hello", t0);
        batcher.addMessage(fatherId, "how are you", t0.plusSeconds(3));

        BatchResult result = batcher.addMessage(fatherId, "need help", t0.plusSeconds(6));

        // 3 messages within 10s → should wait for batch
        assertThat(result).isInstanceOf(BatchResult.WaitForBatch.class);
        assertThat(batcher.shouldBatch(fatherId)).isTrue();
    }

    @Test
    void flushAfterBatching_returnsCombinedContent() {
        Long fatherId = 2L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");

        batcher.addMessage(fatherId, "first", t0);
        batcher.addMessage(fatherId, "second", t0.plusSeconds(2));
        batcher.addMessage(fatherId, "third", t0.plusSeconds(4));

        Optional<String> combined = batcher.flush(fatherId);

        assertThat(combined).isPresent();
        assertThat(combined.get()).isEqualTo("first\nsecond\nthird");
    }

    @Test
    void messagesOutsideWindow_startNewBuffer() {
        Long fatherId = 3L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
        Instant t1 = t0.plusSeconds(15); // 15 seconds later — outside 10s window

        batcher.addMessage(fatherId, "first", t0);
        batcher.addMessage(fatherId, "second", t0.plusSeconds(3));

        // New message outside window resets the buffer
        batcher.addMessage(fatherId, "new message", t1);

        assertThat(batcher.getBufferSize(fatherId)).isEqualTo(1);
    }

    @Test
    void lessThanThreeMessages_doesNotBatch() {
        Long fatherId = 4L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");

        BatchResult r1 = batcher.addMessage(fatherId, "hi", t0);
        BatchResult r2 = batcher.addMessage(fatherId, "there", t0.plusSeconds(5));

        // Under threshold — should not be a WaitForBatch
        assertThat(r2).isNotInstanceOf(BatchResult.WaitForBatch.class);
        assertThat(batcher.shouldBatch(fatherId)).isFalse();
    }

    @Test
    void waitForBatch_returnsCorrectProcessAfterTime() {
        Long fatherId = 5L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
        Instant lastMsg = t0.plusSeconds(8);

        batcher.addMessage(fatherId, "a", t0);
        batcher.addMessage(fatherId, "b", t0.plusSeconds(4));
        BatchResult result = batcher.addMessage(fatherId, "c", lastMsg);

        assertThat(result).isInstanceOf(BatchResult.WaitForBatch.class);
        BatchResult.WaitForBatch waitResult = (BatchResult.WaitForBatch) result;
        // Process after = last message time + 5 seconds
        assertThat(waitResult.processAfter()).isEqualTo(lastMsg.plusSeconds(5));
    }

    @Test
    void flushEmptyBuffer_returnsEmpty() {
        Optional<String> result = batcher.flush(999L);
        assertThat(result).isEmpty();
    }

    @Test
    void clearAll_removesAllBuffers() {
        Long fatherId = 6L;
        Instant t0 = Instant.now();
        batcher.addMessage(fatherId, "test", t0);

        batcher.clearAll();

        assertThat(batcher.getBufferSize(fatherId)).isEqualTo(0);
    }

    @Test
    void moreMessagesAfterThreshold_allIncludedInBatch() {
        Long fatherId = 7L;
        Instant t0 = Instant.parse("2024-01-01T10:00:00Z");

        batcher.addMessage(fatherId, "1", t0);
        batcher.addMessage(fatherId, "2", t0.plusSeconds(2));
        batcher.addMessage(fatherId, "3", t0.plusSeconds(4));
        batcher.addMessage(fatherId, "4", t0.plusSeconds(6));
        batcher.addMessage(fatherId, "5", t0.plusSeconds(8));

        Optional<String> combined = batcher.flush(fatherId);
        assertThat(combined).isPresent();
        assertThat(combined.get()).isEqualTo("1\n2\n3\n4\n5");
    }
}
