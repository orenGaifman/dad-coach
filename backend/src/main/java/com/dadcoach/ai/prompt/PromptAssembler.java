package com.dadcoach.ai.prompt;

import com.dadcoach.ai.AiMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composes multi-section prompts with strict token budgets.
 *
 * <p>Assembles prompts from ordered sections (system, memory, context, history, output)
 * using tiktoken4j (jtokkit) for exact cl100k_base token counting. Each section has a
 * fixed budget allocation that it cannot exceed:
 * <ul>
 *   <li>System: 400 tokens</li>
 *   <li>Memory: 500 tokens</li>
 *   <li>Context: 300 tokens</li>
 *   <li>History: 600 tokens (with sliding window)</li>
 *   <li>Output: 200 tokens</li>
 * </ul>
 *
 * <p>The sliding window for history always guarantees inclusion of the current user
 * message and the last assistant response.
 *
 * @see TokenBudgetManager
 * @see SlidingWindowBuilder
 */
@Service
public class PromptAssembler {

    /**
     * Assemble a complete prompt from its sections, enforcing token budgets.
     *
     * @param systemContent  the system prompt content (role, phase, style, boundaries)
     * @param memoryContent  the memory block content (selected memories)
     * @param contextContent the context block content (goals, missions, phase info)
     * @param history        the full conversation history (oldest to newest)
     * @param outputContent  the output instruction content
     * @return an unmodifiable list of AiMessage objects ready for the AI provider
     */
    public List<AiMessage> assemble(
            String systemContent,
            String memoryContent,
            String contextContent,
            List<AiMessage> history,
            String outputContent) {

        Objects.requireNonNull(systemContent, "systemContent must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(outputContent, "outputContent must not be null");

        TokenBudgetManager budget = new TokenBudgetManager();
        List<AiMessage> messages = new ArrayList<>();

        // 1. System section (400 tokens max)
        String truncatedSystem = budget.allocateSystem(systemContent);
        messages.add(AiMessage.system(truncatedSystem));

        // 2. Memory section (500 tokens max) — injected as a system message
        if (memoryContent != null && !memoryContent.isBlank()) {
            String truncatedMemory = budget.allocateMemory(memoryContent);
            messages.add(AiMessage.system(truncatedMemory));
        }

        // 3. Context section (300 tokens max) — injected as a system message
        if (contextContent != null && !contextContent.isBlank()) {
            String truncatedContext = budget.allocateContext(contextContent);
            messages.add(AiMessage.system(truncatedContext));
        }

        // 4. History section (600 tokens max) — sliding window with guarantees
        SlidingWindowBuilder slidingWindow = new SlidingWindowBuilder(budget);
        List<AiMessage> windowedHistory = slidingWindow.buildWindow(history, budget.getHistoryBudget());
        messages.addAll(windowedHistory);

        // Record actual history token usage
        int historyTokens = windowedHistory.stream()
                .mapToInt(msg -> budget.countTokens(msg.content()))
                .sum();
        budget.recordHistoryUsed(historyTokens);

        // 5. Output instructions section (200 tokens max) — as a system message
        String truncatedOutput = budget.allocateOutput(outputContent);
        messages.add(AiMessage.system(truncatedOutput));

        return Collections.unmodifiableList(messages);
    }

    /**
     * Assemble a prompt using only system content and history (minimal assembly).
     * Useful for fallback scenarios when context/memory is unavailable.
     *
     * @param systemContent the system prompt content
     * @param history       the conversation history
     * @return an unmodifiable list of AiMessage objects
     */
    public List<AiMessage> assembleMinimal(String systemContent, List<AiMessage> history) {
        return assemble(systemContent, null, null, history, "");
    }
}
