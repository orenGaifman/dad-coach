package com.dadcoach.api.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting API audit entries.
 * <p>
 * This repository is intentionally append-only: it provides only save and read
 * operations. No update or delete methods are exposed, ensuring the audit trail
 * remains immutable once written.
 * <p>
 * Custom query methods support operational queries (by actor, by resource, by time range)
 * for admin dashboards and compliance reporting.
 */
@Repository
public interface ApiAuditRepository extends JpaRepository<ApiAuditEntry, UUID> {

    /**
     * Find audit entries by actor ID, ordered by most recent first.
     */
    List<ApiAuditEntry> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    /**
     * Find audit entries by resource type and resource ID, ordered by most recent first.
     */
    List<ApiAuditEntry> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType, UUID resourceId);

    /**
     * Find audit entries within a time range for a specific actor.
     */
    @Query("SELECT e FROM ApiAuditEntry e WHERE e.actorId = :actorId " +
            "AND e.createdAt >= :from AND e.createdAt <= :to ORDER BY e.createdAt DESC")
    List<ApiAuditEntry> findByActorIdAndTimeRange(
            @Param("actorId") UUID actorId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Find audit entries by request ID (for correlating with error responses).
     */
    List<ApiAuditEntry> findByRequestId(UUID requestId);
}
