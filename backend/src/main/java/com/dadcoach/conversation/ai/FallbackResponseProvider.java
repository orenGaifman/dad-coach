package com.dadcoach.conversation.ai;

/**
 * Provides pre-written fallback responses per conversation type.
 * Fallback messages are static text (never AI-generated), written in
 * English (default) or Hebrew based on father's language preference.
 *
 * <p>Used when the AI is unavailable, produces invalid output, or any
 * unhandled error occurs in the pipeline.
 */
public interface FallbackResponseProvider {

    /**
     * Returns a pre-written fallback message appropriate for the conversation type.
     *
     * @param conversationType the type of conversation (e.g., ONBOARDING, DAILY_COACHING)
     * @return a safe, pre-written response in the default language (English)
     */
    String getForType(String conversationType);

    /**
     * Returns a generic fallback message when the conversation type is unknown or null.
     *
     * @return a safe, generic response in the default language (English)
     */
    String getGenericFallback();
}
