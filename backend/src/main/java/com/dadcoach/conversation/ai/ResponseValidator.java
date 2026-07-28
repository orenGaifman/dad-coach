package com.dadcoach.conversation.ai;

import com.dadcoach.ai.output.CoachingResponse;
import com.dadcoach.conversation.context.ConversationContext;

/**
 * Validates AI-generated coaching responses before delivery.
 *
 * <p>Per SPEC-005 Requirement 5 criteria 3: validates schema compliance,
 * language, length, safety, relevance, and confidentiality.
 *
 * <p>Implementations of this interface are used by {@link AiOrchestrator}
 * to gate responses. If validation fails, the orchestrator retries once
 * with correction context before falling back to a pre-written response.
 */
public interface ResponseValidator {

    /**
     * Validates a coaching response against all quality and safety criteria.
     *
     * @param response the AI-generated coaching response to validate
     * @param context  the conversation context used during generation
     * @return the validation result containing pass/fail and any failure reasons
     */
    ValidationResult validate(CoachingResponse response, ConversationContext context);
}
