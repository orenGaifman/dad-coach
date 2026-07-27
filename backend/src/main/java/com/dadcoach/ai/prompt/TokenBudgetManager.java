package com.dadcoach.ai.prompt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import java.util.Objects;

/**
 * Manages token budget allocation for prompt assembly sections.
 *
 * <p>Uses tiktoken4j (jtokkit) with cl100k_base encoding for exact token counting.
 * The total budget is 2000 tokens distributed across fixed sections:
 * <ul>
 *   <li>System: 400 tokens (20%)</li>
 *   <li>Memory: 500 tokens (25%)</li>
 *   <li>Context: 300 tokens (15%)</li>
 *   <li>History: 600 tokens (30%)</li>
 *   <li>Output: 200 tokens (10%)</li>
 * </ul>
 *
 * @see PromptAssembler
 */
public class TokenBudgetManager {

    public static final int TOTAL_BUDGET = 2000;
    public static final int SYSTEM_BUDGET = 400;
    public static final int MEMORY_BUDGET = 500;
    public static final int CONTEXT_BUDGET = 300;
    public static final int HISTORY_BUDGET = 600;
    public static final int OUTPUT_BUDGET = 200;

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private int systemUsed;
    private int memoryUsed;
    private int contextUsed;
    private int historyUsed;
    private int outputUsed;

    public TokenBudgetManager() {
        this.systemUsed = 0;
        this.memoryUsed = 0;
        this.contextUsed = 0;
        this.historyUsed = 0;
        this.outputUsed = 0;
    }

    /**
     * Count the exact number of tokens in a text string using cl100k_base encoding.
     *
     * @param text the text to count tokens for
     * @return the exact token count
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    /**
     * Truncate text to fit within a maximum token count.
     * Uses the encoding's built-in truncation support via encode(text, maxTokens).
     *
     * @param text      the text to truncate
     * @param maxTokens the maximum number of tokens allowed
     * @return the text truncated to fit within the token limit
     */
    public String truncateToFit(String text, int maxTokens) {
        Objects.requireNonNull(text, "text must not be null");
        if (maxTokens <= 0) {
            return "";
        }
        if (text.isEmpty()) {
            return "";
        }
        EncodingResult result = ENCODING.encode(text, maxTokens);
        if (!result.isTruncated()) {
            return text;
        }
        IntArrayList truncatedTokens = result.getTokens();
        return ENCODING.decode(truncatedTokens);
    }

    /**
     * Try to allocate tokens for the system section.
     *
     * @param content the system content to allocate
     * @return the content, possibly truncated to fit within the system budget
     */
    public String allocateSystem(String content) {
        String result = truncateToFit(content, SYSTEM_BUDGET);
        this.systemUsed = countTokens(result);
        return result;
    }

    /**
     * Try to allocate tokens for the memory section.
     *
     * @param content the memory content to allocate
     * @return the content, possibly truncated to fit within the memory budget
     */
    public String allocateMemory(String content) {
        String result = truncateToFit(content, MEMORY_BUDGET);
        this.memoryUsed = countTokens(result);
        return result;
    }

    /**
     * Try to allocate tokens for the context section.
     *
     * @param content the context content to allocate
     * @return the content, possibly truncated to fit within the context budget
     */
    public String allocateContext(String content) {
        String result = truncateToFit(content, CONTEXT_BUDGET);
        this.contextUsed = countTokens(result);
        return result;
    }

    /**
     * Try to allocate tokens for the output instructions section.
     *
     * @param content the output instruction content to allocate
     * @return the content, possibly truncated to fit within the output budget
     */
    public String allocateOutput(String content) {
        String result = truncateToFit(content, OUTPUT_BUDGET);
        this.outputUsed = countTokens(result);
        return result;
    }

    /**
     * Get the remaining budget available for the history section.
     * History budget is a fixed 600 tokens.
     *
     * @return the history budget (600 tokens)
     */
    public int getHistoryBudget() {
        return HISTORY_BUDGET;
    }

    /**
     * Record the actual tokens used by the history section.
     *
     * @param tokens the number of tokens used by history
     */
    public void recordHistoryUsed(int tokens) {
        this.historyUsed = Math.min(tokens, HISTORY_BUDGET);
    }

    /**
     * @return total tokens used across all sections
     */
    public int getTotalUsed() {
        return systemUsed + memoryUsed + contextUsed + historyUsed + outputUsed;
    }

    public int getSystemUsed() {
        return systemUsed;
    }

    public int getMemoryUsed() {
        return memoryUsed;
    }

    public int getContextUsed() {
        return contextUsed;
    }

    public int getHistoryUsed() {
        return historyUsed;
    }

    public int getOutputUsed() {
        return outputUsed;
    }

    /**
     * Check if total usage is within the total budget.
     *
     * @return true if total tokens used does not exceed TOTAL_BUDGET
     */
    public boolean isWithinBudget() {
        return getTotalUsed() <= TOTAL_BUDGET;
    }
}
