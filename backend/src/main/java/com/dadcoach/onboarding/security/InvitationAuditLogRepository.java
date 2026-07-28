package com.dadcoach.onboarding.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for invitation audit log entries.
 */
@Repository
public interface InvitationAuditLogRepository extends JpaRepository<InvitationAuditLog, UUID> {

    /**
     * Finds all audit log entries for a given token hash.
     */
    List<InvitationAuditLog> findByTokenHash(String tokenHash);

    /**
     * Finds all audit log entries from a given IP address.
     */
    List<InvitationAuditLog> findByIpAddress(String ipAddress);

    /**
     * Deletes audit log entries older than the given cutoff (90-day retention).
     */
    @Modifying
    @Query("DELETE FROM InvitationAuditLog l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
