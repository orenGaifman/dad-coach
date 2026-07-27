package com.dadcoach.ai.routing;

import com.dadcoach.ai.provider.AiProvider;
import com.dadcoach.ai.provider.AiProviderException;
import com.dadcoach.ai.provider.AiProviderRequest;
import com.dadcoach.ai.provider.AiProviderResponse;
import com.dadcoach.conversation.ConversationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements the ordered fallback chain for AI provider calls.
 *
 * <p>Fallback order (strict, no step skipped):
 * <ol>
 *   <li>Primary model on primary provider (from routing table)</li>
 *   <li>Same provider, lower-tier model</li>
 *   <li>Secondary provider</li>
 *   <li>Pre-written fallback response</li>
 * </ol>
 *
 * <p>The total chain must complete within 30 seconds. Each step is attempted
 * in order and never skipped — a step is only bypassed if it throws an exception.
 */
public class FallbackChain {

    private static final Logger log = LoggerFactory.getLogger(FallbackChain.class);
    private static final Duration MAX_CHAIN_DURATION = Duration.ofSeconds(30);

    /**
     * Lower-tier model mapping: primary model → fallback model on same provider.
     */
    private static final Map<String, String> LOWER_TIER_MODELS = Map.of(
        "gpt-4o", "gpt-4o-mini",
        "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022"
    );

    private final List<AiProvider> providers;
    private final FallbackResponseProvider fallbackResponseProvider;

    /**
     * @param providers              ordered list of providers (first = primary, second = secondary, etc.)
     * @param fallbackResponseProvider provider of pre-written fallback responses
     */
    public FallbackChain(List<AiProvider> providers, FallbackResponseProvider fallbackResponseProvider) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one AI provider is required");
        }
        this.providers = List.copyOf(providers);
        this.fallbackResponseProvider = fallbackResponseProvider;
    }

    /**
     * Execute the fallback chain for a given request and conversation type.
     *
     * <p>Attempts each step in strict order:
     * <ol>
     *   <li>Primary model on primary provider</li>
     *   <li>Lower-tier model on same provider (if available)</li>
     *   <li>Secondary provider (if available)</li>
     *   <li>Pre-written fallback</li>
     * </ol>
     *
     * @param request          the AI provider request
     * @param config           the model config from the routing table
     * @param conversationType the conversation type (for fallback response selection)
     * @return the response from the first successful step
     */
    public FallbackResult execute(AiProviderRequest request, ModelConfig config, ConversationType conversationType) {
        Instant chainStart = Instant.now();
        List<FallbackAttempt> attempts = new ArrayList<>();

        // Step 1: Primary model on primary provider
        AiProvider primaryProvider = findProviderForModel(config.model());
        if (primaryProvider != null) {
            FallbackAttempt attempt = tryProvider(primaryProvider, request, chainStart);
            attempts.add(attempt);
            if (attempt.success()) {
                return new FallbackResult(attempt.response(), 0, attempts);
            }
        }

        // Step 2: Same provider, lower-tier model
        String lowerTierModel = LOWER_TIER_MODELS.get(config.model());
        if (lowerTierModel != null && !isTimeoutExceeded(chainStart)) {
            AiProvider lowerTierProvider = findProviderForModel(lowerTierModel);
            if (lowerTierProvider != null) {
                AiProviderRequest fallbackRequest = withModel(request, lowerTierModel);
                FallbackAttempt attempt = tryProvider(lowerTierProvider, fallbackRequest, chainStart);
                attempts.add(attempt);
                if (attempt.success()) {
                    return new FallbackResult(attempt.response(), 1, attempts);
                }
            }
        }

        // Step 3: Secondary provider
        if (!isTimeoutExceeded(chainStart)) {
            AiProvider secondaryProvider = findSecondaryProvider(primaryProvider);
            if (secondaryProvider != null) {
                // Find a model the secondary provider supports
                String secondaryModel = findSupportedModel(secondaryProvider);
                if (secondaryModel != null) {
                    AiProviderRequest secondaryRequest = withModel(request, secondaryModel);
                    FallbackAttempt attempt = tryProvider(secondaryProvider, secondaryRequest, chainStart);
                    attempts.add(attempt);
                    if (attempt.success()) {
                        return new FallbackResult(attempt.response(), 2, attempts);
                    }
                }
            }
        }

        // Step 4: Pre-written fallback (always succeeds)
        log.warn("All providers failed for conversation type {}. Using pre-written fallback.", conversationType);
        AiProviderResponse fallbackResponse = fallbackResponseProvider.getFallbackResponse(conversationType);
        attempts.add(new FallbackAttempt("fallback", "pre-written", true, fallbackResponse, null));
        return new FallbackResult(fallbackResponse, 3, attempts);
    }

    private FallbackAttempt tryProvider(AiProvider provider, AiProviderRequest request, Instant chainStart) {
        if (isTimeoutExceeded(chainStart)) {
            return new FallbackAttempt(provider.getProviderName(), request.model(), false, null,
                new AiProviderException(provider.getProviderName(), AiProviderException.ErrorType.TIMEOUT,
                    "Fallback chain timeout exceeded"));
        }

        try {
            AiProviderResponse response = provider.sendPrompt(request);
            log.debug("Provider {} with model {} succeeded", provider.getProviderName(), request.model());
            return new FallbackAttempt(provider.getProviderName(), request.model(), true, response, null);
        } catch (AiProviderException e) {
            log.warn("Provider {} with model {} failed: {} ({})",
                provider.getProviderName(), request.model(), e.getErrorType(), e.getMessage());
            return new FallbackAttempt(provider.getProviderName(), request.model(), false, null, e);
        }
    }

    private boolean isTimeoutExceeded(Instant chainStart) {
        return Duration.between(chainStart, Instant.now()).compareTo(MAX_CHAIN_DURATION) > 0;
    }

    private AiProvider findProviderForModel(String model) {
        return providers.stream()
            .filter(p -> p.supportsModel(model))
            .findFirst()
            .orElse(null);
    }

    private AiProvider findSecondaryProvider(AiProvider primaryProvider) {
        if (primaryProvider == null && providers.size() > 1) {
            return providers.get(1);
        }
        return providers.stream()
            .filter(p -> !p.getProviderName().equals(
                primaryProvider != null ? primaryProvider.getProviderName() : ""))
            .findFirst()
            .orElse(null);
    }

    private String findSupportedModel(AiProvider provider) {
        // Try common models in order of capability
        List<String> candidates = List.of(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "gpt-4o",
            "gpt-4o-mini"
        );
        return candidates.stream()
            .filter(provider::supportsModel)
            .findFirst()
            .orElse(null);
    }

    private AiProviderRequest withModel(AiProviderRequest original, String newModel) {
        return new AiProviderRequest(
            newModel,
            original.messages(),
            original.temperature(),
            original.topP(),
            original.maxTokens(),
            original.jsonMode(),
            original.metadata()
        );
    }

    /**
     * Result of a single fallback attempt.
     */
    public record FallbackAttempt(
        String providerName,
        String model,
        boolean success,
        AiProviderResponse response,
        AiProviderException exception
    ) {}

    /**
     * Result of the complete fallback chain execution.
     *
     * @param response       the successful response
     * @param fallbackLevel  which level succeeded (0=primary, 1=lower-tier, 2=secondary, 3=pre-written)
     * @param attempts       all attempts made during the chain
     */
    public record FallbackResult(
        AiProviderResponse response,
        int fallbackLevel,
        List<FallbackAttempt> attempts
    ) {
        public boolean usedFallback() {
            return fallbackLevel > 0;
        }
    }
}
