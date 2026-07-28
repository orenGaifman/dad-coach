package com.dadcoach.conversation.sideeffect;

import java.util.Map;
import java.util.UUID;

/**
 * Writes side-effect entries to the outbox within the main transaction.
 * Guarantees that side-effects are committed atomically with conversation state changes.
 *
 * <p>The SideEffectProcessor (background poller) processes these entries asynchronously.
 * <p>This interface is designed to be called within an existing @Transactional method —
 * it does NOT start its own transaction, so the outbox write is guaranteed to commit
 * (or rollback) with the caller's transaction.
 */
public interface SideEffectScheduler {

    /**
     * Schedules a side-effect to be processed asynchronously after transaction commit.
     *
     * @param type           the type of side-effect (e.g., MEMORY_EXTRACTION, EVENT_PUBLISH)
     * @param fatherId       the father this side-effect relates to
     * @param conversationId the conversation this side-effect relates to (nullable)
     * @param payload        the side-effect payload data
     */
    void schedule(String type, UUID fatherId, UUID conversationId, Map<String, Object> payload);

    /**
     * Schedules a side-effect using the type-safe SideEffect enum.
     * Max retries are determined by the effect type (mandatory = unlimited, best-effort = 3).
     *
     * @param type           the side-effect type enum
     * @param fatherId       the father this side-effect relates to
     * @param conversationId the conversation this side-effect relates to (nullable)
     * @param payload        the side-effect payload data
     */
    void schedule(SideEffect type, UUID fatherId, UUID conversationId, Map<String, Object> payload);
}
