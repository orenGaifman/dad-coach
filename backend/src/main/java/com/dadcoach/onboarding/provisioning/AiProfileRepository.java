package com.dadcoach.onboarding.provisioning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AiProfile} entities.
 */
@Repository
public interface AiProfileRepository extends JpaRepository<AiProfile, UUID> {

    /**
     * Find the AI profile for a given father.
     */
    Optional<AiProfile> findByFatherId(Long fatherId);
}
