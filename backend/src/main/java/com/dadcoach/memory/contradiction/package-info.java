/**
 * Contradiction detection and resolution for the Memory System.
 *
 * <p>This package implements SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution):
 * <ul>
 *   <li>{@link com.dadcoach.memory.contradiction.ContradictionDetectionService} - Main service for
 *       detecting contradictions between memories of the same subject</li>
 *   <li>{@link com.dadcoach.memory.contradiction.Contradiction} - Record representing a detected
 *       contradiction pair with confidence score</li>
 *   <li>{@link com.dadcoach.memory.contradiction.ContradictionType} - Types of contradictions
 *       (negation, different value, mutually exclusive, semantic, explicit correction)</li>
 * </ul>
 *
 * <p>Contradiction detection workflow:
 * <ol>
 *   <li>Find existing memories with same subject (fatherId, childId, category, subjectType)</li>
 *   <li>Analyze content for contradiction indicators:
 *       <ul>
 *         <li>Negation patterns ("likes" vs "doesn't like")</li>
 *         <li>Different values (times, ages, quantities)</li>
 *         <li>Semantic conflicts (via embeddings)</li>
 *         <li>Explicit corrections ("actually", "I was wrong")</li>
 *       </ul>
 *   </li>
 *   <li>Return contradictions with confidence scores for resolution</li>
 * </ol>
 *
 * @see com.dadcoach.memory.MemoryRepository#findForContradictionDetection
 */
package com.dadcoach.memory.contradiction;
