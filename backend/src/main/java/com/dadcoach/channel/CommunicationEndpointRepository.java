package com.dadcoach.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CommunicationEndpoint} entities.
 * Provides queries for endpoint resolution by father and channel identity.
 */
@Repository
public interface CommunicationEndpointRepository extends JpaRepository<CommunicationEndpoint, UUID> {

    /**
     * Find the primary communication endpoint for a given father.
     * Used for outbound message routing when no specific channel is requested.
     */
    @Query("SELECT e FROM CommunicationEndpoint e WHERE e.fatherId = :fatherId AND e.primary = true")
    Optional<CommunicationEndpoint> findPrimaryByFatherId(@Param("fatherId") UUID fatherId);

    /**
     * Find an endpoint by channel and channel identity.
     * Used for inbound message resolution (e.g., looking up a father by WhatsApp phone number).
     */
    Optional<CommunicationEndpoint> findByChannelAndChannelIdentity(String channel, String channelIdentity);

    /**
     * Find all endpoints for a given father.
     * A father may have multiple endpoints across different channels.
     */
    List<CommunicationEndpoint> findByFatherId(UUID fatherId);

    /**
     * Delete all communication endpoints for a given father.
     * Used when deleting a father account.
     */
    void deleteByFatherId(UUID fatherId);
}
