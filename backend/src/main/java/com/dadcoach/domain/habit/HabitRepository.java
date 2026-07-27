package com.dadcoach.domain.habit;

import com.dadcoach.common.HabitStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Habit} entities.
 */
@Repository
public interface HabitRepository extends JpaRepository<Habit, Long> {

    /**
     * Find all habits for a father with a specific status.
     * Used to retrieve active habits (enforces max 5 active habits per father).
     */
    List<Habit> findByFatherIdAndStatus(Long fatherId, HabitStatus status);

    /**
     * Count the number of habits for a given father with a specific status.
     * Used to enforce the max-5-active-habits-per-father business rule.
     */
    long countByFatherIdAndStatus(Long fatherId, HabitStatus status);
}
