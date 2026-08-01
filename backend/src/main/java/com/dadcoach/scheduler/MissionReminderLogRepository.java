package com.dadcoach.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for mission reminder log entries.
 */
@Repository
public interface MissionReminderLogRepository extends JpaRepository<MissionReminderLog, Long> {

    /**
     * Find reminder logs for a father, ordered by sent time descending.
     */
    List<MissionReminderLog> findByFatherIdOrderBySentAtDesc(Long fatherId);

    /**
     * Find reminder logs for a mission, ordered by sent time descending.
     */
    List<MissionReminderLog> findByMissionIdOrderBySentAtDesc(Long missionId);

    /**
     * Find all reminders of a specific type sent since a given time.
     */
    List<MissionReminderLog> findByReminderTypeAndSentAtAfter(String reminderType, Instant since);

    /**
     * Count reminders sent for a mission today.
     */
    long countByMissionIdAndSentAtAfter(Long missionId, Instant since);
}
