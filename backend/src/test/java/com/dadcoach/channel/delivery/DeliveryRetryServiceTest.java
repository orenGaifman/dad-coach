package com.dadcoach.channel.delivery;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import com.dadcoach.channel.dto.StatusUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeliveryRetryService verifying:
 * - Retry schedule with exponential backoff (2s, 4s, 8s, 16s, 32s)
 * - Delivery status lifecycle tracking (PENDING → SENT → DELIVERED → READ / FAILED)
 * - Status update correlation by provider_message_id
 * - Discarding unknown provider_message_id status updates
 * - DeliveryRecord persistence throughout lifecycle
 * - FAILED marking after max retries exhausted
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryRetryService Unit Tests")
class DeliveryRetryServiceTest {

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @Mock
    private DeliveryService deliveryService;

    private DeliveryRetryService retryService;

    private static final UUID FATHER_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final String PROVIDER_MSG_ID = "wamid.HBgLMTIzNDU2Nzg5MA";

    @BeforeEach
    void setUp() {
        retryService = spy(new DeliveryRetryService(deliveryRecordRepository, deliveryService));
        // Skip actual sleeping in tests (lenient because not all tests trigger retries)
        lenient().doNothing().when(retryService).sleep(anyLong());
    }

    private OutboundMessageDto textMessage() {
        return new OutboundMessageDto(
                MESSAGE_ID, FATHER_ID, "WHATSAPP", MessageType.TEXT,
                "Hello!", null, false, null, null,
                MessagePriority.IMMEDIATE, Instant.now());
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.1 Retry schedule: 2s, 4s, 8s, 16s, 32s (5 attempts max)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.1 Retry schedule with exponential backoff")
    class RetryScheduleTests {

        @Test
        @DisplayName("calculates correct delay for each retry attempt")
        void calculatesExponentialBackoffDelays() {
            assertEquals(2000L, retryService.calculateDelayMs(0));   // 2s
            assertEquals(4000L, retryService.calculateDelayMs(1));   // 4s
            assertEquals(8000L, retryService.calculateDelayMs(2));   // 8s
            assertEquals(16000L, retryService.calculateDelayMs(3));  // 16s
            assertEquals(32000L, retryService.calculateDelayMs(4));  // 32s
        }

        @Test
        @DisplayName("caps delay at 32 seconds for attempts beyond 4")
        void capsDelayAtMax() {
            assertEquals(32000L, retryService.calculateDelayMs(5));
            assertEquals(32000L, retryService.calculateDelayMs(10));
        }

        @Test
        @DisplayName("retries up to 5 times on transient failure then marks FAILED")
        void retriesMaxTimesOnTransientFailure() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Provider timeout"));

            DeliveryResult result = retryService.deliverWithRetry(message);

            assertFalse(result.isSuccessful());
            assertTrue(result.failureReason().contains("Max retries exhausted"));
            // Initial attempt + 5 retries = 6 total calls
            verify(deliveryService, times(6)).deliver(message);
        }

        @Test
        @DisplayName("sleeps with correct backoff delays between retries")
        void sleepsWithCorrectDelays() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Provider timeout"));

            retryService.deliverWithRetry(message);

            // Verify exponential backoff delays were used
            verify(retryService).sleep(2000L);
            verify(retryService).sleep(4000L);
            verify(retryService).sleep(8000L);
            verify(retryService).sleep(16000L);
            verify(retryService).sleep(32000L);
        }

        @Test
        @DisplayName("stops retrying on successful delivery")
        void stopsRetryingOnSuccess() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Timeout"))
                    .thenReturn(DeliveryResult.failed("Timeout"))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            DeliveryResult result = retryService.deliverWithRetry(message);

            assertTrue(result.isSuccessful());
            assertEquals(PROVIDER_MSG_ID, result.providerMessageId());
            // Initial attempt + 2 failed retries + 1 successful = 4 total
            // Wait: first call fails (initial), then retry 0 fails, then retry 1 succeeds = 3 total delivery calls
            // Actually: initial fails, retry[0] fails, retry[1] succeeds = 3
            verify(deliveryService, times(3)).deliver(message);
        }

        @Test
        @DisplayName("does not retry non-retryable failures")
        void doesNotRetryNonRetryableFailures() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.rejected("SESSION_CLOSED"));

            DeliveryResult result = retryService.deliverWithRetry(message);

            assertFalse(result.isSuccessful());
            assertEquals("SESSION_CLOSED", result.failureReason());
            // Only 1 attempt, no retries
            verify(deliveryService, times(1)).deliver(message);
            verify(retryService, never()).sleep(anyLong());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.2 Track delivery status: PENDING → SENT → DELIVERED → READ / FAILED
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.2 Delivery status lifecycle tracking")
    class StatusLifecycleTests {

        @Test
        @DisplayName("creates record in PENDING status initially")
        void createsRecordInPendingStatus() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            retryService.deliverWithRetry(message);

            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    record.getMessageId().equals(MESSAGE_ID)));
        }

        @Test
        @DisplayName("transitions to SENT on successful delivery")
        void transitionsToSentOnSuccess() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            retryService.deliverWithRetry(message);

            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    record.getStatus() == DeliveryStatus.SENT
                            && PROVIDER_MSG_ID.equals(record.getProviderMessageId())
                            && record.getSentAt() != null));
        }

        @Test
        @DisplayName("processes DELIVERED status update from webhook")
        void processesDeliveredStatusUpdate() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "delivered", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertTrue(processed);
            assertEquals(DeliveryStatus.DELIVERED, record.getStatus());
            assertNotNull(record.getDeliveredAt());
        }

        @Test
        @DisplayName("processes READ status update from webhook")
        void processesReadStatusUpdate() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());
            record.markDelivered(Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "read", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertTrue(processed);
            assertEquals(DeliveryStatus.READ, record.getStatus());
            assertNotNull(record.getReadAt());
        }

        @Test
        @DisplayName("accepts READ without prior DELIVERED (out-of-order)")
        void acceptsReadWithoutPriorDelivered() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());
            // Note: no markDelivered() call — simulates out-of-order

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "read", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertTrue(processed);
            assertEquals(DeliveryStatus.READ, record.getStatus());
            // Should infer DELIVERED
            assertNotNull(record.getDeliveredAt());
            assertNotNull(record.getReadAt());
        }

        @Test
        @DisplayName("processes FAILED status update with error details")
        void processesFailedStatusUpdate() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "failed", "+5491155551234",
                    Instant.now(), 131, "Message undeliverable");

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertTrue(processed);
            assertEquals(DeliveryStatus.FAILED, record.getStatus());
            assertNotNull(record.getFailedAt());
            assertTrue(record.getFailureReason().contains("131"));
            assertTrue(record.getFailureReason().contains("Message undeliverable"));
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.3 Correlate status updates by provider_message_id
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.3 Status correlation by provider_message_id")
    class StatusCorrelationTests {

        @Test
        @DisplayName("looks up delivery record by provider_message_id")
        void looksUpByProviderMessageId() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "delivered", "+5491155551234",
                    Instant.now(), null, null);

            retryService.processStatusUpdate(statusUpdate);

            verify(deliveryRecordRepository).findByProviderMessageId(PROVIDER_MSG_ID);
        }

        @Test
        @DisplayName("stores provider_message_id on successful send")
        void storesProviderMessageIdOnSend() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            retryService.deliverWithRetry(message);

            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    PROVIDER_MSG_ID.equals(record.getProviderMessageId())));
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.4 Discard unknown provider_message_id status updates
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.4 Discard unknown provider_message_id")
    class DiscardUnknownTests {

        @Test
        @DisplayName("discards status update with unknown provider_message_id")
        void discardsUnknownProviderMessageId() {
            when(deliveryRecordRepository.findByProviderMessageId("wamid.unknown"))
                    .thenReturn(Optional.empty());

            var statusUpdate = new StatusUpdateDto(
                    "wamid.unknown", "delivered", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertFalse(processed);
            verify(deliveryRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("discards status update with null provider_message_id")
        void discardsNullProviderMessageId() {
            var statusUpdate = new StatusUpdateDto(
                    null, "delivered", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertFalse(processed);
            verify(deliveryRecordRepository, never()).findByProviderMessageId(any());
        }

        @Test
        @DisplayName("discards status update with blank provider_message_id")
        void discardsBlankProviderMessageId() {
            var statusUpdate = new StatusUpdateDto(
                    "  ", "delivered", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertFalse(processed);
            verify(deliveryRecordRepository, never()).findByProviderMessageId(any());
        }

        @Test
        @DisplayName("discards status update with unknown status string")
        void discardsUnknownStatusString() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));

            var statusUpdate = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "unknown_status", "+5491155551234",
                    Instant.now(), null, null);

            boolean processed = retryService.processStatusUpdate(statusUpdate);

            assertFalse(processed);
            verify(deliveryRecordRepository, never()).save(any());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.5 Persist full lifecycle per message in DeliveryRecord
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.5 Full lifecycle persistence in DeliveryRecord")
    class LifecyclePersistenceTests {

        @Test
        @DisplayName("persists record on initial creation")
        void persistsOnCreation() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            retryService.deliverWithRetry(message);

            // At least 2 saves: initial creation + mark sent
            verify(deliveryRecordRepository, atLeast(2)).save(any(DeliveryRecord.class));
        }

        @Test
        @DisplayName("persists record with correct initial fields")
        void persistsWithCorrectInitialFields() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.sent(PROVIDER_MSG_ID));

            retryService.deliverWithRetry(message);

            // The first save persists the initial record with these fields
            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    record.getMessageId().equals(MESSAGE_ID)
                            && record.getFatherId().equals(FATHER_ID)
                            && "WHATSAPP".equals(record.getChannel())
                            && "OUTBOUND".equals(record.getDirection())));
        }

        @Test
        @DisplayName("persists each status update to database")
        void persistsEachStatusUpdate() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());

            when(deliveryRecordRepository.findByProviderMessageId(PROVIDER_MSG_ID))
                    .thenReturn(Optional.of(record));
            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var delivered = new StatusUpdateDto(
                    PROVIDER_MSG_ID, "delivered", "+5491155551234",
                    Instant.now(), null, null);

            retryService.processStatusUpdate(delivered);

            verify(deliveryRecordRepository).save(record);
            assertEquals(DeliveryStatus.DELIVERED, record.getStatus());
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 8.6 Mark failed deliveries after max retries as FAILED with reason
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8.6 Failed deliveries after max retries")
    class FailedAfterMaxRetriesTests {

        @Test
        @DisplayName("marks as FAILED with reason after max retries")
        void marksFailedAfterMaxRetries() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Provider timeout"));

            DeliveryResult result = retryService.deliverWithRetry(message);

            assertFalse(result.isSuccessful());
            assertTrue(result.failureReason().contains("Max retries exhausted"));
            assertTrue(result.failureReason().contains("5 attempts"));
            assertTrue(result.failureReason().contains("Provider timeout"));
        }

        @Test
        @DisplayName("persists FAILED status with failedAt timestamp")
        void persistsFailedStatusWithTimestamp() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Network error"));

            retryService.deliverWithRetry(message);

            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    record.getStatus() == DeliveryStatus.FAILED
                            && record.getFailedAt() != null
                            && record.getFailureReason() != null));
        }

        @Test
        @DisplayName("tracks retry count correctly through all attempts")
        void tracksRetryCountCorrectly() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.failed("Timeout"));

            retryService.deliverWithRetry(message);

            // Final save should have retry_count = 5
            verify(deliveryRecordRepository, atLeastOnce()).save(argThat(record ->
                    record.getRetryCount() == DeliveryRetryService.MAX_RETRIES));
        }

        @Test
        @DisplayName("immediately fails for permanent errors without retrying")
        void immediatelyFailsForPermanentErrors() {
            var message = textMessage();

            when(deliveryRecordRepository.save(any(DeliveryRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(deliveryService.deliver(message))
                    .thenReturn(DeliveryResult.rejected("ENDPOINT_NOT_FOUND"));

            DeliveryResult result = retryService.deliverWithRetry(message);

            assertFalse(result.isSuccessful());
            assertEquals("ENDPOINT_NOT_FOUND", result.failureReason());
            verify(deliveryService, times(1)).deliver(message);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // DeliveryRecord entity tests
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DeliveryRecord entity behavior")
    class DeliveryRecordTests {

        @Test
        @DisplayName("new record starts in PENDING with zero retries")
        void newRecordStartsInPending() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");

            assertEquals(DeliveryStatus.PENDING, record.getStatus());
            assertEquals(0, record.getRetryCount());
            assertEquals("OUTBOUND", record.getDirection());
            assertNull(record.getProviderMessageId());
            assertNull(record.getSentAt());
            assertNull(record.getDeliveredAt());
            assertNull(record.getReadAt());
            assertNull(record.getFailedAt());
        }

        @Test
        @DisplayName("markSent sets status, providerMessageId, and sentAt")
        void markSentSetsFields() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            Instant now = Instant.now();

            record.markSent(PROVIDER_MSG_ID, now);

            assertEquals(DeliveryStatus.SENT, record.getStatus());
            assertEquals(PROVIDER_MSG_ID, record.getProviderMessageId());
            assertEquals(now, record.getSentAt());
        }

        @Test
        @DisplayName("markDelivered sets status and deliveredAt")
        void markDeliveredSetsFields() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            record.markSent(PROVIDER_MSG_ID, Instant.now());
            Instant now = Instant.now();

            record.markDelivered(now);

            assertEquals(DeliveryStatus.DELIVERED, record.getStatus());
            assertEquals(now, record.getDeliveredAt());
        }

        @Test
        @DisplayName("markDelivered infers sentAt if missing")
        void markDeliveredInfersSentAt() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            Instant now = Instant.now();

            record.markDelivered(now);

            assertEquals(now, record.getSentAt());
        }

        @Test
        @DisplayName("markRead sets status and infers intermediate states")
        void markReadInfersIntermediateStates() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            Instant now = Instant.now();

            record.markRead(now);

            assertEquals(DeliveryStatus.READ, record.getStatus());
            assertEquals(now, record.getReadAt());
            assertEquals(now, record.getDeliveredAt());
            assertEquals(now, record.getSentAt());
        }

        @Test
        @DisplayName("markFailed sets status, reason, and failedAt")
        void markFailedSetsFields() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");
            Instant now = Instant.now();

            record.markFailed("Number invalid", now);

            assertEquals(DeliveryStatus.FAILED, record.getStatus());
            assertEquals("Number invalid", record.getFailureReason());
            assertEquals(now, record.getFailedAt());
        }

        @Test
        @DisplayName("incrementRetryCount increases count by one")
        void incrementRetryCountWorks() {
            var record = new DeliveryRecord(MESSAGE_ID, FATHER_ID, "WHATSAPP");

            record.incrementRetryCount();
            assertEquals(1, record.getRetryCount());

            record.incrementRetryCount();
            assertEquals(2, record.getRetryCount());
        }
    }
}
