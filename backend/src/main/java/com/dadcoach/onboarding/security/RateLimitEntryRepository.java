package com.dadcoach.onboarding.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for rate limit entries backed by the rate_limit_entries table.
 */
@Repository
public interface RateLimitEntryRepository extends JpaRepository<RateLimitEntry, UUID> {

    /**
     * Finds a rate limit entry for a given key in the current window.
     */
    Optional<RateLimitEntry> findByKeyTypeAndKeyValueAndWindowStart(
        String keyType, String keyValue, Instant windowStart);

    /**
     * Deletes expired rate limit entries (older than the given cutoff).
     */
    @Modifying
    @Query("DELETE FROM RateLimitEntry e WHERE e.windowStart < :cutoff")
    int deleteExpiredEntries(@Param("cutoff") Instant cutoff);
}
