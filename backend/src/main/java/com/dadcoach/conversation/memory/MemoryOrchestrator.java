package com.dadcoach.conversation.memory;

import com.dadcoach.conversation.entity.Conversation;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates memory operations within the conversation pipeline.
 * Schedules extraction as side-effects, records injected memories,
 * and triggers confirmations.
 *
 * <p>Delegates to MemoryService for actual operations. All extraction
 * is asynchronous via the outbox — never blocks conversation response.
 */
public interface MemoryOrchestrator {

    /**
     * Records which memories were injected into the AI context for this conversation turn.
     *
     * @param conversationId the conversation UUID
     * @param injectedMemoryIds the list of memory IDs that were included in the prompt
     */
    void recordInjectedMemories(UUID conversationId, List<UUID> injectedMemoryIds);

    /**
     * Schedules memory extraction as a side-effect for a completed/expired conversation.
     * Only schedules if the conversation has at least 2 father messages.
     *
     * @param conversation the conversation that reached a terminal state
     */
    void scheduleExtraction(Conversation conversation);

    /**
     * Triggers memory confirmation when a father explicitly validates information.
     * Delegates to MemoryService to boost confidence on the confirmed memory.
     *
     * @param fatherId the father's UUID
     * @param memoryId the memory UUID being confirmed
     */
    void triggerConfirmation(UUID fatherId, UUID memoryId);

    /**
     * Checks eligibility and schedules extraction if the conversation qualifies.
     * A conversation qualifies if it has at least 2 father messages.
     *
     * @param conversation the conversation to evaluate
     * @return true if extraction was scheduled, false otherwise
     */
    boolean scheduleExtractionIfEligible(Conversation conversation);
}
