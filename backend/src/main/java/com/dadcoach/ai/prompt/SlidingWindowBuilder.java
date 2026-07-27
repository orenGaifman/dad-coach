package com.dadcoach.ai.prompt;

import com.dadcoach.ai.AiMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds a sliding window of conversation history messages within a token budget.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>The current user message is always included</li>
 *   <li>The last assistant response is always included (if it exists)</li>
 *   <li>When the budget is exceeded, oldest messages are removed first</li>
 *   <li>Total tokens for selected history never exceed the allocated budget</li>
 * </ul>
 *
 * @see TokenBudgetManager
 * @see PromptAssembler
 */
public class SlidingWindowBuilder {

    private final TokenBudgetManager budgetManager;

    public SlidingWindowBuilder(TokenBudgetManager budgetManager) {
        this.budgetManager = Objects.requireNonNull(budgetManager, "budgetManager must not be null");
    }

    /**
     * Build a sliding window from the conversation history that fits within the history budget.
     *
     * <p>The algorithm:
     * 1. Always include the current user message (last message in the list)
     * 2. Always include the last assistant response (if present)
     * 3. Fill remaining budget with messages from most recent to oldest
     * 4. If budget is exceeded, remove oldest messages first
     *
     * @param history      the full conversation history (ordered oldest to newest)
     * @param tokenBudget  the maximum number of tokens allowed for history
     * @return a list of messages fitting within the budget, maintaining chronological order
     */
    public List<AiMessage> buildWindow(List<AiMessage> history, int tokenBudget) {
        Objects.requireNonNull(history, "history must not be null");

        if (history.isEmpty()) {
            return List.of();
        }

        if (tokenBudget <= 0) {
            return List.of();
        }

        // Identify the guaranteed messages: current user message + last assistant response
        AiMessage currentUserMessage = findLastUserMessage(history);
        AiMessage lastAssistantResponse = findLastAssistantResponse(history);

        // Calculate tokens needed for guaranteed messages
        int guaranteedTokens = 0;
        if (currentUserMessage != null) {
            guaranteedTokens += budgetManager.countTokens(currentUserMessage.content());
        }
        if (lastAssistantResponse != null) {
            guaranteedTokens += budgetManager.countTokens(lastAssistantResponse.content());
        }

        // If guaranteed messages alone exceed budget, truncate them to fit
        if (guaranteedTokens > tokenBudget && currentUserMessage != null) {
            List<AiMessage> result = new ArrayList<>();
            if (lastAssistantResponse != null) {
                int halfBudget = tokenBudget / 2;
                String truncatedAssistant = budgetManager.truncateToFit(
                    lastAssistantResponse.content(), halfBudget);
                String truncatedUser = budgetManager.truncateToFit(
                    currentUserMessage.content(), tokenBudget - budgetManager.countTokens(truncatedAssistant));
                result.add(new AiMessage(lastAssistantResponse.role(), truncatedAssistant));
                result.add(new AiMessage(currentUserMessage.role(), truncatedUser));
            } else {
                String truncatedUser = budgetManager.truncateToFit(currentUserMessage.content(), tokenBudget);
                result.add(new AiMessage(currentUserMessage.role(), truncatedUser));
            }
            return result;
        }

        // Fill the window from most recent to oldest, excluding guaranteed messages
        int remainingBudget = tokenBudget - guaranteedTokens;
        List<AiMessage> additionalMessages = new ArrayList<>();

        // Iterate from the most recent to oldest, skipping guaranteed messages
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage msg = history.get(i);
            if (msg == currentUserMessage || msg == lastAssistantResponse) {
                continue;
            }

            int msgTokens = budgetManager.countTokens(msg.content());
            if (msgTokens <= remainingBudget) {
                additionalMessages.add(msg);
                remainingBudget -= msgTokens;
            } else {
                // Once we can't fit a message, stop (older messages would be even less relevant)
                break;
            }
        }

        // Reconstruct in chronological order
        Collections.reverse(additionalMessages);

        List<AiMessage> result = new ArrayList<>(additionalMessages);
        if (lastAssistantResponse != null && !additionalMessages.contains(lastAssistantResponse)) {
            result.add(lastAssistantResponse);
        }
        if (currentUserMessage != null && !additionalMessages.contains(currentUserMessage)) {
            result.add(currentUserMessage);
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Find the last user message in the history.
     */
    private AiMessage findLastUserMessage(List<AiMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) {
                return history.get(i);
            }
        }
        return null;
    }

    /**
     * Find the last assistant response in the history.
     */
    private AiMessage findLastAssistantResponse(List<AiMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).role())) {
                return history.get(i);
            }
        }
        return null;
    }
}
