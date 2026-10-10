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

    /** 1 when this call created the (scope, key) row, 0 when it already existed - atomic, never a constraint error. */
    @Modifying
    @Query(value = "INSERT INTO tool_idempotency (id, scope, idempotency_key, status, created_at, completed_at, expires_at) "
            + "VALUES (:id, :scope, :key, :status, :now, CASE WHEN :status = 'IN_PROGRESS' THEN NULL ELSE CAST(:now AS timestamptz) END, :expires) "
            + "ON CONFLICT (scope, idempotency_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("scope") String scope, @Param("key") String key,
                       @Param("status") String status, @Param("now") Instant now, @Param("expires") Instant expires);

    /** Takes over an IN_PROGRESS claim taken before {@code staleBefore} (its worker died); 1 = taken over. */
    @Modifying
    @Query("update ToolIdempotency t set t.createdAt = :now where t.scope = :scope and t.idempotencyKey = :key "
            + "and t.status = 'IN_PROGRESS' and t.createdAt < :staleBefore")
    int takeOverStale(@Param("scope") String scope, @Param("key") String key, @Param("staleBefore") Instant staleBefore,
                      @Param("now") Instant now);

    @Modifying
    @Query("delete from ToolIdempotency t where t.scope = :scope and t.idempotencyKey = :key")
    int deleteKey(@Param("scope") String scope, @Param("key") String key);
}
