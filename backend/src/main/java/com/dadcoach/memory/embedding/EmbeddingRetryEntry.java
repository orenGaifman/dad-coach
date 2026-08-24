package com.dadcoach.memory.embedding;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a memory queued for embedding retry.
 *
 * <p>From SPEC-004 Design Document - Error Handling:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <p>This entity tracks:
 * <ul>
 *   <li>The memory ID that needs embedding</li>
 *   <li>Number of retry attempts made</li>
 *   <li>Timestamps for scheduling and tracking</li>
 *   <li>Error information from last failure</li>
 *   <li>Status (PENDING, PROCESSING, COMPLETED, PERMANENTLY_FAILED)</li>
 * </ul>
 *
 * <p><strong>Retry Schedule:</strong>
 * Attempts are spread over 24 hours using exponential backoff:
 * <ul>
 *   <li>Attempt 1: Immediate (when first queued)</li>
 *   <li>Attempt 2: After 4 hours</li>
 *   <li>Attempt 3: After 12 hours (from previous attempt)</li>
 * </ul>
 *
 * <p><strong>Validates: Task 9 - Retry queue: 3 attempts over 24 hours for failed embeddings</strong>
 *
 * @see EmbeddingRetryQueueService
 * @see EmbeddingRetryProcessor
 */
@Entity
@Table(name = "embedding_retry_queue",
        indexes = {
                @Index(name = "idx_embedding_retry_status_next_attempt", 
                       columnList = "status, next_attempt_at"),
                @Index(name = "idx_embedding_retry_memory", columnList = "memory_id")
        })
public class EmbeddingRetryEntry {

    /**
     * Maximum number of retry attempts before marking as permanently failed.
     */
    public static final int MAX_ATTEMPTS = 3;

    /**
     * Backoff delays in hours for retry attempts.
     * Attempt 1 → 2: 4 hours
     * Attempt 2 → 3: 12 hours
     * Total: ~16 hours spread, well within 24 hours
     */
    public static final int[] BACKOFF_HOURS = {0, 4, 12};

    /**
     * Status of the retry entry.
     */
    public enum Status {
        /**
         * Waiting to be processed.
         */
        PENDING,

        /**
         * Currently being processed by the retry job.
         */
        PROCESSING,

        /**
         * Embedding was successfully generated.
         */
        COMPLETED,

        /**
         * All retry attempts exhausted; embedding permanently failed.
         */
        PERMANENTLY_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The memory that needs embedding generation.
     */
    @NotNull
    @Column(name = "memory_id", nullable = false, unique = true)
    private UUID memoryId;

    /**
     * The memory content to embed (cached to avoid refetch).
     */
    @NotNull
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Current status of the retry entry.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status = Status.PENDING;

    /**
     * Number of retry attempts made (0-3).
     */
    @NotNull
    @Min(0)
    @Max(MAX_ATTEMPTS)
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /**
     * When the next retry attempt should be made.
     */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /**
     * When the last attempt was made.
     */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
     * Error type from the last failed attempt.
     */
    @Column(name = "last_error_type", length = 50)
    private String lastErrorType;

    /**
     * Error message from the last failed attempt.
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * When this entry was created.
     */
    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When this entry was last updated.
     */
    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─── Constructors ────────────────────────────────────────────────────

    /**
     * JPA requires a no-arg constructor.
     */
    protected EmbeddingRetryEntry() {
    }

    /**
     * Creates a new retry entry for a memory.
     *
     * @param memoryId the ID of the memory that needs embedding
     * @param content  the memory content to embed
     */
    public EmbeddingRetryEntry(UUID memoryId, String content) {
        this.memoryId = memoryId;
        this.content = content;
        this.status = Status.PENDING;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.nextAttemptAt = Instant.now(); // Ready for immediate first attempt
    }

    // ─── Business Methods ────────────────────────────────────────────────

    /**
     * Records a successful embedding generation.
     */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a failed attempt and schedules the next retry.
     *
     * @param errorType    the type of error that occurred
     * @param errorMessage the error message
     */
    public void recordFailure(String errorType, String errorMessage) {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.lastErrorType = errorType;
        this.lastErrorMessage = truncateErrorMessage(errorMessage);
        this.updatedAt = Instant.now();

        if (this.attemptCount >= MAX_ATTEMPTS) {
            this.status = Status.PERMANENTLY_FAILED;
            this.nextAttemptAt = null;
        } else {
            this.status = Status.PENDING;
            this.nextAttemptAt = calculateNextAttemptTime();
        }
    }

    /**
     * Marks the entry as currently being processed.
     */
    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Resets to pending if processing failed without recording an attempt.
     */
    public void resetToPending() {
        this.status = Status.PENDING;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if more retry attempts are available.
     *
     * @return true if retry attempts remain
     */
    public boolean canRetry() {
        return this.attemptCount < MAX_ATTEMPTS && 
               (this.status == Status.PENDING || this.status == Status.PROCESSING);
    }

    /**
     * Checks if the entry is ready for processing.
     *
     * @return true if the entry is pending and next attempt time has passed
     */
    public boolean isReadyForProcessing() {
        return this.status == Status.PENDING && 
               this.nextAttemptAt != null && 
               !this.nextAttemptAt.isAfter(Instant.now());
    }

    /**
     * Calculates the next attempt time based on exponential backoff.
     */
    private Instant calculateNextAttemptTime() {
        int backoffIndex = Math.min(this.attemptCount, BACKOFF_HOURS.length - 1);
        int hoursDelay = BACKOFF_HOURS[backoffIndex];
        return Instant.now().plusSeconds(hoursDelay * 3600L);
    }

    /**
     * Truncates error message to prevent database issues.
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) + "..." : message;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(UUID memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public String getLastErrorType() {
        return lastErrorType;
    }

    public void setLastErrorType(String lastErrorType) {
        this.lastErrorType = lastErrorType;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "EmbeddingRetryEntry{" +
                "id=" + id +
                ", memoryId=" + memoryId +
                ", status=" + status +
                ", attemptCount=" + attemptCount +
                ", nextAttemptAt=" + nextAttemptAt +
                '}';
    }
}
