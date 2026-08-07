package com.dadcoach.workspace.commitment;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing quality time commitments.
 * 
 * Handles:
 * - Creating commitments from WhatsApp conversations or dashboard
 * - Tracking completion
 * - Awarding points for completed commitments
 */
@Service
public class CommitmentService {

    private static final Logger log = LoggerFactory.getLogger(CommitmentService.class);
    private static final int COMMITMENT_POINTS = 15; // Points for completing a commitment

    private final QualityTimeCommitmentRepository repository;
    private final FatherRepository fatherRepository;
    private final Clock clock;

    public CommitmentService(QualityTimeCommitmentRepository repository,
                             FatherRepository fatherRepository,
                             Clock clock) {
        this.repository = repository;
        this.fatherRepository = fatherRepository;
        this.clock = clock;
    }

    /**
     * Creates a new quality time commitment.
     * 
     * @param fatherId The father making the commitment
     * @param childId Optional child ID (null if not specified)
     * @param scheduledAt When the quality time is scheduled (UTC)
     * @param activityType Type of activity (PLAY, HOMEWORK, etc.)
     * @param activityNote Free text description
     * @param createdVia How it was created (WHATSAPP, DASHBOARD)
     * @param conversationId Optional conversation that led to this commitment
     * @return The created commitment
     */
    @Transactional
    public QualityTimeCommitment createCommitment(Long fatherId, 
                                                   Long childId,
                                                   Instant scheduledAt,
                                                   String activityType,
                                                   String activityNote,
                                                   String createdVia,
                                                   UUID conversationId) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        // Convert to local date/time using father's timezone
        ZoneId zone = getTimezone(father);
        ZonedDateTime zdt = scheduledAt.atZone(zone);
        LocalDate localDate = zdt.toLocalDate();
        LocalTime localTime = zdt.toLocalTime();

        QualityTimeCommitment commitment = new QualityTimeCommitment(fatherId, scheduledAt, localDate, localTime);
        commitment.setChildId(childId);
        commitment.setActivityType(activityType);
        commitment.setActivityNote(activityNote);
        commitment.setCreatedVia(createdVia != null ? createdVia : "WHATSAPP");
        commitment.setConversationId(conversationId);

        QualityTimeCommitment saved = repository.save(commitment);
        log.info("Created commitment {} for father {} at {}", saved.getId(), fatherId, scheduledAt);

        return saved;
    }

    /**
     * Creates a commitment using day-of-week and time.
     * Useful when the AI extracts "יום ראשון ב-17:00" from conversation.
     */
    @Transactional
    public QualityTimeCommitment createCommitmentForDayAndTime(Long fatherId,
                                                                Long childId,
                                                                DayOfWeek dayOfWeek,
                                                                LocalTime time,
                                                                String activityNote,
                                                                UUID conversationId) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        ZoneId zone = getTimezone(father);
        LocalDate today = LocalDate.now(zone);
        
        // Find the next occurrence of the specified day
        LocalDate targetDate = today;
        while (targetDate.getDayOfWeek() != dayOfWeek) {
            targetDate = targetDate.plusDays(1);
        }
        
        // If today is the target day but the time has passed, schedule for next week
        if (targetDate.equals(today)) {
            LocalTime now = LocalTime.now(zone);
            if (time.isBefore(now)) {
                targetDate = targetDate.plusWeeks(1);
            }
        }

        Instant scheduledAt = ZonedDateTime.of(targetDate, time, zone).toInstant();
        
        return createCommitment(fatherId, childId, scheduledAt, null, activityNote, "WHATSAPP", conversationId);
    }

    /**
     * Marks a commitment as completed.
     */
    @Transactional
    public QualityTimeCommitment completeCommitment(Long commitmentId, String completionNote) {
        QualityTimeCommitment commitment = repository.findById(commitmentId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + commitmentId));

        if (commitment.getStatus() == QualityTimeCommitment.CommitmentStatus.COMPLETED) {
            log.warn("Commitment {} is already completed", commitmentId);
            return commitment;
        }

        commitment.markCompleted(completionNote, COMMITMENT_POINTS);
        repository.save(commitment);

        log.info("Completed commitment {} for father {}", commitmentId, commitment.getFatherId());
        return commitment;
    }

    /**
     * Cancels a commitment.
     */
    @Transactional
    public void cancelCommitment(Long commitmentId) {
        QualityTimeCommitment commitment = repository.findById(commitmentId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + commitmentId));
        
        commitment.cancel();
        repository.save(commitment);
        log.info("Cancelled commitment {} for father {}", commitmentId, commitment.getFatherId());
    }

    /**
     * Gets the next upcoming commitment for a father.
     */
    public Optional<QualityTimeCommitment> getNextCommitment(Long fatherId) {
        return repository.findNextUpcoming(fatherId, Instant.now(clock));
    }

    /**
     * Gets all upcoming commitments for a father.
     */
    public List<QualityTimeCommitment> getUpcomingCommitments(Long fatherId) {
        return repository.findUpcomingByFatherId(fatherId, Instant.now(clock));
    }

    /**
     * Gets all commitments for a father (for dashboard).
     */
    public List<QualityTimeCommitment> getAllCommitments(Long fatherId) {
        return repository.findByFatherIdOrderByScheduledAtDesc(fatherId);
    }

    /**
     * Gets commitment statistics for a father.
     */
    public CommitmentStats getStats(Long fatherId) {
        long completed = repository.countByFatherIdAndStatus(fatherId, QualityTimeCommitment.CommitmentStatus.COMPLETED);
        long scheduled = repository.countByFatherIdAndStatus(fatherId, QualityTimeCommitment.CommitmentStatus.SCHEDULED);
        long reminded = repository.countByFatherIdAndStatus(fatherId, QualityTimeCommitment.CommitmentStatus.REMINDED);
        long missed = repository.countByFatherIdAndStatus(fatherId, QualityTimeCommitment.CommitmentStatus.MISSED);
        
        return new CommitmentStats(completed, scheduled + reminded, missed);
    }

    private ZoneId getTimezone(Father father) {
        String tz = father.getTimezone();
        if (tz != null && !tz.isBlank()) {
            try {
                return ZoneId.of(tz);
            } catch (Exception e) {
                log.warn("Invalid timezone '{}' for father {}, using default", tz, father.getId());
            }
        }
        return ZoneId.of("Asia/Jerusalem");
    }

    /**
     * Statistics about a father's commitments.
     */
    public record CommitmentStats(long completed, long upcoming, long missed) {
        public long total() {
            return completed + upcoming + missed;
        }
        
        public double completionRate() {
            long total = completed + missed;
            return total > 0 ? (double) completed / total * 100 : 0;
        }
    }
}
