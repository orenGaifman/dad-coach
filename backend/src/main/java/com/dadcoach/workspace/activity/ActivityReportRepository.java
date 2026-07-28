package com.dadcoach.workspace.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ActivityReport} entities.
 */
@Repository
public interface ActivityReportRepository extends JpaRepository<ActivityReport, UUID> {

    /**
     * Finds all activity reports for a father on a specific date.
     */
    List<ActivityReport> findByFatherIdAndActivityDate(UUID fatherId, LocalDate activityDate);

    /**
     * Counts reports by father, report type, and date — used for rate limiting.
     */
    long countByFatherIdAndReportTypeAndActivityDate(UUID fatherId, String reportType, LocalDate activityDate);

    /**
     * Checks for duplicate quality time reports based on the combination of
     * father, child, duration, and activity date.
     */
    boolean existsByFatherIdAndChildIdAndDurationMinutesAndActivityDate(
            UUID fatherId, UUID childId, Integer durationMinutes, LocalDate activityDate);
}
