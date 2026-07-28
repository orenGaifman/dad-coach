package com.dadcoach.onboarding.provisioning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LanguagePreference} entities.
 */
@Repository
public interface LanguagePreferenceRepository extends JpaRepository<LanguagePreference, UUID> {

    Optional<LanguagePreference> findByFatherId(UUID fatherId);
}
