package com.dadcoach.domain.mission;

import com.dadcoach.mission.MissionStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Mission} entities.
 * Provides complex queries for:
 * <ul>
 *   <li>Active missions by child (single-active constraint enforcement)</li>
 *   <li>Recent missions by category (non-repetition rule)</li>
 *   <li>Equitable distribution counts</li>
 *   <li>Completion stats and metrics</li>
 * </ul>
 *
 * Indexes leveraged:
 * <ul>
 *   <li>idx_mission_father_status ON mission(father_id, status)</li>
 *   <li>idx_mission_child ON mission(child_id)</li>
 *   <li>idx_mission_father_assigned ON mission(father_id, assigned_at)</li>
 * </ul>
 */
@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    // ─── Active Mission Queries (Single-Active Constraint) ────────────────

    /**
     * Find all active missions for a child.
     * Active states: ASSIGNED, ACCEPTED, IN_PROGRESS.
     * Uses SELECT FOR UPDATE to prevent concurrent assignment of multiple active missions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.ASSIGNED, " +
           "com.dadcoach.mission.MissionStatus.ACCEPTED, " +
           "com.dadcoach.mission.MissionStatus.IN_PROGRESS)")
    List<Mission> findActiveMissionsByChildIdForUpdate(@Param("childId") Long childId);

    /**
     * Find all active missions for a child (without locking, for read-only checks).
     * Active states: ASSIGNED, ACCEPTED, IN_PROGRESS.
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.ASSIGNED, " +
           "com.dadcoach.mission.MissionStatus.ACCEPTED, " +
           "com.dadcoach.mission.MissionStatus.IN_PROGRESS)")
    List<Mission> findActiveMissionsByChildId(@Param("childId") Long childId);

    /**
     * Count active missions for a child.
     * Used to enforce the single-active-mission-per-child business rule.
     */
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.childId = :childId " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.ASSIGNED, " +
           "com.dadcoach.mission.MissionStatus.ACCEPTED, " +
           "com.dadcoach.mission.MissionStatus.IN_PROGRESS)")
    long countActiveMissionsByChildId(@Param("childId") Long childId);

    // ─── Category Non-Repetition Queries ──────────────────────────────────

    /**
     * Find recent missions for a child in a specific category since a given time.
     * Used to enforce the max-2-per-category-per-7-day-window business rule.
     *
     * @param childId  the child ID
     * @param category the mission category
     * @param since    the start of the time window (e.g., 7 days ago)
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.category = :category AND m.assignedAt >= :since " +
           "ORDER BY m.assignedAt DESC")
    List<Mission> findRecentByChildIdAndCategory(@Param("childId") Long childId,
                                                 @Param("category") String category,
                                                 @Param("since") Instant since);

    /**
     * Count missions in a specific category for a child since a given time.
     * Convenience method for non-repetition check without loading entities.
     */
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.childId = :childId " +
           "AND m.category = :category AND m.assignedAt >= :since")
    long countByChildIdAndCategorySince(@Param("childId") Long childId,
                                       @Param("category") String category,
                                       @Param("since") Instant since);

    /**
     * Find all recent missions for a child since a given time, ordered by assignedAt descending.
     * Used for difficulty adaptation (checking consecutive skips) and child selection (last mission time).
     *
     * @param childId the child ID
     * @param since   the start of the time window
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.assignedAt >= :since " +
           "ORDER BY m.assignedAt DESC")
    List<Mission> findRecentByChildIdSince(@Param("childId") Long childId,
                                          @Param("since") Instant since);

    /**
     * Find the most recent mission for a child (regardless of status).
     * Used for child selection tiebreaker (longest since last mission).
     *
     * @param childId the child ID
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "ORDER BY m.assignedAt DESC LIMIT 1")
    List<Mission> findMostRecentByChildId(@Param("childId") Long childId);

    // ─── Equitable Distribution Queries ───────────────────────────────────

    /**
     * Count all missions assigned to a child since a given time.
     * Used for equitable distribution checks across siblings.
     *
     * @param childId the child ID
     * @param since   the start of the time window (e.g., 7 days ago)
     */
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.childId = :childId " +
           "AND m.assignedAt >= :since")
    long countMissionsByChildIdSince(@Param("childId") Long childId,
                                    @Param("since") Instant since);

    // ─── Completion Stats and Metrics ─────────────────────────────────────

    /**
     * Find completed missions for a father since a given time.
     * Used for metrics (completion rate, average outcome ratings).
     *
     * @param fatherId the father ID
     * @param since    the start of the time window
     */
    @Query("SELECT m FROM Mission m WHERE m.fatherId = :fatherId " +
           "AND m.status = com.dadcoach.mission.MissionStatus.COMPLETED " +
           "AND m.completedAt >= :since " +
           "ORDER BY m.completedAt DESC")
    List<Mission> findCompletedByFatherIdSince(@Param("fatherId") Long fatherId,
                                              @Param("since") Instant since);

    /**
     * Count missions for a father with a specific status since a given time.
     * General-purpose stats query (e.g., count ASSIGNED, COMPLETED, SKIPPED in a window).
     *
     * @param fatherId the father ID
     * @param status   the mission status to count
     * @param since    the start of the time window
     */
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.fatherId = :fatherId " +
           "AND m.status = :status AND m.assignedAt >= :since")
    long countByFatherIdAndStatusSince(@Param("fatherId") Long fatherId,
                                      @Param("status") MissionStatus status,
                                      @Param("since") Instant since);

    // ─── Father Mission History ───────────────────────────────────────────

    /**
     * Find recent missions for a father ordered by assigned_at descending.
     * Used for displaying mission history and difficulty adaptation.
     */
    List<Mission> findByFatherIdOrderByAssignedAtDesc(Long fatherId);

    /**
     * Find recent missions for a father with pagination support.
     * Leverages the idx_mission_father_assigned index.
     */
    @Query("SELECT m FROM Mission m WHERE m.fatherId = :fatherId " +
           "ORDER BY m.assignedAt DESC LIMIT :limit")
    List<Mission> findRecentByFatherId(@Param("fatherId") Long fatherId,
                                      @Param("limit") int limit);

    // ─── Difficulty Adaptation Queries ────────────────────────────────────

    /**
     * Find recent completed or reflected missions for a child, ordered by completion time.
     * Used for difficulty adaptation logic (check recent outcome ratings).
     *
     * @param childId the child ID
     * @param limit   max number of results
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.COMPLETED, " +
           "com.dadcoach.mission.MissionStatus.REFLECTED) " +
           "ORDER BY m.completedAt DESC LIMIT :limit")
    List<Mission> findRecentCompletedByChildId(@Param("childId") Long childId,
                                              @Param("limit") int limit);

    /**
     * Count consecutive skipped or expired missions for a child (most recent first).
     * Used for difficulty adaptation: after 3 consecutive skips/expired, difficulty decreases.
     */
    @Query("SELECT m FROM Mission m WHERE m.childId = :childId " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.SKIPPED, " +
           "com.dadcoach.mission.MissionStatus.EXPIRED) " +
           "AND m.assignedAt >= :since " +
           "ORDER BY m.assignedAt DESC")
    List<Mission> findRecentSkippedOrExpiredByChildId(@Param("childId") Long childId,
                                                     @Param("since") Instant since);

    // ─── Expiration Queries ───────────────────────────────────────────────

    /**
     * Find missions that are past their expiration time and still in an expirable state.
     * Used by the scheduled expiration job.
     */
    @Query("SELECT m FROM Mission m WHERE m.expiresAt < :now " +
           "AND m.status IN (com.dadcoach.mission.MissionStatus.ASSIGNED, " +
           "com.dadcoach.mission.MissionStatus.ACCEPTED)")
    List<Mission> findExpiredMissions(@Param("now") Instant now);
}
