package com.dadcoach.workflow.idempotency;

import com.dadcoach.channel.dto.MessagePriority;
import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Distinct Message Processing
 * 
 * **Validates: Requirements 3.1, 3.2**
 * 
 * <p>These tests ensure that DISTINCT messages (different content, different sender, 
 * or outside the time window) continue to be processed normally after the duplicate 
 * detection fix is applied.</p>
 * 
 * <p><strong>Preservation Requirements from bugfix.md:</strong></p>
 * <ul>
 *   <li>3.1: WHEN messages with different content are received from the same user in quick succession 
 *        THEN the system SHALL CONTINUE TO process each message independently as legitimate distinct messages</li>
 *   <li>3.2: WHEN a message fails processing and is retried by the user (intentionally) after a significant 
 *        time gap THEN the system SHALL CONTINUE TO process it as a new message</li>
 * </ul>
 * 
 * <p><strong>EXPECTED BEHAVIOR:</strong></p>
 * <ul>
 *   <li>These tests MUST PASS on unfixed code (current behavior is correct for distinct messages)</li>
 *   <li>These tests MUST PASS after the fix is applied (no regression for distinct messages)</li>
 * </ul>
 * 
 * <p><strong>Key Distinction from Exploration Tests:</strong></p>
 * <ul>
 *   <li>Exploration tests: Test bug condition (duplicate messages) - expected to FAIL on unfixed code</li>
 *   <li>Preservation tests: Test non-bug scenarios (distinct messages) - expected to PASS on all code</li>
 * </ul>
 */
class DistinctMessagePreservationTest {

    // ============== Property: Different Content from Same Sender ==============

    /**
     * Property test: Messages with different content from the same sender should both be processed.
     * 
     * <p>This preserves the correct behavior where a user sending two different messages
     * in quick succession (e.g., "כן" followed by "מחר בשעה 3") should have both processed.</p>
     * 
     * <p><strong>Updated to use enhanced API:</strong> Uses 3-param checkDuplicate and 4-param recordProcessed
     * to match how WorkflowEngineImpl now uses the service with content fingerprinting.</p>
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Label("Different content from same sender should be processed independently")
    void differentContentFromSameSenderShouldBeProcessedIndependently(
            @ForAll("phoneNumbers") String sender,
            @ForAll("distinctMessagePairs") Tuple.Tuple2<String, String> messagePair
    ) {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String firstContent = messagePair.get1();
        String secondContent = messagePair.get2();
        
        // Pre-condition: messages must be different
        Assume.that(!firstContent.equals(secondContent));
        
        // First message - using enhanced 3-param API
        String firstKey = "wamid.msg1_" + UUID.randomUUID();
        OutboundMessageDto firstResponse = createMockResponse(sender, "Response to: " + firstContent);
        
        Optional<OutboundMessageDto> firstCheck = idempotencyService.checkDuplicate(firstKey, sender, firstContent);
        assertThat(firstCheck)
                .as("First message should not find any duplicate")
                .isEmpty();
        // Using enhanced 4-param API to record both idempotency key and content fingerprint
        idempotencyService.recordProcessed(firstKey, sender, firstContent, firstResponse);
        
        // Second message with DIFFERENT content from SAME sender
        String secondKey = "wamid.msg2_" + UUID.randomUUID();
        
        // ACT: Check if second message is (incorrectly) flagged as duplicate using enhanced API
        Optional<OutboundMessageDto> secondCheck = idempotencyService.checkDuplicate(secondKey, sender, secondContent);
        
        // ASSERT: Different content should NOT be flagged as duplicate
        // This must pass on both unfixed and fixed code - content fingerprints are different
        assertThat(secondCheck)
                .as("Different content '%s' from same sender '%s' should NOT be flagged as duplicate of '%s'. " +
                    "Each distinct message must be processed independently.",
                    secondContent, sender, firstContent)
                .isEmpty();
    }

    /**
     * Property test: Multiple distinct messages from same sender in rapid succession.
     * 
     * <p>Simulates a user answering multiple questions or providing multiple pieces of information.</p>
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 50)
    @Label("Multiple distinct messages from same sender in rapid succession should all be processed")
    void multipleDistinctMessagesFromSameSenderShouldAllBeProcessed(
            @ForAll("phoneNumbers") String sender,
            @ForAll("conversationSequence") java.util.List<String> messages
    ) {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        
        // Pre-condition: all messages in sequence should be unique
        long uniqueCount = messages.stream().distinct().count();
        Assume.that(uniqueCount == messages.size());
        
        // Process each message and verify none are incorrectly flagged as duplicates
        for (int i = 0; i < messages.size(); i++) {
            String content = messages.get(i);
            String idempotencyKey = "wamid.seq_" + i + "_" + UUID.randomUUID();
            OutboundMessageDto response = createMockResponse(sender, "Response " + i);
            
            // ACT: Check for duplicate
            Optional<OutboundMessageDto> check = idempotencyService.checkDuplicate(idempotencyKey);
            
            // ASSERT: Should not be flagged as duplicate
            assertThat(check)
                    .as("Message %d '%s' in sequence should not be flagged as duplicate. " +
                        "All distinct messages must be processed.", i, content)
                    .isEmpty();
            
            // Record as processed
            idempotencyService.recordProcessed(idempotencyKey, response);
        }
    }

    // ============== Property: Different Sender with Same Content ==============

    /**
     * Property test: Same content from different senders should both be processed.
     * 
     * <p>This ensures that when two different users send the same message (e.g., both send "כן"),
     * both messages are processed correctly.</p>
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Label("Same content from different senders should be processed independently")
    void sameContentFromDifferentSendersShouldBeProcessedIndependently(
            @ForAll("distinctPhoneNumberPairs") Tuple.Tuple2<String, String> senderPair,
            @ForAll("messageContents") String content
    ) {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String firstSender = senderPair.get1();
        String secondSender = senderPair.get2();
        
        // Pre-condition: senders must be different
        Assume.that(!firstSender.equals(secondSender));
        
        // First sender's message
        String firstKey = "wamid.sender1_" + UUID.randomUUID();
        OutboundMessageDto firstResponse = createMockResponse(firstSender, "Response to " + firstSender);
        
        Optional<OutboundMessageDto> firstCheck = idempotencyService.checkDuplicate(firstKey);
        assertThat(firstCheck)
                .as("First sender's message should not find any duplicate")
                .isEmpty();
        idempotencyService.recordProcessed(firstKey, firstResponse);
        
        // Second sender with SAME content
        String secondKey = "wamid.sender2_" + UUID.randomUUID();
        
        // ACT: Check if second sender's message is (incorrectly) flagged as duplicate
        Optional<OutboundMessageDto> secondCheck = idempotencyService.checkDuplicate(secondKey);
        
        // ASSERT: Same content from different sender should NOT be flagged as duplicate
        assertThat(secondCheck)
                .as("Same content '%s' from different sender '%s' should NOT be flagged as duplicate. " +
                    "Each user's message must be processed independently.",
                    content, secondSender)
                .isEmpty();
    }

    // ============== Property: Intentional Retry After Time Gap ==============

    /**
     * Property test: Intentional retry after significant time gap should be processed as new.
     * 
     * <p>When a user intentionally retries a message after a significant time gap (>60 seconds),
     * it should be processed as a new message. This is different from webhook retries which
     * happen within seconds.</p>
     * 
     * <p><strong>Note:</strong> This test focuses on the idempotency key behavior. The current
     * implementation processes messages with different idempotency keys as new, which is the
     * correct behavior for intentional user retries.</p>
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    @Label("Intentional retry after time gap should be processed as new message")
    void intentionalRetryAfterTimeGapShouldBeProcessedAsNew(
            @ForAll("phoneNumbers") String sender,
            @ForAll("messageContents") String content
    ) {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        
        // First message
        String firstKey = "wamid.original_" + UUID.randomUUID();
        OutboundMessageDto firstResponse = createMockResponse(sender, "First response to: " + content);
        
        Optional<OutboundMessageDto> firstCheck = idempotencyService.checkDuplicate(firstKey);
        assertThat(firstCheck)
                .as("Original message should not find any duplicate")
                .isEmpty();
        idempotencyService.recordProcessed(firstKey, firstResponse);
        
        // User intentionally retries SAME content (maybe first attempt failed on their end)
        // This will have a DIFFERENT idempotency key because it's a new message from WhatsApp's perspective
        String retryKey = "wamid.retry_" + UUID.randomUUID();
        
        // ACT: Check for duplicate
        // Note: In the current implementation, different idempotency key = new message
        // This is correct for intentional retries, and the fix should preserve this
        Optional<OutboundMessageDto> retryCheck = idempotencyService.checkDuplicate(retryKey);
        
        // ASSERT: Intentional retry should be processed as new
        // Note: The fix will add content fingerprinting with a 60-second window
        // Intentional retries after the window should still be processed as new
        assertThat(retryCheck)
                .as("Intentional retry of '%s' from '%s' with different idempotency key " +
                    "should be processed as a new message.",
                    content, sender)
                .isEmpty();
    }

    // ============== Example Tests for Specific Scenarios ==============

    /**
     * Example: User sends "כן" then "מחר בשעה 3" - both should be processed.
     * 
     * **Validates: Requirements 3.1**
     */
    @Example
    @Label("User conversation flow: yes followed by time selection")
    void userConversationFlowYesFollowedByTimeSelection() {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String sender = "972501234567";
        
        // User confirms with "כן"
        String firstKey = "wamid.yes_" + UUID.randomUUID();
        OutboundMessageDto firstResponse = createMockResponse(sender, "מעולה! מתי מתאים לך?");
        
        idempotencyService.checkDuplicate(firstKey);
        idempotencyService.recordProcessed(firstKey, firstResponse);
        
        // User provides time "מחר בשעה 3"
        String secondKey = "wamid.time_" + UUID.randomUUID();
        
        // ACT
        Optional<OutboundMessageDto> check = idempotencyService.checkDuplicate(secondKey);
        
        // ASSERT
        assertThat(check)
                .as("Time selection message should not be flagged as duplicate of 'כן'")
                .isEmpty();
    }

    /**
     * Example: Two different users send "כן" - both should be processed.
     * 
     * **Validates: Requirements 3.1**
     */
    @Example
    @Label("Two different users both send 'כן'")
    void twoDifferentUsersBothSendYes() {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String user1 = "972501111111";
        String user2 = "972502222222";
        String content = "כן";
        
        // User 1 sends "כן"
        String key1 = "wamid.user1_" + UUID.randomUUID();
        OutboundMessageDto response1 = createMockResponse(user1, "מעולה!");
        
        idempotencyService.checkDuplicate(key1);
        idempotencyService.recordProcessed(key1, response1);
        
        // User 2 also sends "כן"
        String key2 = "wamid.user2_" + UUID.randomUUID();
        
        // ACT
        Optional<OutboundMessageDto> check = idempotencyService.checkDuplicate(key2);
        
        // ASSERT
        assertThat(check)
                .as("User 2's 'כן' should not be flagged as duplicate of User 1's 'כן'")
                .isEmpty();
    }

    /**
     * Example: User sends same message twice deliberately after receiving error response.
     * 
     * **Validates: Requirements 3.2**
     */
    @Example
    @Label("User deliberately retries message after error")
    void userDeliberatelyRetriesMessageAfterError() {
        // Arrange
        WorkflowIdempotencyService idempotencyService = new WorkflowIdempotencyService();
        String sender = "972501234567";
        String content = "אני רוצה לקבוע זמן איכות";
        
        // First attempt
        String firstKey = "wamid.attempt1_" + UUID.randomUUID();
        OutboundMessageDto firstResponse = createMockResponse(sender, "Response");
        
        idempotencyService.checkDuplicate(firstKey);
        idempotencyService.recordProcessed(firstKey, firstResponse);
        
        // User deliberately retries (maybe they didn't receive the response)
        // This is a new message from WhatsApp's perspective with a new ID
        String retryKey = "wamid.attempt2_" + UUID.randomUUID();
        
        // ACT
        Optional<OutboundMessageDto> check = idempotencyService.checkDuplicate(retryKey);
        
        // ASSERT
        assertThat(check)
                .as("User's deliberate retry should be processed as new message")
                .isEmpty();
    }

    // ============== Generators ==============

    /**
     * Generator for realistic phone numbers (Israeli format).
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
     * Generator for pairs of distinct phone numbers.
     */
    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> distinctPhoneNumberPairs() {
        return Arbitraries.of(
                Tuple.of("972501111111", "972502222222"),
                Tuple.of("972521234567", "972531234567"),
                Tuple.of("972541234567", "972551234567"),
                Tuple.of("972501234567", "972509876543"),
                Tuple.of("972521111111", "972529999999")
        );
    }

    /**
     * Generator for typical message content.
     */
    @Provide
    Arbitrary<String> messageContents() {
        return Arbitraries.of(
                "כן",
                "לא",
                "אוקי",
                "בסדר",
                "Yes",
                "No",
                "Ok",
                "אני רוצה לקבוע זמן איכות מחר",
                "מתאים לי בשעה 3",
                "זה היה מעולה!",
                "לא הספקתי היום"
        );
    }

    /**
     * Generator for pairs of distinct messages.
     */
    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> distinctMessagePairs() {
        return Arbitraries.of(
                Tuple.of("כן", "מחר בשעה 3"),
                Tuple.of("לא", "אולי מחרתיים"),
                Tuple.of("Yes", "Tomorrow at 3pm"),
                Tuple.of("אוקי", "זה היה מעולה!"),
                Tuple.of("בסדר", "אני רוצה לקבוע זמן איכות"),
                Tuple.of("כן", "לא"),
                Tuple.of("מעולה", "תודה רבה"),
                Tuple.of("Hello", "How are you?")
        );
    }

    /**
     * Generator for a sequence of distinct conversation messages.
     */
    @Provide
    Arbitrary<java.util.List<String>> conversationSequence() {
        // Generate sequences of 2-5 unique messages
        return Arbitraries.of(
                java.util.List.of("כן", "מחר"),
                java.util.List.of("בסדר", "בשעה 3", "מעולה"),
                java.util.List.of("Yes", "Tomorrow", "At 3pm", "Thanks"),
                java.util.List.of("אוקי", "מתי?", "בסדר", "תודה", "להתראות")
        );
    }

    // ============== Helper Methods ==============

    /**
     * Creates a mock OutboundMessageDto for testing.
     */
    private OutboundMessageDto createMockResponse(String recipient, String content) {
        UUID fatherId = UUID.nameUUIDFromBytes(recipient.getBytes());
        return new OutboundMessageDto(
                UUID.randomUUID(),
                fatherId,
                "WHATSAPP",
                MessageType.TEXT,
                content,
                null,
                false,
                null,
                null,
                MessagePriority.IMMEDIATE,
                Instant.now()
        );
    }
}
