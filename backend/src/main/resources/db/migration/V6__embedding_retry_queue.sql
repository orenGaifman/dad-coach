-- V3__embedding_retry_queue.sql
-- Embedding retry queue for failed embedding generation
-- SPEC-004: Memory stored without embedding on failure; queue retry (3 attempts / 24h)

CREATE TABLE embedding_retry_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id           UUID NOT NULL UNIQUE,
    content             TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0 AND attempt_count <= 3),
    next_attempt_at     TIMESTAMPTZ,
    last_attempt_at     TIMESTAMPTZ,
    last_error_type     VARCHAR(50),
    last_error_message  TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Constraint: status must be one of the defined values
    CONSTRAINT chk_embedding_retry_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'PERMANENTLY_FAILED')
    )
);

-- Index for finding entries ready for processing
-- Used by: EmbeddingRetryRepository.findReadyForProcessingNative()
CREATE INDEX idx_embedding_retry_status_next_attempt 
    ON embedding_retry_queue(status, next_attempt_at)
    WHERE status = 'PENDING';

-- Index for looking up by memory ID
-- Used by: EmbeddingRetryRepository.findByMemoryId()
CREATE INDEX idx_embedding_retry_memory 
    ON embedding_retry_queue(memory_id);

-- Index for cleanup queries
-- Used by: deleteCompletedOlderThan, deleteFailedOlderThan
CREATE INDEX idx_embedding_retry_status_updated 
    ON embedding_retry_queue(status, updated_at);

COMMENT ON TABLE embedding_retry_queue IS 
    'Queue for retrying failed embedding generation. Max 3 attempts over 24 hours.';

COMMENT ON COLUMN embedding_retry_queue.memory_id IS 
    'References memories.id - the memory that needs embedding';

COMMENT ON COLUMN embedding_retry_queue.status IS 
    'PENDING=waiting, PROCESSING=in progress, COMPLETED=success, PERMANENTLY_FAILED=max attempts reached';

COMMENT ON COLUMN embedding_retry_queue.attempt_count IS 
    'Number of attempts made (0-3). After 3 failed attempts, status becomes PERMANENTLY_FAILED';

COMMENT ON COLUMN embedding_retry_queue.next_attempt_at IS 
    'When the next retry should be attempted. Backoff: 0h -> 4h -> 12h';
