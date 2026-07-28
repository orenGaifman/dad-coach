package com.dadcoach.channel.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for DeliveryRecord entities.
 * Provides methods to look up delivery records by provider_message_id
 * for correlating webhook status updates to internal messages.
 */
@Repository
public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {

    /**
     * Finds a delivery record by the provider-assigned message identifier.
     * Used to correlate incoming status webhook updates to internal messages.
     *
     * @param providerMessageId the provider-specific message ID (e.g., WhatsApp wamid.xxx)
     * @return the delivery record if found
     */
    Optional<DeliveryRecord> findByProviderMessageId(String providerMessageId);

    /**
     * Finds a delivery record by the internal message identifier.
     *
     * @param messageId the internal message UUID
     * @return the delivery record if found
     */
    Optional<DeliveryRecord> findByMessageId(UUID messageId);
}
