/**
 * Embedding generation services for the Memory & Knowledge System.
 *
 * <p>This package provides vector embedding generation capabilities using OpenAI's
 * text-embedding-ada-002 model. Embeddings are 1536-dimension vectors used for:
 * <ul>
 *   <li>Semantic similarity search in memory retrieval</li>
 *   <li>Duplicate detection before memory creation</li>
 *   <li>Memory consolidation similarity matching</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.dadcoach.memory.embedding.EmbeddingService} - Core service for generating embeddings</li>
 *   <li>{@link com.dadcoach.memory.embedding.GracefulEmbeddingService} - Graceful degradation wrapper</li>
 *   <li>{@link com.dadcoach.memory.embedding.EmbeddingException} - Exception for embedding failures</li>
 *   <li>{@link com.dadcoach.memory.embedding.EmbeddingRetryEntry} - Entity for retry queue entries</li>
 *   <li>{@link com.dadcoach.memory.embedding.EmbeddingRetryQueueService} - Retry queue management</li>
 *   <li>{@link com.dadcoach.memory.embedding.EmbeddingRetryProcessor} - Scheduled retry processor</li>
 * </ul>
 *
 * <h2>Design References</h2>
 * <p>SPEC-004 Design Document:
 * <pre>
 * embedding/
 * ├── EmbeddingService.java           # Generates embeddings via AI provider
 * └── EmbeddingQueue.java             # Retry queue for failed embeddings
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <p>From SPEC-004 Error Handling table:
 * <blockquote>
 * Embedding generation fails → Store memory without embedding; queue retry (3 attempts / 24h);
 * exclude from similarity search until embedded
 * </blockquote>
 *
 * <h2>Retry Queue</h2>
 * <p>When embedding generation fails, memories are queued for retry with the following schedule:
 * <ul>
 *   <li>Attempt 1: Immediate</li>
 *   <li>Attempt 2: After 4 hours</li>
 *   <li>Attempt 3: After 12 hours</li>
 * </ul>
 * <p>After 3 failed attempts, the memory is marked as permanently failed and excluded
 * from similarity search indefinitely.
 *
 * @see com.dadcoach.memory.Memory#EMBEDDING_DIMENSION
 * @see com.dadcoach.memory.extraction.DuplicateDetector
 */
package com.dadcoach.memory.embedding;
