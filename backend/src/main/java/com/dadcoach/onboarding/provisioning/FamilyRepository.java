package com.dadcoach.onboarding.provisioning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Family} entities.
 */
@Repository
public interface FamilyRepository extends JpaRepository<Family, UUID> {

    /**
     * Find the family for a given father.
     */
    Optional<Family> findByFatherId(UUID fatherId);
}
