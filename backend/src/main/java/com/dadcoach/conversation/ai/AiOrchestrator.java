package com.dadcoach.conversation.ai;

import com.dadcoach.conversation.context.ConversationContext;
import com.dadcoach.conversation.dto.InboundMessageDto;

/**
 * Orchestrates the AI sub-pipeline: safety classification → generate → validate → retry/fallback.
 *
 * <p>This interface never throws — it always produces a deliverable response,
 * falling back to pre-written safe messages if AI is unavailable or produces invalid output.
 */
public interface AiOrchestrator {

    /**
     * Executes the AI orchestration pipeline and produces a response.
     * Guaranteed to return a valid, deliverable result (never null, never throws).
     *
     * @param context the assembled conversation context
     * @param message the inbound message that triggered this pipeline
     * @return an AiResult containing the response content and metadata
     */
    AiResult orchestrate(ConversationContext context, InboundMessageDto message);
}
