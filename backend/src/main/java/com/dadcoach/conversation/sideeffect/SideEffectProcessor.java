package com.dadcoach.conversation.sideeffect;

import com.dadcoach.conversation.entity.SideEffectOutbox;
import com.dadcoach.conversation.repository.SideEffectOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Background poller that processes pending side-effect outbox entries.
 * <p>
 * Runs on a dedicated thread pool ("sideEffectExecutor") separate from the main request
 * handling threads. Polls every 5 seconds for up to 20 PENDING entries, dispatches each,
 * and handles retries with exponential backoff.
 * <p>
 * Status flow: PENDING → PROCESSING → COMPLETED | FAILED
 * <p>
 * On failure:
 * <ul>
 *   <li>Increments retry_count</li>
 *   <li>Computes next_retry_at with exponential backoff (2^retryCount seconds)</li>
 *   <li>Best-effort effects (maxRetries=3): marked FAILED after 3 retries</li>
 *   <li>Mandatory effects (maxRetries=MAX_VALUE): retried indefinitely</li>
 * </ul>
 * <p>
 * Resumes processing on application startup via ApplicationReadyEvent.
 */
@Component
public class SideEffectProcessor {

    private static final Logger log = LoggerFactory.getLogger(SideEffectProcessor.class);

    private static final int BATCH_SIZE = 20;

    private final SideEffectOutboxRepository outboxRepository;
    private final Map<SideEffect, SideEffectHandler> handlers;

    public SideEffectProcessor(SideEffectOutboxRepository outboxRepository,
                               List<SideEffectHandler> handlerList) {
        this.outboxRepository = outboxRepository;
        this.handlers = handlerList.stream()
                .collect(java.util.stream.Collectors.toMap(SideEffectHandler::getType, h -> h));
        log.info("SideEffectProcessor initialized with {} handlers: {}",
                handlers.size(), handlers.keySet());
    }

    /**
     * Polls for pending side-effects every 5 seconds.
     * Runs on the "sideEffectExecutor" thread pool, isolated from request threads.
     */
    @Async("sideEffectExecutor")
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        try {
            List<SideEffectOutbox> pending = outboxRepository.findPending(Limit.of(BATCH_SIZE));

            if (pending.isEmpty()) {
                return;
            }

            log.debug("Processing {} pending side-effects", pending.size());

            for (var entry : pending) {
                processEntry(entry);
            }
        } catch (Exception e) {
            // Never crash the polling loop — log and continue on next cycle
            log.error("Unexpected error in side-effect polling loop", e);
        }
    }

    /**
     * Resumes processing on application startup.
     * Ensures any entries left in PROCESSING state (from a prior crash) are reset to PENDING,
     * and triggers an immediate poll.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        log.info("Side-effect processor starting — resuming pending entries on startup");
        resetStaleProcessingEntries();
        poll();
    }

    /**
     * Processes a single outbox entry: transitions to PROCESSING, dispatches, then
     * marks COMPLETED or handles failure with retry/backoff.
     */
    private void processEntry(SideEffectOutbox entry) {
        try {
            // Transition: PENDING → PROCESSING
            entry.setStatus("PROCESSING");
            outboxRepository.save(entry);

            // Dispatch the side-effect
            dispatch(entry);

            // Transition: PROCESSING → COMPLETED
            entry.setStatus("COMPLETED");
            entry.setCompletedAt(Instant.now());
            entry.setErrorDetail(null);
            outboxRepository.save(entry);

            log.debug("Side-effect completed: id={}, type={}", entry.getId(), entry.getEffectType());

        } catch (Exception e) {
            handleFailure(entry, e);
        }
    }

    /**
     * Dispatches a side-effect entry to the appropriate handler based on effect type.
     * If no handler is registered for the type, the entry is completed with a warning.
     */
    private void dispatch(SideEffectOutbox entry) {
        SideEffect type;
        try {
            type = SideEffect.valueOf(entry.getEffectType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown side-effect type '{}' for entry {}, marking completed (no handler)",
                    entry.getEffectType(), entry.getId());
            return;
        }

        SideEffectHandler handler = handlers.get(type);
        if (handler == null) {
            log.debug("No handler registered for side-effect type '{}' (entry {}). Completing as no-op.",
                    type, entry.getId());
            return;
        }

        try {
            handler.handle(entry);
        } catch (Exception e) {
            throw new RuntimeException("Handler failed for " + type + ": " + e.getMessage(), e);
        }
    }

    /**
     * Handles a failed dispatch: increments retry count, computes exponential backoff,
     * and marks FAILED if max retries reached.
     */
    private void handleFailure(SideEffectOutbox entry, Exception e) {
        int newRetryCount = entry.getRetryCount() + 1;
        entry.setRetryCount(newRetryCount);
        entry.setErrorDetail(truncateError(e.getMessage()));

        if (newRetryCount >= entry.getMaxRetries()) {
            // Max retries exceeded — mark as permanently FAILED
            entry.setStatus("FAILED");
            log.error("Side-effect permanently failed after {} retries: id={}, type={}",
                    newRetryCount, entry.getId(), entry.getEffectType(), e);
        } else {
            // Compute exponential backoff: next_retry_at = now + 2^retryCount seconds
            Instant nextRetryAt = computeBackoff(newRetryCount);
            entry.setStatus("PENDING");
            entry.setNextRetryAt(nextRetryAt);
            log.warn("Side-effect failed (attempt {}), retrying at {}: id={}, type={}, error={}",
                    newRetryCount, nextRetryAt, entry.getId(), entry.getEffectType(), e.getMessage());
        }

        outboxRepository.save(entry);
    }

    /**
     * Computes exponential backoff: now + 2^retryCount seconds.
     * Capped at 1 hour to prevent excessively long delays.
     */
    private Instant computeBackoff(int retryCount) {
        long delaySeconds = (long) Math.pow(2, retryCount);
        // Cap at 1 hour (3600 seconds)
        delaySeconds = Math.min(delaySeconds, 3600);
        return Instant.now().plusSeconds(delaySeconds);
    }

    /**
     * Resets entries stuck in PROCESSING state (from a prior crash/restart) back to PENDING
     * so they are picked up in the next poll cycle.
     */
    private void resetStaleProcessingEntries() {
        // Use a native approach: find all PROCESSING entries and reset them
        List<SideEffectOutbox> stale = outboxRepository.findAll().stream()
                .filter(e -> "PROCESSING".equals(e.getStatus()))
                .toList();

        if (!stale.isEmpty()) {
            log.info("Resetting {} stale PROCESSING entries to PENDING", stale.size());
            for (var entry : stale) {
                entry.setStatus("PENDING");
                entry.setNextRetryAt(null);
            }
            outboxRepository.saveAll(stale);
        }
    }

    /**
     * Truncates error messages to avoid storing excessively long text in the database.
     */
    private String truncateError(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
