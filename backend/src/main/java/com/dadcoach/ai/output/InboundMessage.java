package com.dadcoach.ai.output;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an inbound message from a father, ready for safety classification.
 *
 * @param fatherId  the father's unique identifier
 * @param content   the message text content
 * @param timestamp when the message was received
 */
public record InboundMessage(
    UUID fatherId,
    String content,
    Instant timestamp
) {
    public InboundMessage {
        if (fatherId == null) {
            throw new IllegalArgumentException("fatherId must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be null or blank");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
