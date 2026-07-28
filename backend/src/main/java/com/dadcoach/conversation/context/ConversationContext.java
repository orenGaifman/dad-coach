package com.dadcoach.conversation.context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Value object holding the assembled context for AI response generation.
 * Built by the ContextAssembler, consumed by the AiOrchestrator.
 *
 * <p>Contains all subsystem data needed for a single AI interaction:
 * father profile, children, goals, missions, memories, and conversation history.
 */
public record ConversationContext(
        UUID fatherId,
        UUID conversationId,
        String conversationType,
        Map<String, Object> fatherProfile,
        List<Map<String, Object>> children,
        List<Map<String, Object>> activeGoals,
        List<Map<String, Object>> activeMissions,
        List<Map<String, Object>> rankedMemories,
        List<Map<String, Object>> conversationHistory,
        Map<String, Object> temporalContext
) {

    public ConversationContext {
        if (fatherId == null) throw new IllegalArgumentException("fatherId is required");
        if (conversationId == null) throw new IllegalArgumentException("conversationId is required");
        if (conversationType == null) throw new IllegalArgumentException("conversationType is required");
        if (fatherProfile == null) fatherProfile = Map.of();
        if (children == null) children = List.of();
        if (activeGoals == null) activeGoals = List.of();
        if (activeMissions == null) activeMissions = List.of();
        if (rankedMemories == null) rankedMemories = List.of();
        if (conversationHistory == null) conversationHistory = List.of();
        if (temporalContext == null) temporalContext = Map.of();
    }
}
