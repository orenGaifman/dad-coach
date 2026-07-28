package com.dadcoach.conversation;

import com.dadcoach.conversation.dto.OutboundMessageDto;
import com.dadcoach.conversation.entity.ConversationMessage;
import com.dadcoach.conversation.entity.ProcessedMessage;
import com.dadcoach.conversation.repository.ConversationMessageRepository;
import com.dadcoach.conversation.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for idempotency detection in the conversation pipeline.
 * Checks if an inbound message has already been processed (by its idempotency key)
 * BEFORE any business logic executes. If a duplicate is detected, the cached
 * response is returned immediately without re-executing the pipeline.
 *
 * <p>After successful processing, the idempotency key is stored with a 24-hour TTL.
 * Expired entries are cleaned up by a scheduled task running every 6 hours.</p>
 *
 * <p>The idempotency_key column is the primary key on the ProcessedMessage entity,
 * providing an inherent unique constraint — no duplicate keys can be inserted.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedMessageRepository processedMessageRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    @Value("${conversation.idempotency.ttl-hours:24}")
    private int ttlHours;

    public IdempotencyService(ProcessedMessageRepository processedMessageRepository,
                              ConversationMessageRepository conversationMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
        this.conversationMessageRepository = conversationMessageRepository;
    }

    /**
     * Checks if an idempotency key has already been processed.
     * This MUST be called BEFORE any business logic in the pipeline.
     *
     * <p>If the key exists and the associated response can be found, the cached
     * OutboundMessageDto is reconstructed from the stored ConversationMessage
     * (looked up by response_id). If the key exists but the response cannot
     * be reconstructed (e.g., message was deleted or response_id is null),
     * an empty Optional is returned to allow reprocessing.</p>
     *
     * @param idempotencyKey the unique key identifying the inbound message
     * @param recipientId    the recipient identifier (for reconstructing the cached response)
     * @return Optional containing the cached response if duplicate, empty if new message
     */
    @Transactional(readOnly = true)
    public Optional<OutboundMessageDto> checkDuplicate(String idempotencyKey, String recipientId) {
        Optional<ProcessedMessage> existing = processedMessageRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        ProcessedMessage processed = existing.get();
        UUID responseId = processed.getResponseId();

        if (responseId == null) {
            log.warn("Duplicate detected for key '{}' but no response_id linked. Allowing reprocessing.",
                    idempotencyKey);
            return Optional.empty();
        }

        // Look up the outbound ConversationMessage by responseId to reconstruct cached response
        Optional<ConversationMessage> cachedMessage = conversationMessageRepository.findById(responseId);

        if (cachedMessage.isEmpty()) {
            log.warn("Duplicate detected for key '{}' but outbound message {} not found. Allowing reprocessing.",
                    idempotencyKey, responseId);
            return Optional.empty();
        }

        ConversationMessage outbound = cachedMessage.get();
        OutboundMessageDto cachedResponse = new OutboundMessageDto(
                recipientId,
                outbound.getContent(),
                outbound.getMessageType(),
                outbound.getConversationId(),
                outbound.getMetadata() != null ? outbound.getMetadata() : Map.of()
        );

        log.info("Duplicate message detected for key '{}'. Returning cached response.", idempotencyKey);
        return Optional.of(cachedResponse);
    }

    /**
     * Records a successfully processed message with its response link.
     * Called after the pipeline completes successfully, within the same transaction.
     * The entry is stored with a 24-hour TTL (configured via conversation.idempotency.ttl-hours).
     *
     * <p>The idempotency_key is the primary key on ProcessedMessage, so inserting a
     * duplicate key will fail with a constraint violation — this is the inherent
     * unique constraint ensuring exactly-once processing.</p>
     *
     * @param idempotencyKey the unique key identifying the processed inbound message
     * @param fatherId       the father ID who sent the message
     * @param responseId     the UUID of the outbound ConversationMessage produced
     */
    @Transactional
    public void recordProcessed(String idempotencyKey, UUID fatherId, UUID responseId) {
        ProcessedMessage record = ProcessedMessage.builder()
                .idempotencyKey(idempotencyKey)
                .fatherId(fatherId)
                .responseId(responseId)
                .build();

        processedMessageRepository.save(record);
        log.debug("Recorded idempotency key '{}' with response_id {} (expires in {} hours)",
                idempotencyKey, responseId, ttlHours);
    }

    /**
     * Periodically cleans up expired idempotency entries.
     * Runs every 6 hours (21,600,000 milliseconds).
     * Deletes all ProcessedMessage records whose expiresAt is in the past.
     */
    @Scheduled(fixedRate = 21600000)
    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now();
        int deleted = processedMessageRepository.deleteByExpiresAtBefore(now);
        if (deleted > 0) {
            log.info("Idempotency cleanup: removed {} expired entries", deleted);
        } else {
            log.debug("Idempotency cleanup: no expired entries found");
        }
    }
}
