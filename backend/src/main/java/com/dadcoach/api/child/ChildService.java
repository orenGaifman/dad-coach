package com.dadcoach.api.child;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Child domain operations.
 * <p>
 * The API controller delegates all business logic to this service.
 * The implementation is provided by the domain layer (SPEC-002).
 * Controllers never perform direct DB writes.
 */
public interface ChildService {

    /**
     * Creates a new child for the given father.
     *
     * @param fatherId the owning father's ID
     * @param request  the validated creation request
     * @return the created child response DTO
     * @throws com.dadcoach.api.error.LimitExceededException if father already has 8 children
     */
    ChildResponseDto createChild(UUID fatherId, ChildCreateRequest request);

    /**
     * Lists all active children for the given father.
     *
     * @param fatherId the owning father's ID
     * @return list of child response DTOs
     */
    List<ChildResponseDto> listChildren(UUID fatherId);

    /**
     * Finds a child by ID.
     *
     * @param childId the child's ID
     * @return the child response DTO, or empty if not found
     */
    Optional<ChildResponseDto> findById(UUID childId);

    /**
     * Updates a child's details.
     *
     * @param childId the child's ID
     * @param request the validated update request
     * @return the updated child response DTO
     * @throws com.dadcoach.api.error.ResourceNotFoundException if child not found
     */
    ChildResponseDto updateChild(UUID childId, ChildCreateRequest request);

    /**
     * Soft-deletes (archives) a child.
     *
     * @param childId the child's ID
     * @throws com.dadcoach.api.error.ResourceNotFoundException if child not found
     */
    void deleteChild(UUID childId);

    /**
     * Returns the count of active children for a given father.
     *
     * @param fatherId the father's ID
     * @return count of active children
     */
    int countActiveChildren(UUID fatherId);

    /**
     * Returns the father ID that owns the given child.
     *
     * @param childId the child's ID
     * @return the owning father's ID, or empty if child not found
     */
    Optional<UUID> getOwnerFatherId(UUID childId);
}
