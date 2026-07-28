package com.dadcoach.workspace.aggregation;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for reading father data from the domain layer.
 *
 * <p>This interface decouples the workspace read aggregation from the Father domain
 * entity and its persistence layer. Implementations should read from the Father
 * repository and map to {@link FatherReadModel}.</p>
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public interface FatherDataService {

    /**
     * Retrieves the father's read model by ID.
     *
     * @param fatherId the father's unique identifier
     * @return the father read model, or empty if not found
     */
    Optional<FatherReadModel> getFather(UUID fatherId);
}
