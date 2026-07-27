package com.dadcoach.statemachine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the append-only state transition audit log.
 */
@Repository
public interface StateTransitionLogRepository extends JpaRepository<StateTransitionLog, Long> {

    /**
     * Find all transition logs for a specific entity, ordered by most recent first.
     */
    List<StateTransitionLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId);

    /**
     * Find all transition logs for a specific entity type.
     */
    List<StateTransitionLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}
