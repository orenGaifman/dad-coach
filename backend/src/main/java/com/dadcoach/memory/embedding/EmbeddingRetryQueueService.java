package com.dadcoach.memory.embedding;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing the embedding retry queue.
 *
 * <p>From SPEC-004 Design Document - Error Handling:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <p>This service provides:
 * <ul>
 *   <li>Queuing memories for embedding retry when generation fails</li>
 *   <li>Retrieving entries ready for retry processing</li>
 *   <li>Recording retry attempt results (success/failure)</li>
 *   <li>Cleanup of completed and old failed entries</li>
 *   <li>Metrics and monitoring support</li>
 * </ul>
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * <h3>Retry Schedule</h3>
 * Attempts are spread over 24 hours using exponential backoff:
 * <ul>
 *   <li>Attempt 1: Immediate (when first queued)</li>
 *   <li>Attempt 2: After 4 hours</li>
 *   <li>Attempt 3: After 12 hours (from previous attempt)</li>
 * </ul>
 *
 * @see EmbeddingRetryEntry
 * @see EmbeddingRetryProcessor
 */
@Service
public class EmbeddingRetryQueueService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRetryQueueService.class);

    /**
     * Default batch size for processing retry entries.
     */
    public static final int DEFAULT_BATCH_SIZE = 10;

    /**
     * Threshold for considering a processing entry as stuck (5 minutes).
     */
    public static final int STUCK_PROCESSING_THRESHOLD_MINUTES = 5;

    private final EmbeddingRetryRepository retryRepository;
    private final MemoryRepository memoryRepository;

    public EmbeddingRetryQueueService(
            EmbeddingRetryRepository retryRepository,
            MemoryRepository memoryRepository) {
        this.retryRepository = retryRepository;
        this.memoryRepository = memoryRepository;
    }

    // ─── Queue Management ────────────────────────────────────────────────

    /**
     * Queues a memory for embedding retry.
     *
     * <p>If the memory is already in the queue, this method does nothing.
     * The memory must exist and have no embedding.
     *
     * @param memoryId the ID of the memory to queue
     * @param content  the memory content to embed
     * @return the created or existing retry entry
     */
    @Transactional
    public EmbeddingRetryEntry queueForRetry(UUID memoryId, String content) {
        // Check if already queued
        Optional<EmbeddingRetryEntry> existing = retryRepository.findByMemoryId(memoryId);
        if (existing.isPresent()) {
            EmbeddingRetryEntry entry = existing.get();
            // If it's already completed or permanently failed, don't re-queue
            if (entry.getStatus() == EmbeddingRetryEntry.Status.COMPLETED ||
                entry.getStatus() == EmbeddingRetryEntry.Status.PERMANENTLY_FAILED) {
                log.debug("Memory {} already processed (status={}), not re-queueing", 
                         memoryId, entry.getStatus());
                return entry;
            }
            log.debug("Memory {} already in retry queue with status {}", memoryId, entry.getStatus());
            return entry;
        }

        // Create new entry
        EmbeddingRetryEntry entry = new EmbeddingRetryEntry(memoryId, content);
        entry = retryRepository.save(entry);
        log.info("Queued memory {} for embedding retry", memoryId);
        return entry;
    }

    /**
     * Queues a memory for embedding retry, looking up content from the memory.
     *
     * @param memoryId the ID of the memory to queue
     * @return the created or existing retry entry, or empty if memory not found
     */
    @Transactional
    public Optional<EmbeddingRetryEntry> queueForRetry(UUID memoryId) {
        Optional<Memory> memory = memoryRepository.findById(memoryId);
        if (memory.isEmpty()) {
            log.warn("Cannot queue memory {} for retry - not found", memoryId);
            return Optional.empty();
        }

        if (memory.get().hasEmbedding()) {
            log.debug("Memory {} already has embedding, not queueing", memoryId);
            return Optional.empty();
        }

        return Optional.of(queueForRetry(memoryId, memory.get().getContent()));
    }

    /**
     * Removes a memory from the retry queue.
     *
     * @param memoryId the ID of the memory to remove
     */
    @Transactional
    public void removeFromQueue(UUID memoryId) {
        retryRepository.deleteByMemoryId(memoryId);
        log.debug("Removed memory {} from retry queue", memoryId);
    }

    // ─── Retry Processing ────────────────────────────────────────────────

    /**
     * Finds entries ready for retry processing.
     *
     * @param batchSize maximum number of entries to return
     * @return list of entries ready for processing
     */
    @Transactional(readOnly = true)
    public List<EmbeddingRetryEntry> findReadyForProcessing(int batchSize) {
        return retryRepository.findReadyForProcessingNative(Instant.now(), batchSize);
    }

    /**
     * Finds entries ready for retry processing with default batch size.
     *
     * @return list of entries ready for processing
     */
    @Transactional(readOnly = true)
    public List<EmbeddingRetryEntry> findReadyForProcessing() {
        return findReadyForProcessing(DEFAULT_BATCH_SIZE);
    }

    /**
     * Marks an entry as currently being processed.
     *
     * @param entry the entry to mark
     */
    @Transactional
    public void markProcessing(EmbeddingRetryEntry entry) {
        entry.markProcessing();
        retryRepository.save(entry);
        log.debug("Marked entry {} as processing", entry.getId());
    }

    /**
     * Records a successful embedding generation.
     *
     * @param entry     the retry entry
     * @param embedding the generated embedding
     */
    @Transactional
    public void recordSuccess(EmbeddingRetryEntry entry, float[] embedding) {
        // Update the memory with the embedding
        Optional<Memory> memory = memoryRepository.findById(entry.getMemoryId());
        if (memory.isPresent()) {
            memory.get().setEmbedding(embedding);
            memory.get().setLastUpdatedAt(Instant.now());
            memoryRepository.save(memory.get());
            log.info("Updated memory {} with embedding from retry queue", entry.getMemoryId());
        } else {
            log.warn("Memory {} not found when updating embedding", entry.getMemoryId());
        }

        // Mark entry as completed
        entry.markCompleted();
        retryRepository.save(entry);
        log.info("Embedding retry succeeded for memory {} after {} attempts", 
                 entry.getMemoryId(), entry.getAttemptCount() + 1);
    }

    /**
     * Records a failed embedding attempt.
     *
     * @param entry        the retry entry
     * @param errorType    the type of error
     * @param errorMessage the error message
     */
    @Transactional
    public void recordFailure(EmbeddingRetryEntry entry, String errorType, String errorMessage) {
        entry.recordFailure(errorType, errorMessage);
        retryRepository.save(entry);

        if (entry.getStatus() == EmbeddingRetryEntry.Status.PERMANENTLY_FAILED) {
            log.warn("Embedding permanently failed for memory {} after {} attempts: {} - {}",
                     entry.getMemoryId(), entry.getAttemptCount(), errorType, errorMessage);
        } else {
            log.info("Embedding retry {} of {} failed for memory {}: {} - {}. Next attempt at {}",
                     entry.getAttemptCount(), EmbeddingRetryEntry.MAX_ATTEMPTS,
                     entry.getMemoryId(), errorType, errorMessage, entry.getNextAttemptAt());
        }
    }

    /**
     * Resets stuck processing entries back to pending.
     * This handles cases where the processor crashed during processing.
     *
     * @return number of entries reset
     */
    @Transactional
    public int resetStuckProcessing() {
        Instant threshold = Instant.now().minusSeconds(STUCK_PROCESSING_THRESHOLD_MINUTES * 60L);
        List<EmbeddingRetryEntry> stuck = retryRepository.findStuckProcessing(
                EmbeddingRetryEntry.Status.PROCESSING, threshold);

        for (EmbeddingRetryEntry entry : stuck) {
            entry.resetToPending();
            retryRepository.save(entry);
            log.warn("Reset stuck processing entry {} for memory {}", entry.getId(), entry.getMemoryId());
        }

        if (!stuck.isEmpty()) {
            log.info("Reset {} stuck processing entries", stuck.size());
        }
        return stuck.size();
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────

    /**
     * Cleans up old completed entries.
     *
     * @param olderThanDays delete entries completed more than this many days ago
     * @return number of deleted entries
     */
    @Transactional
    public int cleanupCompleted(int olderThanDays) {
        Instant threshold = Instant.now().minusSeconds(olderThanDays * 24L * 60 * 60);
        int deleted = retryRepository.deleteCompletedOlderThan(
                EmbeddingRetryEntry.Status.COMPLETED, threshold);
        if (deleted > 0) {
            log.info("Deleted {} completed retry entries older than {} days", deleted, olderThanDays);
        }
        return deleted;
    }

    /**
     * Cleans up old permanently failed entries.
     *
     * @param olderThanDays delete entries failed more than this many days ago
     * @return number of deleted entries
     */
    @Transactional
    public int cleanupFailed(int olderThanDays) {
        Instant threshold = Instant.now().minusSeconds(olderThanDays * 24L * 60 * 60);
        int deleted = retryRepository.deleteFailedOlderThan(
                EmbeddingRetryEntry.Status.PERMANENTLY_FAILED, threshold);
        if (deleted > 0) {
            log.info("Deleted {} permanently failed retry entries older than {} days", deleted, olderThanDays);
        }
        return deleted;
    }

    // ─── Metrics & Monitoring ────────────────────────────────────────────

    /**
     * Gets the count of entries by status.
     *
     * @param status the status to count
     * @return count of entries
     */
    @Transactional(readOnly = true)
    public long countByStatus(EmbeddingRetryEntry.Status status) {
        return retryRepository.countByStatus(status);
    }

    /**
     * Gets the total count of pending entries.
     *
     * @return count of pending entries
     */
    @Transactional(readOnly = true)
    public long countPending() {
        return countByStatus(EmbeddingRetryEntry.Status.PENDING);
    }

    /**
     * Gets the total count of permanently failed entries.
     *
     * @return count of permanently failed entries
     */
    @Transactional(readOnly = true)
    public long countPermanentlyFailed() {
        return countByStatus(EmbeddingRetryEntry.Status.PERMANENTLY_FAILED);
    }

    /**
     * Gets a retry entry by memory ID.
     *
     * @param memoryId the memory ID
     * @return the retry entry if found
     */
    @Transactional(readOnly = true)
    public Optional<EmbeddingRetryEntry> findByMemoryId(UUID memoryId) {
        return retryRepository.findByMemoryId(memoryId);
    }

    /**
     * Checks if a memory has a pending retry.
     *
     * @param memoryId the memory ID
     * @return true if the memory has a pending or processing retry entry
     */
    @Transactional(readOnly = true)
    public boolean hasPendingRetry(UUID memoryId) {
        Optional<EmbeddingRetryEntry> entry = retryRepository.findByMemoryId(memoryId);
        return entry.isPresent() && 
               (entry.get().getStatus() == EmbeddingRetryEntry.Status.PENDING ||
                entry.get().getStatus() == EmbeddingRetryEntry.Status.PROCESSING);
    }
}
