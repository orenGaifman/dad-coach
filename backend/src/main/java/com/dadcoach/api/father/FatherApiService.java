package com.dadcoach.api.father;

import com.dadcoach.domain.father.Father;

import java.util.UUID;

/**
 * Service interface for Father API operations.
 * <p>
 * The controller delegates all business logic to this service layer.
 * The controller NEVER accesses the database directly.
 */
public interface FatherApiService {

    /**
     * Retrieves the father's profile by actor ID.
     *
     * @param actorId the authenticated actor's UUID
     * @return the Father entity
     */
    Father getProfile(UUID actorId);

    /**
     * Updates the father's preferences.
     *
     * @param actorId the authenticated actor's UUID
     * @param request the update request containing fields to change
     * @return the updated Father entity
     */
    Father updatePreferences(UUID actorId, FatherUpdateRequest request);

    /**
     * Initiates a GDPR account deletion request.
     * <p>
     * This does NOT immediately delete the account. It triggers the deletion flow
     * which includes a grace period and data purge according to GDPR requirements.
     *
     * @param actorId the authenticated actor's UUID
     */
    void requestDeletion(UUID actorId);
}
