package com.dadcoach.domain.child;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Child} entities.
 */
@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {

    /**
     * Find all children belonging to a given father.
     */
    List<Child> findByFatherId(Long fatherId);

    /**
     * Find all children belonging to a given father with a specific status.
     * @param fatherId the father's internal database ID
     * @param status the child status (e.g., "ACTIVE")
     * @return list of children matching the criteria
     */
    List<Child> findByFatherIdAndStatus(Long fatherId, String status);

    /**
     * Count the number of active children for a given father.
     * Used to enforce the max-8-children-per-father business rule.
     */
    @Query("SELECT COUNT(c) FROM Child c WHERE c.fatherId = :fatherId AND c.status = 'ACTIVE'")
    long countActiveByFatherId(@Param("fatherId") Long fatherId);

    /**
     * Delete all children belonging to a given father.
     * Used when deleting a father account.
     */
    void deleteByFatherId(Long fatherId);
}
