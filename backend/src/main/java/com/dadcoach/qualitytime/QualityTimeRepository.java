package com.dadcoach.qualitytime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link QualityTime} entities.
 * 
 * Provides methods for querying Quality Time events for workflow operations,
 * scheduling, and follow-up transitions.
 * 
 * Requirements: 3.4, 6.6, 12.4
 */
@Repository
public interface QualityTimeRepository extends JpaRepository<QualityTime, UUID> {

    /**
     * Find all Quality Time events for a specific father.
     * 
     * @param fatherId the father's internal database ID
     * @return list of Quality Time events ordered by scheduled start descending
     */
    List<QualityTime> findByFatherIdOrderByScheduledStartDesc(Long fatherId);

    /**
     * Find the next scheduled Quality Time for a father (earliest upcoming).
     * Used to show the next upcoming Quality Time on the dashboard.
     * 
     * Requirements: 6.6
     * 
     * @param fatherId the father's internal database ID
     * @return the next scheduled Quality Time, if any
     */
    Optional<QualityTime> findFirstByFatherIdAndStatusOrderByScheduledStartAsc(Long fatherId, QualityTimeStatus status);

    /**
     * Find Quality Time events by father ID and status.
     * 
     * @param fatherId the father's internal database ID
     * @param status the Quality Time status
     * @return list of matching Quality Time events
     */
    List<QualityTime> findByFatherIdAndStatus(Long fatherId, QualityTimeStatus status);

    /**
     * Find all scheduled Quality Time events for a father that have a Google Calendar event ID.
     * Used for syncing externally deleted calendar events (Requirement 3.7).
     * 
     * @param fatherId the father's internal database ID
     * @return list of scheduled Quality Time events with calendar event IDs
     */
    @Query("SELECT qt FROM QualityTime qt WHERE qt.fatherId = :fatherId " +
           "AND qt.status = 'SCHEDULED' " +
           "AND qt.googleCalendarEventId IS NOT NULL")
    List<QualityTime> findScheduledWithCalendarEventByFatherId(@Param("fatherId") Long fatherId);
}
