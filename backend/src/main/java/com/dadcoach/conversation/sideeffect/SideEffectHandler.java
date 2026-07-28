package com.dadcoach.conversation.sideeffect;

import com.dadcoach.conversation.entity.SideEffectOutbox;

/**
 * Handler interface for processing a specific type of side-effect.
 * Implementations are registered in the {@link SideEffectProcessor} and invoked
 * when a matching outbox entry is polled.
 */
public interface SideEffectHandler {

    /**
     * Returns the side-effect type this handler processes.
     */
    SideEffect getType();

    /**
     * Processes the side-effect entry.
     * Implementations should be idempotent — the same entry may be retried.
     *
     * @param entry the outbox entry to process
     * @throws Exception if processing fails (triggers retry with backoff)
     */
    void handle(SideEffectOutbox entry) throws Exception;
}
