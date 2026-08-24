/**
 * Memory extraction components for the Memory & Knowledge System.
 *
 * <p>This package contains components responsible for:
 * <ul>
 *   <li>Duplicate detection before memory creation ({@link DuplicateDetector})</li>
 *   <li>Memory extraction from conversations (future: MemoryExtractionService)</li>
 *   <li>Validation of AI extraction recommendations (future: ExtractionValidator)</li>
 * </ul>
 *
 * <p>From SPEC-004 Requirements 3 and 9:
 * <ul>
 *   <li>Memory extraction processes conversation transcripts asynchronously</li>
 *   <li>Duplicate detection uses pgvector cosine similarity to identify semantic duplicates</li>
 *   <li>All AI recommendations are validated before persistence</li>
 * </ul>
 *
 * @see com.dadcoach.memory.extraction.DuplicateDetector
 * @see com.dadcoach.memory.extraction.DuplicateResult
 */
package com.dadcoach.memory.extraction;
