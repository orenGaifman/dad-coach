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

    /** Marks the link used only if it is unused and unexpired; 1 = this caller consumed it, 0 = invalid. */
    @Modifying
    @Query("update LoginLink l set l.usedAt = :now where l.tokenHash = :hash and l.usedAt is null and l.expiresAt > :now")
    int consumeIfValid(@Param("hash") String tokenHash, @Param("now") Instant now);
}
