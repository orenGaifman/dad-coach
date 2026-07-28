package com.dadcoach.channel.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for media asset persistence.
 * Provides queries for retrieval by message and cleanup of expired assets.
 */
@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    /**
     * Find all media assets for a given message.
     */
    List<MediaAsset> findByMessageId(UUID messageId);

    /**
     * Find all media assets for a given father.
     */
    List<MediaAsset> findByFatherId(UUID fatherId);

    /**
     * Find all expired media assets (expires_at before the given cutoff time).
     */
    List<MediaAsset> findByExpiresAtBefore(Instant cutoff);

    /**
     * Delete all expired media assets in bulk.
     * Returns the number of deleted records.
     */
    @Modifying
    @Query("DELETE FROM MediaAsset m WHERE m.expiresAt < :cutoff")
    int deleteExpiredAssets(@Param("cutoff") Instant cutoff);
}
