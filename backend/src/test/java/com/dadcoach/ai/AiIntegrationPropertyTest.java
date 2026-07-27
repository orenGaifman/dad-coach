package com.dadcoach.ai;

import com.dadcoach.conversation.ConversationType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for AI integration layer.
 * Tests model selection, context token budget, and rate limiting.
 */
@Tag("Feature: product-domain-business-logic")
class AiIntegrationPropertyTest {

    // ===== Property 29: AI Model Selection by Conversation Type =====

    /**
     * **Validates: Requirements 17.6**
     *
     * Property 29: For any conversation type, the selected AI model should be:
     * GPT-4o for {ONBOARDING, DIFFICULT_SITUATION, REFLECTION},
     * GPT-4o-mini for {DAILY_COACHING, FOLLOW_UP, CELEBRATION, INACTIVITY_CHECK}.
     */
    @Property(tries = 100)
    @Tag("Property 29: AI model selection by conversation type")
    void modelSelectionForComplexTypes(@ForAll("complexConversationTypes") ConversationType type) {
        AiModel selected = AiModel.forConversationType(type);
        assertThat(selected).isEqualTo(AiModel.GPT_4O);
    }

    @Property(tries = 100)
    @Tag("Property 29: AI model selection by conversation type")
    void modelSelectionForRoutineTypes(@ForAll("routineConversationTypes") ConversationType type) {
        AiModel selected = AiModel.forConversationType(type);
        assertThat(selected).isEqualTo(AiModel.GPT_4O_MINI);
    }

    @Property(tries = 100)
    @Tag("Property 29: AI model selection by conversation type")
    void everyConversationTypeMapsToAModel(@ForAll("allConversationTypes") ConversationType type) {
        AiModel selected = AiModel.forConversationType(type);
        assertThat(selected).isNotNull();
        assertThat(selected).isIn(AiModel.GPT_4O, AiModel.GPT_4O_MINI);
    }

    // ===== Property 30: Context Token Budget =====

    /**
     * **Validates: Requirements 17.4**
     *
     * Property 30: For any coaching session, the total token count of
     * (system prompt + memories + conversation history) should not exceed 2000 tokens.
     */
    @Property(tries = 100)
    @Tag("Property 30: Context token budget")
    void enforcedContextNeverExceedsBudget(
        @ForAll("systemPrompts") String systemPrompt,
        @ForAll("conversationHistories") List<AiMessage> history
    ) {
        List<AiMessage> trimmed = TokenEstimator.enforceTokenBudget(systemPrompt, history);
        int totalTokens = TokenEstimator.estimateContextTokens(systemPrompt, trimmed);
        assertThat(totalTokens).isLessThanOrEqualTo(TokenEstimator.MAX_CONTEXT_TOKENS);
    }

    @Property(tries = 100)
    @Tag("Property 30: Context token budget")
    void enforcedContextPreservesNewestMessages(
        @ForAll("systemPrompts") String systemPrompt,
        @ForAll("conversationHistories") List<AiMessage> history
    ) {
        List<AiMessage> trimmed = TokenEstimator.enforceTokenBudget(systemPrompt, history);

        // Trimmed messages should be a suffix (tail) of the original
        if (!trimmed.isEmpty() && !history.isEmpty()) {
            AiMessage lastTrimmed = trimmed.get(trimmed.size() - 1);
            AiMessage lastOriginal = history.get(history.size() - 1);
            assertThat(lastTrimmed).isEqualTo(lastOriginal);
        }
    }

    @Property(tries = 100)
    @Tag("Property 30: Context token budget")
    void tokenEstimationIsNonNegative(@ForAll @StringLength(max = 1000) String text) {
        int tokens = TokenEstimator.estimateTokens(text);
        assertThat(tokens).isGreaterThanOrEqualTo(0);
    }

    @Property(tries = 100)
    @Tag("Property 30: Context token budget")
    void emptyTextHasZeroTokens(@ForAll("emptyOrNullStrings") String text) {
        int tokens = TokenEstimator.estimateTokens(text);
        assertThat(tokens).isEqualTo(0);
    }

    // ===== Property 34: AI Rate Limiting =====

    /**
     * **Validates: Requirements 10.12**
     *
     * Property 34: For any Father on any calendar day, the total AI API calls
     * should not exceed 20.
     */
    @Property(tries = 100)
    @Tag("Property 34: AI rate limiting")
    void rateLimiterEnforcesMaxCalls(
        @ForAll @IntRange(min = 1, max = 100) int fatherId,
        @ForAll @IntRange(min = 1, max = 30) int callAttempts
    ) {
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        AiRateLimiter rateLimiter = new AiRateLimiter(fixedClock);
        Long id = (long) fatherId;

        int successfulCalls = 0;
        for (int i = 0; i < callAttempts; i++) {
            if (rateLimiter.canMakeCall(id)) {
                rateLimiter.recordCall(id);
                successfulCalls++;
            }
        }

        assertThat(successfulCalls).isLessThanOrEqualTo(AiRateLimiter.MAX_DAILY_CALLS);
        assertThat(rateLimiter.getDailyCount(id)).isLessThanOrEqualTo(AiRateLimiter.MAX_DAILY_CALLS);
    }

    @Property(tries = 100)
    @Tag("Property 34: AI rate limiting")
    void rateLimiterResetsOnNewDay(
        @ForAll @IntRange(min = 1, max = 100) int fatherId,
        @ForAll @IntRange(min = 1, max = 20) int callsDay1
    ) {
        // Day 1
        LocalDate day1 = LocalDate.of(2024, 6, 15);
        Clock day1Clock = Clock.fixed(
            day1.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        AiRateLimiter rateLimiter = new AiRateLimiter(day1Clock);
        Long id = (long) fatherId;

        for (int i = 0; i < callsDay1; i++) {
            rateLimiter.recordCall(id);
        }
        assertThat(rateLimiter.getDailyCount(id)).isEqualTo(callsDay1);

        // Day 2 — count should be 0 for a new day
        LocalDate day2 = day1.plusDays(1);
        Clock day2Clock = Clock.fixed(
            day2.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        AiRateLimiter rateLimiterDay2 = new AiRateLimiter(day2Clock);

        assertThat(rateLimiterDay2.getDailyCount(id)).isEqualTo(0);
        assertThat(rateLimiterDay2.canMakeCall(id)).isTrue();
    }

    @Property(tries = 100)
    @Tag("Property 34: AI rate limiting")
    void canMakeCallReturnsFalseAtLimit(@ForAll @IntRange(min = 1, max = 100) int fatherId) {
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        AiRateLimiter rateLimiter = new AiRateLimiter(fixedClock);
        Long id = (long) fatherId;

        // Fill up to the limit
        for (int i = 0; i < AiRateLimiter.MAX_DAILY_CALLS; i++) {
            assertThat(rateLimiter.canMakeCall(id)).isTrue();
            rateLimiter.recordCall(id);
        }

        // At limit
        assertThat(rateLimiter.canMakeCall(id)).isFalse();
        assertThat(rateLimiter.getRemainingCalls(id)).isEqualTo(0);
    }

    // ===== Arbitraries =====

    @Provide
    Arbitrary<ConversationType> complexConversationTypes() {
        return Arbitraries.of(
            ConversationType.ONBOARDING,
            ConversationType.DIFFICULT_SITUATION,
            ConversationType.REFLECTION
        );
    }

    @Provide
    Arbitrary<ConversationType> routineConversationTypes() {
        return Arbitraries.of(
            ConversationType.DAILY_COACHING,
            ConversationType.FOLLOW_UP,
            ConversationType.CELEBRATION,
            ConversationType.INACTIVITY_CHECK
        );
    }

    @Provide
    Arbitrary<ConversationType> allConversationTypes() {
        return Arbitraries.of(ConversationType.values());
    }

    @Provide
    Arbitrary<String> systemPrompts() {
        // Generate system prompts of varying lengths (100-5000 chars)
        return Arbitraries.strings()
            .ofMinLength(100)
            .ofMaxLength(5000)
            .alpha();
    }

    @Provide
    Arbitrary<List<AiMessage>> conversationHistories() {
        Arbitrary<AiMessage> messageArb = Arbitraries.of("user", "assistant")
            .flatMap(role -> Arbitraries.strings()
                .ofMinLength(10)
                .ofMaxLength(500)
                .alpha()
                .map(content -> new AiMessage(role, content)));

        return messageArb.list().ofMinSize(0).ofMaxSize(20);
    }

    @Provide
    Arbitrary<String> emptyOrNullStrings() {
        return Arbitraries.of("", null);
    }
}
