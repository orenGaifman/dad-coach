package com.dadcoach.workspace.growth.milestone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Milestone} definition entities.
 *
 * <p>Provides queries for retrieving milestone definitions.</p>
 *
 * @see Milestone
 */
@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
}
