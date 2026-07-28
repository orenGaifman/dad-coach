package com.dadcoach.workspace.growth.milestone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FatherMilestone} reached record entities.
 *
 * <p>Provides queries for tracking which milestones a father has reached,
 * checking for existing records, and counting reached milestones.</p>
 *
 * @see FatherMilestone
 */
@Repository
public interface FatherMilestoneRepository extends JpaRepository<FatherMilestone, UUID> {

    /**
     * Finds all reached milestones for a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return list of reached milestone records
     */
    List<FatherMilestone> findByFatherId(UUID fatherId);

    /**
     * Counts the number of milestones reached by a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return the count of reached milestones
     */
    long countByFatherId(UUID fatherId);

    /**
     * Checks whether a specific father has already reached a specific milestone.
     * Used for idempotent milestone tracking — prevents duplicate records.
     *
     * @param fatherId    the father's unique identifier
     * @param milestoneId the milestone's unique identifier
     * @return true if the father has already reached this milestone
     */
    boolean existsByFatherIdAndMilestoneId(UUID fatherId, UUID milestoneId);
}
