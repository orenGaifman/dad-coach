package com.dadcoach.channel.delivery;

import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.dto.StatusUpdateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages transport-level delivery retries with exponential backoff and
 * delivery status tracking correlated by provider_message_id.
 *
 * <p>Retry schedule: 2s, 4s, 8s, 16s, 32s (5 attempts max).
 * Total retry duration bounded to ~62s (well under the 5-minute max).
 *
 * <p>Status lifecycle: PENDING → SENT → DELIVERED → READ / FAILED.
 * Status updates don't need to arrive in order (READ without prior DELIVERED is OK).
 * Unknown provider_message_id status updates are discarded (logged and skipped).
 */
@Service
public class DeliveryRetryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryService.class);

    /** Maximum number of retry attempts for transient failures. */
    static final int MAX_RETRIES = 5;

    /** Base delay in milliseconds for exponential backoff (2 seconds). */
    static final long BASE_DELAY_MS = 2000L;

    /** Maximum delay in milliseconds (32 seconds). */
    static final long MAX_DELAY_MS = 32000L;

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final DeliveryService deliveryService;

    public DeliveryRetryService(
            DeliveryRecordRepository deliveryRecordRepository,
            DeliveryService deliveryService) {
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.deliveryService = deliveryService;
    }

    /**
     * Creates a DeliveryRecord in PENDING status for an outbound message
     * and attempts delivery with retries on transient failure.
     *
     * @param message the outbound message to deliver
     * @return the delivery result from the final attempt
     */
    public DeliveryResult deliverWithRetry(OutboundMessageDto message) {
        DeliveryRecord record = new DeliveryRecord(
                message.messageId(), message.fatherId(),
                message.channel() != null ? message.channel() : "WHATSAPP");
        record = deliveryRecordRepository.save(record);

        log.debug("Created delivery record {} for message {}", record.getId(), message.messageId());

        DeliveryResult result = attemptDelivery(message, record);

        if (result.isSuccessful()) {
            record.markSent(result.providerMessageId(), Instant.now());
            deliveryRecordRepository.save(record);
            return result;
        }

        // If the failure is a rejection (not transient), fail immediately
        if (isNonRetryableFailure(result)) {
            record.markFailed(result.failureReason(), Instant.now());
            deliveryRecordRepository.save(record);
            return result;
        }

        // Retry with exponential backoff
        return retryWithBackoff(message, record, result);
    }

    /**
     * Processes a delivery status update from a provider webhook.
     * Correlates by provider_message_id. Discards unknown IDs.
     *
     * @param statusUpdate the status update from the provider
     * @return true if the update was processed, false if discarded (unknown ID)
     */
    public boolean processStatusUpdate(StatusUpdateDto statusUpdate) {
        if (statusUpdate.providerMessageId() == null || statusUpdate.providerMessageId().isBlank()) {
            log.warn("Received status update with null/blank provider_message_id, discarding");
            return false;
        }

        Optional<DeliveryRecord> recordOpt = deliveryRecordRepository
                .findByProviderMessageId(statusUpdate.providerMessageId());

        if (recordOpt.isEmpty()) {
            log.info("Discarding status update for unknown provider_message_id: {}",
                    statusUpdate.providerMessageId());
            return false;
        }

        DeliveryRecord record = recordOpt.get();
        Instant timestamp = statusUpdate.timestamp() != null ? statusUpdate.timestamp() : Instant.now();

        switch (statusUpdate.status().toLowerCase()) {
            case "sent" -> record.markSent(statusUpdate.providerMessageId(), timestamp);
            case "delivered" -> record.markDelivered(timestamp);
            case "read" -> record.markRead(timestamp);
            case "failed" -> {
                String reason = buildFailureReason(statusUpdate);
                record.markFailed(reason, timestamp);
            }
            default -> {
                log.warn("Unknown delivery status '{}' for provider_message_id: {}",
                        statusUpdate.status(), statusUpdate.providerMessageId());
                return false;
            }
        }

        deliveryRecordRepository.save(record);
        log.debug("Updated delivery record {} to status {} (provider_message_id: {})",
                record.getId(), record.getStatus(), statusUpdate.providerMessageId());
        return true;
    }

    /**
     * Calculates the delay in milliseconds for a given retry attempt.
     * Uses exponential backoff: 2s, 4s, 8s, 16s, 32s.
     *
     * @param attempt the retry attempt number (0-based: 0=first retry)
     * @return delay in milliseconds
     */
    public long calculateDelayMs(int attempt) {
        long delay = BASE_DELAY_MS * (1L << attempt);
        return Math.min(delay, MAX_DELAY_MS);
    }

    /**
     * Retrieves a delivery record by its provider message ID.
     */
    public Optional<DeliveryRecord> findByProviderMessageId(String providerMessageId) {
        return deliveryRecordRepository.findByProviderMessageId(providerMessageId);
    }

    /**
     * Retrieves a delivery record by internal message ID.
     */
    public Optional<DeliveryRecord> findByMessageId(UUID messageId) {
        return deliveryRecordRepository.findByMessageId(messageId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private DeliveryResult attemptDelivery(OutboundMessageDto message, DeliveryRecord record) {
        try {
            return deliveryService.deliver(message);
        } catch (Exception e) {
            log.error("Delivery attempt failed with exception for message {}: {}",
                    message.messageId(), e.getMessage(), e);
            return DeliveryResult.failed("Exception: " + e.getMessage());
        }
    }

    private DeliveryResult retryWithBackoff(OutboundMessageDto message, DeliveryRecord record, DeliveryResult lastResult) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            long delayMs = calculateDelayMs(attempt);
            log.info("Retry {}/{} for message {} after {}ms delay",
                    attempt + 1, MAX_RETRIES, message.messageId(), delayMs);

            sleep(delayMs);

            record.incrementRetryCount();
            DeliveryResult result = attemptDelivery(message, record);

            if (result.isSuccessful()) {
                record.markSent(result.providerMessageId(), Instant.now());
                deliveryRecordRepository.save(record);
                return result;
            }

            // Non-retryable failures fail immediately
            if (isNonRetryableFailure(result)) {
                record.markFailed(result.failureReason(), Instant.now());
                deliveryRecordRepository.save(record);
                return result;
            }

            lastResult = result;
        }

        // All retries exhausted — mark as FAILED
        String reason = "Max retries exhausted (" + MAX_RETRIES + " attempts). Last error: " + lastResult.failureReason();
        record.markFailed(reason, Instant.now());
        deliveryRecordRepository.save(record);

        log.warn("All {} retries exhausted for message {}. Marking as FAILED: {}",
                MAX_RETRIES, message.messageId(), reason);

        return DeliveryResult.failed(reason);
    }

    /**
     * Determines if a failure is non-retryable (permanent).
     * SESSION_CLOSED, TEMPLATE_UNAVAILABLE, ENDPOINT_NOT_FOUND, and UNSUPPORTED_TYPE
     * are business-level rejections that won't resolve with retries.
     */
    private boolean isNonRetryableFailure(DeliveryResult result) {
        if (result.failureReason() == null) {
            return false;
        }
        String reason = result.failureReason();
        return reason.equals(DeliveryService.SESSION_CLOSED)
                || reason.equals(DeliveryService.TEMPLATE_UNAVAILABLE)
                || reason.equals(DeliveryService.ENDPOINT_NOT_FOUND)
                || reason.startsWith(DeliveryService.UNSUPPORTED_TYPE);
    }

    private String buildFailureReason(StatusUpdateDto statusUpdate) {
        StringBuilder reason = new StringBuilder();
        if (statusUpdate.errorCode() != null) {
            reason.append("Error ").append(statusUpdate.errorCode());
        }
        if (statusUpdate.errorMessage() != null) {
            if (!reason.isEmpty()) {
                reason.append(": ");
            }
            reason.append(statusUpdate.errorMessage());
        }
        if (reason.isEmpty()) {
            reason.append("Provider reported failure");
        }
        // Truncate to fit the 100-char column
        return reason.length() > 100 ? reason.substring(0, 100) : reason.toString();
    }

    /**
     * Sleeps for the specified duration. Extracted for testability.
     */
    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted for delivery");
        }
    }
}
