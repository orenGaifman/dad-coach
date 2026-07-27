package com.dadcoach.domain.reflection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Reflection} entities.
 *
 * <p>Indexes leveraged:</p>
 * <ul>
 *   <li>idx_reflection_father ON reflection(father_id)</li>
 * </ul>
 */
@Repository
public interface ReflectionRepository extends JpaRepository<Reflection, Long> {

    /**
     * Find all reflections for a father ordered by creation time descending.
     *
     * @param fatherId the father ID
     * @return list of reflections
     */
    List<Reflection> findByFatherIdOrderByCreatedAtDesc(Long fatherId);

    /**
     * Count reflections for a father within a given time window.
     * Used to enforce the max-1-per-day business rule (Property 35).
     *
     * @param fatherId the father ID
     * @param start    the start of the time window (inclusive)
     * @param end      the end of the time window (exclusive)
     * @return the number of reflections in the window
     */
    @Query("SELECT COUNT(r) FROM Reflection r WHERE r.fatherId = :fatherId " +
           "AND r.createdAt >= :start AND r.createdAt < :end")
    int countByFatherIdAndCreatedAtBetween(@Param("fatherId") Long fatherId,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end);

    /**
     * Find reflections for a father within a given time window.
     *
     * @param fatherId the father ID
     * @param start    the start of the time window (inclusive)
     * @param end      the end of the time window (exclusive)
     * @return list of reflections in the window
     */
    @Query("SELECT r FROM Reflection r WHERE r.fatherId = :fatherId " +
           "AND r.createdAt >= :start AND r.createdAt < :end")
    List<Reflection> findByFatherIdAndCreatedAtBetween(@Param("fatherId") Long fatherId,
                                                       @Param("start") Instant start,
                                                       @Param("end") Instant end);
}
