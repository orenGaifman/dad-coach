package com.dadcoach.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardSessionRepository extends JpaRepository<DashboardSession, UUID> {

    Optional<DashboardSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update DashboardSession s set s.revokedAt = :now, s.revokedReason = :reason "
            + "where s.revokedAt is null and s.fatherId = :fatherId")
    int revokeAllForFather(@Param("fatherId") Long fatherId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update DashboardSession s set s.revokedAt = :now, s.revokedReason = :reason "
            + "where s.revokedAt is null and s.staffUserId = :staffUserId")
    int revokeAllForStaff(@Param("staffUserId") UUID staffUserId, @Param("now") Instant now, @Param("reason") String reason);

    /** Housekeeping on sign-in: idle-expired sessions, and revoked ones past their audit retention. */
    @Modifying
    @Query("delete from DashboardSession s where s.fatherId = :fatherId "
            + "and (s.idleExpiresAt < :now or (s.revokedAt is not null and s.revokedAt < :revokedBefore))")
    int deleteDeadForFather(@Param("fatherId") Long fatherId, @Param("now") Instant now,
                            @Param("revokedBefore") Instant revokedBefore);

    @Modifying
    @Query("delete from DashboardSession s where s.staffUserId = :staffUserId "
            + "and (s.idleExpiresAt < :now or (s.revokedAt is not null and s.revokedAt < :revokedBefore))")
    int deleteDeadForStaff(@Param("staffUserId") UUID staffUserId, @Param("now") Instant now,
                           @Param("revokedBefore") Instant revokedBefore);
}
