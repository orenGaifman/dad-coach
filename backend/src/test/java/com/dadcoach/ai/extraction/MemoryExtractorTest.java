package com.dadcoach.ai.extraction;

import com.dadcoach.ai.AiMessage;
import com.dadcoach.ai.output.CompletedConversation;
import com.dadcoach.ai.output.MemoryExtractionOutput;
import com.dadcoach.ai.output.MemoryExtractionOutput.ExtractedMemory;
import com.dadcoach.ai.prompt.TokenBudgetManager;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.ai.routing.FallbackChain;
import com.dadcoach.ai.routing.ModelRouter;
import com.dadcoach.domain.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemoryExtractor — validates extraction logic, content validation,
 * score ranges, structured output, and token budget enforcement.
 */
class MemoryExtractorTest {

    private ModelRouter modelRouter;
    private MemoryExtractor extractor;
    private TokenBudgetManager tokenBudgetManager;

    @BeforeEach
    void setUp() {
        modelRouter = mock(ModelRouter.class);
        tokenBudgetManager = new TokenBudgetManager();
        extractor = new MemoryExtractor(modelRouter, tokenBudgetManager, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private CompletedConversation sampleConversation() {
        return new CompletedConversation(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ConversationType.DAILY_COACHING,
            List.of(
                AiMessage.user("My son is 5 years old and has trouble sharing with other kids."),
                AiMessage.assistant("I understand your concern. Sharing is a skill that develops over time."),
                AiMessage.user("Yes, I worry that he won't make friends."),
                AiMessage.assistant("That's a natural feeling. Children at 5 years old are learning social skills.")
            )
        );
    }

    private FallbackChain.FallbackResult mockSuccessResult(String content) {
        AiProviderResponse response = new AiProviderResponse(
            content, "gpt-4o-mini", "openai", 100, 200, "stop", Duration.ofMillis(500));
        return new FallbackChain.FallbackResult(response, 0, List.of());
    }

    @Nested
    @DisplayName("9.1 Extract memories from conversation transcript via AI")
    class ExtractFromTranscript {

        @Test
        @DisplayName("should extract memories from a valid AI response")
        void extractsMemoriesFromValidResponse() {
            String aiResponse = """
                [
                  {"category": "CHALLENGE", "content": "Child has difficulty sharing with other kids", "importance_score": 7, "confidence_score": 0.9, "subject_type": "child"},
                  {"category": "IDENTITY", "content": "5-year-old child", "importance_score": 5, "confidence_score": 0.95, "subject_type": "child"}
                ]
                """;
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(aiResponse));

            MemoryExtractionOutput output = extractor.extract(sampleConversation());

            assertNotNull(output);
            assertEquals(2, output.memories().size());
            assertTrue(output.validationPassed());
        }

        @Test
        @DisplayName("should call model router with conversation context")
        void callsModelRouterWithContext() {
            String aiResponse = "[]";
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(aiResponse));

            extractor.extract(sampleConversation());

            verify(modelRouter).route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING));
        }

        @Test
        @DisplayName("should return empty output when AI call fails")
        void returnsEmptyOnAiFailure() {
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenThrow(new RuntimeException("AI provider unavailable"));

            CompletedConversation conversation = sampleConversation();
            MemoryExtractionOutput output = extractor.extract(conversation);

            assertNotNull(output);
            assertTrue(output.memories().isEmpty());
            assertFalse(output.validationPassed());
        }

        @Test
        @DisplayName("should handle empty conversation gracefully")
        void handlesEmptyConversation() {
            CompletedConversation conversation = new CompletedConversation(
                UUID.randomUUID(), UUID.randomUUID(), ConversationType.DAILY_COACHING, List.of());

            String aiResponse = "[]";
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(aiResponse));

            MemoryExtractionOutput output = extractor.extract(conversation);

            assertNotNull(output);
            assertEquals(0, output.memories().size());
        }
    }

    @Nested
    @DisplayName("9.2 Include category, content, importance_score, confidence_score, subject_type in output")
    class StructuredFields {

        @Test
        @DisplayName("each extracted memory should have all required fields")
        void allFieldsPresent() {
            String aiResponse = """
                [
                  {"category": "GOAL", "content": "Father wants his child to make friends", "importance_score": 8, "confidence_score": 0.85, "subject_type": "father"}
                ]
                """;
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(aiResponse));

            MemoryExtractionOutput output = extractor.extract(sampleConversation());

            assertEquals(1, output.memories().size());
            ExtractedMemory memory = output.memories().get(0);
            assertEquals("GOAL", memory.category());
            assertEquals("Father wants his child to make friends", memory.content());
            assertEquals(8, memory.importanceScore());
            assertEquals(0.85, memory.confidenceScore(), 0.01);
            assertEquals("father", memory.subjectType());
        }

        @Test
        @DisplayName("should accept all valid categories")
        void acceptsAllValidCategories() {
            for (String category : MemoryExtractor.VALID_CATEGORIES) {
                Map<String, Object> raw = Map.of(
                    "category", category,
                    "content", "Test content for " + category,
                    "importance_score", 5,
                    "confidence_score", 0.8,
                    "subject_type", "child"
                );
                ExtractedMemory memory = extractor.validateAndBuildMemory(raw);
                assertNotNull(memory, "Category " + category + " should be valid");
                assertEquals(category, memory.category());
            }
        }

        @Test
        @DisplayName("should accept all valid subject types")
        void acceptsAllValidSubjectTypes() {
            for (String subjectType : MemoryExtractor.VALID_SUBJECT_TYPES) {
                Map<String, Object> raw = Map.of(
                    "category", "IDENTITY",
                    "content", "Test content",
                    "importance_score", 5,
                    "confidence_score", 0.8,
                    "subject_type", subjectType
                );
                ExtractedMemory memory = extractor.validateAndBuildMemory(raw);
                assertNotNull(memory);
                assertEquals(subjectType, memory.subjectType());
            }
        }
    }

    @Nested
    @DisplayName("9.3 Limit content to 500 characters per memory")
    class ContentLengthLimit {

        @Test
        @DisplayName("content at exactly 500 characters should pass")
        void contentAt500CharsIsValid() {
            String content500 = "a".repeat(500);
            Map<String, Object> raw = Map.of(
                "category", "CONTEXT",
                "content", content500,
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "family"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(500, memory.content().length());
        }

        @Test
        @DisplayName("content exceeding 500 characters should be truncated")
        void contentExceeding500IsTruncated() {
            String longContent = "a".repeat(600);
            Map<String, Object> raw = Map.of(
                "category", "CONTEXT",
                "content", longContent,
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "family"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(500, memory.content().length());
        }

        @Test
        @DisplayName("empty content should be rejected")
        void emptyContentRejected() {
            Map<String, Object> raw = Map.of(
                "category", "CONTEXT",
                "content", "",
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "family"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNull(memory);
        }
    }

    @Nested
    @DisplayName("9.4 Ensure importance_score between 1-10 and confidence between 0.0-1.0")
    class ScoreRanges {

        @Test
        @DisplayName("importance_score 0 should be clamped to 1")
        void importanceZeroClampedTo1() {
            Map<String, Object> raw = Map.of(
                "category", "IDENTITY",
                "content", "Test memory",
                "importance_score", 0,
                "confidence_score", 0.5,
                "subject_type", "child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(1, memory.importanceScore());
        }

        @Test
        @DisplayName("importance_score 11 should be clamped to 10")
        void importance11ClampedTo10() {
            Map<String, Object> raw = Map.of(
                "category", "IDENTITY",
                "content", "Test memory",
                "importance_score", 11,
                "confidence_score", 0.5,
                "subject_type", "child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(10, memory.importanceScore());
        }

        @Test
        @DisplayName("importance_score within range should pass unchanged")
        void importanceInRangeUnchanged() {
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> raw = Map.of(
                    "category", "IDENTITY",
                    "content", "Test memory",
                    "importance_score", i,
                    "confidence_score", 0.5,
                    "subject_type", "child"
                );
                ExtractedMemory memory = extractor.validateAndBuildMemory(raw);
                assertNotNull(memory);
                assertEquals(i, memory.importanceScore());
            }
        }

        @Test
        @DisplayName("confidence_score -0.1 should be clamped to 0.0")
        void negativeConfidenceClampedTo0() {
            Map<String, Object> raw = Map.of(
                "category", "IDENTITY",
                "content", "Test memory",
                "importance_score", 5,
                "confidence_score", -0.1,
                "subject_type", "child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(0.0, memory.confidenceScore(), 0.001);
        }

        @Test
        @DisplayName("confidence_score 1.5 should be clamped to 1.0")
        void confidenceAbove1ClampedTo1() {
            Map<String, Object> raw = Map.of(
                "category", "IDENTITY",
                "content", "Test memory",
                "importance_score", 5,
                "confidence_score", 1.5,
                "subject_type", "child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals(1.0, memory.confidenceScore(), 0.001);
        }

        @Test
        @DisplayName("confidence_score 0.0 and 1.0 boundary values should be valid")
        void confidenceBoundaryValues() {
            Map<String, Object> raw0 = Map.of(
                "category", "IDENTITY", "content", "Test", "importance_score", 5,
                "confidence_score", 0.0, "subject_type", "child"
            );
            Map<String, Object> raw1 = Map.of(
                "category", "IDENTITY", "content", "Test", "importance_score", 5,
                "confidence_score", 1.0, "subject_type", "child"
            );

            ExtractedMemory mem0 = extractor.validateAndBuildMemory(raw0);
            ExtractedMemory mem1 = extractor.validateAndBuildMemory(raw1);

            assertNotNull(mem0);
            assertEquals(0.0, mem0.confidenceScore(), 0.001);
            assertNotNull(mem1);
            assertEquals(1.0, mem1.confidenceScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("9.5 Return structured MemoryExtractionOutput (not raw AI text)")
    class StructuredOutput {

        @Test
        @DisplayName("output should be a MemoryExtractionOutput record with all fields")
        void returnsStructuredRecord() {
            String aiResponse = """
                [
                  {"category": "PREFERENCE", "content": "Father prefers gentle discipline", "importance_score": 6, "confidence_score": 0.8, "subject_type": "father"}
                ]
                """;
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(aiResponse));

            CompletedConversation conversation = sampleConversation();
            MemoryExtractionOutput output = extractor.extract(conversation);

            assertNotNull(output);
            assertInstanceOf(MemoryExtractionOutput.class, output);
            assertEquals(conversation.conversationId().toString(), output.conversationId());
            assertEquals("gpt-4o-mini", output.model());
            assertTrue(output.validationPassed());
            assertNotNull(output.memories());
            assertFalse(output.memories().isEmpty());
        }

        @Test
        @DisplayName("should handle markdown-wrapped JSON response")
        void handlesMarkdownWrappedJson() {
            String wrappedResponse = """
                ```json
                [{"category": "MILESTONE", "content": "Child learned to share", "importance_score": 8, "confidence_score": 0.9, "subject_type": "child"}]
                ```
                """;
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(wrappedResponse));

            MemoryExtractionOutput output = extractor.extract(sampleConversation());

            assertEquals(1, output.memories().size());
            assertEquals("MILESTONE", output.memories().get(0).category());
        }

        @Test
        @DisplayName("should return empty list for invalid JSON response")
        void returnsEmptyForInvalidJson() {
            String invalidResponse = "This is not JSON at all. I extracted some memories from the text.";
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(invalidResponse));

            MemoryExtractionOutput output = extractor.extract(sampleConversation());

            assertNotNull(output);
            assertTrue(output.memories().isEmpty());
            assertFalse(output.validationPassed());
        }

        @Test
        @DisplayName("should filter out invalid memories and keep valid ones")
        void filtersInvalidKeepsValid() {
            String mixedResponse = """
                [
                  {"category": "IDENTITY", "content": "Valid memory", "importance_score": 5, "confidence_score": 0.8, "subject_type": "child"},
                  {"category": "INVALID_CATEGORY", "content": "Bad memory", "importance_score": 5, "confidence_score": 0.8, "subject_type": "child"},
                  {"category": "GOAL", "content": "Another valid one", "importance_score": 7, "confidence_score": 0.9, "subject_type": "father"}
                ]
                """;
            when(modelRouter.route(any(AiProviderRequest.class), eq(ConversationType.DAILY_COACHING)))
                .thenReturn(mockSuccessResult(mixedResponse));

            MemoryExtractionOutput output = extractor.extract(sampleConversation());

            assertEquals(2, output.memories().size());
            assertEquals("IDENTITY", output.memories().get(0).category());
            assertEquals("GOAL", output.memories().get(1).category());
        }
    }

    @Nested
    @DisplayName("9.6 Never exceed token budget for extraction prompt")
    class TokenBudget {

        @Test
        @DisplayName("extraction prompt should fit within token budget")
        void promptFitsWithinBudget() {
            CompletedConversation conversation = sampleConversation();
            List<AiMessage> messages = extractor.buildExtractionPrompt(conversation);

            int totalTokens = extractor.countPromptTokens(messages);

            assertTrue(totalTokens <= MemoryExtractor.EXTRACTION_TOKEN_BUDGET,
                "Prompt tokens (" + totalTokens + ") should not exceed budget (" +
                    MemoryExtractor.EXTRACTION_TOKEN_BUDGET + ")");
        }

        @Test
        @DisplayName("long conversation should be truncated to fit budget")
        void longConversationTruncated() {
            // Create a very long conversation that would exceed the budget
            List<AiMessage> longMessages = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                longMessages.add(AiMessage.user("This is a very long message from the father who " +
                    "is talking about many different things with his son and his coach. " +
                    "Message number " + i));
                longMessages.add(AiMessage.assistant("This is a detailed response from the coach " +
                    "who provides guidance on parenting and fatherhood. Response " + i));
            }

            CompletedConversation longConversation = new CompletedConversation(
                UUID.randomUUID(), UUID.randomUUID(), ConversationType.DAILY_COACHING, longMessages);

            List<AiMessage> messages = extractor.buildExtractionPrompt(longConversation);
            int totalTokens = extractor.countPromptTokens(messages);

            assertTrue(totalTokens <= MemoryExtractor.EXTRACTION_TOKEN_BUDGET,
                "Even with long conversation, prompt tokens (" + totalTokens +
                    ") should not exceed budget (" + MemoryExtractor.EXTRACTION_TOKEN_BUDGET + ")");
        }

        @Test
        @DisplayName("system prompt should not exceed system budget")
        void systemPromptWithinBudget() {
            CompletedConversation conversation = sampleConversation();
            List<AiMessage> messages = extractor.buildExtractionPrompt(conversation);

            // System prompt is the first message
            int systemTokens = tokenBudgetManager.countTokens(messages.get(0).content());

            assertTrue(systemTokens <= MemoryExtractor.SYSTEM_PROMPT_TOKEN_BUDGET,
                "System prompt tokens (" + systemTokens + ") should not exceed system budget (" +
                    MemoryExtractor.SYSTEM_PROMPT_TOKEN_BUDGET + ")");
        }
    }

    @Nested
    @DisplayName("Validation edge cases")
    class ValidationEdgeCases {

        @Test
        @DisplayName("should reject memory with null category")
        void rejectsNullCategory() {
            Map<String, Object> raw = new java.util.HashMap<>();
            raw.put("category", null);
            raw.put("content", "Test");
            raw.put("importance_score", 5);
            raw.put("confidence_score", 0.5);
            raw.put("subject_type", "child");

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNull(memory);
        }

        @Test
        @DisplayName("should default invalid subject_type to family")
        void defaultsInvalidSubjectTypeToFamily() {
            Map<String, Object> raw = Map.of(
                "category", "CONTEXT",
                "content", "Test memory",
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "unknown_type"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals("family", memory.subjectType());
        }

        @Test
        @DisplayName("should handle case-insensitive category matching")
        void handlesCaseInsensitiveCategory() {
            Map<String, Object> raw = Map.of(
                "category", "identity",
                "content", "Test memory",
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals("IDENTITY", memory.category());
        }

        @Test
        @DisplayName("should handle case-insensitive subject_type matching")
        void handlesCaseInsensitiveSubjectType() {
            Map<String, Object> raw = Map.of(
                "category", "GOAL",
                "content", "Test memory",
                "importance_score", 5,
                "confidence_score", 0.7,
                "subject_type", "Child"
            );

            ExtractedMemory memory = extractor.validateAndBuildMemory(raw);

            assertNotNull(memory);
            assertEquals("child", memory.subjectType());
        }
    }

    @Nested
    @DisplayName("Transcript formatting")
    class TranscriptFormatting {

        @Test
        @DisplayName("should format messages with role labels")
        void formatsWithRoleLabels() {
            List<AiMessage> messages = List.of(
                AiMessage.user("Hello"),
                AiMessage.assistant("Hi there")
            );

            String transcript = extractor.formatTranscript(messages);

            assertTrue(transcript.contains("Father: Hello"));
            assertTrue(transcript.contains("Coach: Hi there"));
        }

        @Test
        @DisplayName("should handle empty message list")
        void handlesEmptyMessages() {
            String transcript = extractor.formatTranscript(List.of());
            assertEquals("", transcript);
        }

        @Test
        @DisplayName("should handle null message list")
        void handlesNullMessages() {
            String transcript = extractor.formatTranscript(null);
            assertEquals("", transcript);
        }
    }
}
