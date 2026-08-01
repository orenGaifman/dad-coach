package com.dadcoach.onboarding.provisioning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CommunicationPreference} entities.
 */
@Repository
public interface CommunicationPreferenceRepository extends JpaRepository<CommunicationPreference, UUID> {

    Optional<CommunicationPreference> findByFatherId(UUID fatherId);

    /**
     * Delete the communication preference for a given father.
     * Used when deleting a father account.
     */
    void deleteByFatherId(UUID fatherId);
}
