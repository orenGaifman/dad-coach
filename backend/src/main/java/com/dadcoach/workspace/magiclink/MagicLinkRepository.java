package com.dadcoach.workspace.magiclink;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for magic link token persistence.
 */
@Repository
public interface MagicLinkRepository extends JpaRepository<MagicLink, UUID> {

    /**
     * Finds a magic link by its token.
     */
    Optional<MagicLink> findByToken(String token);

    /**
     * Finds the most recent valid (unconsumed, unexpired) token for a father.
     */
    @Query("""
        SELECT m FROM MagicLink m 
        WHERE m.fatherId = :fatherId 
          AND m.consumedAt IS NULL 
          AND m.expiresAt > :now 
        ORDER BY m.createdAt DESC 
        LIMIT 1
        """)
    Optional<MagicLink> findValidByFatherId(@Param("fatherId") Long fatherId, 
                                            @Param("now") Instant now);

    /**
     * Invalidates all existing tokens for a father (used before creating new token).
     */
    @Modifying
    @Query("""
        UPDATE MagicLink m 
        SET m.consumedAt = :now 
        WHERE m.fatherId = :fatherId 
          AND m.consumedAt IS NULL
        """)
    int invalidateAllForFather(@Param("fatherId") Long fatherId, 
                               @Param("now") Instant now);

    /**
     * Deletes expired tokens older than the retention period (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM MagicLink m WHERE m.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
