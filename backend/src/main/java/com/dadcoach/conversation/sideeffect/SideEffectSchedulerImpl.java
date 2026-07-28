package com.dadcoach.conversation.sideeffect;

import com.dadcoach.conversation.entity.SideEffectOutbox;
import com.dadcoach.conversation.repository.SideEffectOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Implementation of SideEffectScheduler that writes side-effect entries to the outbox table.
 * <p>
 * This service is designed to be called within an existing transaction (e.g., within
 * ConversationOrchestrator.processMessage()). It does NOT declare its own @Transactional
 * boundary — the outbox write participates in the caller's transaction, guaranteeing that
 * side-effects are committed atomically with conversation state changes.
 * <p>
 * If the caller's transaction rolls back, the outbox entry is also rolled back —
 * no orphaned side-effects are created.
 */
@Service
public class SideEffectSchedulerImpl implements SideEffectScheduler {

    private static final Logger log = LoggerFactory.getLogger(SideEffectSchedulerImpl.class);

    private final SideEffectOutboxRepository outboxRepository;

    public SideEffectSchedulerImpl(SideEffectOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void schedule(String type, UUID fatherId, UUID conversationId, Map<String, Object> payload) {
        int maxRetries = resolveMaxRetries(type);
        persistEntry(type, fatherId, conversationId, payload, maxRetries);
    }

    @Override
    public void schedule(SideEffect type, UUID fatherId, UUID conversationId, Map<String, Object> payload) {
        persistEntry(type.name(), fatherId, conversationId, payload, type.getMaxRetries());
    }

    private void persistEntry(String effectType, UUID fatherId, UUID conversationId,
                              Map<String, Object> payload, int maxRetries) {
        var entry = SideEffectOutbox.builder()
                .fatherId(fatherId)
                .conversationId(conversationId)
                .effectType(effectType)
                .payload(payload != null ? payload : Map.of())
                .status("PENDING")
                .maxRetries(maxRetries)
                .build();

        outboxRepository.save(entry);

        log.debug("Scheduled side-effect: type={}, fatherId={}, conversationId={}, maxRetries={}",
                effectType, fatherId, conversationId, maxRetries);
    }

    /**
     * Resolves max retries for a string-based effect type.
     * Attempts to parse as SideEffect enum; falls back to best-effort (3) if unknown.
     */
    private int resolveMaxRetries(String type) {
        try {
            return SideEffect.valueOf(type).getMaxRetries();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown side-effect type '{}', defaulting to best-effort max retries (3)", type);
            return 3;
        }
    }
}
