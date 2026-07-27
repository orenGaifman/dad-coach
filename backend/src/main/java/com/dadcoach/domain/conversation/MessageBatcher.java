package com.dadcoach.domain.conversation;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batches inbound messages from a father when 3+ arrive within a 10-second window.
 *
 * <p>Business rule (Requirement 12.13): When a Father sends 3 or more messages within
 * 10 seconds, wait 5 seconds after the final message, then process the batch as a
 * single combined input.</p>
 *
 * <p>Thread-safe: uses ConcurrentHashMap for multi-threaded access from webhook handlers.</p>
 */
@Component
public class MessageBatcher {

    static final int BATCH_THRESHOLD = 3;
    static final Duration WINDOW_DURATION = Duration.ofSeconds(10);
    static final Duration WAIT_AFTER_LAST = Duration.ofSeconds(5);

    private final ConcurrentHashMap<Long, MessageBuffer> buffers = new ConcurrentHashMap<>();

    /**
     * Adds a message to the buffer for a given father.
     *
     * @param fatherId the father's ID
     * @param message  the inbound message text
     * @param receivedAt the time the message was received
     * @return a BatchResult indicating whether to process immediately or wait
     */
    public BatchResult addMessage(Long fatherId, String message, Instant receivedAt) {
        MessageBuffer buffer = buffers.compute(fatherId, (id, existing) -> {
            if (existing == null || existing.isExpired(receivedAt)) {
                // Start a new buffer
                MessageBuffer newBuffer = new MessageBuffer(receivedAt);
                newBuffer.addMessage(message, receivedAt);
                return newBuffer;
            }
            existing.addMessage(message, receivedAt);
            return existing;
        });

        if (buffer.size() < BATCH_THRESHOLD) {
            // Not yet at threshold; if this is a single message with no recent siblings, process now
            if (buffer.size() == 1 && !buffer.isWithinWindow(receivedAt)) {
                buffers.remove(fatherId);
                return BatchResult.processImmediately(message);
            }
            return BatchResult.waitForMore();
        }

        // 3+ messages in the window — caller should wait WAIT_AFTER_LAST from lastMessageAt
        return BatchResult.waitForBatch(buffer.getLastMessageAt().plus(WAIT_AFTER_LAST));
    }

    /**
     * Flushes the buffer for a father, combining all messages into one string.
     * Should be called after the wait period has elapsed.
     *
     * @param fatherId the father's ID
     * @return the combined message text, or empty if no buffer exists
     */
    public Optional<String> flush(Long fatherId) {
        MessageBuffer buffer = buffers.remove(fatherId);
        if (buffer == null || buffer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buffer.getCombinedContent());
    }

    /**
     * Returns the current buffer size for a father (for testing).
     */
    public int getBufferSize(Long fatherId) {
        MessageBuffer buffer = buffers.get(fatherId);
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * Checks if a father's buffer has reached the batching threshold.
     */
    public boolean shouldBatch(Long fatherId) {
        MessageBuffer buffer = buffers.get(fatherId);
        return buffer != null && buffer.size() >= BATCH_THRESHOLD;
    }

    /**
     * Clears all buffers (useful for testing).
     */
    public void clearAll() {
        buffers.clear();
    }

    // ─── Inner Classes ───────────────────────────────────────────────────

    /**
     * Internal buffer holding messages for a single father within a time window.
     */
    static class MessageBuffer {
        private final Instant windowStart;
        private final List<String> messages = new ArrayList<>();
        private Instant lastMessageAt;

        MessageBuffer(Instant windowStart) {
            this.windowStart = windowStart;
            this.lastMessageAt = windowStart;
        }

        void addMessage(String message, Instant receivedAt) {
            messages.add(message);
            lastMessageAt = receivedAt;
        }

        int size() {
            return messages.size();
        }

        boolean isEmpty() {
            return messages.isEmpty();
        }

        Instant getLastMessageAt() {
            return lastMessageAt;
        }

        /**
         * Checks if the buffer's window has expired relative to a new message time.
         * A window expires if the new message is more than WINDOW_DURATION after the window start.
         */
        boolean isExpired(Instant newMessageAt) {
            return Duration.between(windowStart, newMessageAt).compareTo(WINDOW_DURATION) > 0;
        }

        /**
         * Checks if a timestamp is within the current window.
         */
        boolean isWithinWindow(Instant timestamp) {
            return Duration.between(windowStart, timestamp).compareTo(WINDOW_DURATION) <= 0;
        }

        /**
         * Combines all buffered messages into a single string, separated by newlines.
         */
        String getCombinedContent() {
            return String.join("\n", messages);
        }
    }

    // ─── Result Types ────────────────────────────────────────────────────

    /**
     * Result of adding a message to the batcher.
     */
    public sealed interface BatchResult {
        /** Process this single message immediately (no batching needed). */
        record ProcessImmediately(String message) implements BatchResult {}

        /** Still accumulating — wait for more messages or for the window to close. */
        record WaitForMore() implements BatchResult {}

        /** Batch threshold reached — process combined after processAfter time. */
        record WaitForBatch(Instant processAfter) implements BatchResult {}

        static BatchResult processImmediately(String message) {
            return new ProcessImmediately(message);
        }

        static BatchResult waitForMore() {
            return new WaitForMore();
        }

        static BatchResult waitForBatch(Instant processAfter) {
            return new WaitForBatch(processAfter);
        }
    }
}
