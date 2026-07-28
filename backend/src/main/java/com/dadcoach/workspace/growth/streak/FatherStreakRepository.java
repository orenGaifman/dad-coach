package com.dadcoach.workspace.growth.streak;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FatherStreak} entities.
 */
@Repository
public interface FatherStreakRepository extends JpaRepository<FatherStreak, UUID> {

    /**
     * Finds the streak record for a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return the streak record, or empty if not yet created
     */
    Optional<FatherStreak> findByFatherId(UUID fatherId);

    /**
     * Finds all streaks where the last qualifying date is before the given date.
     * Used by the daily streak reset job to identify expired streaks.
     *
     * @param date the cutoff date (exclusive — streaks not updated since before this date)
     * @return list of streaks that have not been updated since before the given date
     */
    @Query("SELECT fs FROM FatherStreak fs WHERE fs.lastQualifyingDate < :date AND fs.currentStreakDays > 0")
    List<FatherStreak> findStreaksNotUpdatedSince(@Param("date") LocalDate date);
}
