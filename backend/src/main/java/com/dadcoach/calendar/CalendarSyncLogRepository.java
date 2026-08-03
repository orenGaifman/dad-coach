package com.dadcoach.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for calendar sync log entries.
 */
@Repository
public interface CalendarSyncLogRepository extends JpaRepository<CalendarSyncLog, Long> {

    /**
     * Find sync logs for a father, ordered by sync time descending.
     */
    List<CalendarSyncLog> findByFatherIdOrderBySyncedAtDesc(Long fatherId);

    /**
     * Find sync logs for a mission.
     */
    List<CalendarSyncLog> findByMissionIdOrderBySyncedAtDesc(Long missionId);

    /**
     * Find failed sync attempts since a given time.
     */
    List<CalendarSyncLog> findBySuccessAndSyncedAtAfter(Boolean success, Instant since);

    /**
     * Find the most recent successful calendar sync log.
     * Used by health indicator to check Google Calendar API status.
     * 
     * Requirements: 16.5 - Health endpoint reports Google Calendar API status
     * 
     * @return the most recent successful sync log, or empty if none exists
     */
    Optional<CalendarSyncLog> findTopBySuccessTrueOrderBySyncedAtDesc();
}
