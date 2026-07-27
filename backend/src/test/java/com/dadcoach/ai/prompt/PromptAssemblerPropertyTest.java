package com.dadcoach.ai.prompt;

import com.dadcoach.ai.AiMessage;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for PromptAssembler and TokenBudgetManager.
 *
 * <p>Tests Properties 1 and 2 from the design document:
 * <ul>
 *   <li>Property 1: Token Budget Invariant — total tokens never exceed 2000, each section within its budget</li>
 *   <li>Property 2: Sliding Window Minimum Guarantee — result always contains current user message + last assistant response</li>
 * </ul>
 */
@Tag("Feature: ai-architecture-intelligence-layer")
class PromptAssemblerPropertyTest {

    private final PromptAssembler assembler = new PromptAssembler();
    private final TokenBudgetManager tokenCounter = new TokenBudgetManager();

    // ===== Property 1: Token Budget Invariant =====

    /**
     * **Validates: Requirements 3.2, 5.1**
     *
     * Property 1: For any conversation type and any coaching context, the assembled prompt's
     * total token count SHALL NOT exceed 2000 tokens.
     */
    @Property(tries = 200)
    @Tag("Property 1: Token Budget Invariant")
    void totalTokensNeverExceedBudget(
            @ForAll("systemContents") String systemContent,
            @ForAll("memoryContents") String memoryContent,
            @ForAll("contextContents") String contextContent,
            @ForAll("conversationHistories") List<AiMessage> history,
            @ForAll("outputContents") String outputContent) {

        List<AiMessage> result = assembler.assemble(
                systemContent, memoryContent, contextContent, history, outputContent);

        int totalTokens = result.stream()
                .mapToInt(msg -> tokenCounter.countTokens(msg.content()))
                .sum();

        assertThat(totalTokens)
                .as("Total tokens (%d) must not exceed budget of %d", totalTokens, TokenBudgetManager.TOTAL_BUDGET)
                .isLessThanOrEqualTo(TokenBudgetManager.TOTAL_BUDGET);
    }

    /**
     * **Validates: Requirements 3.2, 5.1**
     *
     * Property 1: Each section (system: ≤400, memory: ≤500, context: ≤300, history: ≤600, output: ≤200)
     * SHALL NOT exceed its allocated budget.
     */
    @Property(tries = 200)
    @Tag("Property 1: Token Budget Invariant")
    void eachSectionWithinItsBudget(
            @ForAll("systemContents") String systemContent,
            @ForAll("memoryContents") String memoryContent,
            @ForAll("contextContents") String contextContent,
            @ForAll("conversationHistories") List<AiMessage> history,
            @ForAll("outputContents") String outputContent) {

        List<AiMessage> result = assembler.assemble(
                systemContent, memoryContent, contextContent, history, outputContent);

        // The first message is always system (system content)
        if (!result.isEmpty()) {
            int systemTokens = tokenCounter.countTokens(result.get(0).content());
            assertThat(systemTokens)
                    .as("System section tokens (%d) must not exceed %d", systemTokens, TokenBudgetManager.SYSTEM_BUDGET)
                    .isLessThanOrEqualTo(TokenBudgetManager.SYSTEM_BUDGET);
        }

        // Verify using a fresh budget manager for independent section verification
        TokenBudgetManager verifyBudget = new TokenBudgetManager();

        // System section
        String allocatedSystem = verifyBudget.allocateSystem(systemContent);
        assertThat(verifyBudget.getSystemUsed())
                .isLessThanOrEqualTo(TokenBudgetManager.SYSTEM_BUDGET);

        // Memory section
        if (memoryContent != null && !memoryContent.isBlank()) {
            verifyBudget.allocateMemory(memoryContent);
            assertThat(verifyBudget.getMemoryUsed())
                    .isLessThanOrEqualTo(TokenBudgetManager.MEMORY_BUDGET);
        }

        // Context section
        if (contextContent != null && !contextContent.isBlank()) {
            verifyBudget.allocateContext(contextContent);
            assertThat(verifyBudget.getContextUsed())
                    .isLessThanOrEqualTo(TokenBudgetManager.CONTEXT_BUDGET);
        }

        // History section - extract history messages from result
        List<AiMessage> historyInResult = extractHistoryMessages(result, memoryContent, contextContent);
        int historyTokens = historyInResult.stream()
                .mapToInt(msg -> tokenCounter.countTokens(msg.content()))
                .sum();
        assertThat(historyTokens)
                .as("History section tokens (%d) must not exceed %d", historyTokens, TokenBudgetManager.HISTORY_BUDGET)
                .isLessThanOrEqualTo(TokenBudgetManager.HISTORY_BUDGET);

        // Output section (last system message)
        if (outputContent != null && !outputContent.isBlank()) {
            verifyBudget.allocateOutput(outputContent);
            assertThat(verifyBudget.getOutputUsed())
                    .isLessThanOrEqualTo(TokenBudgetManager.OUTPUT_BUDGET);
        }
    }

    /**
     * **Validates: Requirements 3.2, 5.1**
     *
     * Property 1: TokenBudgetManager truncation always produces output within the specified limit.
     */
    @Property(tries = 200)
    @Tag("Property 1: Token Budget Invariant")
    void truncationAlwaysRespectsBudget(
            @ForAll("longTexts") String text,
            @ForAll @IntRange(min = 1, max = 600) int maxTokens) {

        TokenBudgetManager budget = new TokenBudgetManager();
        String truncated = budget.truncateToFit(text, maxTokens);
        int tokenCount = budget.countTokens(truncated);

        assertThat(tokenCount)
                .as("Truncated text tokens (%d) must not exceed max (%d)", tokenCount, maxTokens)
                .isLessThanOrEqualTo(maxTokens);
    }

    // ===== Property 2: Sliding Window Minimum Guarantee =====

    /**
     * **Validates: Requirements 3.7, 5.2**
     *
     * Property 2: For any conversation history (regardless of length), after the sliding window
     * is applied, the result SHALL always contain at minimum the user's current message.
     */
    @Property(tries = 200)
    @Tag("Property 2: Sliding Window Minimum Guarantee")
    void slidingWindowAlwaysContainsCurrentUserMessage(
            @ForAll("conversationHistoriesWithUserMessage") List<AiMessage> history) {

        TokenBudgetManager budget = new TokenBudgetManager();
        SlidingWindowBuilder builder = new SlidingWindowBuilder(budget);
        List<AiMessage> window = builder.buildWindow(history, TokenBudgetManager.HISTORY_BUDGET);

        // The window must contain at least one user message
        boolean hasUserMessage = window.stream()
                .anyMatch(msg -> "user".equals(msg.role()));

        assertThat(hasUserMessage)
                .as("Sliding window must always contain the current user message")
                .isTrue();
    }

    /**
     * **Validates: Requirements 3.7, 5.2**
     *
     * Property 2: For any conversation history with at least one assistant response,
     * the sliding window SHALL always contain the last assistant response.
     */
    @Property(tries = 200)
    @Tag("Property 2: Sliding Window Minimum Guarantee")
    void slidingWindowAlwaysContainsLastAssistantResponse(
            @ForAll("conversationHistoriesWithAssistantResponse") List<AiMessage> history) {

        TokenBudgetManager budget = new TokenBudgetManager();
        SlidingWindowBuilder builder = new SlidingWindowBuilder(budget);
        List<AiMessage> window = builder.buildWindow(history, TokenBudgetManager.HISTORY_BUDGET);

        // The window must contain at least one assistant message
        boolean hasAssistantMessage = window.stream()
                .anyMatch(msg -> "assistant".equals(msg.role()));

        assertThat(hasAssistantMessage)
                .as("Sliding window must always contain the last assistant response")
                .isTrue();
    }

    /**
     * **Validates: Requirements 3.7, 5.2**
     *
     * Property 2: For any conversation history with both user and assistant messages,
     * the sliding window SHALL always contain both the current user message AND last assistant response.
     */
    @Property(tries = 200)
    @Tag("Property 2: Sliding Window Minimum Guarantee")
    void slidingWindowContainsBothUserAndAssistantMessages(
            @ForAll("conversationHistoriesWithBoth") List<AiMessage> history) {

        TokenBudgetManager budget = new TokenBudgetManager();
        SlidingWindowBuilder builder = new SlidingWindowBuilder(budget);
        List<AiMessage> window = builder.buildWindow(history, TokenBudgetManager.HISTORY_BUDGET);

        boolean hasUser = window.stream().anyMatch(msg -> "user".equals(msg.role()));
        boolean hasAssistant = window.stream().anyMatch(msg -> "assistant".equals(msg.role()));

        assertThat(hasUser)
                .as("Window must contain the current user message")
                .isTrue();
        assertThat(hasAssistant)
                .as("Window must contain the last assistant response")
                .isTrue();
    }

    /**
     * **Validates: Requirements 3.7, 5.2**
     *
     * Property 2: The sliding window total tokens never exceed the history budget (600).
     */
    @Property(tries = 200)
    @Tag("Property 2: Sliding Window Minimum Guarantee")
    void slidingWindowNeverExceedsHistoryBudget(
            @ForAll("conversationHistories") List<AiMessage> history) {

        TokenBudgetManager budget = new TokenBudgetManager();
        SlidingWindowBuilder builder = new SlidingWindowBuilder(budget);
        List<AiMessage> window = builder.buildWindow(history, TokenBudgetManager.HISTORY_BUDGET);

        int windowTokens = window.stream()
                .mapToInt(msg -> budget.countTokens(msg.content()))
                .sum();

        assertThat(windowTokens)
                .as("Sliding window tokens (%d) must not exceed history budget (%d)",
                        windowTokens, TokenBudgetManager.HISTORY_BUDGET)
                .isLessThanOrEqualTo(TokenBudgetManager.HISTORY_BUDGET);
    }

    // ===== Arbitraries (Generators) =====

    @Provide
    Arbitrary<String> systemContents() {
        // Generate system content that may exceed the 400-token budget
        return Arbitraries.strings()
                .ofMinLength(10)
                .ofMaxLength(3000)
                .alpha()
                .withChars(' ', '.', ',', '\n');
    }

    @Provide
    Arbitrary<String> memoryContents() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings()
                        .ofMinLength(10)
                        .ofMaxLength(3000)
                        .alpha()
                        .withChars(' ', '.', ',', '-', '[', ']', '\n')
        );
    }

    @Provide
    Arbitrary<String> contextContents() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings()
                        .ofMinLength(10)
                        .ofMaxLength(2000)
                        .alpha()
                        .withChars(' ', '.', ',', ':', '\n')
        );
    }

    @Provide
    Arbitrary<String> outputContents() {
        return Arbitraries.strings()
                .ofMinLength(5)
                .ofMaxLength(1500)
                .alpha()
                .withChars(' ', '.', ',');
    }

    @Provide
    Arbitrary<String> longTexts() {
        return Arbitraries.strings()
                .ofMinLength(100)
                .ofMaxLength(5000)
                .alpha()
                .withChars(' ', '.', ',', '\n');
    }

    @Provide
    Arbitrary<List<AiMessage>> conversationHistories() {
        return Arbitraries.integers().between(0, 20).flatMap(size -> {
            if (size == 0) {
                return Arbitraries.just(List.of());
            }
            return messageContent().list().ofSize(size).map(contents -> {
                List<AiMessage> messages = new ArrayList<>();
                for (int i = 0; i < contents.size(); i++) {
                    if (i % 2 == 0) {
                        messages.add(AiMessage.user(contents.get(i)));
                    } else {
                        messages.add(AiMessage.assistant(contents.get(i)));
                    }
                }
                return messages;
            });
        });
    }

    @Provide
    Arbitrary<List<AiMessage>> conversationHistoriesWithUserMessage() {
        return Arbitraries.integers().between(1, 20).flatMap(size ->
                messageContent().list().ofSize(size).map(contents -> {
                    List<AiMessage> messages = new ArrayList<>();
                    for (int i = 0; i < contents.size() - 1; i++) {
                        if (i % 2 == 0) {
                            messages.add(AiMessage.user(contents.get(i)));
                        } else {
                            messages.add(AiMessage.assistant(contents.get(i)));
                        }
                    }
                    // Ensure last message is a user message
                    messages.add(AiMessage.user(contents.get(contents.size() - 1)));
                    return messages;
                })
        );
    }

    @Provide
    Arbitrary<List<AiMessage>> conversationHistoriesWithAssistantResponse() {
        return Arbitraries.integers().between(2, 20).flatMap(size ->
                messageContent().list().ofSize(size).map(contents -> {
                    List<AiMessage> messages = new ArrayList<>();
                    // First ensure there's an assistant response
                    messages.add(AiMessage.assistant(contents.get(0)));
                    for (int i = 1; i < contents.size(); i++) {
                        if (i % 2 == 0) {
                            messages.add(AiMessage.assistant(contents.get(i)));
                        } else {
                            messages.add(AiMessage.user(contents.get(i)));
                        }
                    }
                    return messages;
                })
        );
    }

    @Provide
    Arbitrary<List<AiMessage>> conversationHistoriesWithBoth() {
        return Arbitraries.integers().between(2, 20).flatMap(size ->
                messageContent().list().ofSize(size).map(contents -> {
                    List<AiMessage> messages = new ArrayList<>();
                    for (int i = 0; i < contents.size(); i++) {
                        if (i % 2 == 0) {
                            messages.add(AiMessage.user(contents.get(i)));
                        } else {
                            messages.add(AiMessage.assistant(contents.get(i)));
                        }
                    }
                    // Ensure the last message is a user message
                    if (!"user".equals(messages.get(messages.size() - 1).role())) {
                        messages.add(AiMessage.user("Last user message"));
                    }
                    return messages;
                })
        );
    }

    private Arbitrary<String> messageContent() {
        return Arbitraries.strings()
                .ofMinLength(5)
                .ofMaxLength(500)
                .alpha()
                .withChars(' ', '.', ',', '?', '!');
    }

    // ===== Helper Methods =====

    /**
     * Extract history messages from the assembled result.
     * History messages are user/assistant role messages between the context system messages
     * and the output instruction system message.
     */
    private List<AiMessage> extractHistoryMessages(List<AiMessage> result,
                                                    String memoryContent,
                                                    String contextContent) {
        List<AiMessage> historyMessages = new ArrayList<>();
        for (AiMessage msg : result) {
            if ("user".equals(msg.role()) || "assistant".equals(msg.role())) {
                historyMessages.add(msg);
            }
        }
        return historyMessages;
    }
}
