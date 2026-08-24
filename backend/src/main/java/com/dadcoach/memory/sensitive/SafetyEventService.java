package com.dadcoach.memory.sensitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for recording and managing safety events.
 *
 * <p>From SPEC-004 design, safety events are stored separately from normal memories
 * with long retention for legal/compliance reasons.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Record safety events with proper metadata</li>
 *   <li>Support review workflow for safety personnel</li>
 *   <li>Query safety events for support use cases</li>
 *   <li>Ensure safety events are NEVER mixed with normal memory retrieval</li>
 *   <li>Safety events are NOT deleted during GDPR erasure (retained for legal compliance)</li>
 * </ul>
 *
 * @see SafetyEventRecord
 * @see SafetyEventRepository
 */
@Service
@Transactional
public class SafetyEventService {

    private static final Logger log = LoggerFactory.getLogger(SafetyEventService.class);

    private final SafetyEventRepository safetyEventRepository;

    public SafetyEventService(SafetyEventRepository safetyEventRepository) {
        this.safetyEventRepository = safetyEventRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Record Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Records a new safety event with a brief summary.
     *
     * @param fatherId  the father associated with this event
     * @param eventType the type of safety event
     * @param severity  the severity level
     * @param summary   brief summary of the event (≤100 chars as per SPEC-004 Task 12.2)
     * @return the created safety event record
     */
    public SafetyEventRecord recordEvent(UUID fatherId, SafetyEventType eventType,
                                         SafetyEventSeverity severity, String summary) {
        SafetyEventRecord event = new SafetyEventRecord(fatherId, eventType, severity, summary);
        SafetyEventRecord saved = safetyEventRepository.save(event);

        log.info("Recorded safety event: type={}, severity={}, fatherId={}, eventId={}",
                eventType, severity, fatherId, saved.getId());

        return saved;
    }

    /**
     * Records a safety event with summary and detailed description.
     *
     * @param fatherId    the father associated with this event
     * @param eventType   the type of safety event
     * @param severity    the severity level
     * @param summary     brief summary of the event (≤100 chars)
     * @param description detailed description of the event (≤500 chars)
     * @return the created safety event record
     */
    public SafetyEventRecord recordEventWithDescription(UUID fatherId, SafetyEventType eventType,
                                                        SafetyEventSeverity severity, String summary,
                                                        String description) {
        SafetyEventRecord event = new SafetyEventRecord(fatherId, eventType, severity, summary, description);
        SafetyEventRecord saved = safetyEventRepository.save(event);

        log.info("Recorded safety event with description: type={}, severity={}, fatherId={}, eventId={}",
                eventType, severity, fatherId, saved.getId());

        return saved;
    }

    /**
     * Records a safety event with conversation context.
     *
     * @param fatherId       the father associated with this event
     * @param eventType      the type of safety event
     * @param severity       the severity level
     * @param summary        brief summary of the event (≤100 chars)
     * @param conversationId the conversation where the event was detected
     * @return the created safety event record
     */
    public SafetyEventRecord recordEventFromConversation(UUID fatherId, SafetyEventType eventType,
                                                         SafetyEventSeverity severity, String summary,
                                                         UUID conversationId) {
        SafetyEventRecord event = new SafetyEventRecord(fatherId, eventType, severity, summary);
        event.setConversationId(conversationId);
        SafetyEventRecord saved = safetyEventRepository.save(event);

        log.info("Recorded safety event from conversation: type={}, severity={}, " +
                        "fatherId={}, conversationId={}, eventId={}",
                eventType, severity, fatherId, conversationId, saved.getId());

        return saved;
    }

    /**
     * Records a safety event with full context including metadata.
     *
     * @param fatherId       the father associated with this event
     * @param eventType      the type of safety event
     * @param severity       the severity level
     * @param summary        brief summary of the event (≤100 chars)
     * @param conversationId optional conversation context
     * @param memoryId       optional related memory
     * @param metadata       optional additional context
     * @return the created safety event record
     */
    public SafetyEventRecord recordEventWithContext(UUID fatherId, SafetyEventType eventType,
                                                    SafetyEventSeverity severity, String summary,
                                                    UUID conversationId, UUID memoryId,
                                                    Map<String, Object> metadata) {
        SafetyEventRecord event = new SafetyEventRecord(fatherId, eventType, severity, summary);
        event.setConversationId(conversationId);
        event.setMemoryId(memoryId);
        event.setMetadata(metadata);
        SafetyEventRecord saved = safetyEventRepository.save(event);

        log.info("Recorded safety event with context: type={}, severity={}, " +
                        "fatherId={}, conversationId={}, memoryId={}, eventId={}",
                eventType, severity, fatherId, conversationId, memoryId, saved.getId());

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Review Workflow
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Marks a safety event as reviewed.
     *
     * @param eventId    the event to mark as reviewed
     * @param reviewerId the ID of the reviewer
     * @param notes      optional review notes
     * @return the updated event, or empty if not found
     */
    public Optional<SafetyEventRecord> markAsReviewed(UUID eventId, UUID reviewerId, String notes) {
        return safetyEventRepository.findById(eventId)
                .map(event -> {
                    event.markReviewed(reviewerId, notes);
                    SafetyEventRecord saved = safetyEventRepository.save(event);
                    log.info("Safety event reviewed: eventId={}, reviewerId={}", eventId, reviewerId);
                    return saved;
                });
    }

    /**
     * Flags a safety event for re-review (e.g., after escalation).
     *
     * @param eventId the event to flag
     * @return the updated event, or empty if not found
     */
    public Optional<SafetyEventRecord> flagForReview(UUID eventId) {
        return safetyEventRepository.findById(eventId)
                .map(event -> {
                    event.flagForReview();
                    SafetyEventRecord saved = safetyEventRepository.save(event);
                    log.info("Safety event flagged for re-review: eventId={}", eventId);
                    return saved;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets a safety event by ID.
     *
     * @param eventId the event ID
     * @return the event if found
     */
    @Transactional(readOnly = true)
    public Optional<SafetyEventRecord> getById(UUID eventId) {
        return safetyEventRepository.findById(eventId);
    }

    /**
     * Gets all safety events for a father.
     * Used for support use cases.
     *
     * @param fatherId the father's ID
     * @return list of safety events for the father
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getByFatherId(UUID fatherId) {
        return safetyEventRepository.findByFatherIdOrderByCreatedAtDesc(fatherId);
    }

    /**
     * Gets safety events for a father within a time range.
     *
     * @param fatherId  the father's ID
     * @param startTime start of the time range
     * @param endTime   end of the time range
     * @return list of safety events within the range
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getByFatherIdAndTimeRange(UUID fatherId, Instant startTime, Instant endTime) {
        return safetyEventRepository.findByFatherIdAndTimeRange(fatherId, startTime, endTime);
    }

    /**
     * Gets all events requiring review, ordered by priority.
     *
     * @return list of events requiring review
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getAllRequiringReview() {
        return safetyEventRepository.findAllRequiringReview();
    }

    /**
     * Gets high-priority events (HIGH or CRITICAL) requiring review.
     *
     * @return list of high-priority events requiring review
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getHighPriorityRequiringReview() {
        return safetyEventRepository.findRequiringReviewBySeverityAtLeast(SafetyEventSeverity.HIGH);
    }

    /**
     * Gets events requiring review for a specific father.
     *
     * @param fatherId the father's ID
     * @return list of pending reviews for the father
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getPendingReviewsForFather(UUID fatherId) {
        return safetyEventRepository.findByFatherIdAndRequiresReviewTrueOrderBySeverityDescCreatedAtAsc(fatherId);
    }

    /**
     * Gets safety events from a specific conversation.
     *
     * @param conversationId the conversation ID
     * @return list of safety events from that conversation
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getByConversationId(UUID conversationId) {
        return safetyEventRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
    }

    /**
     * Gets safety events linked to a specific memory.
     *
     * @param memoryId the memory ID
     * @return list of safety events linked to that memory
     */
    @Transactional(readOnly = true)
    public List<SafetyEventRecord> getByMemoryId(UUID memoryId) {
        return safetyEventRepository.findByMemoryIdOrderByCreatedAtDesc(memoryId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the count of safety events for a father.
     *
     * @param fatherId the father's ID
     * @return count of safety events
     */
    @Transactional(readOnly = true)
    public long countByFatherId(UUID fatherId) {
        return safetyEventRepository.countByFatherId(fatherId);
    }

    /**
     * Gets the count of events requiring review.
     *
     * @return count of pending reviews
     */
    @Transactional(readOnly = true)
    public long countRequiringReview() {
        return safetyEventRepository.countByRequiresReviewTrue();
    }

    /**
     * Gets the count of high-priority events requiring review.
     *
     * @return count of high-priority pending reviews
     */
    @Transactional(readOnly = true)
    public long countHighPriorityRequiringReview() {
        return safetyEventRepository.countRequiringReviewBySeverityAtLeast(SafetyEventSeverity.HIGH);
    }

    /**
     * Checks if a father has any safety events.
     *
     * @param fatherId the father's ID
     * @return true if the father has any safety events
     */
    @Transactional(readOnly = true)
    public boolean hasSafetyEvents(UUID fatherId) {
        return safetyEventRepository.existsByFatherId(fatherId);
    }

    /**
     * Checks if a father has any unreviewed safety events.
     *
     * @param fatherId the father's ID
     * @return true if the father has pending reviews
     */
    @Transactional(readOnly = true)
    public boolean hasUnreviewedEvents(UUID fatherId) {
        return safetyEventRepository.existsByFatherIdAndRequiresReviewTrue(fatherId);
    }
}
