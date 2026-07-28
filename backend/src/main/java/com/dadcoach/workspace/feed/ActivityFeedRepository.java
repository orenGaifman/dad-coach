package com.dadcoach.workspace.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ActivityFeedItem} entities.
 *
 * <p>Provides cursor-based pagination via event_timestamp and purge support
 * for expired feed items (90-day retention).</p>
 */
@Repository
public interface ActivityFeedRepository extends JpaRepository<ActivityFeedItem, UUID> {

    /**
     * Finds feed items for a father with event_timestamp before the given cursor,
     * ordered by most recent first. Used for cursor-based pagination.
     *
     * @param fatherId the father's ID
     * @param cursor   the cursor timestamp (exclusive upper bound)
     * @param pageable page size control
     * @return list of feed items before the cursor
     */
    @Query("SELECT f FROM ActivityFeedItem f WHERE f.fatherId = :fatherId AND f.eventTimestamp < :cursor ORDER BY f.eventTimestamp DESC")
    List<ActivityFeedItem> findByFatherIdAndEventTimestampBefore(
            @Param("fatherId") UUID fatherId,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    /**
     * Finds the most recent feed items for a father, ordered by most recent first.
     * Used when no cursor is provided (first page).
     *
     * @param fatherId the father's ID
     * @param pageable page size control
     * @return list of most recent feed items
     */
    @Query("SELECT f FROM ActivityFeedItem f WHERE f.fatherId = :fatherId ORDER BY f.eventTimestamp DESC")
    List<ActivityFeedItem> findByFatherIdOrderByEventTimestampDesc(
            @Param("fatherId") UUID fatherId,
            Pageable pageable);

    /**
     * Deletes all feed items whose expiration timestamp is before the given time.
     * Used by the daily purge job to enforce 90-day retention.
     *
     * @param now the current time
     * @return number of deleted items
     */
    @Modifying
    @Query("DELETE FROM ActivityFeedItem f WHERE f.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
