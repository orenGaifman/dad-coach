package com.dadcoach.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginLinkRepository extends JpaRepository<LoginLink, UUID> {

    Optional<LoginLink> findByTokenHash(String tokenHash);

    /** Counts one use if the link is unrevoked and unexpired (D-027: reusable); 1 = valid, 0 = invalid. */
    @Modifying
    @Query("update LoginLink l set l.usedAt = coalesce(l.usedAt, :now), l.lastUsedAt = :now, l.useCount = l.useCount + 1 "
            + "where l.tokenHash = :hash and l.revokedAt is null and l.expiresAt > :now")
    int useIfValid(@Param("hash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("update LoginLink l set l.revokedAt = :now, l.revokedReason = :reason "
            + "where l.revokedAt is null and l.fatherId = :fatherId")
    int revokeAllForFather(@Param("fatherId") Long fatherId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update LoginLink l set l.revokedAt = :now, l.revokedReason = :reason "
            + "where l.revokedAt is null and l.staffUserId = :staffUserId")
    int revokeAllForStaff(@Param("staffUserId") UUID staffUserId, @Param("now") Instant now, @Param("reason") String reason);

    /** A button to his page went out to him since then (delivery SENT) - the dashboard card of this turn. */
    @Query("select count(l) > 0 from LoginLink l where l.fatherId = :fatherId and l.createdAt >= :since and l.deliveryStatus = 'SENT'")
    boolean sentToFatherSince(@Param("fatherId") Long fatherId, @Param("since") Instant since);
}
