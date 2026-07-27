package com.dadcoach.domain.father;

import com.dadcoach.father.FatherStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Father} entities.
 */
@Repository
public interface FatherRepository extends JpaRepository<Father, Long> {

    /**
     * Find a father by phone number.
     */
    Optional<Father> findByPhone(String phone);

    /**
     * Find all fathers with a given status.
     */
    List<Father> findByStatus(FatherStatus status);

    /**
     * Find all fathers with status ACTIVE whose lastInteractionAt is before the given timestamp.
     * Useful for detecting inactive fathers who may need re-engagement.
     */
    @Query("SELECT f FROM Father f WHERE f.status = 'ACTIVE' AND f.lastInteractionAt < :since")
    List<Father> findInactiveSince(@Param("since") Instant since);

    /**
     * Alternative Spring Data query derivation for finding inactive fathers.
     * Equivalent to {@link #findInactiveSince(Instant)} but uses method-name-based query generation.
     */
    List<Father> findByStatusAndLastInteractionAtBefore(FatherStatus status, Instant since);
}
