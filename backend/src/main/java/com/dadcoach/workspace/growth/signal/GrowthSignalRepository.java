package com.dadcoach.workspace.growth.signal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link GrowthSignal} entities.
 *
 * <p>Provides queries for signal retrieval, score aggregation, time-range filtering,
 * and duplicate detection. Growth signals are immutable (append-only), so only read
 * and insert operations are meaningful.</p>
 *
 * @see GrowthSignal
 * @see GrowthSignalType
 */
@Repository
public interface GrowthSignalRepository extends JpaRepository<GrowthSignal, UUID> {

    /**
     * Retrieves all growth signals for a father, ordered by most recent first.
     *
     * @param fatherId the father's unique identifier
     * @return list of signals ordered by creation timestamp descending
     */
    List<GrowthSignal> findByFatherIdOrderByCreatedAtDesc(UUID fatherId);

    /**
     * Retrieves growth signals for a father with pagination, ordered by most recent first.
     *
     * @param fatherId the father's unique identifier
     * @param pageable pagination parameters
     * @return paginated signals ordered by creation timestamp descending
     */
    Page<GrowthSignal> findByFatherIdOrderByCreatedAtDesc(UUID fatherId, Pageable pageable);

    /**
     * Counts the number of signals of a specific type recorded for a father.
     * Useful for achievement criteria evaluation (e.g., "Complete 10 missions").
     *
     * @param fatherId   the father's unique identifier
     * @param signalType the type of growth signal to count
     * @return the count of matching signals
     */
    long countByFatherIdAndSignalType(UUID fatherId, GrowthSignalType signalType);

    /**
     * Computes the total growth score for a father by summing all awarded points.
     * This is the authoritative score calculation (source of truth) per Design Decision AD-9.
     *
     * @param fatherId the father's unique identifier
     * @return the total points sum, or 0 if no signals exist
     */
    @Query("SELECT COALESCE(SUM(g.pointsAwarded), 0) FROM GrowthSignal g WHERE g.fatherId = :fatherId")
    int sumPointsByFatherId(@Param("fatherId") UUID fatherId);

    /**
     * Retrieves all growth signals for a father within a specific time range.
     * Used for weekly/monthly statistics and progress reporting.
     *
     * @param fatherId the father's unique identifier
     * @param from     the start of the time range (inclusive)
     * @param to       the end of the time range (exclusive)
     * @return list of signals within the specified time range
     */
    List<GrowthSignal> findByFatherIdAndCreatedAtBetween(UUID fatherId, Instant from, Instant to);

    /**
     * Checks whether a signal already exists for the given father, signal type, and source entity.
     * Used for duplicate detection to ensure idempotent signal recording (Requirement 11.6).
     *
     * @param fatherId       the father's unique identifier
     * @param signalType     the type of growth signal
     * @param sourceEntityId the source entity that triggered the signal
     * @return true if a matching signal already exists
     */
    boolean existsByFatherIdAndSignalTypeAndSourceEntityId(UUID fatherId,
                                                          GrowthSignalType signalType,
                                                          UUID sourceEntityId);
}
