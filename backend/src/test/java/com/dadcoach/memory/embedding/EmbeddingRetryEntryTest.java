package com.dadcoach.memory.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Tests for EmbeddingRetryEntry.
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Maximum 3 retry attempts per memory</li>
 *   <li>Exponential backoff: 0h, 4h, 12h</li>
 *   <li>Status transitions: PENDING → PROCESSING → COMPLETED/PERMANENTLY_FAILED</li>
 *   <li>Tracking of attempt counts and timestamps</li>
 * </ul>
 *
 * @see EmbeddingRetryEntry
 */
@DisplayName("EmbeddingRetryEntry Tests")
class EmbeddingRetryEntryTest {

    private static final UUID TEST_MEMORY_ID = UUID.randomUUID();
    private static final String TEST_CONTENT = "Lucas loves dinosaurs";

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Entry Creation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entry Creation Tests")
    class EntryCreationTests {

        @Test
        @DisplayName("Should create entry with correct initial state")
        void shouldCreateWithCorrectInitialState() {
            // Act
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(entry.getMemoryId()).isEqualTo(TEST_MEMORY_ID);
            assertThat(entry.getContent()).isEqualTo(TEST_CONTENT);
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
            assertThat(entry.getAttemptCount()).isEqualTo(0);
            assertThat(entry.getCreatedAt()).isNotNull();
            assertThat(entry.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should be ready for immediate first attempt")
        void shouldBeReadyForImmediateFirstAttempt() {
            // Act
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(entry.getNextAttemptAt()).isNotNull();
            assertThat(entry.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
            assertThat(entry.isReadyForProcessing()).isTrue();
        }

        @Test
        @DisplayName("Should be able to retry initially")
        void shouldBeAbleToRetryInitially() {
            // Act
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Assert
            assertThat(entry.canRetry()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Retry Attempt Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retry Attempt Tracking Tests")
    class RetryAttemptTrackingTests {

        @Test
        @DisplayName("Should track attempt count after failure")
        void shouldTrackAttemptCountAfterFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.recordFailure("TIMEOUT", "Request timed out");

            // Assert
            assertThat(entry.getAttemptCount()).isEqualTo(1);
            assertThat(entry.getLastAttemptAt()).isNotNull();
            assertThat(entry.getLastErrorType()).isEqualTo("TIMEOUT");
            assertThat(entry.getLastErrorMessage()).isEqualTo("Request timed out");
        }

        @Test
        @DisplayName("Should remain PENDING after first failure")
        void shouldRemainPendingAfterFirstFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.recordFailure("TIMEOUT", "Request timed out");

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
            assertThat(entry.canRetry()).isTrue();
        }

        @Test
        @DisplayName("Should remain PENDING after second failure")
        void shouldRemainPendingAfterSecondFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.recordFailure("TIMEOUT", "Request timed out");
            entry.recordFailure("RATE_LIMIT", "Too many requests");

            // Assert
            assertThat(entry.getAttemptCount()).isEqualTo(2);
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
            assertThat(entry.canRetry()).isTrue();
        }

        @Test
        @DisplayName("Should become PERMANENTLY_FAILED after third failure")
        void shouldBecomePermanentlyFailedAfterThirdFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.recordFailure("TIMEOUT", "Attempt 1");
            entry.recordFailure("TIMEOUT", "Attempt 2");
            entry.recordFailure("TIMEOUT", "Attempt 3");

            // Assert
            assertThat(entry.getAttemptCount()).isEqualTo(3);
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED);
            assertThat(entry.canRetry()).isFalse();
            assertThat(entry.getNextAttemptAt()).isNull();
        }

        @Test
        @DisplayName("Should update error info on each failure")
        void shouldUpdateErrorInfoOnEachFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act - First failure
            entry.recordFailure("TIMEOUT", "Timeout error");
            assertThat(entry.getLastErrorType()).isEqualTo("TIMEOUT");

            // Act - Second failure with different error
            entry.recordFailure("RATE_LIMIT", "Rate limit error");

            // Assert - Should have updated to latest error
            assertThat(entry.getLastErrorType()).isEqualTo("RATE_LIMIT");
            assertThat(entry.getLastErrorMessage()).isEqualTo("Rate limit error");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Exponential Backoff
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exponential Backoff Tests")
    class ExponentialBackoffTests {

        @Test
        @DisplayName("Should schedule second attempt 4 hours after first failure")
        void shouldScheduleSecondAttemptAfter4Hours() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            Instant beforeFailure = Instant.now();

            // Act
            entry.recordFailure("TIMEOUT", "Attempt 1 failed");

            // Assert - Should be scheduled approximately 4 hours from now
            assertThat(entry.getNextAttemptAt()).isNotNull();
            Instant expectedEarliest = beforeFailure.plus(4, ChronoUnit.HOURS).minusSeconds(10);
            Instant expectedLatest = beforeFailure.plus(4, ChronoUnit.HOURS).plusSeconds(60);
            assertThat(entry.getNextAttemptAt()).isBetween(expectedEarliest, expectedLatest);
        }

        @Test
        @DisplayName("Should schedule third attempt 12 hours after second failure")
        void shouldScheduleThirdAttemptAfter12Hours() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.recordFailure("TIMEOUT", "Attempt 1 failed");
            Instant beforeSecondFailure = Instant.now();

            // Act
            entry.recordFailure("TIMEOUT", "Attempt 2 failed");

            // Assert - Should be scheduled approximately 12 hours from now
            assertThat(entry.getNextAttemptAt()).isNotNull();
            Instant expectedEarliest = beforeSecondFailure.plus(12, ChronoUnit.HOURS).minusSeconds(10);
            Instant expectedLatest = beforeSecondFailure.plus(12, ChronoUnit.HOURS).plusSeconds(60);
            assertThat(entry.getNextAttemptAt()).isBetween(expectedEarliest, expectedLatest);
        }

        @Test
        @DisplayName("Should not schedule any attempt after third failure")
        void shouldNotScheduleAttemptAfterThirdFailure() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.recordFailure("TIMEOUT", "Attempt 1");
            entry.recordFailure("TIMEOUT", "Attempt 2");

            // Act
            entry.recordFailure("TIMEOUT", "Attempt 3");

            // Assert - No more attempts scheduled
            assertThat(entry.getNextAttemptAt()).isNull();
        }

        @Test
        @DisplayName("Should spread attempts over approximately 16 hours")
        void shouldSpreadAttemptsOverApproximately16Hours() {
            // Total time: 0h (initial) + 4h (first backoff) + 12h (second backoff) = 16 hours
            // This is well within the 24 hour requirement

            assertThat(EmbeddingRetryEntry.BACKOFF_HOURS).containsExactly(0, 4, 12);

            int totalBackoffHours = 0;
            for (int hours : EmbeddingRetryEntry.BACKOFF_HOURS) {
                totalBackoffHours += hours;
            }

            // Total is 16 hours, well within 24 hour window
            assertThat(totalBackoffHours).isEqualTo(16);
            assertThat(totalBackoffHours).isLessThanOrEqualTo(24);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Status Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Status Transition Tests")
    class StatusTransitionTests {

        @Test
        @DisplayName("Should transition to PROCESSING when marked processing")
        void shouldTransitionToProcessing() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.markProcessing();

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PROCESSING);
        }

        @Test
        @DisplayName("Should transition to COMPLETED when marked completed")
        void shouldTransitionToCompleted() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.markProcessing();

            // Act
            entry.markCompleted();

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.COMPLETED);
        }

        @Test
        @DisplayName("Should transition back to PENDING when reset")
        void shouldTransitionBackToPendingWhenReset() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.markProcessing();

            // Act
            entry.resetToPending();

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PENDING);
        }

        @Test
        @DisplayName("Should transition to PERMANENTLY_FAILED after max attempts")
        void shouldTransitionToPermanentlyFailedAfterMaxAttempts() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            for (int i = 0; i < EmbeddingRetryEntry.MAX_ATTEMPTS; i++) {
                entry.recordFailure("ERROR", "Attempt " + (i + 1));
            }

            // Assert
            assertThat(entry.getStatus()).isEqualTo(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Ready for Processing Check
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ready for Processing Tests")
    class ReadyForProcessingTests {

        @Test
        @DisplayName("Should be ready when PENDING and next attempt is in the past")
        void shouldBeReadyWhenPendingAndPastDue() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.setNextAttemptAt(Instant.now().minusSeconds(60)); // 1 minute ago

            // Assert
            assertThat(entry.isReadyForProcessing()).isTrue();
        }

        @Test
        @DisplayName("Should not be ready when PENDING but next attempt is in the future")
        void shouldNotBeReadyWhenPendingButFuture() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.setNextAttemptAt(Instant.now().plusSeconds(3600)); // 1 hour from now

            // Assert
            assertThat(entry.isReadyForProcessing()).isFalse();
        }

        @Test
        @DisplayName("Should not be ready when PROCESSING")
        void shouldNotBeReadyWhenProcessing() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.markProcessing();

            // Assert
            assertThat(entry.isReadyForProcessing()).isFalse();
        }

        @Test
        @DisplayName("Should not be ready when COMPLETED")
        void shouldNotBeReadyWhenCompleted() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            entry.markCompleted();

            // Assert
            assertThat(entry.isReadyForProcessing()).isFalse();
        }

        @Test
        @DisplayName("Should not be ready when PERMANENTLY_FAILED")
        void shouldNotBeReadyWhenPermanentlyFailed() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            for (int i = 0; i < EmbeddingRetryEntry.MAX_ATTEMPTS; i++) {
                entry.recordFailure("ERROR", "Fail");
            }

            // Assert
            assertThat(entry.isReadyForProcessing()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Error Message Truncation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Message Handling Tests")
    class ErrorMessageHandlingTests {

        @Test
        @DisplayName("Should truncate long error messages")
        void shouldTruncateLongErrorMessages() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);
            String longMessage = "x".repeat(2000);

            // Act
            entry.recordFailure("ERROR", longMessage);

            // Assert
            assertThat(entry.getLastErrorMessage()).isNotNull();
            assertThat(entry.getLastErrorMessage().length()).isLessThanOrEqualTo(1003); // 1000 + "..."
        }

        @Test
        @DisplayName("Should handle null error message")
        void shouldHandleNullErrorMessage() {
            // Arrange
            EmbeddingRetryEntry entry = new EmbeddingRetryEntry(TEST_MEMORY_ID, TEST_CONTENT);

            // Act
            entry.recordFailure("ERROR", null);

            // Assert
            assertThat(entry.getLastErrorMessage()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Constants
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have MAX_ATTEMPTS set to 3")
        void shouldHaveMaxAttempts3() {
            assertThat(EmbeddingRetryEntry.MAX_ATTEMPTS).isEqualTo(3);
        }

        @Test
        @DisplayName("Should have correct backoff hours array")
        void shouldHaveCorrectBackoffHours() {
            assertThat(EmbeddingRetryEntry.BACKOFF_HOURS).hasSize(3);
            assertThat(EmbeddingRetryEntry.BACKOFF_HOURS[0]).isEqualTo(0);  // Immediate
            assertThat(EmbeddingRetryEntry.BACKOFF_HOURS[1]).isEqualTo(4);  // 4 hours
            assertThat(EmbeddingRetryEntry.BACKOFF_HOURS[2]).isEqualTo(12); // 12 hours
        }
    }
}
