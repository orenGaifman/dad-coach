package com.dadcoach.workspace.growth.celebration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing {@link CelebrationEvent} persistence.
 */
@Repository
public interface CelebrationEventRepository extends JpaRepository<CelebrationEvent, UUID> {

    /**
     * Finds all undisplayed celebration events for a father.
     *
     * @param fatherId the father's unique identifier
     * @return list of celebration events not yet shown to the father
     */
    List<CelebrationEvent> findByFatherIdAndDisplayedFalse(UUID fatherId);

    /**
     * Finds celebration events for a father created after a given timestamp.
     *
     * @param fatherId  the father's unique identifier
     * @param createdAt the timestamp cutoff (exclusive)
     * @return list of celebration events created after the given time
     */
    List<CelebrationEvent> findByFatherIdAndCreatedAtAfter(UUID fatherId, Instant createdAt);
}
