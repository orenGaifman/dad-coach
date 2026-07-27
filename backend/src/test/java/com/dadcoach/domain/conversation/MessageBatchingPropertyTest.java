package com.dadcoach.domain.conversation;

import com.dadcoach.domain.conversation.MessageBatcher.BatchResult;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for message batching (Property #33).
 *
 * <p>Property: For any N messages within a 10-second window where N >= 3,
 * the system should produce a single combined output after 5 seconds.</p>
 *
 * <p><b>Validates: Requirements 12.13</b></p>
 *
 * @Tag("Feature: product-domain-business-logic, Property 33: Message Batching")
 */
@Tag("Feature: product-domain-business-logic, Property 33: Message Batching")
class MessageBatchingPropertyTest {

    /**
     * Property: For any number of messages N >= 3 sent within a 10-second window,
     * the batcher should trigger batching and produce a single combined output
     * containing all N messages when flushed.
     *
     * <b>Validates: Requirements 12.13</b>
     */
    @Property(tries = 100)
    void batchingTriggersForThreeOrMoreMessagesWithinWindow(
            @ForAll @IntRange(min = 3, max = 20) int messageCount,
            @ForAll("fatherIds") Long fatherId
    ) {
        MessageBatcher batcher = new MessageBatcher();
        Instant t0 = Instant.parse("2024-06-15T12:00:00Z");

        // Send N messages within a 10-second window (evenly spaced)
        long intervalMs = 9000L / messageCount; // Keep all within 9s of window start
        BatchResult lastResult = null;

        for (int i = 0; i < messageCount; i++) {
            Instant msgTime = t0.plusMillis(i * intervalMs);
            lastResult = batcher.addMessage(fatherId, "msg_" + i, msgTime);
        }

        // Property: with 3+ messages, batcher should trigger batching
        assertThat(batcher.shouldBatch(fatherId)).isTrue();
        assertThat(lastResult).isInstanceOf(BatchResult.WaitForBatch.class);

        // Property: flushing produces a single combined output containing all messages
        Optional<String> combined = batcher.flush(fatherId);
        assertThat(combined).isPresent();

        String combinedText = combined.get();
        for (int i = 0; i < messageCount; i++) {
            assertThat(combinedText).contains("msg_" + i);
        }

        // Property: combined output is a single string (not split into multiple)
        // The number of lines should equal the number of messages
        String[] lines = combinedText.split("\n");
        assertThat(lines).hasSize(messageCount);
    }

    /**
     * Property: For any number of messages N >= 3 in a 10-second window,
     * the processAfter time should be exactly 5 seconds after the last message.
     *
     * <b>Validates: Requirements 12.13</b>
     */
    @Property(tries = 100)
    void processAfterIsExactly5SecondsFromLastMessage(
            @ForAll @IntRange(min = 3, max = 15) int messageCount,
            @ForAll("fatherIds") Long fatherId,
            @ForAll @IntRange(min = 0, max = 9) int lastMsgSecond
    ) {
        MessageBatcher batcher = new MessageBatcher();
        Instant t0 = Instant.parse("2024-06-15T12:00:00Z");

        // Send messages; the last one arrives at t0 + lastMsgSecond seconds
        for (int i = 0; i < messageCount - 1; i++) {
            long offset = (long) lastMsgSecond * i / (messageCount - 1);
            batcher.addMessage(fatherId, "msg_" + i, t0.plusSeconds(offset));
        }

        Instant lastMsgTime = t0.plusSeconds(lastMsgSecond);
        BatchResult result = batcher.addMessage(fatherId, "last", lastMsgTime);

        // Property: processAfter = lastMessageAt + 5 seconds
        assertThat(result).isInstanceOf(BatchResult.WaitForBatch.class);
        BatchResult.WaitForBatch waitResult = (BatchResult.WaitForBatch) result;
        Instant expectedProcessAfter = lastMsgTime.plus(Duration.ofSeconds(5));
        assertThat(waitResult.processAfter()).isEqualTo(expectedProcessAfter);
    }

    /**
     * Property: For any number of messages N < 3 within a window,
     * the batcher should NOT trigger batching.
     *
     * <b>Validates: Requirements 12.13</b>
     */
    @Property(tries = 100)
    void noBatchingForFewerThanThreeMessages(
            @ForAll @IntRange(min = 1, max = 2) int messageCount,
            @ForAll("fatherIds") Long fatherId
    ) {
        MessageBatcher batcher = new MessageBatcher();
        Instant t0 = Instant.parse("2024-06-15T12:00:00Z");

        for (int i = 0; i < messageCount; i++) {
            batcher.addMessage(fatherId, "msg_" + i, t0.plusSeconds(i * 3));
        }

        // Property: with fewer than 3 messages, batching should NOT be triggered
        assertThat(batcher.shouldBatch(fatherId)).isFalse();
    }

    // ─── Arbitraries ─────────────────────────────────────────────────────

    @Provide
    Arbitrary<Long> fatherIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}
