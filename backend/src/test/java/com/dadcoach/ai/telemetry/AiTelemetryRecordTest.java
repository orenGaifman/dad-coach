package com.dadcoach.ai.telemetry;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AiTelemetryRecord builder and validation.
 */
class AiTelemetryRecordTest {

    @Test
    void buildsRecordWithAllRequiredFields() {
        UUID requestId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();

        AiTelemetryRecord record = AiTelemetryRecord.builder()
                .requestId(requestId)
                .fatherId(fatherId)
                .interactionType("coaching")
                .modelProvider("openai")
                .modelName("gpt-4o")
                .inputTokens(500)
                .outputTokens(200)
                .totalLatencyMs(1500)
                .validationPassed(true)
                .build();

        assertEquals(requestId, record.getRequestId());
        assertEquals(fatherId, record.getFatherId());
        assertEquals("coaching", record.getInteractionType());
        assertEquals("openai", record.getModelProvider());
        assertEquals("gpt-4o", record.getModelName());
        assertEquals(500, record.getInputTokens());
        assertEquals(200, record.getOutputTokens());
        assertEquals(1500, record.getTotalLatencyMs());
        assertTrue(record.isValidationPassed());
        assertFalse(record.isFallbackUsed());
        assertEquals(0, record.getRetryCount());
        assertNotNull(record.getCreatedAt());
    }

    @Test
    void buildsRecordWithOptionalFields() {
        UUID requestId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AiTelemetryRecord record = AiTelemetryRecord.builder()
                .requestId(requestId)
                .fatherId(fatherId)
                .conversationId(conversationId)
                .conversationType("DAILY_COACHING")
                .interactionType("coaching")
                .promptVersion("v1.2")
                .modelProvider("openai")
                .modelName("gpt-4o-mini")
                .temperature(0.8f)
                .inputTokens(300)
                .outputTokens(150)
                .estimatedCostUsd(0.002f)
                .totalLatencyMs(800)
                .llmLatencyMs(600)
                .validationPassed(true)
                .fallbackUsed(true)
                .retryCount(1)
                .qualityScore(85.5f)
                .safetyClassification("SAFE")
                .abTestGroup("A")
                .build();

        assertEquals(conversationId, record.getConversationId());
        assertEquals("DAILY_COACHING", record.getConversationType());
        assertEquals("v1.2", record.getPromptVersion());
        assertEquals(0.8f, record.getTemperature());
        assertEquals(0.002f, record.getEstimatedCostUsd());
        assertEquals(600, record.getLlmLatencyMs());
        assertTrue(record.isFallbackUsed());
        assertEquals(1, record.getRetryCount());
        assertEquals(85.5f, record.getQualityScore());
        assertEquals("SAFE", record.getSafetyClassification());
        assertEquals("A", record.getAbTestGroup());
    }

    @Test
    void throwsWhenRequestIdMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .fatherId(UUID.randomUUID())
                        .interactionType("coaching")
                        .modelProvider("openai")
                        .modelName("gpt-4o")
                        .build());
    }

    @Test
    void throwsWhenFatherIdMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .requestId(UUID.randomUUID())
                        .interactionType("coaching")
                        .modelProvider("openai")
                        .modelName("gpt-4o")
                        .build());
    }

    @Test
    void throwsWhenInteractionTypeMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .requestId(UUID.randomUUID())
                        .fatherId(UUID.randomUUID())
                        .modelProvider("openai")
                        .modelName("gpt-4o")
                        .build());
    }

    @Test
    void throwsWhenModelProviderMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .requestId(UUID.randomUUID())
                        .fatherId(UUID.randomUUID())
                        .interactionType("coaching")
                        .modelName("gpt-4o")
                        .build());
    }

    @Test
    void throwsWhenModelNameMissing() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .requestId(UUID.randomUUID())
                        .fatherId(UUID.randomUUID())
                        .interactionType("coaching")
                        .modelProvider("openai")
                        .build());
    }

    @Test
    void throwsWhenInteractionTypeBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                AiTelemetryRecord.builder()
                        .requestId(UUID.randomUUID())
                        .fatherId(UUID.randomUUID())
                        .interactionType("   ")
                        .modelProvider("openai")
                        .modelName("gpt-4o")
                        .build());
    }
}
