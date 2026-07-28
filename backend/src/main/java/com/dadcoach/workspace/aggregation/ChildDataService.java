package com.dadcoach.workspace.aggregation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for reading child data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Child domain
 * entity and its persistence layer. Implementations should read from the Child
 * repository and map to {@link ChildReadModel}.</p>
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public interface ChildDataService {

    /**
     * Retrieves all children belonging to a specific father.
     *
     * @param fatherId the father's unique identifier
     * @return list of child read models (may be empty)
     */
    List<ChildReadModel> getChildrenByFatherId(UUID fatherId);

    /**
     * Retrieves a specific child by ID.
     *
     * @param childId the child's unique identifier
     * @return the child read model, or empty if not found
     */
    Optional<ChildReadModel> getChild(UUID childId);

    /**
     * Checks whether a child belongs to the specified father.
     *
     * @param fatherId the father's unique identifier
     * @param childId  the child's unique identifier
     * @return true if the child belongs to the father
     */
    boolean childBelongsToFather(UUID fatherId, UUID childId);
}
