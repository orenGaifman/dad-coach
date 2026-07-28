package com.dadcoach.onboarding.provisioning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ActivationRecord} entities.
 */
@Repository
public interface ActivationRecordRepository extends JpaRepository<ActivationRecord, UUID> {

    /**
     * Find the activation record for a given father.
     */
    Optional<ActivationRecord> findByFatherId(UUID fatherId);

    /**
     * Find the activation record for a given session.
     */
    Optional<ActivationRecord> findBySessionId(UUID sessionId);

    /**
     * Find activation records that have timed out:
     * - LINK_CLICKED for more than 30 minutes
     * - PENDING for more than 24 hours
     *
     * @param linkClickedCutoff activations in LINK_CLICKED before this time are timed out (now - 30min)
     * @param pendingCutoff     activations in PENDING before this time are timed out (now - 24h)
     * @return list of timed-out activation records
     */
    @Query("SELECT a FROM ActivationRecord a WHERE " +
           "(a.status = 'LINK_CLICKED' AND a.linkClickedAt < :linkClickedCutoff) OR " +
           "(a.status = 'PENDING' AND a.deepLinkGeneratedAt < :pendingCutoff)")
    List<ActivationRecord> findTimedOutActivations(
            @Param("linkClickedCutoff") Instant linkClickedCutoff,
            @Param("pendingCutoff") Instant pendingCutoff);
}
