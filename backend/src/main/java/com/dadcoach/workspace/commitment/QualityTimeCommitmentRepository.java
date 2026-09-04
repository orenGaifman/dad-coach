package com.dadcoach.workspace.commitment;

import com.dadcoach.workspace.commitment.QualityTimeCommitment.CommitmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for quality time commitments.
 */
@Repository
public interface QualityTimeCommitmentRepository extends JpaRepository<QualityTimeCommitment, Long> {

    /**
     * Find all commitments for a father.
     */
    List<QualityTimeCommitment> findByFatherIdOrderByScheduledAtDesc(Long fatherId);

    /**
     * Find upcoming commitments for a father.
     */
    @Query("SELECT c FROM QualityTimeCommitment c WHERE c.fatherId = :fatherId " +
           "AND c.status IN ('SCHEDULED', 'REMINDED') " +
           "AND c.scheduledAt > :now ORDER BY c.scheduledAt ASC")
    List<QualityTimeCommitment> findUpcomingByFatherId(@Param("fatherId") Long fatherId, 
                                                        @Param("now") Instant now);

    /**
     * Find the next upcoming commitment for a father.
     */
    @Query("SELECT c FROM QualityTimeCommitment c WHERE c.fatherId = :fatherId " +
           "AND c.status IN ('SCHEDULED', 'REMINDED') " +
           "AND c.scheduledAt > :now ORDER BY c.scheduledAt ASC LIMIT 1")
    Optional<QualityTimeCommitment> findNextUpcoming(@Param("fatherId") Long fatherId, 
                                                      @Param("now") Instant now);

    /**
     * Find commitments that need reminders (scheduled within the reminder window).
     */
    @Query("SELECT c FROM QualityTimeCommitment c WHERE c.status = 'SCHEDULED' " +
           "AND c.scheduledAt > :now " +
           "AND c.scheduledAt <= :reminderWindowEnd")
    List<QualityTimeCommitment> findCommitmentsNeedingReminder(@Param("now") Instant now,
                                                                @Param("reminderWindowEnd") Instant reminderWindowEnd);

    /**
     * Find commitments that are past due (missed).
     */
    @Query("SELECT c FROM QualityTimeCommitment c WHERE c.status IN ('SCHEDULED', 'REMINDED') " +
           "AND c.scheduledAt < :now")
    List<QualityTimeCommitment> findPastDueCommitments(@Param("now") Instant now);

    /**
     * Find commitments for a specific date.
     */
    List<QualityTimeCommitment> findByFatherIdAndScheduledDate(Long fatherId, LocalDate date);

    /**
     * Count completed commitments for a father.
     */
    @Query("SELECT COUNT(c) FROM QualityTimeCommitment c WHERE c.fatherId = :fatherId " +
           "AND c.status = 'COMPLETED'")
    long countCompletedByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Count commitments by status for a father.
     */
    long countByFatherIdAndStatus(Long fatherId, CommitmentStatus status);

    /**
     * Find recent completed commitments for activity feed.
     */
    @Query("SELECT c FROM QualityTimeCommitment c WHERE c.fatherId = :fatherId " +
           "AND c.status = 'COMPLETED' ORDER BY c.completedAt DESC LIMIT :limit")
    List<QualityTimeCommitment> findRecentCompleted(@Param("fatherId") Long fatherId, 
                                                     @Param("limit") int limit);

    /**
     * Delete all commitments for a father.
     * This must be called before deleting children due to FK constraint.
     */
    void deleteByFatherId(Long fatherId);
}
