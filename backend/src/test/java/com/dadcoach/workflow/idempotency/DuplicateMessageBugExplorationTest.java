package com.dadcoach.workflow.idempotency;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Duplicate Message Detection
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3**
 * 
 * <p>This test VERIFIES that the duplicate message bug has been FIXED by the content
 * fingerprinting enhancement in WorkflowIdempotencyService.</p>
 * 
 * <p>The original bug manifested when WhatsApp sent the same message multiple times due to 
 * webhook retries. The old single-parameter API only checked by WhatsApp message ID 
 * (idempotencyKey), so duplicate messages with different webhook delivery IDs bypassed detection.</p>
 * 
 * <p><strong>Fix Implementation:</strong></p>
 * <p>The enhanced API now checks BOTH idempotency key AND content fingerprint (sender + content hash).
 * This allows detection of duplicates even when they have different idempotency keys.</p>
 * 
 * <p><strong>EXPECTED BEHAVIOR (with fix):</strong></p>
 * <ul>
 *   <li>All tests should PASS - demonstrating the fix works correctly</li>
 *   <li>Duplicate messages (same sender + content within 60 seconds) are detected</li>
 *   <li>The cached response is returned without reprocessing</li>
 * </ul>
 * 
 * <p><strong>Examples from production (now fixed):</strong></p>
 * <ul>
 *   <li>Message "כן" received at 18:01:55, same "כן" received at 18:02:18 → Duplicate detected, cached response returned</li>
 *   <li>WhatsApp retries same message with different delivery ID → Duplicate detected via content fingerprint</li>
 * </ul>
 */
class DuplicateMessageBugExplorationTest {

    /**
     * Property test: Duplicate messages with different idempotency keys should be detected.
     * 
     * <p><strong>VERIFICATION:</strong> This test verifies the fix is working correctly.
     * Using the new enhanced API with content fingerprinting, duplicates should now be detected.</p>
     * 
     * <p>The test demonstrates that when two messages with:</p>
     * <ul>
     *   <li>Identical content</li>
     *   <li>Same sender</li>
     *   <li>Different idempotency keys (simulating webhook retries)</li>
     * </ul>
     * <p>...are processed within 60 seconds, the second message should be detected as a duplicate
     * and return the cached response. With the fix, content fingerprinting now catches these duplicates.</p>
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3**
     */
    @Property(tries = 100)
    @Label("Duplicate messages with different idempotency keys should return cached response")
    void duplicateMessagesWithDifferentIdempotencyKeysShouldBeDetected(
            @ForAll("phoneNumbers") String sender,
            @ForAll("messageContents") String messageContent
    ) {
        // Arrange: Create a fresh idempotency service
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        
        // Simulate first message processing
        String firstIdempotencyKey = "wamid." + UUID.randomUUID();
        OutboundMessageDto originalResponse = createMockResponse(sender, "Response to: " + messageContent);
        
        // First message is new - should not find duplicate (using enhanced 3-param API)
        Optional<OutboundMessageDto> firstCheck = idempotencyService.checkDuplicate(
                firstIdempotencyKey, sender, messageContent);
        assertThat(firstCheck)
                .as("First message should not find any duplicate")
                .isEmpty();
        
        // Record the first message as processed (using enhanced 4-param API)
        // This records BOTH by idempotency key AND by content fingerprint
        idempotencyService.recordProcessed(firstIdempotencyKey, sender, messageContent, originalResponse);
        
        // Simulate WhatsApp retry - SAME content from SAME sender but DIFFERENT idempotency key
        // This simulates webhook retry with a new delivery ID
        String secondIdempotencyKey = "wamid." + UUID.randomUUID();
        
        // ACT: Check if the duplicate is detected using the enhanced API
        // With the fix, the service checks BOTH idempotency key AND content fingerprint
        // The content fingerprint (sender + content hash) will match even though the 
        // idempotency keys are different
        Optional<OutboundMessageDto> secondCheck = idempotencyService.checkDuplicate(
                secondIdempotencyKey, sender, messageContent);
        
        // ASSERT: The duplicate SHOULD be detected (test passes after fix)
        // 
        // With the fix implemented:
        // - The service now checks content fingerprint in addition to idempotency key
        // - Same sender + same content within 60 seconds = duplicate detected
        // - Returns the cached response without reprocessing
        assertThat(secondCheck)
                .as("Duplicate message with same content from same sender " +
                    "(but different idempotency key) should be detected via content fingerprinting. " +
                    "Sender: %s, Content: %s, First key: %s, Second key: %s",
                    sender, messageContent, firstIdempotencyKey, secondIdempotencyKey)
                .isPresent()
                .get()
                .extracting(OutboundMessageDto::textContent)
                .isEqualTo("Response to: " + messageContent);
    }

    /**
     * Property test: Rapid duplicate messages within time window should be detected.
     * 
     * <p>This test simulates the exact scenario from production logs where messages
     * "כן" arrived at 18:01:55 and 18:02:18 (23 seconds apart). With the fix, these
     * duplicates are now properly detected via content fingerprinting.</p>
     * 
     * **Validates: Requirements 2.1, 2.2**
     */
    @Property(tries = 50)
    @Label("Messages with identical content within 60 seconds should be detected as duplicates")
    void rapidDuplicateMessagesShouldBeDetected(
            @ForAll("phoneNumbers") String sender,
            @ForAll("hebrewResponses") String hebrewContent
    ) {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        
        // First message at T0
        String firstKey = "wamid.first_" + UUID.randomUUID();
        OutboundMessageDto response = createMockResponse(sender, "תגובה: " + hebrewContent);
        
        // Use enhanced 3-param API for checking
        idempotencyService.checkDuplicate(firstKey, sender, hebrewContent);
        // Use enhanced 4-param API to record both idempotency key and content fingerprint
        idempotencyService.recordProcessed(firstKey, sender, hebrewContent, response);
        
        // Second message at T0 + 23 seconds (within 60 second window) - simulating webhook retry
        String secondKey = "wamid.retry_" + UUID.randomUUID();
        
        // This should detect the duplicate based on content+sender fingerprint
        Optional<OutboundMessageDto> result = idempotencyService.checkDuplicate(
                secondKey, sender, hebrewContent);
        
        // ASSERT: Should be detected as duplicate (passes with fix)
        assertThat(result)
                .as("Hebrew message '%s' from sender '%s' sent twice within 60 seconds " +
                    "should be detected as duplicate via content fingerprinting. " +
                    "This verifies the fix for production bug where 'כן' was processed twice.",
                    hebrewContent, sender)
                .isPresent();
    }

    /**
     * Example-based test demonstrating the exact production scenario.
     * 
     * <p>From logs: Message "כן" received at 18:01:55, same "כן" at 18:02:18 → Both processed</p>
     * <p>With the fix: The second message should now be detected as a duplicate via content fingerprinting.</p>
     * 
     * **Validates: Requirements 2.1, 2.2**
     */
    @Example
    @Label("Production bug scenario: Hebrew 'כן' message now correctly detected as duplicate")
    void productionBugScenarioHebrewYesMessage() {
        // Arrange - exact scenario from production
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String sender = "972501234567"; // Israeli phone number format
        String content = "כן"; // Hebrew "yes" - exact content from production bug
        
        // First message at 18:01:55
        String firstMessageId = "wamid.HBgLOTcyNTAxMjM0NTY3FQIAEhggMTgwMTU1"; // Simulated WhatsApp ID
        OutboundMessageDto response = createMockResponse(sender, "מעולה! נמשיך...");
        
        // Use enhanced API with sender and content
        Optional<OutboundMessageDto> firstCheck = idempotencyService.checkDuplicate(
                firstMessageId, sender, content);
        assertThat(firstCheck).isEmpty(); // First message is new
        
        // Record with enhanced API - this stores in BOTH caches
        idempotencyService.recordProcessed(firstMessageId, sender, content, response);
        
        // Same message arrives at 18:02:18 with different webhook delivery ID
        String retryMessageId = "wamid.HBgLOTcyNTAxMjM0NTY3FQIAEhggMTgwMjE4"; // Different ID
        
        // ACT: Check for duplicate using enhanced API
        Optional<OutboundMessageDto> secondCheck = idempotencyService.checkDuplicate(
                retryMessageId, sender, content);
        
        // ASSERT: Should detect duplicate via content fingerprinting (fix verification)
        assertThat(secondCheck)
                .as("Production scenario fixed - 'כן' message received twice within " +
                    "23 seconds (18:01:55 and 18:02:18) should be detected as duplicate " +
                    "via content fingerprinting (sender + content hash).")
                .isPresent();
    }

    // ============== Generators ==============

    /**
     * Generator for realistic phone numbers (Israeli format used in Dad Coach).
     */
    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.of(
                "972501234567",
                "972521234567", 
                "972531234567",
                "972541234567",
                "972551234567",
                "972581234567"
        );
    }

    /**
     * Generator for typical message content that could be duplicated.
     */
    @Provide
    Arbitrary<String> messageContents() {
        return Arbitraries.of(
                // Simple responses (most likely to be duplicated)
                "כן",
                "לא", 
                "אוקי",
                "בסדר",
                "Yes",
                "No",
                "Ok",
                // Longer messages
                "אני רוצה לקבוע זמן איכות מחר",
                "מתאים לי בשעה 3",
                "זה היה מעולה!",
                "לא הספקתי היום"
        );
    }

    /**
     * Generator for Hebrew responses specifically.
     */
    @Provide
    Arbitrary<String> hebrewResponses() {
        return Arbitraries.of(
                "כן",
                "לא",
                "אולי",
                "בסדר",
                "מעולה",
                "תודה"
        );
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
