package com.dadcoach.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A decorator around AiProvider that adds:
 * - Retry with exponential backoff (1s, 2s, 4s, 8s, 16s) per Requirement 10.13
 * - Fallback message when all retries fail per Requirement 10.14
 * - Daily rate limiting per Requirement 10.12
 * - Context token budget enforcement per Property 30
 */
public class RetryableAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(RetryableAiProvider.class);

    /**
     * Exponential backoff delays in milliseconds: 1s, 2s, 4s, 8s, 16s
     */
    static final long[] BACKOFF_DELAYS_MS = {1000, 2000, 4000, 8000, 16000};

    /**
     * Maximum number of retry attempts.
     */
    static final int MAX_ATTEMPTS = 5;

    /**
     * Pre-written fallback message sent when all retries fail (Requirement 10.14).
     */
    public static final String FALLBACK_MESSAGE = "Estoy teniendo un momento técnico — te respondo pronto 💪";

    private final AiProvider delegate;
    private final AiRateLimiter rateLimiter;
    private final Sleeper sleeper;

    /**
     * Abstraction for Thread.sleep to allow testing without real delays.
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public RetryableAiProvider(AiProvider delegate, AiRateLimiter rateLimiter) {
        this(delegate, rateLimiter, Thread::sleep);
    }

    public RetryableAiProvider(AiProvider delegate, AiRateLimiter rateLimiter, Sleeper sleeper) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.sleeper = sleeper;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        // Enforce token budget before sending
        AiRequest budgetedRequest = enforceTokenBudget(request);

        // Retry with exponential backoff
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                AiResponse response = delegate.complete(budgetedRequest);
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("AI provider call failed (attempt {}/{}): {}",
                    attempt + 1, MAX_ATTEMPTS, e.getMessage());

                if (attempt < MAX_ATTEMPTS - 1) {
                    try {
                        sleeper.sleep(BACKOFF_DELAYS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiProviderUnavailableException(
                            "AI provider retry interrupted", attempt + 1, ie);
                    }
                }
            }
        }

        // All retries failed — return fallback response
        log.error("All {} AI provider attempts failed. Returning fallback message.", MAX_ATTEMPTS, lastException);
        return new AiResponse(
            FALLBACK_MESSAGE,
            request.model(),
            0,
            0,
            0,
            "fallback"
        );
    }

    @Override
    public int estimateTokens(String text) {
        return TokenEstimator.estimateTokens(text);
    }

    /**
     * Enforce the context token budget (max 2000 tokens) by trimming conversation history.
     */
    private AiRequest enforceTokenBudget(AiRequest request) {
        int contextTokens = TokenEstimator.estimateContextTokens(
            request.systemPrompt(), request.conversationHistory());

        if (contextTokens <= TokenEstimator.MAX_CONTEXT_TOKENS) {
            return request;
        }

        // Trim conversation history to fit within budget
        List<AiMessage> trimmedHistory = TokenEstimator.enforceTokenBudget(
            request.systemPrompt(), request.conversationHistory());

        return new AiRequest(
            request.model(),
            request.systemPrompt(),
            trimmedHistory,
            request.maxResponseTokens(),
            request.promptVersion()
        );
    }
}
