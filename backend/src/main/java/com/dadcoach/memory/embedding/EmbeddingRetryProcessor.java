package com.dadcoach.memory.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Scheduled processor for the embedding retry queue.
 *
 * <p>From SPEC-004 Design Document - Error Handling:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <p>This processor:
 * <ul>
 *   <li>Runs every 5 minutes to process pending retry entries</li>
 *   <li>Processes entries in batches to avoid overwhelming the embedding service</li>
 *   <li>Handles stuck processing entries (processor crash recovery)</li>
 *   <li>Cleans up old completed and failed entries daily</li>
 *   <li>Logs metrics for monitoring</li>
 * </ul>
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * <h3>Processing Flow</h3>
 * <ol>
 *   <li>Find entries where status=PENDING and next_attempt_at <= now</li>
 *   <li>For each entry: mark as PROCESSING, attempt embedding, record result</li>
 *   <li>Success: update memory with embedding, mark entry as COMPLETED</li>
 *   <li>Failure: increment attempt count, schedule next attempt or mark PERMANENTLY_FAILED</li>
 * </ol>
 *
 * @see EmbeddingRetryQueueService
 * @see EmbeddingRetryEntry
 */
@Service
public class EmbeddingRetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRetryProcessor.class);

    /**
     * Batch size for processing retry entries.
     */
    private static final int BATCH_SIZE = 10;

    /**
     * Days to keep completed entries before cleanup.
     */
    private static final int COMPLETED_RETENTION_DAYS = 7;

    /**
     * Days to keep permanently failed entries before cleanup.
     */
    private static final int FAILED_RETENTION_DAYS = 30;

    private final EmbeddingRetryQueueService retryQueueService;
    private final EmbeddingService embeddingService;

    public EmbeddingRetryProcessor(
            EmbeddingRetryQueueService retryQueueService,
            EmbeddingService embeddingService) {
        this.retryQueueService = retryQueueService;
        this.embeddingService = embeddingService;
    }

    /**
     * Processes pending retry entries.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000) // Every 5 minutes, start after 1 minute
    public void processRetryQueue() {
        log.debug("Starting embedding retry queue processing");

        // First, reset any stuck processing entries
        int resetCount = retryQueueService.resetStuckProcessing();
        if (resetCount > 0) {
            log.info("Reset {} stuck processing entries", resetCount);
        }

        // Find entries ready for processing
        List<EmbeddingRetryEntry> entries = retryQueueService.findReadyForProcessing(BATCH_SIZE);
        if (entries.isEmpty()) {
            log.debug("No entries ready for retry processing");
            return;
        }

        log.info("Processing {} embedding retry entries", entries.size());

        int successCount = 0;
        int failureCount = 0;
        int permanentFailureCount = 0;

        for (EmbeddingRetryEntry entry : entries) {
            try {
                ProcessingResult result = processEntry(entry);
                switch (result) {
                    case SUCCESS -> successCount++;
                    case FAILURE -> failureCount++;
                    case PERMANENT_FAILURE -> permanentFailureCount++;
                }
            } catch (Exception e) {
                log.error("Unexpected error processing retry entry {} for memory {}: {}",
                         entry.getId(), entry.getMemoryId(), e.getMessage(), e);
                failureCount++;
            }
        }

        log.info("Embedding retry processing complete: {} succeeded, {} failed (temp), {} failed (permanent)",
                 successCount, failureCount, permanentFailureCount);
    }

    /**
     * Cleans up old retry entries.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3:00 AM daily
    public void cleanupOldEntries() {
        log.info("Starting embedding retry queue cleanup");

        int completedDeleted = retryQueueService.cleanupCompleted(COMPLETED_RETENTION_DAYS);
        int failedDeleted = retryQueueService.cleanupFailed(FAILED_RETENTION_DAYS);

        log.info("Cleanup complete: deleted {} completed entries, {} failed entries",
                 completedDeleted, failedDeleted);
    }

    /**
     * Logs queue metrics for monitoring.
     * Runs every 30 minutes.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 5 * 60 * 1000) // Every 30 minutes
    public void logQueueMetrics() {
        long pending = retryQueueService.countPending();
        long permanentlyFailed = retryQueueService.countPermanentlyFailed();
        long processing = retryQueueService.countByStatus(EmbeddingRetryEntry.Status.PROCESSING);

        if (pending > 0 || permanentlyFailed > 0 || processing > 0) {
            log.info("Embedding retry queue status: pending={}, processing={}, permanently_failed={}",
                     pending, processing, permanentlyFailed);
        }
    }

    /**
     * Process a single retry entry.
     *
     * @param entry the entry to process
     * @return the processing result
     */
    private ProcessingResult processEntry(EmbeddingRetryEntry entry) {
        // Mark as processing
        retryQueueService.markProcessing(entry);

        try {
            // Attempt to generate embedding
            float[] embedding = embeddingService.generateEmbedding(entry.getContent());

            // Success! Update memory and mark complete
            retryQueueService.recordSuccess(entry, embedding);
            return ProcessingResult.SUCCESS;

        } catch (EmbeddingException e) {
            // Record the failure
            retryQueueService.recordFailure(entry, e.getErrorType().name(), e.getMessage());

            if (entry.getStatus() == EmbeddingRetryEntry.Status.PERMANENTLY_FAILED) {
                return ProcessingResult.PERMANENT_FAILURE;
            }
            return ProcessingResult.FAILURE;

        } catch (Exception e) {
            // Unexpected error
            retryQueueService.recordFailure(entry, "UNEXPECTED_ERROR", e.getMessage());

            if (entry.getStatus() == EmbeddingRetryEntry.Status.PERMANENTLY_FAILED) {
                return ProcessingResult.PERMANENT_FAILURE;
            }
            return ProcessingResult.FAILURE;
        }
    }

    /**
     * Result of processing a retry entry.
     */
    private enum ProcessingResult {
        SUCCESS,
        FAILURE,
        PERMANENT_FAILURE
    }

    // ─── Manual Trigger Methods (for testing/ops) ────────────────────────

    /**
     * Manually triggers processing of the retry queue.
     * Useful for testing or operational recovery.
     *
     * @return number of entries processed
     */
    public int triggerProcessing() {
        List<EmbeddingRetryEntry> entries = retryQueueService.findReadyForProcessing(BATCH_SIZE);
        for (EmbeddingRetryEntry entry : entries) {
            processEntry(entry);
        }
        return entries.size();
    }

    /**
     * Manually triggers cleanup.
     * Useful for testing or operational maintenance.
     */
    public void triggerCleanup() {
        cleanupOldEntries();
    }
}
