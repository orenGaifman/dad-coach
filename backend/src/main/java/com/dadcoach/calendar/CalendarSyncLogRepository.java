package com.dadcoach.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

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
}
