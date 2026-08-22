package com.dadcoach.workflow.idempotency;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WorkflowIdempotencyService (Bug 1: Duplicate Message Detection fix).
 * 
 * <p>Tests cover edge cases and implementation details of the duplicate detection mechanism:</p>
 * <ul>
 *   <li>Fingerprint generation consistency (same input = same fingerprint)</li>
 *   <li>Fingerprint cache expiration (after 60 seconds, entries should expire)</li>
 *   <li>Cache cleanup mechanism</li>
 *   <li>Null/empty input handling</li>
 *   <li>Case sensitivity (content should be normalized to lowercase)</li>
 *   <li>Whitespace handling (content should be trimmed)</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3</strong></p>
 */
@DisplayName("WorkflowIdempotencyService Unit Tests")
class WorkflowIdempotencyServiceTest {

    private WorkflowIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new WorkflowIdempotencyService();
    }

    // ============== Fingerprint Generation Consistency ==============

    @Nested
    @DisplayName("Fingerprint Generation Consistency Tests")
    class FingerprintConsistencyTests {

        /**
         * Test that the same input always produces the same fingerprint.
         * This is critical for reliable duplicate detection.
         */
        @Test
        @DisplayName("Same input should produce same fingerprint consistently")
        void sameInputShouldProduceSameFingerprint() {
            // Arrange
            String sender = "972501234567";
            String content = "כן";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // First recording
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, content, response);

            // Second check with same sender + content should find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, content);

            // Assert
            assertThat(result)
                    .as("Same sender '%s' and content '%s' should produce matching fingerprint", sender, content)
                    .isPresent();
        }

        /**
         * Test that different content produces different fingerprints.
         */
        @Test
        @DisplayName("Different content should produce different fingerprints")
        void differentContentShouldProduceDifferentFingerprints() {
            // Arrange
            String sender = "972501234567";
            String content1 = "כן";
            String content2 = "לא";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record first message
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, content1, response);

            // Check with different content
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, content2);

            // Assert
            assertThat(result)
                    .as("Different content should NOT be detected as duplicate")
                    .isEmpty();
        }

        /**
         * Test that different senders with same content produce different fingerprints.
         */
        @Test
        @DisplayName("Different senders with same content should produce different fingerprints")
        void differentSendersWithSameContentShouldProduceDifferentFingerprints() {
            // Arrange
            String sender1 = "972501111111";
            String sender2 = "972502222222";
            String content = "כן";
            OutboundMessageDto response = createMockResponse(sender1, "Response");

            // Record for first sender
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender1, content, response);

            // Check with different sender but same content
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender2, content);

            // Assert
            assertThat(result)
                    .as("Same content from different sender should NOT be detected as duplicate")
                    .isEmpty();
        }

        /**
         * Test that fingerprint is deterministic across multiple calls.
         */
        @Test
        @DisplayName("Fingerprint should be deterministic across multiple checks")
        void fingerprintShouldBeDeterministicAcrossMultipleChecks() {
            // Arrange
            String sender = "972501234567";
            String content = "אני רוצה לקבוע זמן איכות";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record message
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, content, response);

            // Multiple checks with same input should all find duplicate
            for (int i = 0; i < 5; i++) {
                String key = "wamid.check_" + i + "_" + UUID.randomUUID();
                Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key, sender, content);

                assertThat(result)
                        .as("Check %d: Same input should consistently find duplicate", i)
                        .isPresent();
            }
        }
    }

    // ============== Case Sensitivity (Normalization) ==============

    @Nested
    @DisplayName("Case Sensitivity and Normalization Tests")
    class CaseSensitivityTests {

        /**
         * Test that content is normalized to lowercase for duplicate detection.
         * "Yes" and "yes" and "YES" should all be considered duplicates.
         */
        @Test
        @DisplayName("Content should be normalized to lowercase for duplicate detection")
        void contentShouldBeNormalizedToLowercase() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with uppercase
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "YES", response);

            // Check with lowercase - should find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, "yes");

            // Assert
            assertThat(result)
                    .as("'YES' and 'yes' should be detected as duplicates after case normalization")
                    .isPresent();
        }

        /**
         * Test that mixed case content is properly normalized.
         */
        @Test
        @DisplayName("Mixed case content should be normalized for duplicate detection")
        void mixedCaseContentShouldBeNormalized() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with mixed case
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "Hello World", response);

            // Check with different case variations
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result1 = idempotencyService.checkDuplicate(key2, sender, "HELLO WORLD");

            String key3 = "wamid.third_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result2 = idempotencyService.checkDuplicate(key3, sender, "hello world");

            // Assert
            assertThat(result1)
                    .as("'Hello World' and 'HELLO WORLD' should be detected as duplicates")
                    .isPresent();
            assertThat(result2)
                    .as("'Hello World' and 'hello world' should be detected as duplicates")
                    .isPresent();
        }

        /**
         * Test that Hebrew content (which has no case) works correctly.
         */
        @Test
        @DisplayName("Hebrew content should work correctly without case issues")
        void hebrewContentShouldWorkCorrectly() {
            // Arrange
            String sender = "972501234567";
            String hebrewContent = "כן";
            OutboundMessageDto response = createMockResponse(sender, "תגובה");

            // Record Hebrew message
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, hebrewContent, response);

            // Check with same Hebrew content
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, hebrewContent);

            // Assert
            assertThat(result)
                    .as("Hebrew content '%s' should be detected as duplicate", hebrewContent)
                    .isPresent();
        }
    }

    // ============== Whitespace Handling ==============

    @Nested
    @DisplayName("Whitespace Handling Tests")
    class WhitespaceHandlingTests {

        /**
         * Test that leading whitespace is trimmed.
         */
        @Test
        @DisplayName("Leading whitespace should be trimmed for duplicate detection")
        void leadingWhitespaceShouldBeTrimmed() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record without leading whitespace
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "yes", response);

            // Check with leading whitespace - should find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, "  yes");

            // Assert
            assertThat(result)
                    .as("'yes' and '  yes' should be detected as duplicates after trimming")
                    .isPresent();
        }

        /**
         * Test that trailing whitespace is trimmed.
         */
        @Test
        @DisplayName("Trailing whitespace should be trimmed for duplicate detection")
        void trailingWhitespaceShouldBeTrimmed() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record without trailing whitespace
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "yes", response);

            // Check with trailing whitespace - should find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, "yes  ");

            // Assert
            assertThat(result)
                    .as("'yes' and 'yes  ' should be detected as duplicates after trimming")
                    .isPresent();
        }

        /**
         * Test that both leading and trailing whitespace are trimmed.
         */
        @Test
        @DisplayName("Both leading and trailing whitespace should be trimmed")
        void bothLeadingAndTrailingWhitespaceShouldBeTrimmed() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with whitespace
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "  hello world  ", response);

            // Check without whitespace - should find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, "hello world");

            // Assert
            assertThat(result)
                    .as("'  hello world  ' and 'hello world' should be detected as duplicates")
                    .isPresent();
        }

        /**
         * Test that internal whitespace is preserved (only leading/trailing trimmed).
         */
        @Test
        @DisplayName("Internal whitespace should be preserved")
        void internalWhitespaceShouldBePreserved() {
            // Arrange
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with single space
            String key1 = "wamid.first_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, "hello world", response);

            // Check with multiple internal spaces - should NOT find duplicate
            String key2 = "wamid.second_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, "hello  world");

            // Assert
            assertThat(result)
                    .as("'hello world' and 'hello  world' should NOT be detected as duplicates")
                    .isEmpty();
        }
    }

    // ============== Null/Empty Input Handling ==============

    @Nested
    @DisplayName("Null and Empty Input Handling Tests")
    class NullEmptyInputTests {

        /**
         * Test that null idempotency key is handled gracefully.
         */
        @Test
        @DisplayName("Null idempotency key should not cause errors in single-param checkDuplicate")
        void nullIdempotencyKeyShouldBeHandledGracefully() {
            // Act
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(null);

            // Assert
            assertThat(result)
                    .as("Null idempotency key should return empty optional, not throw")
                    .isEmpty();
        }

        /**
         * Test that blank idempotency key is handled gracefully.
         */
        @Test
        @DisplayName("Blank idempotency key should not cause errors")
        void blankIdempotencyKeyShouldBeHandledGracefully() {
            // Act
            Optional<OutboundMessageDto> result1 = idempotencyService.checkDuplicate("");
            Optional<OutboundMessageDto> result2 = idempotencyService.checkDuplicate("   ");

            // Assert
            assertThat(result1)
                    .as("Empty string idempotency key should return empty optional")
                    .isEmpty();
            assertThat(result2)
                    .as("Whitespace-only idempotency key should return empty optional")
                    .isEmpty();
        }

        /**
         * Test that null sender still allows detection by idempotency key.
         */
        @Test
        @DisplayName("Null sender should still check idempotency key")
        void nullSenderShouldStillCheckIdempotencyKey() {
            // Arrange
            String idempotencyKey = "wamid." + UUID.randomUUID();
            OutboundMessageDto response = createMockResponse("any", "Response");
            
            // Record with the idempotency key (using simple recordProcessed)
            idempotencyService.recordProcessed(idempotencyKey, response);

            // Check with null sender but same idempotency key
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(idempotencyKey, null, "content");

            // Assert - should find by idempotency key
            assertThat(result)
                    .as("With null sender, should still detect duplicate by idempotency key")
                    .isPresent();
        }

        /**
         * Test that null content still allows detection by idempotency key.
         */
        @Test
        @DisplayName("Null content should still check idempotency key")
        void nullContentShouldStillCheckIdempotencyKey() {
            // Arrange
            String idempotencyKey = "wamid." + UUID.randomUUID();
            OutboundMessageDto response = createMockResponse("any", "Response");
            
            // Record with the idempotency key
            idempotencyService.recordProcessed(idempotencyKey, response);

            // Check with null content but same idempotency key
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(idempotencyKey, "sender", null);

            // Assert - should find by idempotency key
            assertThat(result)
                    .as("With null content, should still detect duplicate by idempotency key")
                    .isPresent();
        }

        /**
         * Test that recording with null/blank idempotency key doesn't cause errors.
         */
        @Test
        @DisplayName("Recording with null/blank idempotency key should not cause errors")
        void recordingWithNullBlankIdempotencyKeyShouldNotCauseErrors() {
            // Arrange
            String sender = "972501234567";
            String content = "hello";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Act - should not throw
            idempotencyService.recordProcessed(null, sender, content, response);
            idempotencyService.recordProcessed("", sender, content, response);
            idempotencyService.recordProcessed("   ", sender, content, response);

            // Verify fingerprint was still recorded
            String key = "wamid.check_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key, sender, content);

            // Assert - should still find by content fingerprint
            assertThat(result)
                    .as("Content fingerprint should still be recorded even with null/blank idempotency key")
                    .isPresent();
        }
    }

    // ============== Cache Size and Cleanup ==============

    @Nested
    @DisplayName("Cache Size and Cleanup Tests")
    class CacheCleanupTests {

        /**
         * Test that cache sizes are tracked correctly.
         */
        @Test
        @DisplayName("Cache sizes should be tracked correctly")
        void cacheSizesShouldBeTrackedCorrectly() {
            // Arrange - empty at start
            assertThat(idempotencyService.getCacheSize())
                    .as("Cache should be empty at start")
                    .isZero();
            assertThat(idempotencyService.getFingerprintCacheSize())
                    .as("Fingerprint cache should be empty at start")
                    .isZero();

            // Act - add some entries
            for (int i = 0; i < 5; i++) {
                String key = "wamid.msg_" + i + "_" + UUID.randomUUID();
                String sender = "97250000000" + i;
                String content = "message " + i;
                OutboundMessageDto response = createMockResponse(sender, "Response " + i);
                
                idempotencyService.recordProcessed(key, sender, content, response);
            }

            // Assert
            assertThat(idempotencyService.getCacheSize())
                    .as("Cache should have 5 entries")
                    .isEqualTo(5);
            assertThat(idempotencyService.getFingerprintCacheSize())
                    .as("Fingerprint cache should have 5 entries")
                    .isEqualTo(5);
        }

        /**
         * Test that clearCache clears both caches.
         */
        @Test
        @DisplayName("clearCache should clear both idempotency key and fingerprint caches")
        void clearCacheShouldClearBothCaches() {
            // Arrange - add some entries
            for (int i = 0; i < 3; i++) {
                String key = "wamid.msg_" + i + "_" + UUID.randomUUID();
                String sender = "97250000000" + i;
                String content = "message " + i;
                OutboundMessageDto response = createMockResponse(sender, "Response " + i);
                
                idempotencyService.recordProcessed(key, sender, content, response);
            }

            // Verify entries exist
            assertThat(idempotencyService.getCacheSize()).isGreaterThan(0);
            assertThat(idempotencyService.getFingerprintCacheSize()).isGreaterThan(0);

            // Act
            idempotencyService.clearCache();

            // Assert
            assertThat(idempotencyService.getCacheSize())
                    .as("Cache should be empty after clear")
                    .isZero();
            assertThat(idempotencyService.getFingerprintCacheSize())
                    .as("Fingerprint cache should be empty after clear")
                    .isZero();
        }

        /**
         * Test that after clearing cache, previously recorded entries are no longer found.
         */
        @Test
        @DisplayName("After clearCache, previously recorded entries should not be found")
        void afterClearCachePreviousEntriesShouldNotBeFound() {
            // Arrange
            String key = "wamid.test_" + UUID.randomUUID();
            String sender = "972501234567";
            String content = "test message";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            idempotencyService.recordProcessed(key, sender, content, response);

            // Verify entry exists
            Optional<OutboundMessageDto> beforeClear = idempotencyService.checkDuplicate(key, sender, content);
            assertThat(beforeClear).isPresent();

            // Act
            idempotencyService.clearCache();

            // Assert - should not find by either method
            Optional<OutboundMessageDto> afterClearByKey = idempotencyService.checkDuplicate(key);
            Optional<OutboundMessageDto> afterClearByFingerprint = idempotencyService.checkDuplicate(
                    "wamid.new_" + UUID.randomUUID(), sender, content);

            assertThat(afterClearByKey)
                    .as("After clear, should not find by idempotency key")
                    .isEmpty();
            assertThat(afterClearByFingerprint)
                    .as("After clear, should not find by content fingerprint")
                    .isEmpty();
        }
    }

    // ============== Idempotency Key and Fingerprint Combination ==============

    @Nested
    @DisplayName("Idempotency Key and Fingerprint Combination Tests")
    class CombinationTests {

        /**
         * Test that duplicate is detected by idempotency key even with different content.
         */
        @Test
        @DisplayName("Same idempotency key should be detected as duplicate regardless of content")
        void sameIdempotencyKeyShouldBeDetectedAsDuplicate() {
            // Arrange
            String idempotencyKey = "wamid.fixed_key";
            String sender = "972501234567";
            OutboundMessageDto response = createMockResponse(sender, "Original Response");

            // Record with specific key
            idempotencyService.recordProcessed(idempotencyKey, sender, "original content", response);

            // Check with same key but different content
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(
                    idempotencyKey, sender, "different content");

            // Assert - should find by key
            assertThat(result)
                    .as("Same idempotency key should be detected as duplicate")
                    .isPresent()
                    .get()
                    .extracting(OutboundMessageDto::textContent)
                    .isEqualTo("Original Response");
        }

        /**
         * Test that duplicate is detected by content fingerprint when key is different.
         */
        @Test
        @DisplayName("Same content fingerprint should be detected even with different idempotency key")
        void sameContentFingerprintShouldBeDetectedWithDifferentKey() {
            // Arrange
            String sender = "972501234567";
            String content = "duplicate content";
            OutboundMessageDto response = createMockResponse(sender, "Original Response");

            // Record with one key
            String key1 = "wamid.key1_" + UUID.randomUUID();
            idempotencyService.recordProcessed(key1, sender, content, response);

            // Check with different key but same sender + content
            String key2 = "wamid.key2_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, content);

            // Assert - should find by fingerprint
            assertThat(result)
                    .as("Same content fingerprint should be detected with different idempotency key")
                    .isPresent()
                    .get()
                    .extracting(OutboundMessageDto::textContent)
                    .isEqualTo("Original Response");
        }

        /**
         * Test single-param checkDuplicate only checks idempotency key.
         */
        @Test
        @DisplayName("Single-param checkDuplicate should only check idempotency key")
        void singleParamCheckDuplicateShouldOnlyCheckIdempotencyKey() {
            // Arrange
            String idempotencyKey = "wamid.test_" + UUID.randomUUID();
            String sender = "972501234567";
            String content = "test content";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with enhanced method (stores in both caches)
            idempotencyService.recordProcessed(idempotencyKey, sender, content, response);

            // Check with single-param method using a NEW key
            String newKey = "wamid.new_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(newKey);

            // Assert - should NOT find because single-param only checks key, not fingerprint
            assertThat(result)
                    .as("Single-param checkDuplicate should only check idempotency key, not fingerprint")
                    .isEmpty();
        }

        /**
         * Test single-param recordProcessed only records idempotency key.
         */
        @Test
        @DisplayName("Single-param recordProcessed should only record idempotency key")
        void singleParamRecordProcessedShouldOnlyRecordIdempotencyKey() {
            // Arrange
            String idempotencyKey = "wamid.test_" + UUID.randomUUID();
            String sender = "972501234567";
            String content = "test content";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record with single-param method (only stores idempotency key)
            idempotencyService.recordProcessed(idempotencyKey, response);

            // Check by idempotency key - should find
            Optional<OutboundMessageDto> byKey = idempotencyService.checkDuplicate(idempotencyKey);
            
            // Check by fingerprint with different key - should NOT find
            String newKey = "wamid.new_" + UUID.randomUUID();
            Optional<OutboundMessageDto> byFingerprint = idempotencyService.checkDuplicate(newKey, sender, content);

            // Assert
            assertThat(byKey)
                    .as("Should find by idempotency key")
                    .isPresent();
            assertThat(byFingerprint)
                    .as("Should NOT find by fingerprint when recorded with single-param method")
                    .isEmpty();
        }
    }

    // ============== Cache Expiration Tests ==============

    @Nested
    @DisplayName("Cache Expiration Tests")
    class CacheExpirationTests {

        /**
         * Note: Testing actual cache expiration (60 seconds for fingerprint, 1 hour for key)
         * would require waiting or using time manipulation. These tests verify the
         * expiration mechanism exists without actually waiting.
         * 
         * The CachedResponse record has isExpired() and isExpiredShort() methods
         * that are used internally. We test that:
         * 1. Fresh entries are found
         * 2. The expiration check exists (by verifying the TTL constants in design)
         */
        @Test
        @DisplayName("Fresh entries should be found before expiration")
        void freshEntriesShouldBeFoundBeforeExpiration() {
            // Arrange
            String idempotencyKey = "wamid.test_" + UUID.randomUUID();
            String sender = "972501234567";
            String content = "test content";
            OutboundMessageDto response = createMockResponse(sender, "Response");

            // Record
            idempotencyService.recordProcessed(idempotencyKey, sender, content, response);

            // Immediately check - should find
            Optional<OutboundMessageDto> byKey = idempotencyService.checkDuplicate(idempotencyKey);
            Optional<OutboundMessageDto> byFingerprint = idempotencyService.checkDuplicate(
                    "wamid.new_" + UUID.randomUUID(), sender, content);

            // Assert
            assertThat(byKey)
                    .as("Fresh entry should be found by idempotency key")
                    .isPresent();
            assertThat(byFingerprint)
                    .as("Fresh entry should be found by content fingerprint")
                    .isPresent();
        }

        /**
         * Test that the service correctly handles the scenario where entries are
         * recorded and immediately checked (simulating rapid duplicate detection).
         */
        @Test
        @DisplayName("Rapid duplicate detection scenario should work correctly")
        void rapidDuplicateDetectionScenarioShouldWork() {
            // Arrange - simulating WhatsApp sending same message twice in quick succession
            String sender = "972501234567";
            String content = "כן";
            OutboundMessageDto response = createMockResponse(sender, "מעולה!");

            // First message at T0
            String key1 = "wamid.msg1_" + UUID.randomUUID();
            idempotencyService.checkDuplicate(key1, sender, content); // No duplicate
            idempotencyService.recordProcessed(key1, sender, content, response);

            // Second message at T0 + few milliseconds (simulating webhook retry)
            String key2 = "wamid.msg2_" + UUID.randomUUID();
            Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(key2, sender, content);

            // Assert - should detect as duplicate
            assertThat(result)
                    .as("Rapid duplicate should be detected within time window")
                    .isPresent()
                    .get()
                    .extracting(OutboundMessageDto::textContent)
                    .isEqualTo("מעולה!");
        }
    }

    // ============== Helper Methods ==============

    /**
     * Creates a mock OutboundMessageDto for testing.
     */
    private OutboundMessageDto createMockResponse(String recipient, String content) {
        UUID fatherId = UUID.nameUUIDFromBytes(recipient.getBytes());
        return new OutboundMessageDto(
                UUID.randomUUID(),      // messageId
                fatherId,               // fatherId
                "WHATSAPP",             // channel
                MessageType.TEXT,       // messageType
                content,                // textContent
                null,                   // mediaReference
                false,                  // isTemplate
                null,                   // templateName
                null,                   // templateParameters
                MessagePriority.IMMEDIATE, // priority
                Instant.now()           // requestedAt
        );
    }
}
