package com.dadcoach.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolIdempotencyRepository extends JpaRepository<ToolIdempotency, UUID> {

    Optional<ToolIdempotency> findByScopeAndIdempotencyKey(String scope, String key);

    @Modifying
    @Query("delete from ToolIdempotency t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
