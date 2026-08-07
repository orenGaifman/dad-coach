package com.dadcoach.ai.routing;

import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.domain.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackResponseProviderTest {

    private FallbackResponseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FallbackResponseProvider();
    }

    @Test
    @DisplayName("Every conversation type has a pre-written fallback")
    void everyTypeHasFallback() {
        for (ConversationType type : ConversationType.values()) {
            assertThat(provider.hasFallbackFor(type)).isTrue();
        }
    }

    @Test
    @DisplayName("Fallback responses are in English by default")
    void fallbacksAreInEnglish() {
        for (ConversationType type : ConversationType.values()) {
            String text = provider.getFallbackText(type);
            assertThat(text).isNotBlank();
            // English indicators: common words
            assertThat(text).containsAnyOf("I'm", "you", "help", "your", "here", "with", "today", "can", "moment");
        }
    }

    @Test
    @DisplayName("Fallback responses in Hebrew contain Hebrew characters")
    void fallbacksInHebrew() {
        for (ConversationType type : ConversationType.values()) {
            String text = provider.getFallbackText(type, "he");
            assertThat(text).isNotBlank();
            // Hebrew indicator: contains Hebrew Unicode characters
            assertThat(text).matches(".*[\\u0590-\\u05FF].*");
        }
    }

    @Test
    @DisplayName("Fallback responses include a re-engagement hook")
    void fallbacksIncludeReEngagementHook() {
        // Most fallback responses end with a question or light prompt
        for (ConversationType type : ConversationType.values()) {
            if (type == ConversationType.MISSION_GENERATION || type == ConversationType.CELEBRATION) {
                continue; // JSON output and celebrations don't need a question hook
            }
            String text = provider.getFallbackText(type);
            assertThat(text).contains("?");
        }
    }

    @Test
    @DisplayName("getFallbackResponse returns valid AiProviderResponse")
    void returnsValidResponse() {
        AiProviderResponse response = provider.getFallbackResponse(ConversationType.DAILY_COACHING);
        assertThat(response.content()).isNotBlank();
        assertThat(response.provider()).isEqualTo("fallback");
        assertThat(response.model()).isEqualTo("pre-written");
        assertThat(response.finishReason()).isEqualTo("fallback");
        assertThat(response.inputTokens()).isZero();
        assertThat(response.outputTokens()).isZero();
    }

    @Test
    @DisplayName("MISSION_GENERATION fallback returns valid JSON")
    void missionFallbackIsValidJson() {
        String text = provider.getFallbackText(ConversationType.MISSION_GENERATION);
        assertThat(text).contains("\"title\"");
        assertThat(text).contains("\"description\"");
        assertThat(text).contains("\"category\"");
        assertThat(text).contains("\"difficulty\"");
        assertThat(text).contains("\"estimated_minutes\"");
    }

    @Test
    @DisplayName("Fallback responses use warm, non-clinical tone")
    void warmTone() {
        for (ConversationType type : ConversationType.values()) {
            String text = provider.getFallbackText(type);
            // Ensure no clinical/corporate language
            assertThat(text.toLowerCase()).doesNotContain("optimize", "kpi", "synergy", "metrics");
        }
    }
}
